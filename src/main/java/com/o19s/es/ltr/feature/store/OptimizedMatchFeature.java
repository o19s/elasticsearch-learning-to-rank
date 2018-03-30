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

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Accountable;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryShardException;
import org.elasticsearch.index.query.support.QueryParsers;
import org.elasticsearch.index.search.MatchQuery;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.shallowSizeOfInstance;
import static org.elasticsearch.common.lucene.search.Queries.newUnmappedFieldQuery;

public class OptimizedMatchFeature implements Feature, Accountable {
    public static final String TEMPLATE_LANGUAGE = "optimized_match";
    private static final long BASE_RAM_USED = shallowSizeOfInstance(OptimizedMatchFeature.class)
            + shallowSizeOfInstance(MatchQueryBuilder.class);
    private static final String CACHED_TOKEN_STREAMS_KEY = "_ltrcachedtokenstreams_";

    private static final Pattern EXTRACT_FIELD = Pattern.compile("^\\Q{{\\E(.*)\\Q}}\\E$");
    private final String name;
    private final String queryParam;
    private final MatchQueryBuilder builder;

    public static OptimizedMatchFeature build(String name, String template) {
        MatchQueryBuilder builder;
        try (XContentParser parser = XContentFactory.xContent(template)
                .createParser(NamedXContentRegistry.EMPTY, template)) {
            ;
            if (parser.nextToken() != XContentParser.Token.START_OBJECT ) {
                throw new ParsingException( parser.getTokenLocation(), "Expected START_OBJECT" );
            }
            if (parser.nextToken() != XContentParser.Token.FIELD_NAME ) {
                throw new ParsingException( parser.getTokenLocation(), "Expected FIELD_NAME" );
            }
            if (!MatchQueryBuilder.NAME.equals(parser.currentName())) {
                throw new ParsingException( parser.getTokenLocation(), "Expected a field named [" + MatchQueryBuilder.NAME + "]" );
            }
            if (parser.nextToken() != XContentParser.Token.START_OBJECT ) {
                throw new ParsingException( parser.getTokenLocation(), "Expected START_OBJECT" );
            }
            builder = MatchQueryBuilder.fromXContent(parser);
        } catch(IOException ioe) {
            throw new IllegalArgumentException("Cannot parse optimized_match feature [" + name + "]", ioe);
        }
        String value = builder.value().toString();
        Matcher m = EXTRACT_FIELD.matcher(value);
        if (!m.find()) {
            throw new IllegalArgumentException( "Cannot parse optimized_match parameter name [" + value + "]");
        }
        String paramName = m.group(1);
        return new OptimizedMatchFeature(name, builder, paramName);
    }

    OptimizedMatchFeature(String name, MatchQueryBuilder builder, String queryParam) {
        this.name = Objects.requireNonNull(name);
        this.builder = builder;
        this.queryParam = queryParam;
    }

    /**
     * The feature name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Transform this feature into a lucene query
     */
    @Override
    public Query doToQuery(LtrQueryContext context, FeatureSet set, Map<String, Object> params) {
        NamedAnalyzer analyzer;
        if (this.builder.analyzer() != null) {
            analyzer = context.getQueryShardContext().getIndexAnalyzers().get(this.builder.analyzer());
            throw new QueryShardException(context.getQueryShardContext(), "Feature ["+ this.name +"] [" + MatchQueryBuilder.NAME + "]" +
                    " analyzer [" + analyzer + "] not found");
        } else {
            MappedFieldType fieldType = context.getQueryShardContext().fieldMapper(builder.fieldName());
            if (fieldType == null) {
                return newUnmappedFieldQuery(builder.fieldName());
            }
            analyzer = fieldType.searchAnalyzer();
        }
        CachedTokenStreams cache = context.getCachedTokenStreams();

        Object oquery = params.get(this.queryParam);

        if (oquery == null) {
            throw new IllegalArgumentException("Missing required param: [" + queryParam + "]");
        }
        Analyzer cachedAnalyzer = cache.get(analyzer, oquery.toString());
        MatchQuery matchQuery = new MatchQuery(context.getQueryShardContext());
        matchQuery.setOccur(builder.operator().toBooleanClauseOccur());
        matchQuery.setAnalyzer(cachedAnalyzer);
        matchQuery.setFuzziness(builder.fuzziness());
        matchQuery.setFuzzyPrefixLength(builder.prefixLength());
        matchQuery.setMaxExpansions(builder.maxExpansions());
        matchQuery.setTranspositions(builder.fuzzyTranspositions());
        matchQuery.setFuzzyRewriteMethod(QueryParsers.parseRewriteMethod(builder.fuzzyRewrite(), null));
        matchQuery.setLenient(builder.lenient());
        matchQuery.setCommonTermsCutoff(builder.cutoffFrequency());
        matchQuery.setZeroTermsQuery(builder.zeroTermsQuery());
        matchQuery.setAutoGenerateSynonymsPhraseQuery(builder.autoGenerateSynonymsPhraseQuery());

        Query query = null;
        try {
            query = matchQuery.parse(MatchQuery.Type.BOOLEAN, builder.fieldName(), oquery.toString());
        } catch (IOException e) {
            throw new QueryShardException(context.getQueryShardContext(), "Cannot create query while parsing feature [" + name +"]", e);
        }
        return Queries.maybeApplyMinimumShouldMatch(query, builder.minimumShouldMatch());

    }

    /**
     * Return the memory usage of this object in bytes. Negative values are illegal.
     */
    @Override
    public long ramBytesUsed() {
        return BASE_RAM_USED +
                (Character.BYTES * name.length()) + NUM_BYTES_ARRAY_HEADER +
                (Character.BYTES * queryParam.length()) + NUM_BYTES_ARRAY_HEADER;
    }
}
