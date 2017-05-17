/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.Feature;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.QueryShardException;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptType;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_REF;

public class StoredFeature implements Feature, Accountable {
    private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(StoredFeature.class);
    private static final String DEFAULT_TEMPLATE_LANGUAGE = "mustache";
    private final String name;
    private final Collection<String> queryParams;
    private final String templateLanguage;
    private final String template;

    private static final ObjectParser<ParsingState, Void> PARSER;

    static {
        PARSER = new ObjectParser<>("ltr_feature", ParsingState::new);
        PARSER.declareString(ParsingState::setName, new ParseField("name"));
        PARSER.declareStringArray(ParsingState::setQueryParams, new ParseField("params"));
        PARSER.declareString(ParsingState::setTemplateLanguage, new ParseField("template_language"));
        PARSER.declareField(ParsingState::setTemplate, (parser, value) -> {
            if (parser.currentToken() == XContentParser.Token.START_OBJECT) {
                try (XContentBuilder builder = XContentFactory.contentBuilder(parser.contentType())) {
                    return builder.copyCurrentStructure(parser).bytes().utf8ToString();
                } catch (IOException e) {
                    throw new ParsingException(parser.getTokenLocation(), "Could not parse inline template", e);
                }
            } else {
                return parser.text();
            }
        }, new ParseField("template"), ObjectParser.ValueType.OBJECT_OR_STRING);
    }

    public StoredFeature(String name, Collection<String> params, String templateLanguage, String template) {
        this.name = Objects.requireNonNull(name);
        this.queryParams = Objects.requireNonNull(params);
        this.templateLanguage = Objects.requireNonNull(templateLanguage);
        this.template = Objects.requireNonNull(template);
    }

    public static StoredFeature parse(XContentParser parser) {
        try {
            ParsingState state = PARSER.apply(parser, null);
            if (state.name == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [name] is mandatory");
            }
            if (state.queryParams == null) {
                state.queryParams = Collections.emptyList();
            }
            if (state.template == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [template] is mandatory");
            }
            return new StoredFeature(state.name, Collections.unmodifiableCollection(state.queryParams),
                    state.templateLanguage, state.template);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Query doToQuery(QueryShardContext context, Map<String, Object> params) {
        boolean missingParam = queryParams.stream().anyMatch(x -> !params.containsKey(x));
        if (missingParam) {
            // Is it sane to do this?
            // should we fail?
            return new MatchNoDocsQuery();
        }
        ExecutableScript script = context.getExecutableScript(new Script(ScriptType.INLINE,
                templateLanguage, template, params), ScriptContext.Standard.SEARCH);
        Object source = script.run();

        try {
            XContentParser parser = createParser(source, context.getXContentRegistry());
            QueryParseContext parserContext = context.newParseContext(parser);
            QueryBuilder queryBuilder = parserContext.parseInnerQueryBuilder().orElseThrow(
                    () -> new ParsingException(parser.getTokenLocation(), "ltr inner query cannot be empty"));
            // XXX: QueryShardContext extends QueryRewriteContext (for now)
            return QueryBuilder.rewriteQuery(queryBuilder, context).toQuery(context);
        } catch (IOException|ParsingException|IllegalArgumentException e) {
            // wrap common exceptions as well so we can attach the feature's name to the stack
            throw new QueryShardException(context, "Cannot create query while parsing feature [" + name +"]", e);
        }
    }

    private XContentParser createParser(Object source, NamedXContentRegistry registry) throws IOException {
        if (source instanceof String) {
            return XContentFactory.xContent((String) source).createParser(registry, (String) source);
        } else if (source instanceof BytesReference) {
            return XContentFactory.xContent((BytesReference) source).createParser(registry, (BytesReference) source);
        } else if (source instanceof byte[]) {
            return XContentFactory.xContent((byte[]) source).createParser(registry, (byte[]) source);
        } else {
            throw new IllegalArgumentException("Template engine returned an unsupported object type [" +
                    source.getClass().getCanonicalName() + "]");
        }
    }

    protected Collection<String> queryParams() {
        return queryParams;
    }

    protected String templateLanguage() {
        return templateLanguage;
    }

    protected String template() {
        return template;
    }

    @Override
    public long ramBytesUsed() {
        // rough estimation...
        return BASE_RAM_USED +
                (Character.BYTES * name.length()) + NUM_BYTES_ARRAY_HEADER +
                queryParams.stream()
                        .mapToLong(x -> (Character.BYTES * x.length()) +
                                NUM_BYTES_OBJECT_REF + NUM_BYTES_OBJECT_HEADER + NUM_BYTES_ARRAY_HEADER).sum() +
                (Character.BYTES * templateLanguage.length()) + NUM_BYTES_ARRAY_HEADER +
                (Character.BYTES * template.length()) + NUM_BYTES_ARRAY_HEADER;
    }

    private static class ParsingState {
        private String name;
        private Collection<String> queryParams;
        private String templateLanguage = DEFAULT_TEMPLATE_LANGUAGE;

        private String template;
        public void setName(String name) {
            this.name = name;
        }

        void setQueryParams(Collection<String> queryParams) {
            this.queryParams = queryParams;
        }

        public void setTemplateLanguage(String templateLanguage) {
            this.templateLanguage = templateLanguage;
        }

        void setTemplate(String template) {
            this.template = template;
        }
    }
}
