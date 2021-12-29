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

package com.o19s.es.ltr.logging;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.action.BaseIntegrationTest;
import com.o19s.es.ltr.feature.store.ScriptFeature;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.query.StoredLtrQueryBuilder;
import com.o19s.es.ltr.ranker.parser.LinearRankerParserTests;
import org.apache.lucene.search.join.ScoreMode;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.rescore.QueryRescorerBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;

public class LoggingIT extends BaseIntegrationTest {
    public static final float FACTOR = 1.2F;

    public void prepareModels() throws Exception {
        List<StoredFeature> features = new ArrayList<>(3);
        features.add(new StoredFeature("text_feature1", Collections.singletonList("query"), "mustache",
                QueryBuilders.matchQuery("field1", "{{query}}").toString()));
        features.add(new StoredFeature("text_feature2", Collections.singletonList("query"), "mustache",
                QueryBuilders.matchQuery("field2", "{{query}}").toString()));
        features.add(new StoredFeature("numeric_feature1", Collections.singletonList("query"), "mustache",
                new FunctionScoreQueryBuilder(QueryBuilders.matchAllQuery(), new FieldValueFactorFunctionBuilder("scorefield1")
                        .factor(FACTOR)
                        .modifier(FieldValueFactorFunction.Modifier.LN2P)
                        .missing(0F)).scoreMode(FunctionScoreQuery.ScoreMode.MULTIPLY).toString()));
        features.add(new StoredFeature("derived_feature", Collections.singletonList("query"), "derived_expression",
                "100"));

        StoredFeatureSet set = new StoredFeatureSet("my_set", features);
        addElement(set);
        StoredLtrModel model = new StoredLtrModel("my_model", set,
                new StoredLtrModel.LtrModelDefinition("model/linear",
                        LinearRankerParserTests.generateRandomModelString(set), true));
        addElement(model);
    }
    public void prepareModelsExtraLogging() throws Exception {
        List<StoredFeature> features = new ArrayList<>(3);
        features.add(new StoredFeature("text_feature1", Collections.singletonList("query"), "mustache",
                QueryBuilders.matchQuery("field1", "{{query}}").toString()));
        features.add(new StoredFeature("text_feature2", Collections.singletonList("query"), "mustache",
                QueryBuilders.matchQuery("field2", "{{query}}").toString()));
        features.add(new StoredFeature("numeric_feature1", Collections.singletonList("query"), "mustache",
                new FunctionScoreQueryBuilder(QueryBuilders.matchAllQuery(), new FieldValueFactorFunctionBuilder("scorefield1")
                        .factor(FACTOR)
                        .modifier(FieldValueFactorFunction.Modifier.LN2P)
                        .missing(0F)).scoreMode(FunctionScoreQuery.ScoreMode.MULTIPLY).toString()));
        features.add(new StoredFeature("derived_feature", Collections.singletonList("query"), "derived_expression",
                "100"));
        features.add(new StoredFeature("extra_logging_feature", Arrays.asList("query"), ScriptFeature.TEMPLATE_LANGUAGE,
                "{\"lang\": \"native\", \"source\": \"feature_extractor_extra_logging\", \"params\": {}}"));

        StoredFeatureSet set = new StoredFeatureSet("my_set", features);
        addElement(set);
        StoredLtrModel model = new StoredLtrModel("my_model", set,
                new StoredLtrModel.LtrModelDefinition("model/linear",
                        LinearRankerParserTests.generateRandomModelString(set), true));
        addElement(model);
    }
    public void prepareExternalScriptFeatures() throws Exception {
        List<StoredFeature> features = new ArrayList<>(3);
        features.add(new StoredFeature("test_inject", Arrays.asList(), ScriptFeature.TEMPLATE_LANGUAGE,
                "{\"lang\": \"inject\", \"source\": \"df\", \"params\": {\"term_stat\": { " +
                        "\"analyzer\": \"analyzerParam\", " +
                        "\"terms\": \"termsParam\", " +
                        "\"fields\": \"fieldsParam\" } } }"));

        StoredFeatureSet set = new StoredFeatureSet("my_set", features);
        addElement(set);
    }

    public void prepareInternalScriptFeatures() throws Exception {
        List<StoredFeature> features = new ArrayList<>(3);
        features.add(new StoredFeature("test_inject", Arrays.asList("query"), ScriptFeature.TEMPLATE_LANGUAGE,
                "{\"lang\": \"inject\", \"source\": \"df\", \"params\": {\"term_stat\": { " +
                        "\"analyzer\": \"!standard\", " +
                        "\"terms\": [\"found\"], " +
                        "\"fields\": [\"field1\"] } } }"));

        StoredFeatureSet set = new StoredFeatureSet("my_set", features);
        addElement(set);
    }

    public void testFailures() throws Exception {
        prepareModels();
        buildIndex();
        QueryBuilder query = QueryBuilders.matchQuery("field1", "found")
                .boost(random().nextInt(3))
                .queryName("not_sltr");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)));

        assertExcWithMessage(() -> client().prepareSearch("test_index")
                .setTypes("test")
                .setSource(sourceBuilder).get(), IllegalArgumentException.class, "No query named [test] found");

        SearchSourceBuilder sourceBuilder2 = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "not_sltr", false)));

        assertExcWithMessage(() -> client().prepareSearch("test_index")
                .setTypes("test")
                .setSource(sourceBuilder2).get(), IllegalArgumentException.class, "Query named [not_sltr] must be a " +
                "[sltr] query [TermQuery] found");

        SearchSourceBuilder sourceBuilder3 = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addRescoreLogging("first_log", 0, false)));
        assertExcWithMessage(() -> client().prepareSearch("test_index")
                .setTypes("test")
                .setSource(sourceBuilder3).get(), IllegalArgumentException.class, "rescore index [0] is out of bounds, " +
                "only [0]");

        SearchSourceBuilder sourceBuilder4 = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(QueryBuilders.matchAllQuery()))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addRescoreLogging("first_log", 0, false)));
        assertExcWithMessage(() -> client().prepareSearch("test_index")
                .setTypes("test")
                .setSource(sourceBuilder4).get(), IllegalArgumentException.class, "Expected a [sltr] query but found " +
                "a [MatchAllDocsQuery] at index [0]");
    }

    private void assertExcWithMessage(ThrowingRunnable r, Class<? extends Exception> exc, String msg) {
        Throwable e = expectThrows(Throwable.class, r);
        e = ExceptionsHelper.unwrap(e, exc);
        assertNotNull(e);
        assertThat(e, instanceOf(IllegalArgumentException.class));
        assertThat(e.getMessage(), containsString(msg));

    }

    public void testLog() throws Exception {
        prepareModels();
        Map<String, Doc> docs = buildIndex();

        Map<String, Object> params = new HashMap<>();
        params.put("query", "found");
        List<String> idsColl = new ArrayList<>(docs.keySet());
        Collections.shuffle(idsColl, random());
        String[] ids = idsColl.subList(0, TestUtil.nextInt(random(), 5, 15)).toArray(new String[0]);
        StoredLtrQueryBuilder sbuilder = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                .featureSetName("my_set")
                .params(params)
                .queryName("test")
                .boost(random().nextInt(3));

        StoredLtrQueryBuilder sbuilder_rescore = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                .featureSetName("my_set")
                .params(params)
                .queryName("test_rescore")
                .boost(random().nextInt(3));

        QueryBuilder query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()))
                .filter(QueryBuilders.idsQuery("test").addIds(ids));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)
                                .addRescoreLogging("second_log", 0, true)));

        SearchResponse resp = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHits(docs, resp);
        sbuilder.featureSetName(null);
        sbuilder.modelName("my_model");
        sbuilder.boost(random().nextInt(3));
        sbuilder_rescore.featureSetName(null);
        sbuilder_rescore.modelName("my_model");
        sbuilder_rescore.boost(random().nextInt(3));

        query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()))
                .filter(QueryBuilders.idsQuery("test").addIds(ids));
        sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)
                                .addRescoreLogging("second_log", 0, true)));

        SearchResponse resp2 = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHits(docs, resp2);

        query = QueryBuilders.boolQuery()
                .must(new WrapperQueryBuilder(sbuilder.toString()))
                .must(
                    QueryBuilders.nestedQuery(
                        "nesteddocs1",
                        QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("nesteddocs1.field1", "nestedvalue")),
                        ScoreMode.None
                    ).innerHit(new InnerHitBuilder())
        );
        sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)
                                .addRescoreLogging("second_log", 0, true)));
        SearchResponse resp3 = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHits(docs, resp3);

        query = QueryBuilders.boolQuery().filter(QueryBuilders.idsQuery("test").addIds(ids));
        sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder.toString())))
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addRescoreLogging("first_log", 0, false)
                                .addRescoreLogging("second_log", 1, true)));

        SearchResponse resp4 = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHits(docs, resp4);
    }

    public void testLogExtraLogging() throws Exception {
        prepareModelsExtraLogging();
        Map<String, Doc> docs = buildIndex();

        Map<String, Object> params = new HashMap<>();
        params.put("query", "found");
        List<String> idsColl = new ArrayList<>(docs.keySet());
        Collections.shuffle(idsColl, random());
        String[] ids = idsColl.subList(0, TestUtil.nextInt(random(), 5, 15)).toArray(new String[0]);
        StoredLtrQueryBuilder sbuilder = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                .featureSetName("my_set")
                .params(params)
                .queryName("test")
                .boost(random().nextInt(3));

        StoredLtrQueryBuilder sbuilder_rescore = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                .featureSetName("my_set")
                .params(params)
                .queryName("test_rescore")
                .boost(random().nextInt(3));

        QueryBuilder query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()))
                .filter(QueryBuilders.idsQuery("test").addIds(ids));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)
                                .addRescoreLogging("second_log", 0, true)));

        SearchResponse resp = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHitsExtraLogging(docs, resp);
        sbuilder.featureSetName(null);
        sbuilder.modelName("my_model");
        sbuilder.boost(random().nextInt(3));
        sbuilder_rescore.featureSetName(null);
        sbuilder_rescore.modelName("my_model");
        sbuilder_rescore.boost(random().nextInt(3));

        query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()))
                .filter(QueryBuilders.idsQuery("test").addIds(ids));
        sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)
                                .addRescoreLogging("second_log", 0, true)));

        SearchResponse resp2 = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHitsExtraLogging(docs, resp2);

        query = QueryBuilders.boolQuery()
                .must(new WrapperQueryBuilder(sbuilder.toString()))
                .must(
                        QueryBuilders.nestedQuery(
                                "nesteddocs1",
                                QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("nesteddocs1.field1", "nestedvalue")),
                                ScoreMode.None
                        ).innerHit(new InnerHitBuilder())
                );
        sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)
                                .addRescoreLogging("second_log", 0, true)));
        SearchResponse resp3 = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHitsExtraLogging(docs, resp3);
    }

    public void testLogWithFeatureScoreCache() throws Exception {
        prepareModels();
        Map<String, Doc> docs = buildIndex();

        Map<String, Object> params = new HashMap<>();
        params.put("query", "found");
        List<String> idsColl = new ArrayList<>(docs.keySet());
        Collections.shuffle(idsColl, random());
        String[] ids = idsColl.subList(0, TestUtil.nextInt(random(), 5, 15)).toArray(new String[0]);
        StoredLtrQueryBuilder sbuilder = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                .featureSetName("my_set")
                .params(params)
                .queryName("test")
                .boost(1)
                .featureScoreCacheFlag(Boolean.TRUE);
        /*
            Note: Feature score caching with boosts other than 1 affects logged feature scores.
            This behavior is a bug. See: https://github.com/o19s/elasticsearch-learning-to-rank/issues/368
        */

        StoredLtrQueryBuilder sbuilder_rescore = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                .featureSetName("my_set")
                .params(params)
                .queryName("test_rescore")
                .boost(1)
                .featureScoreCacheFlag(Boolean.TRUE);

        QueryBuilder query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()))
                .filter(QueryBuilders.idsQuery("test").addIds(ids));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)
                                .addRescoreLogging("second_log", 0, true)));

        SearchResponse resp = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHits(docs, resp);
        sbuilder.featureSetName(null);
        sbuilder.modelName("my_model");
        sbuilder_rescore.featureSetName(null);
        sbuilder_rescore.modelName("my_model");

        query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()))
                .filter(QueryBuilders.idsQuery("test").addIds(ids));
        sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)
                                .addRescoreLogging("second_log", 0, true)));

        SearchResponse resp2 = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHits(docs, resp2);

        query = QueryBuilders.boolQuery()
                .must(new WrapperQueryBuilder(sbuilder.toString()))
                .must(
                        QueryBuilders.nestedQuery(
                                "nesteddocs1",
                                QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("nesteddocs1.field1", "nestedvalue")),
                                ScoreMode.None
                        ).innerHit(new InnerHitBuilder())
                );
        sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)
                                .addRescoreLogging("second_log", 0, true)));
        SearchResponse resp3 = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHits(docs, resp3);

        query = QueryBuilders.boolQuery().filter(QueryBuilders.idsQuery("test").addIds(ids));
        sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder.toString())))
                .addRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(sbuilder_rescore.toString())))
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addRescoreLogging("first_log", 0, false)
                                .addRescoreLogging("second_log", 1, true)));

        SearchResponse resp4 = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();
        assertSearchHits(docs, resp4);
    }

    public void testScriptLogInternalParams() throws Exception {
        prepareInternalScriptFeatures();
        Map<String, Doc> docs = buildIndex();

        Map<String, Object> params = new HashMap<>();
        params.put("query", "found");

        List<String> idsColl = new ArrayList<>(docs.keySet());
        Collections.shuffle(idsColl, random());
        String[] ids = idsColl.subList(0, TestUtil.nextInt(random(), 5, 15)).toArray(new String[0]);
        StoredLtrQueryBuilder sbuilder = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                .featureSetName("my_set")
                .params(params)
                .queryName("test")
                .boost(random().nextInt(3));

        QueryBuilder query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()))
                .filter(QueryBuilders.idsQuery("test").addIds(ids));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)));

        SearchResponse resp = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();

        SearchHits hits = resp.getHits();
        SearchHit testHit = hits.getAt(0);
        Map<String, List<Map<String, Object>>> logs = testHit.getFields().get("_ltrlog").getValue();

        assertTrue(logs.containsKey("first_log"));
        List<Map<String, Object>> log = logs.get("first_log");

        assertEquals(log.get(0).get("name"), "test_inject");
        assertTrue((Float)log.get(0).get("value") > 0.0F);
    }

    public void testScriptLogExternalParams() throws Exception {
        prepareExternalScriptFeatures();
        Map<String, Doc> docs = buildIndex();

        Map<String, Object> params = new HashMap<>();
        ArrayList<String> terms = new ArrayList<>();
        terms.add("found");
        params.put("termsParam", terms);

        ArrayList<String> fields = new ArrayList<>();
        fields.add("field1");
        params.put("fieldsParam", fields);

        params.put("analyzerParam", "standard");

        List<String> idsColl = new ArrayList<>(docs.keySet());
        Collections.shuffle(idsColl, random());
        String[] ids = idsColl.subList(0, TestUtil.nextInt(random(), 5, 15)).toArray(new String[0]);
        StoredLtrQueryBuilder sbuilder = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                .featureSetName("my_set")
                .params(params)
                .queryName("test")
                .boost(random().nextInt(3));

        QueryBuilder query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()))
                .filter(QueryBuilders.idsQuery("test").addIds(ids));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)));

        SearchResponse resp = client().prepareSearch("test_index").setTypes("test").setSource(sourceBuilder).get();

        SearchHits hits = resp.getHits();
        SearchHit testHit = hits.getAt(0);
        Map<String, List<Map<String, Object>>> logs = testHit.getFields().get("_ltrlog").getValue();

        assertTrue(logs.containsKey("first_log"));
        List<Map<String, Object>> log = logs.get("first_log");

        assertEquals(log.get(0).get("name"), "test_inject");
        assertTrue((Float)log.get(0).get("value") > 0.0F);
    }

    public void testScriptLogInvalidExternalParams() throws Exception {
        prepareExternalScriptFeatures();
        Map<String, Doc> docs = buildIndex();

        Map<String, Object> params = new HashMap<>();
        params.put("query", "found");

        List<String> idsColl = new ArrayList<>(docs.keySet());
        Collections.shuffle(idsColl, random());
        String[] ids = idsColl.subList(0, TestUtil.nextInt(random(), 5, 15)).toArray(new String[0]);
        StoredLtrQueryBuilder sbuilder = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                .featureSetName("my_set")
                .params(params)
                .queryName("test")
                .boost(random().nextInt(3));

        QueryBuilder query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()))
                .filter(QueryBuilders.idsQuery("test").addIds(ids));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query)
                .fetchSource(false)
                .size(10)
                .ext(Collections.singletonList(
                        new LoggingSearchExtBuilder()
                                .addQueryLogging("first_log", "test", false)));

        assertExcWithMessage(() -> client().prepareSearch("test_index")
                        .setTypes("test")
                        .setSource(sourceBuilder).get(),
                IllegalArgumentException.class, "Term Stats injection requires fields and terms");
    }

    protected void assertSearchHits(Map<String, Doc> docs, SearchResponse resp) {
        for (SearchHit hit: resp.getHits()) {
            assertTrue(hit.getFields().containsKey("_ltrlog"));
            Map<String, List<Map<String, Object>>> logs = hit.getFields().get("_ltrlog").getValue();
            assertTrue(logs.containsKey("first_log"));
            assertTrue(logs.containsKey("second_log"));

            List<Map<String, Object>> log1 = logs.get("first_log");
            List<Map<String, Object>> log2 = logs.get("second_log");
            Doc d = docs.get(hit.getId());

            assertEquals(4, log1.size());
            assertEquals(4, log2.size());
            if (d.field1.equals("found")) {
                assertEquals(log1.get(0).get("name"), "text_feature1");
                assertEquals(log2.get(0).get("name"), "text_feature1");

                assertTrue((Float)log1.get(0).get("value") > 0F);
                assertTrue((Float)log2.get(0).get("value") > 0F);

                assertEquals(log1.get(1).get("name"), "text_feature2");
                assertFalse(log1.get(1).containsKey("value"));

                assertEquals(log2.get(1).get("name"), "text_feature2");
                assertEquals(log2.get(1).get("value"), 0F);

            } else {
                assertEquals(log1.get(0).get("name"), "text_feature1");
                assertEquals(log2.get(0).get("name"), "text_feature1");

                assertTrue((Float)log1.get(1).get("value") > 0F);
                assertTrue((Float)log2.get(1).get("value") > 0F);

                assertEquals(log1.get(0).get("name"), "text_feature1");
                assertEquals(log2.get(0).get("name"), "text_feature1");

                assertEquals(0F, (Float)log2.get(0).get("value"), 0F);
            }
            float score = (float) Math.log1p((d.scorefield1 * FACTOR) + 1);
            assertEquals(log1.get(2).get("name"), "numeric_feature1");
            assertEquals(log2.get(2).get("name"), "numeric_feature1");

            assertEquals(score, (Float)log1.get(2).get("value"), Math.ulp(score));
            assertEquals(score, (Float)log2.get(2).get("value"), Math.ulp(score));

            assertEquals(log1.get(3).get("name"), "derived_feature");
            assertEquals(log2.get(3).get("name"), "derived_feature");

            assertEquals(100.0, (Float) log1.get(3).get("value"), Math.ulp(100.0));
            assertEquals(100.0, (Float) log2.get(3).get("value"), Math.ulp(100.0));

        }
    }

    @SuppressWarnings("unchecked")
    protected void assertSearchHitsExtraLogging(Map<String, Doc> docs, SearchResponse resp) {
        for (SearchHit hit: resp.getHits()) {
            assertTrue(hit.getFields().containsKey("_ltrlog"));
            Map<String, List<Map<String, Object>>> logs = hit.getFields().get("_ltrlog").getValue();
            assertTrue(logs.containsKey("first_log"));
            assertTrue(logs.containsKey("second_log"));

            List<Map<String, Object>> log1 = logs.get("first_log");
            List<Map<String, Object>> log2 = logs.get("second_log");
            Doc d = docs.get(hit.getId());

            assertEquals(6, log1.size());
            assertEquals(6, log2.size());
            if (d.field1.equals("found")) {
                assertEquals(log1.get(0).get("name"), "text_feature1");
                assertEquals(log2.get(0).get("name"), "text_feature1");

                assertTrue((Float)log1.get(0).get("value") > 0F);
                assertTrue((Float)log2.get(0).get("value") > 0F);

                assertEquals(log1.get(1).get("name"), "text_feature2");
                assertFalse(log1.get(1).containsKey("value"));

                assertEquals(log2.get(1).get("name"), "text_feature2");
                assertEquals(log2.get(1).get("value"), 0F);

            } else {
                assertEquals(log1.get(0).get("name"), "text_feature1");
                assertEquals(log2.get(0).get("name"), "text_feature1");

                assertTrue((Float)log1.get(1).get("value") > 0F);
                assertTrue((Float)log2.get(1).get("value") > 0F);

                assertEquals(log1.get(0).get("name"), "text_feature1");
                assertEquals(log2.get(0).get("name"), "text_feature1");

                assertEquals(0F, (Float)log2.get(0).get("value"), 0F);
            }
            float score = (float) Math.log1p((d.scorefield1 * FACTOR) + 1);
            assertEquals(log1.get(2).get("name"), "numeric_feature1");
            assertEquals(log2.get(2).get("name"), "numeric_feature1");

            assertEquals(score, (Float)log1.get(2).get("value"), Math.ulp(score));
            assertEquals(score, (Float)log2.get(2).get("value"), Math.ulp(score));

            assertEquals(log1.get(3).get("name"), "derived_feature");
            assertEquals(log2.get(3).get("name"), "derived_feature");

            assertEquals(100.0, (Float) log1.get(3).get("value"), Math.ulp(100.0));
            assertEquals(100.0, (Float) log2.get(3).get("value"), Math.ulp(100.0));

            assertEquals(log1.get(4).get("name"), "extra_logging_feature");
            assertEquals(log2.get(4).get("name"), "extra_logging_feature");

            assertEquals(1.0, (Float) log1.get(4).get("value"), Math.ulp(1.0));
            assertEquals(1.0, (Float) log2.get(4).get("value"), Math.ulp(1.0));

            assertEquals(log1.get(5).get("name"), "extra_logging");
            assertEquals(log2.get(5).get("name"), "extra_logging");

            Map<String,Object> extraMap1 = (Map<String,Object>) log1.get(5).get("value");
            Map<String,Object> extraMap2 = (Map<String,Object>) log2.get(5).get("value");

            assertEquals(2, extraMap1.size());
            assertEquals(2, extraMap2.size());
            assertEquals(10.0f, extraMap1.get("extra_float"));
            assertEquals(10.0f, extraMap2.get("extra_float"));
            assertEquals("additional_info", extraMap1.get("extra_string"));
            assertEquals("additional_info", extraMap2.get("extra_string"));
        }
    }

    public Map<String,Doc> buildIndex() {
        client().admin().indices().prepareCreate("test_index")
                .addMapping(
                        "test",
                        "{\"properties\":{\"scorefield1\": {\"type\": \"float\"}, \"nesteddocs1\": {\"type\": \"nested\"}}}}",
                        XContentType.JSON)
                .get();

        int numDocs = TestUtil.nextInt(random(), 20, 100);
        Map<String, Doc> docs = new HashMap<>();
        for (int i = 0; i < numDocs; i++) {
            boolean field1IsFound = random().nextBoolean();
            int numNestedDocs = TestUtil.nextInt(random(), 1, 20);
            List<NestedDoc> nesteddocs1 = new ArrayList<>();
            for (int j = 0; j < numNestedDocs; j++) {
                nesteddocs1.add(
                        new NestedDoc(
                                "nestedvalue",
                                Math.abs(random().nextFloat())));
            }
            Doc d = new Doc(
                    field1IsFound ? "found" : "notfound",
                    field1IsFound ? "notfound" : "found",
                    Math.abs(random().nextFloat()),
                    nesteddocs1);
            indexDoc(d);
            docs.put(d.id, d);
        }
        client().admin().indices().prepareRefresh("test_index").get();
        return docs;
    }

    public void indexDoc(Doc d) {
        IndexResponse resp = client().prepareIndex("test_index", "test")
                .setSource("field1", d.field1, "field2", d.field2, "scorefield1", d.scorefield1, "nesteddocs1", d.getNesteddocs1())
                .get();
        d.id = resp.getId();
    }

    static class Doc {
        String id;
        String field1;
        String field2;
        float scorefield1;
        List<NestedDoc> nesteddocs1;

        Doc(String field1, String field2, float scorefield1, List<NestedDoc> nesteddocs1) {
            this.field1 = field1;
            this.field2 = field2;
            this.scorefield1 = scorefield1;
            this.nesteddocs1 = nesteddocs1;
        }

        List<Map<String, Object>> getNesteddocs1() {
            return nesteddocs1.stream().map(nd -> nd.toMap()).collect(Collectors.toList());
        }
    }

    static class NestedDoc {
        String field1;
        float sortfield1;

        NestedDoc(String field1, float sortfield1) {
            this.field1 = field1;
            this.sortfield1 = sortfield1;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("field1", field1);
            map.put("sortfield1", sortfield1);
            return map;
        }
    }

}
