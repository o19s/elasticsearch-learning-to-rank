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
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.template.mustache.MustacheUtils;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_REF;

public class StoredFeature implements Feature, Accountable, StorableElement {
    private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(StoredFeature.class);
    private static final String DEFAULT_TEMPLATE_LANGUAGE = MustacheUtils.TEMPLATE_LANGUAGE;
    public static final String TYPE = "feature";
    private final String name;
    private final List<String> queryParams;
    private final String templateLanguage;
    private final String template;
    private final boolean templateAsString;

    private static final ObjectParser<ParsingState, Void> PARSER;

    private static final ParseField NAME = new ParseField("name");
    private static final ParseField PARAMS = new ParseField("params");
    private static final ParseField TEMPLATE_LANGUAGE = new ParseField("template_language");
    public static final ParseField TEMPLATE = new ParseField("template");

    static {
        PARSER = new ObjectParser<>(TYPE, ParsingState::new);
        PARSER.declareString(ParsingState::setName, NAME);
        PARSER.declareStringArray(ParsingState::setQueryParams, PARAMS);
        PARSER.declareString(ParsingState::setTemplateLanguage, TEMPLATE_LANGUAGE);
        PARSER.declareField(ParsingState::setTemplate, (parser, value) -> {
            if (parser.currentToken() == XContentParser.Token.START_OBJECT) {
                // Force json
                try (XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON)) {
                    return builder.copyCurrentStructure(parser);
                } catch (IOException e) {
                    throw new ParsingException(parser.getTokenLocation(), "Could not parse inline template", e);
                }
            } else {
                return parser.text();
            }
        }, TEMPLATE, ObjectParser.ValueType.OBJECT_OR_STRING);
    }

    public StoredFeature(String name, List<String> params, String templateLanguage, String template, boolean storedAsString) {
        this.name = Objects.requireNonNull(name);
        this.queryParams = Objects.requireNonNull(params);
        this.templateLanguage = Objects.requireNonNull(templateLanguage);
        this.template = Objects.requireNonNull(template);
        this.templateAsString = storedAsString;
    }

    public StoredFeature(StreamInput input) throws IOException {
        name = input.readString();
        queryParams = input.readList(StreamInput::readString);
        templateLanguage = input.readString();
        template = input.readString();
        templateAsString = input.readBoolean();
    }

    public StoredFeature(String name, List<String> params, String templateLanguage, String template) {
        this(name, params, templateLanguage, template, true);
    }

    public StoredFeature(String name, List<String> params, String templateLanguage, XContentBuilder template) {
        this(name, params, templateLanguage, Objects.requireNonNull(template).bytes().utf8ToString(), false);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeStringList(queryParams);
        out.writeString(templateLanguage);
        out.writeString(template);
        out.writeBoolean(templateAsString);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME.getPreferredName(), name);
        builder.field(PARAMS.getPreferredName(), queryParams);
        builder.field(TEMPLATE_LANGUAGE.getPreferredName(), templateLanguage);
        if (templateAsString) {
            builder.field(TEMPLATE.getPreferredName(), template);
        } else {
            builder.field(TEMPLATE.getPreferredName());
            // it's ok to use NamedXContentRegistry.EMPTY because we don't really parse we copy the structure...
            XContentParser parser = XContentFactory.xContent(template).createParser(NamedXContentRegistry.EMPTY, template);
            builder.copyCurrentStructure(parser);
        }
        builder.endObject();
        return builder;
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
            if (state.template instanceof String) {
                return new StoredFeature(state.name, Collections.unmodifiableList(state.queryParams),
                        state.templateLanguage, (String) state.template);
            } else {
                assert state.template instanceof XContentBuilder;
                return new StoredFeature(state.name, Collections.unmodifiableList(state.queryParams),
                        state.templateLanguage, (XContentBuilder) state.template);
            }
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    @Override
    public Feature optimize() {
        switch(templateLanguage) {
            case MustacheUtils.TEMPLATE_LANGUAGE:
                return PrecompiledTemplateFeature.compile(this);
            case PrecompiledExpressionFeature.TEMPLATE_LANGUAGE:
                return PrecompiledExpressionFeature.compile(this);
            default:
                return this;
        }
    }

    @Override
    public String name() {
        return name;
    }

    public String type() {
        return TYPE;
    }

    @Override
    public Query doToQuery(QueryShardContext context, FeatureSet set, Map<String, Object> params) {
        List<String> missingParams = queryParams.stream()
                .filter((x) -> params == null || !params.containsKey(x))
                .collect(Collectors.toList());
        if (!missingParams.isEmpty()) {
            String names = missingParams.stream().collect(Collectors.joining(","));
            throw new IllegalArgumentException("Missing required param(s): [" + names + "]");
        }

        // mustache templates must be optimized
        assert !DEFAULT_TEMPLATE_LANGUAGE.equals(templateLanguage);
        // XXX: we hope that in most case users will use mustache that is embedded in the plugin
        // compiling the template from the script engine may hit a circuit breaker
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

    Collection<String> queryParams() {
        return queryParams;
    }

    String templateLanguage() {
        return templateLanguage;
    }

    String template() {
        return template;
    }

    boolean templateAsString() {
        return templateAsString;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoredFeature)) return false;

        StoredFeature feature = (StoredFeature) o;
        if (templateAsString != feature.templateAsString) return false;
        if (!name.equals(feature.name)) return false;
        if (!queryParams.equals(feature.queryParams)) return false;
        if (!templateLanguage.equals(feature.templateLanguage)) return false;
        return template.equals(feature.template);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + queryParams.hashCode();
        result = 31 * result + templateLanguage.hashCode();
        result = 31 * result + template.hashCode();
        result = 31 * result + (templateAsString ? 1 : 0);
        return result;
    }

    private static class ParsingState {
        private String name;
        private List<String> queryParams;
        private String templateLanguage = DEFAULT_TEMPLATE_LANGUAGE;
        private Object template;

        public void setName(String name) {
            this.name = name;
        }

        void setQueryParams(List<String> queryParams) {
            this.queryParams = queryParams;
        }

        void setTemplateLanguage(String templateLanguage) {
            this.templateLanguage = templateLanguage;
        }

        void setTemplate(Object template) {
            assert template instanceof String || template instanceof XContentBuilder;
            this.template = template;
        }
    }
}
