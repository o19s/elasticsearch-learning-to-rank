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

package com.o19s.es.ltr.query;

import com.o19s.es.ltr.LtrQueryParserPlugin;
import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.MemStore;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import com.o19s.es.ltr.utils.FeatureStoreLoader;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.junit.Before;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;

public class StoredLtrQueryBuilderTests extends AbstractQueryTestCase<StoredLtrQueryBuilder> {
    private static final MemStore store = new MemStore();

    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Arrays.asList(TestPlugin.class);
    }

    /**
     * Returns a set of object names that won't trigger any exception (uncluding their children) when testing that unknown
     * objects cause parse exceptions through {@link #testUnknownObjectException()}. Default is an empty set. Can be overridden
     * by subclasses that test queries which contain objects that get parsed on the data nodes (e.g. score functions) or objects
     * that can contain arbitrary content (e.g. documents for percolate or more like this query, params for scripts). In such
     * cases no exception would get thrown.
     */
    @Override
    protected Set<String> getObjectsHoldingArbitraryContent() {
        return Collections.singletonMap(StoredLtrQueryBuilder.PARAMS.getPreferredName(), null).keySet();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        store.clear();
        StoredFeature feature1 = new StoredFeature("match1", Collections.singletonList("query_string"),
                "mustache",
                new MatchQueryBuilder("field1", "{{query_string}}").toString());
        StoredFeature feature2 = new StoredFeature("match2", Collections.singletonList("query_string"),
                "mustache",
                new MatchQueryBuilder("field2", "{{query_string}}").toString());
        StoredFeature feature3 = new StoredFeature("score3", Collections.emptyList(),
                "mustache",
                new FunctionScoreQueryBuilder(new FieldValueFactorFunctionBuilder("scorefield2")
                        .factor(1.2F)
                        .modifier(FieldValueFactorFunction.Modifier.LN2P)
                        .missing(0F)).toString());
        StoredFeatureSet set = new StoredFeatureSet("set1", Arrays.asList(feature1, feature2, feature3));
        store.add(set);
        LtrRanker ranker = new LinearRanker(new float[]{0.1F, 0.2F, 0.3F});
        CompiledLtrModel model = new CompiledLtrModel("model1", set, ranker);
        store.add(model);
    }

    /**
     * Create the query that is being tested
     */
    @Override
    protected StoredLtrQueryBuilder doCreateTestQueryBuilder() {
        StoredLtrQueryBuilder builder = new StoredLtrQueryBuilder(LtrTestUtils.wrapMemStore(store));
        if (random().nextBoolean()) {
            builder.modelName("model1");
        } else {
            builder.featureSetName("set1");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("query_string", "a wonderful query");
        builder.params(params);
        return builder;
    }

    public void testMissingParams() {
        StoredLtrQueryBuilder builder = new StoredLtrQueryBuilder(LtrTestUtils.wrapMemStore(StoredLtrQueryBuilderTests.store));
        builder.modelName("model1");

        assertThat(expectThrows(IllegalArgumentException.class, () -> builder.toQuery(createShardContext())).getMessage(),
                equalTo("Missing required param(s): [query_string]"));

        Map<String, Object> params = new HashMap<>();
        params.put("query_string2", "a wonderful query");
        builder.params(params);
        assertThat(expectThrows(IllegalArgumentException.class, () -> builder.toQuery(createShardContext())).getMessage(),
                equalTo("Missing required param(s): [query_string]"));

    }

    @Override
    protected void doAssertLuceneQuery(StoredLtrQueryBuilder queryBuilder,
                                       Query query, SearchContext context) throws IOException {
        assertThat(query, instanceOf(RankerQuery.class));
        RankerQuery rquery = (RankerQuery) query;
        Iterator<Query> ite = rquery.stream().iterator();

        assertTrue(ite.hasNext());
        Query featureQuery = ite.next();
        QueryBuilder builder = new MatchQueryBuilder("field1", queryBuilder.params().get("query_string"));
        QueryShardContext qcontext = createShardContext();
        Query expected = QueryBuilder.rewriteQuery(builder, qcontext).toQuery(qcontext);
        assertEquals(expected, featureQuery);

        assertTrue(ite.hasNext());
        featureQuery = ite.next();
        builder = new MatchQueryBuilder("field2", queryBuilder.params().get("query_string"));
        qcontext = createShardContext();
        expected = QueryBuilder.rewriteQuery(builder, qcontext).toQuery(qcontext);
        assertEquals(expected, featureQuery);

        assertTrue(ite.hasNext());

        featureQuery = ite.next();
        builder = new FunctionScoreQueryBuilder(new FieldValueFactorFunctionBuilder("scorefield2")
                .factor(1.2F)
                .modifier(FieldValueFactorFunction.Modifier.LN2P)
                .missing(0F));
        qcontext = createShardContext();
        expected = QueryBuilder.rewriteQuery(builder, qcontext).toQuery(qcontext);
        assertEquals(expected, featureQuery);

        assertThat(rquery.ranker(), instanceOf(LinearRanker.class));
        assertThat(rquery.ranker().newFeatureVector(null), instanceOf(DenseFeatureVector.class));
    }

    @Override
    protected boolean isCachable(StoredLtrQueryBuilder queryBuilder) {
        // This query is not cachable as it needs a ScriptService
        // see QueryShardContext#failIfFrozen()
        return false;
    }

    // Hack to inject our MemStore
    public static class TestPlugin extends LtrQueryParserPlugin {
        public TestPlugin(Settings settings) {
            super(settings);
        }

        @Override
        protected FeatureStoreLoader getFeatureStoreLoader() {
            return LtrTestUtils.wrapMemStore(StoredLtrQueryBuilderTests.store);
        }
    }
}
