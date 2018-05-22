/*
 * Copyright [2016] Doug Turnbull
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.o19s.es.ltr.query;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.search.rescore.QueryRescoreMode;
import org.elasticsearch.search.rescore.QueryRescorerBuilder;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.action.AddFeaturesToSetAction;
import com.o19s.es.ltr.action.AddFeaturesToSetAction.AddFeaturesToSetRequestBuilder;
import com.o19s.es.ltr.action.BaseIntegrationTest;
import com.o19s.es.ltr.action.CachesStatsAction;
import com.o19s.es.ltr.action.CachesStatsAction.CachesStatsNodesResponse;
import com.o19s.es.ltr.action.ClearCachesAction;
import com.o19s.es.ltr.action.CreateModelFromSetAction;
import com.o19s.es.ltr.action.CreateModelFromSetAction.CreateModelFromSetRequestBuilder;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;

/**
 * Created by doug on 12/29/16.
 */
public class StoredLtrQueryIT extends BaseIntegrationTest {

    private static final String SIMPLE_MODEL = "{" +
            "\"feature1\": 1," +
            "\"feature2\": -1," +
            "\"feature3\": 10," +
            "\"feature4\": 1" +
            "}";


    public void testFullUsecase() throws Exception {
        addElement(new StoredFeature("feature1", Collections.singletonList("query"), "mustache",
                QueryBuilders.matchQuery("field1", "{{query}}").toString()));
        addElement(new StoredFeature("feature2", Collections.singletonList("query"), "mustache",
                QueryBuilders.matchQuery("field2", "{{query}}").toString()));
        addElement(new StoredFeature("feature3", Collections.singletonList("query"), "derived_expression",
                "(feature1 - feature2) > 0 ? 1 : -1"));
        addElement(new StoredFeature("feature4", Collections.singletonList("query"), "mustache",
                QueryBuilders.matchQuery("field1", "{{query}}").toString()));

        AddFeaturesToSetRequestBuilder builder = AddFeaturesToSetAction.INSTANCE.newRequestBuilder(client());
        builder.request().setFeatureSet("my_set");
        builder.request().setFeatureNameQuery("feature1");
        builder.request().setStore(IndexFeatureStore.DEFAULT_STORE);
        builder.execute().get();

        builder.request().setFeatureNameQuery("feature2");
        builder.execute().get();

        builder.request().setFeatureNameQuery("feature3");
        builder.execute().get();

        builder.request().setFeatureNameQuery("feature4");
        long version = builder.get().getResponse().getVersion();

        CreateModelFromSetRequestBuilder createModelFromSetRequestBuilder = CreateModelFromSetAction.INSTANCE.newRequestBuilder(client());
        createModelFromSetRequestBuilder.withVersion(IndexFeatureStore.DEFAULT_STORE, "my_set", version,
                "my_model", new StoredLtrModel.LtrModelDefinition("model/linear", SIMPLE_MODEL, true));
        createModelFromSetRequestBuilder.get();
        buildIndex();
        Map<String, Object> params = new HashMap<>();

        boolean negativeScore = false;
        params.put("query", negativeScore ? "bonjour" : "hello");
        SearchRequestBuilder sb = client().prepareSearch("test_index")
                .setQuery(QueryBuilders.matchQuery("field1", "world"))
                .setRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                        .modelName("my_model").params(params).toString()))
                        .setScoreMode(QueryRescoreMode.Total)
                        .setQueryWeight(0)
                        .setRescoreQueryWeight(1));

        SearchResponse sr = sb.get();
        assertEquals(1, sr.getHits().getTotalHits());

        if (negativeScore) {
            assertThat(sr.getHits().getAt(0).getScore(), Matchers.lessThanOrEqualTo(-10.0f));
        } else {
            assertThat(sr.getHits().getAt(0).getScore(), Matchers.greaterThanOrEqualTo(10.0f));
        }

        negativeScore = true;
        params.put("query", negativeScore ? "bonjour" : "hello");
        sb = client().prepareSearch("test_index")
                .setQuery(QueryBuilders.matchQuery("field1", "world"))
                .setRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                        .modelName("my_model").params(params).toString()))
                        .setScoreMode(QueryRescoreMode.Total)
                        .setQueryWeight(0)
                        .setRescoreQueryWeight(1));

        sr = sb.get();
        assertEquals(1, sr.getHits().getTotalHits());

        if(negativeScore) {
            assertThat(sr.getHits().getAt(0).getScore(), Matchers.lessThanOrEqualTo(-10.0f));
        } else {
            assertThat(sr.getHits().getAt(0).getScore(), Matchers.greaterThanOrEqualTo(10.0f));
        }


        //we use only feature4 score and ignore other scores
        params.put("query", "hello");
        sb = client().prepareSearch("test_index")
                .setQuery(QueryBuilders.matchQuery("field1", "world"))
                .setRescorer(new QueryRescorerBuilder(new WrapperQueryBuilder(new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                        .modelName("my_model").params(params).activeFeatures(Arrays.asList("feature4")).toString()))
                        .setScoreMode(QueryRescoreMode.Total)
                        .setQueryWeight(0)
                        .setRescoreQueryWeight(1));

        sr = sb.get();
        assertEquals(1, sr.getHits().getTotalHits());
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.greaterThan(0.0f));
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.lessThanOrEqualTo(1.0f));

        StoredLtrModel model = getElement(StoredLtrModel.class, StoredLtrModel.TYPE, "my_model");
        CachesStatsNodesResponse stats = CachesStatsAction.INSTANCE.newRequestBuilder(client()).execute().get();
        assertEquals(1, stats.getAll().getTotal().getCount());
        assertEquals(model.compile(parserFactory()).ramBytesUsed(), stats.getAll().getTotal().getRam());
        assertEquals(1, stats.getAll().getModels().getCount());
        assertEquals(model.compile(parserFactory()).ramBytesUsed(), stats.getAll().getModels().getRam());
        assertEquals(0, stats.getAll().getFeatures().getCount());
        assertEquals(0, stats.getAll().getFeatures().getRam());
        assertEquals(0, stats.getAll().getFeaturesets().getCount());
        assertEquals(0, stats.getAll().getFeaturesets().getRam());

        ClearCachesAction.RequestBuilder clearCache = ClearCachesAction.INSTANCE.newRequestBuilder(client());
        clearCache.request().clearModel(IndexFeatureStore.DEFAULT_STORE, "my_model");
        clearCache.get();

        stats = CachesStatsAction.INSTANCE.newRequestBuilder(client()).execute().get();
        assertEquals(0, stats.getAll().getTotal().getCount());
        assertEquals(0, stats.getAll().getTotal().getRam());
    }

    public void testInvalidDerived() throws Exception {
        addElement(new StoredFeature("bad_df", Collections.singletonList("query"), "derived_expression",
                "what + is + this"));

        AddFeaturesToSetRequestBuilder builder = AddFeaturesToSetAction.INSTANCE.newRequestBuilder(client());
        builder.request().setFeatureSet("my_bad_set");
        builder.request().setFeatureNameQuery("bad_df");
        builder.request().setStore(IndexFeatureStore.DEFAULT_STORE);

        assertThat(expectThrows(ExecutionException.class, () -> builder.execute().get()).getMessage(),
                CoreMatchers.containsString("refers to unknown feature"));
    }

    public void buildIndex() {
        client().admin().indices().prepareCreate("test_index").get();
        client().prepareIndex("test_index", "test")
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                .setSource("field1", "hello world", "field2", "bonjour world")
                .get();
    }
}
