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

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.learning.RANKER_TYPE;
import ciir.umass.edu.learning.RankList;
import ciir.umass.edu.learning.Ranker;
import ciir.umass.edu.learning.RankerFactory;
import ciir.umass.edu.learning.RankerTrainer;
import ciir.umass.edu.metric.NDCGScorer;
import ciir.umass.edu.utilities.MyThreadPool;
import com.o19s.es.ltr.feature.PrebuiltDerivedFeature;
import com.o19s.es.ltr.feature.PrebuiltFeature;
import com.o19s.es.ltr.feature.PrebuiltFeatureSet;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.ranklib.DenseProgramaticDataPoint;
import com.o19s.es.ltr.ranker.ranklib.RanklibRanker;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.AfterEffectB;
import org.apache.lucene.search.similarities.AxiomaticF3LOG;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelBE;
import org.apache.lucene.search.similarities.BooleanSimilarity;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@LuceneTestCase.SuppressSysoutChecks(bugUrl = "RankURL does this when training models... ")
public class DerivedFeatureTests extends LuceneTestCase {
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
                // TODO: re-add disabled sims when features are logged from the ltr query itself
                // new ClassicSimilarity(),
                // new SweetSpotSimilarity(), // extends Classic
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
            doc.add(newStringField("id", "" + i, Store.YES));
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

    public List<List<Float>> getFeatureScores(List<PrebuiltFeature> features,
                                              List<PrebuiltDerivedFeature> derivedFeatures) throws IOException {
        ArrayList<List<Float>> featuresPerDoc = new ArrayList<>(docs.length);
        // initialize feature outputs
        for (int i = 0; i < docs.length; i++) {
            featuresPerDoc.add(i, new ArrayList<>(features.size() + derivedFeatures.size()));
            for (int ftrIdx = 0; ftrIdx < features.size() + derivedFeatures.size(); ftrIdx++ ) {
                featuresPerDoc.get(i).add(ftrIdx, 0.0f);
            }
        }

        int ftrIdx = 0;
        for (PrebuiltFeature pfeature: features) {
            Query feature = pfeature.getPrebuiltQuery();
            TopDocs topDocs = searcherUnderTest.search(feature, 10);
            ScoreDoc[] scoreDocs = topDocs.scoreDocs;
            for (ScoreDoc scoreDoc: scoreDocs) {
                Document d = searcherUnderTest.doc(scoreDoc.doc);
                String idVal = d.get("id");
                int docId = Integer.decode(idVal);

                featuresPerDoc.get(docId).set(ftrIdx, scoreDoc.score);
            }
            ftrIdx++;
        }

        // TODO: Actually evaluate scripts. The value below matches the single test case
        for (PrebuiltDerivedFeature df : derivedFeatures) {
            for(List<Float> docFeatures : featuresPerDoc) {
                docFeatures.set(ftrIdx, (docFeatures.get(0) * 2.0f));
            }

            ftrIdx++;
        }

        return featuresPerDoc;
    }

    public List<DataPoint> makeQueryJudgements(int qid,
                                               List<List<Float>> featuresPerDoc,
                                               Float[] relevanceGradesPerDoc) {
        assert(featuresPerDoc.size() == docs.length);
        assert(relevanceGradesPerDoc.length == docs.length);

        List<DataPoint> rVal = new ArrayList<>();

        for (int i = 0; i < docs.length; i++) {
            List<Float> featuresForDoc = featuresPerDoc.get(i);

            DataPoint dp = new DenseProgramaticDataPoint(featuresForDoc.size());
            dp.setID(Integer.toString(qid)); /*query ID*/
            dp.setLabel(relevanceGradesPerDoc[i]); /*labeled relevance judgement*/

            // set each feature

            for (int ftrIdx = 0; ftrIdx < featuresForDoc.size(); ftrIdx++) {
                /*RankLib features are 1 based*/
                dp.setFeatureValue(ftrIdx + 1, featuresForDoc.get(ftrIdx));
            }
            rVal.add(i, dp);
        }
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

    public void checkModelWithFeatures(List<PrebuiltFeature> features, List<PrebuiltDerivedFeature> derivedFeatures) throws IOException {
        // Each RankList needed for training corresponds to one query,
        // or that apperas how RankLib wants the data
        List<RankList> samples = new ArrayList<>();

        List<List<Float>> featuresPerDoc = getFeatureScores(features, derivedFeatures);

        RankList rl = new RankList(makeQueryJudgements(0, featuresPerDoc,
                new Float[] {3.0f, 2.0f, 4.0f, 0.0f}));
        samples.add(rl);

        // each RankList appears to correspond to a
        // query
        RankerTrainer trainer = new RankerTrainer();
        Ranker ranker = trainer.train(/*what type of model ot train*/RANKER_TYPE.LINEAR_REGRESSION,
                                      /*The training data*/ samples,
                                      /*which features to use*/new int[] {1,2,3}
                                      /*how to score ranking*/, new NDCGScorer());
        float[] scores = new float[] {(float)ranker.eval(rl.get(0)), (float)ranker.eval(rl.get(1)),
                (float)ranker.eval(rl.get(2)), (float)ranker.eval(rl.get(3))};



        // Ok now lets rerun that as a Lucene Query

        RankerQuery ltrQuery = toRankerQuery(features, derivedFeatures, ranker);
        TopDocs topDocs = searcherUnderTest.search(ltrQuery, 10);
        ScoreDoc[] scoreDocs = topDocs.scoreDocs;
        assert(scoreDocs.length == docs.length);
        for (ScoreDoc scoreDoc: scoreDocs) {
            assertScoresMatch(features, scores, ltrQuery, scoreDoc);
        }

        // Try again with a model serialized

        String modelAsStr = ranker.model();
        RankerFactory rankerFactory = new RankerFactory();
        Ranker rankerAgain = rankerFactory.loadRankerFromString(modelAsStr);
        float[] scoresAgain = new float[] {(float)rankerAgain.eval(rl.get(0)), (float)rankerAgain.eval(rl.get(1)),
                (float)rankerAgain.eval(rl.get(2)), (float)rankerAgain.eval(rl.get(3))};

        ltrQuery = toRankerQuery(features, derivedFeatures, rankerAgain);
        topDocs = searcherUnderTest.search(ltrQuery, 10);
        scoreDocs = topDocs.scoreDocs;
        assert(scoreDocs.length == docs.length);
        for (ScoreDoc scoreDoc: scoreDocs) {
            assertScoresMatch(features, scoresAgain, ltrQuery, scoreDoc);
        }
    }

    private void assertScoresMatch(List<PrebuiltFeature> features, float[] scores,
                                   RankerQuery ltrQuery, ScoreDoc scoreDoc) throws IOException {
        Document d = searcherUnderTest.doc(scoreDoc.doc);
        String idVal = d.get("id");
        int docId = Integer.decode(idVal);
        float modelScore = scores[docId];
        float queryScore = scoreDoc.score;

        assertEquals("Scores match with similarity " + similarity.getClass(), modelScore,
                queryScore, SCORE_NB_ULP_PREC *Math.ulp(modelScore));

        Explanation expl = searcherUnderTest.explain(ltrQuery, docId);
        assertEquals("Explain scores match with similarity " + similarity.getClass(), expl.getValue(),
                queryScore, 5*Math.ulp(modelScore));
        checkFeatureNames(expl, features);
    }

    private RankerQuery toRankerQuery(List<PrebuiltFeature> features, List<PrebuiltDerivedFeature> derivedFeatures, Ranker ranker) {
        LtrRanker ltrRanker = new RanklibRanker(ranker);
        PrebuiltLtrModel model = new PrebuiltLtrModel(ltrRanker.name(), ltrRanker, new PrebuiltFeatureSet(null, features, derivedFeatures));
        return RankerQuery.build(model);
    }

    public void testTrainModel() throws IOException {
        //     public LambdaMART(List<RankList> samples, int[] features, MetricScorer scorer) {
        String userQuery = "brown cow";
        List<Query> features = Arrays.asList(
                new TermQuery(new Term("field",  userQuery.split(" ")[0])),
                new PhraseQuery("field", userQuery.split(" ")));

        List<PrebuiltDerivedFeature> dfs = new ArrayList<>();
        dfs.add(new PrebuiltDerivedFeature("double_it", "$0 * 2"));

        checkModelWithFeatures(toPrebuildFeatureWithNoName(features), dfs);
    }

    private List<PrebuiltFeature> toPrebuildFeatureWithNoName(List<Query> features) {
        return features.stream()
                .map(x -> new PrebuiltFeature(null, x))
                .collect(Collectors.toList());
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
