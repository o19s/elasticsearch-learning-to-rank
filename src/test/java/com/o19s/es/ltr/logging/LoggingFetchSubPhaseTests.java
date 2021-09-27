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

import com.o19s.es.ltr.feature.PrebuiltFeature;
import com.o19s.es.ltr.feature.PrebuiltFeatureSet;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.logging.LoggingFetchSubPhase.LoggingFetchSubPhaseProcessor;
import com.o19s.es.ltr.query.RankerQuery;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.linear.LinearRankerTests;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.fielddata.plain.SortedNumericIndexFieldData;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.FetchSubPhase;
import org.elasticsearch.search.fetch.FetchSubPhaseProcessor;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier.LN2P;
import static org.elasticsearch.index.fielddata.IndexNumericFieldData.NumericType.FLOAT;

public class LoggingFetchSubPhaseTests extends LuceneTestCase {
    public static final float FACTOR = 1.2F;
    private static Directory directory;
    private static IndexSearcher searcher;
    private static Map<String,Document> docs;


    @BeforeClass
    public static void init() throws Exception {
        directory = newDirectory(random());

        try(IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig(new StandardAnalyzer()))) {
            int nDoc = TestUtil.nextInt(random(), 20, 100);
            docs = new HashMap<>();
            for (int i = 0; i < nDoc; i++) {
                Document d = buildDoc(random().nextBoolean() ? "foo" : "bar", random().nextFloat());
                writer.addDocument(d);
                if (random().nextInt(4) == 0) {
                    writer.commit();
                }
                docs.put(d.get("id"), d);
            }
            writer.commit();
        }
        IndexReader reader = closeAfterSuite(DirectoryReader.open(directory));
        searcher = new IndexSearcher(reader);
    }

    @AfterClass
    public static void cleanup() throws IOException {
        try {
            searcher.getIndexReader().close();
        } finally {
            directory.close();
        }
    }

    public void testLogging() throws IOException {
        RankerQuery query1 = buildQuery("foo");
        RankerQuery query2 = buildQuery("bar");
        LoggingFetchSubPhase.HitLogConsumer logger1 = new LoggingFetchSubPhase.HitLogConsumer("logger1", query1.featureSet(), true);
        LoggingFetchSubPhase.HitLogConsumer logger2 = new LoggingFetchSubPhase.HitLogConsumer("logger2", query2.featureSet(), false);
        query1 = query1.toLoggerQuery(logger1);
        query2 = query2.toLoggerQuery(logger2);
        BooleanQuery query = new BooleanQuery.Builder()
                .add(new BooleanClause(query1, BooleanClause.Occur.MUST))
                .add(new BooleanClause(query2, BooleanClause.Occur.MUST))
                .build();
        Weight weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1.0F);
        List<LoggingFetchSubPhase.HitLogConsumer> loggers = Arrays.asList(logger1, logger2);
        LoggingFetchSubPhaseProcessor processor = new LoggingFetchSubPhaseProcessor(() -> new Tuple<>(weight, loggers));

        SearchHit[] hits = preprocessRandomHits(processor);
        for (SearchHit hit : hits) {
            assertTrue(docs.containsKey(hit.getId()));
            Document d = docs.get(hit.getId());
            assertTrue(hit.getFields().containsKey("_ltrlog"));
            Map<String, List<Map<String, Object>>> logs = hit.getFields().get("_ltrlog").getValue();
            assertTrue(logs.containsKey("logger1"));
            assertTrue(logs.containsKey("logger2"));

            List<Map<String, Object>> log1 = logs.get("logger1");
            List<Map<String, Object>> log2 = logs.get("logger2");
            if (d.get("text").equals("foo")) {
                assertEquals(log1.get(0).get("name"), "text_feat");
                assertTrue(log1.get(0).containsKey("value"));
                assertTrue((Float) log1.get(0).get("value") > 0F);
                assertFalse(log2.get(0).containsKey("value"));
            } else {
                assertEquals(log1.get(0).get("name"), "text_feat");
                assertTrue(log1.get(0).containsKey("value"));
                assertEquals((Float) 0.0F, log1.get(0).get("value"));
                assertTrue(log2.get(0).containsKey("value"));
                assertTrue((Float)log2.get(0).get("value") > 0F);
            }
            int bits = (int)(long) d.getField("score").numericValue();
            float rawScore = Float.intBitsToFloat(bits);
            double expectedScore = rawScore*FACTOR;
            expectedScore = Math.log1p(expectedScore+1);
            assertEquals((float) expectedScore, (Float)log1.get(1).get("value"), Math.ulp((float)expectedScore));
            assertEquals((float) expectedScore, (Float)log1.get(1).get("value"), Math.ulp((float)expectedScore));
        }
    }

    public SearchHit[] preprocessRandomHits(FetchSubPhaseProcessor processor) throws IOException {
        int minHits = TestUtil.nextInt(random(), 5, 10);
        int maxHits = TestUtil.nextInt(random(), minHits, minHits+10);
        List<SearchHit> hits = new ArrayList<>(maxHits);
        searcher.search(new MatchAllDocsQuery(), new SimpleCollector() {
            /**
             * Indicates what features are required from the scorer.
             */
            @Override
            public ScoreMode scoreMode() {
                return ScoreMode.COMPLETE_NO_SCORES;
            }

            LeafReaderContext context;

            @Override
            protected void doSetNextReader(LeafReaderContext context) throws IOException {
                super.doSetNextReader(context);
                this.context = context;
                processor.setNextReader(context);
            }

            @Override
            public void collect(int doc) throws IOException {
                if (hits.size() < minHits || (random().nextBoolean() && hits.size() < maxHits)) {
                    Document d = context.reader().document(doc);
                    String id = d.get("id");
                    SearchHit hit = new SearchHit(
                        doc,
                        id,
                        new Text("text"),
                        random().nextBoolean() ? new HashMap<>() : null,
                        null
                    );
                    processor.process(new FetchSubPhase.HitContext(hit, context, doc));
                    hits.add(hit);
                }
            }

        });
        assert hits.size() >= minHits;
        return hits.toArray(new SearchHit[0]);
    }

    public static Document buildDoc(String text, float value) {
        String id = UUID.randomUUID().toString();
        Document d = new Document();
        d.add(newStringField("id", id, Field.Store.YES));
        d.add(newStringField("text", text, Field.Store.NO));
        d.add(new FloatDocValuesField("score", value));
        return d;
    }

    public RankerQuery buildQuery(String text) {
        List<PrebuiltFeature> features = new ArrayList<>(2);
        features.add(new PrebuiltFeature("text_feat", new TermQuery(new Term("text", text))));
        features.add(new PrebuiltFeature("score_feat", buildFunctionScore()));
        PrebuiltFeatureSet set = new PrebuiltFeatureSet("my_set", features);
        LtrRanker ranker = LinearRankerTests.generateRandomRanker(set.size());
        return RankerQuery.build(new PrebuiltLtrModel("my_model", ranker, set));

    }

    public Query buildFunctionScore() {
        FieldValueFactorFunction fieldValueFactorFunction = new FieldValueFactorFunction("score", FACTOR, LN2P, 0D,
                new SortedNumericIndexFieldData("score", FLOAT));
        return new FunctionScoreQuery(new MatchAllDocsQuery(),
                fieldValueFactorFunction, CombineFunction.MULTIPLY, 0F, Float.MAX_VALUE);
    }
}