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

import com.github.mustachejava.Mustache;
import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.template.mustache.MustacheUtils;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardException;
import org.elasticsearch.index.query.Rewriteable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_REF;
import static org.elasticsearch.index.query.AbstractQueryBuilder.parseInnerQueryBuilder;

public class PrecompiledTemplateFeature implements Feature, Accountable {
    private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(StoredFeature.class);

    private final String name;
    private final Mustache template;
    private final String templateString;
    private final Collection<String> queryParams;

    private PrecompiledTemplateFeature(String name, Mustache template, String templateString, Collection<String> queryParams) {
        this.name = name;
        this.template = template;
        this.queryParams = queryParams;
        this.templateString = templateString;
    }

    public static PrecompiledTemplateFeature compile(StoredFeature feature) {
        assert MustacheUtils.TEMPLATE_LANGUAGE.equals(feature.templateLanguage());
        Mustache mustache = MustacheUtils.compile(feature.name(), feature.template());
        // TODO: figure out if we can inspect mustache to assert that feature.queryParams is valid
        return new PrecompiledTemplateFeature(feature.name(), mustache, feature.template(), feature.queryParams());
    }

    @Override
    public long ramBytesUsed() {
        return BASE_RAM_USED +
                (Character.BYTES * name.length()) + NUM_BYTES_ARRAY_HEADER +
                queryParams.stream()
                        .mapToLong(x -> (Character.BYTES * x.length()) +
                                NUM_BYTES_OBJECT_REF + NUM_BYTES_OBJECT_HEADER + NUM_BYTES_ARRAY_HEADER).sum() +
                (((Character.BYTES * templateString.length()) + NUM_BYTES_ARRAY_HEADER) * 2);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Query doToQuery(LtrQueryContext context, FeatureSet set, Map<String, Object> params) {
        List<String> missingParams = queryParams.stream()
                .filter((x) -> params == null || !params.containsKey(x))
                .collect(Collectors.toList());
        if (!missingParams.isEmpty()) {
            String names = missingParams.stream().collect(Collectors.joining(","));
            throw new IllegalArgumentException("Missing required param(s): [" + names + "]");
        }

        String query = MustacheUtils.execute(template, params);
        try {
            XContentParser parser = XContentFactory.xContent(query)
                    .createParser(context.getSearchExecutionContext().getXContentRegistry(),
                            LoggingDeprecationHandler.INSTANCE, query);
            QueryBuilder queryBuilder = parseInnerQueryBuilder(parser);
            // XXX: QueryShardContext extends QueryRewriteContext (for now)
            return Rewriteable.rewrite(queryBuilder, context.getSearchExecutionContext()).toQuery(context.getSearchExecutionContext());
        } catch (IOException | ParsingException | IllegalArgumentException e) {
            // wrap common exceptions as well so we can attach the feature's name to the stack
            throw new QueryShardException(context.getSearchExecutionContext(),
                    "Cannot create query while parsing feature [" + name + "]", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PrecompiledTemplateFeature that = (PrecompiledTemplateFeature) o;

        if (!name.equals(that.name)) {
            return false;
        }
        if (!templateString.equals(that.templateString)) {
            return false;
        }
        return queryParams.equals(that.queryParams);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + templateString.hashCode();
        result = 31 * result + queryParams.hashCode();
        return result;
    }
}
