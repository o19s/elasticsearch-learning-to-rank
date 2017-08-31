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

import com.o19s.es.ltr.feature.PrebuiltFeature;
import com.o19s.es.ltr.feature.PrebuiltFeatureSet;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.ranker.LogLtrRanker;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.ranklib.DenseProgramaticDataPoint;
import com.o19s.es.ltr.ranker.ranklib.RanklibRanker;
import com.o19s.es.ltr.ranker.ranklib.learning.DataPoint;
import com.o19s.es.ltr.ranker.ranklib.learning.Ranker;
import com.o19s.es.ltr.ranker.ranklib.utils.MyThreadPool;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.misc.SweetSpotSimilarity;
import org.apache.lucene.queries.BlendedTermQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.AfterEffectB;
import org.apache.lucene.search.similarities.AxiomaticF3LOG;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelBE;
import org.apache.lucene.search.similarities.BooleanSimilarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.DFISimilarity;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.DistributionLL;
import org.apache.lucene.search.similarities.IBSimilarity;
import org.apache.lucene.search.similarities.IndependenceChiSquared;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.LambdaDF;
import org.apache.lucene.search.similarities.NormalizationH1;
import org.apache.lucene.search.similarities.NormalizationH3;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Created by doug on 12/24/16.
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "RankURL does this when training models... ")
public class LtrQueryTests extends LuceneTestCase {
    // Number of ULPs allowed when checking scores equality
    private static final int SCORE_NB_ULP_PREC = 1;

    private Field newField(String name, String value, Store stored) {
        FieldType tagsFieldType = new FieldType();
        tagsFieldType.setStored(stored == Store.YES);
        IndexOptions idxOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;
        tagsFieldType.setIndexOptions(idxOptions);
        return new Field(name, value, tagsFieldType);
    }

    private IndexSearcher searcherUnderTest;
    private RandomIndexWriter indexWriterUnderTest;
    private IndexReader indexReaderUnderTest;
    private Directory dirUnderTest;
    private Similarity similarity;

    // docs with doc ids array index
    private final String[] docs = new String[] { "how now brown cow",
                                   "brown is the color of cows",
                                   "brown cow",
                                   "banana cows are yummy"};

    @Before
    public void setupIndex() throws IOException {
        dirUnderTest = newDirectory();
        List<Similarity> sims = Arrays.asList(
                new ClassicSimilarity(),
                new SweetSpotSimilarity(), // extends Classic
                new BM25Similarity(),
                new LMDirichletSimilarity(),
                new BooleanSimilarity(),
                new LMJelinekMercerSimilarity(0.2F),
                new AxiomaticF3LOG(0.5F, 10),
                new DFISimilarity(new IndependenceChiSquared()),
                new DFRSimilarity(new BasicModelBE(), new AfterEffectB(), new NormalizationH1()),
                new IBSimilarity(new DistributionLL(), new LambdaDF(), new NormalizationH3())
            );
        similarity = sims.get(random().nextInt(sims.size()));

        indexWriterUnderTest = new RandomIndexWriter(random(), dirUnderTest, newIndexWriterConfig().setSimilarity(similarity));
        for (int i = 0; i < docs.length; i++) {
            Document doc = new Document();
            doc.add(newStringField("id", "" + i, Field.Store.YES));
            doc.add(newField("field", docs[i], Store.YES));
            indexWriterUnderTest.addDocument(doc);
        }
        indexWriterUnderTest.commit();
        indexWriterUnderTest.forceMerge(1);
        indexWriterUnderTest.flush();


        indexReaderUnderTest = indexWriterUnderTest.getReader();
        searcherUnderTest = newSearcher(indexReaderUnderTest);
        searcherUnderTest.setSimilarity(similarity);
    }

    public Map<String, Map<Integer, Float>> getFeatureScores(List<PrebuiltFeature> features) throws IOException {
        Map<String, Map<Integer, Float>> featuresPerDoc = new HashMap<>();
        PrebuiltFeatureSet set = new PrebuiltFeatureSet("test", features);

        Map<Integer, Float> collectedScores = new HashMap<>();
        LogLtrRanker.LogConsumer logger = new LogLtrRanker.LogConsumer() {
            @Override
            public void accept(int featureOrdinal, float score) {
                collectedScores.put(featureOrdinal, score);
            }

            @Override
            public void reset() {
                collectedScores.clear();
            }
        };
        RankerQuery query = RankerQuery.buildLogQuery(logger, set, null, Collections.emptyMap());

        searcherUnderTest.search(query, new SimpleCollector() {
            private LeafReaderContext context;
            private Scorer scorer;
            @Override
            public boolean needsScores() {
                return true;
            }

            @Override
            public void setScorer(Scorer scorer) throws IOException {
                this.scorer = scorer;
            }

            @Override
            protected void doSetNextReader(LeafReaderContext context) throws IOException {
                this.context = context;
            }

            @Override
            public void collect(int doc) throws IOException {
                scorer.score();
                Document d = context.reader().document(doc);
                featuresPerDoc.put(d.get("id"), new HashMap<>(collectedScores));
            }
        });

        return featuresPerDoc;
    }

    public List<DataPoint> makeQueryJudgements(int qid,
                                               Map<String, Map<Integer,Float>> featuresPerDoc,
                                               int modelSize,
                                               Float[] relevanceGradesPerDoc) {
        assert(featuresPerDoc.size() == docs.length);
        assert(relevanceGradesPerDoc.length == docs.length);

        List<DataPoint> rVal = new ArrayList<>();
        SortedMap<Integer, DataPoint> points = new TreeMap<>();
        featuresPerDoc.forEach((doc, vector) -> {
            DenseProgramaticDataPoint dp = new DenseProgramaticDataPoint(modelSize);
            int docId = Integer.decode(doc);
            dp.setLabel(relevanceGradesPerDoc[docId]);
            dp.setID(String.valueOf(qid));
            vector.forEach(dp::setFeatureScore);
            points.put(docId, dp);
        });
        points.forEach((k, v) -> rVal.add(v));
        return rVal;
    }

    public void checkFeatureNames(Explanation expl, List<PrebuiltFeature> features) {
        Explanation[] expls = expl.getDetails();
        int ftrIdx = 0;
        for (Explanation ftrExpl: expls) {
            String ftrName = features.get(ftrIdx).name();
            String expectedFtrName;
            if (ftrName == null) {
                expectedFtrName = "Feature " + ftrIdx + ":";
            } else {
                expectedFtrName = "Feature " + ftrIdx + "(" + ftrName + "):";
            }

            String ftrExplainStart = ftrExpl.getDescription().substring(0,expectedFtrName.length());
            assertEquals(expectedFtrName, ftrExplainStart);

            ftrIdx++;
        }
    }

    /*
    public void checkModelWithFeatures(List<PrebuiltFeature> features) throws IOException {
        // Each RankList needed for training corresponds to one query,
        // or that apperas how RankLib wants the data
        List<RankList> samples = new ArrayList<>();

        Map<String, Map<Integer,Float>> featuresPerDoc = getFeatureScores(features);

        RankList rl = new RankList(makeQueryJudgements(0, featuresPerDoc, features.size(),
                new Float[] {3.0f, 2.0f, 4.0f, 0.0f}));
        samples.add(rl);

        // each RankList appears to correspond to a
        // query
        RankerTrainer trainer = new RankerTrainer();
        Ranker ranker = trainer.train(/*what type of model ot train/RANKER_TYPE.LAMBDAMART,
                                      /*The training data samples,
                                      /*which features to usenew int[] {1,2}
                                      /*how to score ranking new NDCGScorer());
        float[] scores = new float[] {(float)ranker.eval(rl.get(0)), (float)ranker.eval(rl.get(1)),
                (float)ranker.eval(rl.get(2)), (float)ranker.eval(rl.get(3))};



        // Ok now lets rerun that as a Lucene Query

        RankerQuery ltrQuery = toRankerQuery(features, ranker);
        TopDocs topDocs = searcherUnderTest.search(ltrQuery, 10);
        ScoreDoc[] scoreDocs = topDocs.scoreDocs;
        assert(scoreDocs.length == docs.length);
        ScoreDoc sc = scoreDocs[0];
        scoreDocs[0] = scoreDocs[2];
        scoreDocs[2] = sc;

        for (ScoreDoc scoreDoc: scoreDocs) {
            assertScoresMatch(features, scores, ltrQuery, scoreDoc);
        }

        // Try again with a model serialized

        String modelAsStr = ranker.model();
        RankerFactory rankerFactory = new RankerFactory();
        Ranker rankerAgain = rankerFactory.loadRankerFromString(modelAsStr);
        float[] scoresAgain = new float[] {(float)rankerAgain.eval(rl.get(0)), (float)rankerAgain.eval(rl.get(1)),
                (float)rankerAgain.eval(rl.get(2)), (float)rankerAgain.eval(rl.get(3))};

        ltrQuery = toRankerQuery(features, rankerAgain);
        topDocs = searcherUnderTest.search(ltrQuery, 10);
        scoreDocs = topDocs.scoreDocs;
        assert(scoreDocs.length == docs.length);
        for (ScoreDoc scoreDoc: scoreDocs) {
            assertScoresMatch(features, scoresAgain, ltrQuery, scoreDoc);
        }
    }
    */

    private void assertScoresMatch(List<PrebuiltFeature> features, float[] scores,
                                   RankerQuery ltrQuery, ScoreDoc scoreDoc) throws IOException {
        Document d = searcherUnderTest.doc(scoreDoc.doc);
        String idVal = d.get("id");
        int docId = Integer.decode(idVal);
        float modelScore = scores[docId];
        float queryScore = scoreDoc.score;

        assertEquals("Scores match with similarity " + similarity.getClass(), modelScore,
                queryScore, SCORE_NB_ULP_PREC *Math.ulp(modelScore));

        if (!(similarity instanceof TFIDFSimilarity)) {
            // There are precision issues with these similarities when using explain
            // It produces 0.56103003 for feat:0 in doc1 using score() but 0.5610301 using explain
            Explanation expl = searcherUnderTest.explain(ltrQuery, docId);

            assertEquals("Explain scores match with similarity " + similarity.getClass(), expl.getValue(),
                    queryScore, 5 * Math.ulp(modelScore));
            checkFeatureNames(expl, features);
        }
    }

    private RankerQuery toRankerQuery(List<PrebuiltFeature> features, Ranker ranker) {
        LtrRanker ltrRanker = new RanklibRanker(ranker);
        PrebuiltLtrModel model = new PrebuiltLtrModel(ltrRanker.name(), ltrRanker, new PrebuiltFeatureSet(null, features));
        return RankerQuery.build(model);
    }

    public void testTrainModel() throws IOException {
        //     public LambdaMART(List<RankList> samples, int[] features, MetricScorer scorer) {
        String userQuery = "brown cow";
        List<Query> features = Arrays.asList(
                new TermQuery(new Term("field",  userQuery.split(" ")[0])),
                new PhraseQuery("field", userQuery.split(" ")));
       // checkModelWithFeatures(toPrebuildFeatureWithNoName(features));
    }

    public void testExplainWithNames() throws IOException {
        //     public LambdaMART(List<RankList> samples, int[] features, MetricScorer scorer) {
        String userQuery = "brown cow";
        List<PrebuiltFeature> features = Arrays.asList(
                new PrebuiltFeature("funky_term_q", new TermQuery(new Term("field",  userQuery.split(" ")[0]))),
                new PrebuiltFeature("funky_phrase_q", new PhraseQuery("field", userQuery.split(" "))));
        //checkModelWithFeatures(features);
    }

    public void testOnRewrittenQueries() throws IOException {
        String userQuery = "brown cow";

        Term[] termsToBlend = new Term[]{new Term("field",  userQuery.split(" ")[0])};

        Query blended = BlendedTermQuery.booleanBlendedQuery(termsToBlend, false);
        List<Query> features = Arrays.asList(new TermQuery(new Term("field",  userQuery.split(" ")[0])), blended);

        //checkModelWithFeatures(toPrebuildFeatureWithNoName(features));
    }

    private List<PrebuiltFeature> toPrebuildFeatureWithNoName(List<Query> features) {
        return features.stream()
                .map(x -> new PrebuiltFeature(null, x))
                .collect(Collectors.toList());
    }

    public void testNoMatchQueries() throws IOException {
        String userQuery = "brown cow";

        Term[] termsToBlend = new Term[]{new Term("field",  userQuery.split(" ")[0])};

        Query blended = BlendedTermQuery.booleanBlendedQuery(termsToBlend, false);
        List<PrebuiltFeature> features = Arrays.asList(
                new PrebuiltFeature(null, new TermQuery(new Term("field",  "missingterm"))),
                new PrebuiltFeature(null, blended));

        //checkModelWithFeatures(features);
    }

    @After
    public void closeStuff() throws IOException {
        indexReaderUnderTest.close();
        indexWriterUnderTest.close();
        dirUnderTest.close();
        // Ranklib's singleton instance
    }

    @AfterClass
    public static void closeOtherStuff() {
        MyThreadPool.getInstance().shutdown();
    }
}
