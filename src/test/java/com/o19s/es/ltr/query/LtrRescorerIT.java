package com.o19s.es.ltr.query;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.action.BaseIntegrationTest;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.logging.LoggingSearchExtBuilder;
import com.o19s.es.ltr.ranker.parser.LinearRankerParser;
import com.o19s.es.ltr.rescore.LtrRescoreBuilder;
import com.o19s.es.ltr.rescore.LtrRescorer;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFirstHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasId;

public class LtrRescorerIT extends BaseIntegrationTest {
    private static final String SIMPLE_MODEL = "{" +
            "\"feature1\": 0.1," +
            "\"feature2\": 0.2," +
            "\"feature3\": 0.3" +
            "}";


    public void testSimple() throws ExecutionException, InterruptedException {
        buildIndex();
        addModel();

        Map<String, Object> params = new HashMap<>();
        params.put("query", "paris");
        SearchRequestBuilder sb = client().prepareSearch("test_index")
                .setQuery(QueryBuilders.matchQuery("field1", "hello"))
                .setRescorer(new LtrRescoreBuilder().setQuery(new WrapperQueryBuilder(new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                        .modelName("my_model").params(params).toString()))
                        .setScoreMode(LtrRescorer.LtrRescoreMode.Replace)
                        .windowSize(2)
                        .setQueryNormalizer(new Normalizer.IntervalNormalizer(0,1, false, new Normalizer.MinMax(0, 10)))
                        .setRescoreQueryNormalizer(new Normalizer.IntervalNormalizer(1,2, false, new Normalizer.Saturation(1, 1)))
                        .setQueryWeight(1F)
                        .setRescoreQueryWeight(1F));
        SearchResponse sr = sb.get();
        assertEquals(4, sr.getHits().getTotalHits());
        assertFirstHit(sr, hasId("paris"));
        for (SearchHit hit : Arrays.copyOfRange(sr.getHits().getHits(), 0, 2)) {
            assertTrue(hit.getScore() >= 1F);
            assertTrue(hit.getScore() < 2F);
        }
        for (SearchHit hit : Arrays.copyOfRange(sr.getHits().getHits(), 2, 4)) {
            assertTrue(hit.getScore() < 1F);
            assertTrue(hit.getScore() >= 0F);
        }
    }

    public void testLogging() throws ExecutionException, InterruptedException {
        buildIndex();
        addModel();

        Map<String, Object> params = new HashMap<>();
        params.put("query", "paris");
        SearchSourceBuilder builder = new SearchSourceBuilder();
        SearchRequestBuilder sb = client().prepareSearch("test_index")
                .setSource(builder)
                .setQuery(QueryBuilders.matchQuery("field1", "hello"))
                .setRescorer(new LtrRescoreBuilder().setQuery(new WrapperQueryBuilder(new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                        .modelName("my_model").params(params).toString()))
                        .setScoreMode(LtrRescorer.LtrRescoreMode.Replace)
                        .windowSize(2)
                        .setQueryNormalizer(new Normalizer.IntervalNormalizer(0,1, false, new Normalizer.MinMax(0, 10)))
                        .setRescoreQueryNormalizer(new Normalizer.IntervalNormalizer(1,2, false, new Normalizer.Saturation(1, 1)))
                        .setQueryWeight(1F)
                        .setRescoreQueryWeight(1F));
        builder.ext(Collections.singletonList(new LoggingSearchExtBuilder().addRescoreLogging("test", 0, true)));
        SearchResponse sr = sb.get();
        for (SearchHit hit : sr.getHits().getHits()) {
            Map<String, List<Map<String, Object>>> logs = hit.getFields().get("_ltrlog").getValue();
            assertTrue(logs.containsKey("test"));
            List<Map<String, Object>> log = logs.get("test");
            assertEquals("feature3", log.get(2).get("name"));
            assertEquals((float) log.get(0).get("value") * (float) log.get(1).get("value"), (float) log.get(2).get("value"), Math.ulp(1F));
        }
    }

    public void testProfiling() throws ExecutionException, InterruptedException {
        buildIndex();
        addModel();

        Map<String, Object> params = new HashMap<>();
        params.put("query", "paris");
        SearchRequestBuilder sb = client().prepareSearch("test_index")
                .setQuery(QueryBuilders.matchQuery("field1", "hello"))
                .setRescorer(new LtrRescoreBuilder().setQuery(new WrapperQueryBuilder(new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                        .modelName("my_model").params(params).toString()))
                        .setScoreMode(LtrRescorer.LtrRescoreMode.Replace)
                        .windowSize(2)
                        .setQueryNormalizer(new Normalizer.IntervalNormalizer(0,1, false, new Normalizer.MinMax(0, 10)))
                        .setRescoreQueryNormalizer(new Normalizer.IntervalNormalizer(1,2, false, new Normalizer.Saturation(1, 1)))
                        .setQueryWeight(1F)
                        .setRescoreQueryWeight(1F))
                .setProfile(true);
        SearchResponse sr = sb.get();
        assertFalse(sr.getProfileResults().isEmpty());
        assertEquals(4, sr.getHits().getTotalHits());
    }

    public void buildIndex() {
        Settings settings = Settings.builder().put(IndexMetaData.INDEX_NUMBER_OF_SHARDS_SETTING.getKey(), 1).build();
        client().admin().indices().prepareCreate("test_index").setSettings(settings).get();
        for (String w : Arrays.asList("world", "paris", "madrid", "roma")) {
            client().prepareIndex("test_index", "test", w)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                    .setSource("field1", "hello " + w, "field2", "bonjour " + w)
                    .get();
        }
    }

    public void addModel() throws ExecutionException, InterruptedException {
        List<StoredFeature> features = Arrays.asList(
            new StoredFeature("feature1", Collections.singletonList("query"), "mustache",
                QueryBuilders.matchQuery("field1", "{{query}}").toString()),
            new StoredFeature("feature2", Collections.singletonList("query"), "mustache",
                QueryBuilders.matchQuery("field1", "{{query}}").toString()),
            new StoredFeature("feature3", Collections.emptyList(), "derived_expression",
                "feature1 * feature2")

        );

        StoredFeatureSet set = new StoredFeatureSet("set", features);
        addElement(new StoredLtrModel("my_model", set,
                new StoredLtrModel.LtrModelDefinition(LinearRankerParser.TYPE, SIMPLE_MODEL, false)));
    }
}
