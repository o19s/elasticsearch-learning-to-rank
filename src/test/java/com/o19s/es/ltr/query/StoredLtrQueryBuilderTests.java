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
import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizingRanker;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import com.o19s.es.ltr.ranker.normalizer.StandardFeatureNormalizer;
import com.o19s.es.ltr.utils.FeatureStoreLoader;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.io.stream.ByteBufferStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.Rewriteable;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.junit.Before;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;

public class StoredLtrQueryBuilderTests extends AbstractQueryTestCase<StoredLtrQueryBuilder> {
    private static final MemStore store = new MemStore();

    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singletonList(TestPlugin.class);
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
        LtrRanker ranker = new LinearRanker(new float[] {0.1F, 0.2F, 0.3F});

        Map<Integer, Normalizer> ftrNorms = new HashMap<>();
        ftrNorms.put(2, new StandardFeatureNormalizer(1.0f,0.5f));
        ranker = new FeatureNormalizingRanker(ranker, ftrNorms);

        CompiledLtrModel model = new CompiledLtrModel("model1", set, ranker);
        store.add(model);
    }

    /**
     * Create the query that is being tested
     */
    @Override
    protected StoredLtrQueryBuilder doCreateTestQueryBuilder() {
        StoredLtrQueryBuilder builder = new StoredLtrQueryBuilder(LtrTestUtils.wrapMemStore(store));
        if (random().nextBoolean()) { // executing a model
            builder.modelName("model1");
        } else { // logging
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

    public void testInvalidActiveFeatures() {
        StoredLtrQueryBuilder builder = new StoredLtrQueryBuilder(LtrTestUtils.wrapMemStore(StoredLtrQueryBuilderTests.store));
        builder.modelName("model1");
        builder.activeFeatures(Collections.singletonList("non_existent_feature"));
        assertThat(expectThrows(IllegalArgumentException.class, () -> builder.toQuery(createShardContext())).getMessage(),
                equalTo("Feature: [non_existent_feature] provided in active_features does not exist"));
    }

    public void testSerDe() throws IOException {
        StoredLtrQueryBuilder builder = new StoredLtrQueryBuilder(LtrTestUtils.wrapMemStore(StoredLtrQueryBuilderTests.store));
        builder.activeFeatures(Collections.singletonList("match1"));
        BytesStreamOutput out = new BytesStreamOutput();
        builder.writeTo(out);
        out.close();

        BytesRef ref = out.bytes().toBytesRef();
        StreamInput input = ByteBufferStreamInput.wrap(ref.bytes, ref.offset, ref.length);
        StoredLtrQueryBuilder builderFromInputStream = new StoredLtrQueryBuilder(
                LtrTestUtils.wrapMemStore(StoredLtrQueryBuilderTests.store), input);
        List<String> expected = Collections.singletonList("match1");
        assertEquals(expected, builderFromInputStream.activeFeatures());
    }

    public void testDoToQueryWhenFeatureEnabled() throws IOException {
        assertQueryClass(FunctionScoreQuery.class, false);
    }

    public void testDoToQueryWhenFeatureDisabled() throws IOException {
        assertQueryClass(MatchNoDocsQuery.class, true);
    }

    private void assertQueryClass(Class<?> clazz, boolean setActiveFeature) throws IOException {
        StoredLtrQueryBuilder builder = new StoredLtrQueryBuilder(LtrTestUtils.wrapMemStore(StoredLtrQueryBuilderTests.store));
        builder.modelName("model1");
        Map<String, Object> params = new HashMap<>();
        params.put("query_string", "a wonderful query");
        builder.params(params);
        if (setActiveFeature) {
            builder.activeFeatures(Arrays.asList("match1", "match2"));
        }

        RankerQuery rankerQuery = builder.doToQuery(createShardContext());
        List<Query> queries = rankerQuery.stream().collect(Collectors.toList());
        assertEquals(clazz, queries.get(2).getClass());
    }

    @Override
    protected void doAssertLuceneQuery(StoredLtrQueryBuilder queryBuilder,
                                       Query query, QueryShardContext context) throws IOException {
        assertThat(query, instanceOf(RankerQuery.class));
        RankerQuery rquery = (RankerQuery) query;
        Iterator<Query> ite = rquery.stream().iterator();

        // Confirm each feature normalizer when evaluating a model
        LtrRanker ranker = rquery.ranker();
        if (queryBuilder.featureSetName() != null && queryBuilder.featureSetName().equals("set1")) {
            assertThat(ranker, instanceOf(LinearRanker.class));
        } else {
            assertEquals(queryBuilder.modelName(), "model1");
            assertThat(ranker, instanceOf(FeatureNormalizingRanker.class));
        }

        // Check each feature query
        assertTrue(ite.hasNext());
        Query featureQuery = ite.next();
        QueryBuilder builder = new MatchQueryBuilder("field1", queryBuilder.params().get("query_string"));
        QueryShardContext qcontext = createShardContext();

        Query expected = Rewriteable.rewrite(builder, qcontext).toQuery(qcontext);
        assertEquals(expected, featureQuery);

        assertTrue(ite.hasNext());
        featureQuery = ite.next();
        builder = new MatchQueryBuilder("field2", queryBuilder.params().get("query_string"));
        qcontext = createShardContext();
        expected = Rewriteable.rewrite(builder, qcontext).toQuery(qcontext);
        assertEquals(expected, featureQuery);
        assertTrue(ite.hasNext());

        featureQuery = ite.next();
        builder = new FunctionScoreQueryBuilder(new FieldValueFactorFunctionBuilder("scorefield2")
                .factor(1.2F)
                .modifier(FieldValueFactorFunction.Modifier.LN2P)
                .missing(0F));
        qcontext = createShardContext();
        expected = Rewriteable.rewrite(builder, qcontext).toQuery(qcontext);
        assertEquals(expected, featureQuery);

        assertThat(rquery.ranker().newFeatureVector(null), instanceOf(DenseFeatureVector.class));
    }

    @Override
    public void testCacheability() throws IOException {
        StoredLtrQueryBuilder queryBuilder = createTestQueryBuilder();
        QueryShardContext context = createShardContext();
        assert context.isCacheable();
        QueryBuilder rewritten = rewriteQuery(queryBuilder, new QueryShardContext(context));
        assertNotNull(rewritten.toQuery(context));
        assertTrue("query should be cacheable: " + queryBuilder.toString(), context.isCacheable());
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
