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
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.query.StoredLtrQueryBuilder;
import com.o19s.es.ltr.ranker.parser.LinearRankerParserTests;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.index.query.functionscore.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.rescore.QueryRescorerBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                        .missing(0F)).scoreMode(FiltersFunctionScoreQuery.ScoreMode.MULTIPLY).toString()));
        features.add(new StoredFeature("derived_feature", Collections.singletonList("query"), "derived_expression",
                "100"));

        StoredFeatureSet set = new StoredFeatureSet("my_set", features);
        addElement(set);
        StoredLtrModel model = new StoredLtrModel("my_model", set,
                new StoredLtrModel.LtrModelDefinition("model/linear",
                        LinearRankerParserTests.generateRandomModelString(set), true));
        addElement(model);
    }
    public void testFailures() throws Exception {
        prepareModels();
        buildIndex();
        Map<String, Object> params = new HashMap<>();
        params.put("query", "found");
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
    }

    protected void assertSearchHits(Map<String, Doc> docs, SearchResponse resp) {
        for (SearchHit hit: resp.getHits()) {
            assertTrue(hit.getFields().containsKey("_ltrlog"));
            Map<String, Map<String, Float>> logs = hit.getFields().get("_ltrlog").getValue();
            assertTrue(logs.containsKey("first_log"));
            assertTrue(logs.containsKey("second_log"));

            Map<String, Float> log1 = logs.get("first_log");
            Map<String, Float> log2 = logs.get("second_log");
            Doc d = docs.get(hit.getId());
            if (d.field1.equals("found")) {
                assertTrue(log1.containsKey("text_feature1"));
                assertTrue(log2.containsKey("text_feature1"));
                assertTrue(log1.get("text_feature1") > 0F);
                assertTrue(log2.get("text_feature1") > 0F);
                assertFalse(log1.containsKey("text_feature2"));
                assertTrue(log2.containsKey("text_feature2"));
                assertEquals(0F, log2.get("text_feature2"), 0F);
            } else {
                assertTrue(log1.containsKey("text_feature2"));
                assertTrue(log2.containsKey("text_feature2"));
                assertTrue(log1.get("text_feature2") > 0F);
                assertTrue(log2.get("text_feature2") > 0F);
                assertFalse(log1.containsKey("text_feature1"));
                assertTrue(log2.containsKey("text_feature1"));
                assertEquals(0F, log2.get("text_feature1"), 0F);
            }
            float score = (float) Math.log1p((d.scorefield1 * FACTOR) + 1);
            assertTrue(log1.containsKey("numeric_feature1"));
            assertTrue(log2.containsKey("numeric_feature1"));

            assertEquals(score, log1.get("numeric_feature1"), Math.ulp(score));
            assertEquals(score, log2.get("numeric_feature1"), Math.ulp(score));

            assertTrue(log1.containsKey("derived_feature"));
            assertTrue(log2.containsKey("derived_feature"));
            assertEquals(100.0, log1.get("derived_feature"), Math.ulp(100.0));
            assertEquals(100.0, log2.get("derived_feature"), Math.ulp(100.0));
        }
    }

    public Map<String,Doc> buildIndex() {
        client().admin().indices().prepareCreate("test_index")
                .addMapping("test", "{\"properties\":{\"scorefield1\": {\"type\": \"float\"}}}", XContentType.JSON)
                .get();

        int numDocs = TestUtil.nextInt(random(), 20, 100);
        Map<String, Doc> docs = new HashMap<>();
        for (int i = 0; i < numDocs; i++) {
            boolean field1IsFound = random().nextBoolean();
            Doc d = new Doc(
                    field1IsFound ? "found" : "notfound",
                    field1IsFound ? "notfound" : "found",
                    Math.abs(random().nextFloat()));
            indexDoc(d);
            docs.put(d.id, d);
        }
        client().admin().indices().prepareRefresh("test_index").get();
        return docs;
    }

    public void indexDoc(Doc d) {
        IndexResponse resp = client().prepareIndex("test_index", "test")
                .setSource("field1", d.field1, "field2", d.field2, "scorefield1", d.scorefield1)
                .get();
        d.id = resp.getId();
    }

    static class Doc {
        String id;
        String field1;
        String field2;
        float scorefield1;

        Doc(String field1, String field2, float scorefield1) {
            this.field1 = field1;
            this.field2 = field2;
            this.scorefield1 = scorefield1;
        }
    }
}
