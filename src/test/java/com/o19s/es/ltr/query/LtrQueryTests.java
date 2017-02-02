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
import com.o19s.es.ltr.query.DenseProgramaticDataPoint;
import com.o19s.es.ltr.query.LtrQuery;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.queries.BlendedTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.Field.Store;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by doug on 12/24/16.
 */
@LuceneTestCase.SuppressSysoutChecks(bugUrl = "RankURL does this when training models... ")
public class LtrQueryTests extends LuceneTestCase {

    Field newField(String name, String value, Store stored) {
        FieldType tagsFieldType = new FieldType();
        tagsFieldType.setStored(stored == Store.YES);
        IndexOptions idxOptions = IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;
        tagsFieldType.setIndexOptions(idxOptions);
        return new Field(name, value, tagsFieldType);
    }

    IndexSearcher searcherUnderTest;
    RandomIndexWriter indexWriterUnderTest;
    IndexReader indexReaderUnderTest;
    Directory dirUnderTest;
    Ranker ltrModel;

    // docs with doc ids array index
    String[] docs = new String[] { "how now brown cow",
                                   "brown is the color of cows",
                                   "brown cow",
                                   "banana cows are yummy"};

    @Before
    public void setupIndex() throws IOException {
        dirUnderTest = newDirectory();

        indexWriterUnderTest = new RandomIndexWriter(random(), dirUnderTest);
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
        //hardcode the similarity, we expect consisten scoring
        searcherUnderTest.setSimilarity(new ClassicSimilarity());
    }

    public List<List<Float>> getFeatureScores(List<Query> features) throws IOException {


        ArrayList<List<Float>> featuresPerDoc = new ArrayList<List<Float>>(docs.length);
        // initialize feature outputs
        for (int i = 0; i < docs.length; i++) {
            featuresPerDoc.add(i, new ArrayList<Float>(features.size()));
            for (int ftrIdx = 0; ftrIdx < features.size(); ftrIdx++ ) {
                featuresPerDoc.get(i).add(ftrIdx, 0.0f);
            }
        }


        int ftrIdx = 0;
        for (Query feature: features) {
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
        return featuresPerDoc;

    }

    public List<DataPoint> makeQueryJudgements(int qid,
                                               List<List<Float>> featuresPerDoc,
                                               Float[] relevanceGradesPerDoc) {
        assert(featuresPerDoc.size() == docs.length);
        assert(relevanceGradesPerDoc.length == docs.length);

        List<DataPoint> rVal = new ArrayList<DataPoint>();

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

    public void checkModelWithFeatures(List<Query> features) throws IOException {
        // Each RankList needed for training corresponds to one query,
        // or that apperas how RankLib wants the data
        List<RankList> samples = new ArrayList<RankList>();

        List<List<Float>> featuresPerDoc = getFeatureScores(features);
        int numFeatures = featuresPerDoc.get(0).size();

        RankList rl = new RankList(makeQueryJudgements(0, featuresPerDoc,
                new Float[] {3.0f, 2.0f, 4.0f, 0.0f}));
        samples.add(rl);

        // each RankList appears to correspond to a
        // query
        RankerTrainer trainer = new RankerTrainer();
        Ranker ranker = trainer.train(/*what type of model ot train*/RANKER_TYPE.LAMBDAMART,
                                      /*The training data*/ samples,
                                      /*which features to use*/new int[] {1,2}
                                      /*how to score ranking*/, new NDCGScorer());
        float[] scores = new float[] {(float)ranker.eval(rl.get(0)), (float)ranker.eval(rl.get(1)),
                (float)ranker.eval(rl.get(2)), (float)ranker.eval(rl.get(3))};



        // Ok now lets rerun that as a Lucene Query

        LtrQuery ltrQuery = new LtrQuery(features, ranker);
        TopDocs topDocs = searcherUnderTest.search(ltrQuery, 10);
        ScoreDoc[] scoreDocs = topDocs.scoreDocs;
        assert(scoreDocs.length == docs.length);
        for (ScoreDoc scoreDoc: scoreDocs) {
            Document d = searcherUnderTest.doc(scoreDoc.doc);
            String idVal = d.get("id");
            int docId = Integer.decode(idVal);
            float modelScore = scores[docId];
            float queryScore = scoreDoc.score;

            assertEquals(modelScore, queryScore, 0.01);
        }

        // Try again with a model serialized

        String modelAsStr = ranker.model();
        RankerFactory rankerFactory = new RankerFactory();
        Ranker rankerAgain = rankerFactory.loadRankerFromString(modelAsStr);
        float[] scoresAgain = new float[] {(float)rankerAgain.eval(rl.get(0)), (float)rankerAgain.eval(rl.get(1)),
                (float)rankerAgain.eval(rl.get(2)), (float)rankerAgain.eval(rl.get(3))};

        ltrQuery = new LtrQuery(features, rankerAgain);
        topDocs = searcherUnderTest.search(ltrQuery, 10);
        scoreDocs = topDocs.scoreDocs;
        assert(scoreDocs.length == docs.length);
        for (ScoreDoc scoreDoc: scoreDocs) {
            Document d = searcherUnderTest.doc(scoreDoc.doc);
            String idVal = d.get("id");
            int docId = Integer.decode(idVal);
            float modelScore = scoresAgain[docId];
            float queryScore = scoreDoc.score;
            assertEquals(modelScore, queryScore, 0.01);
        }
    }


    public void testTrainModel() throws IOException {
        //     public LambdaMART(List<RankList> samples, int[] features, MetricScorer scorer) {



        String userQuery = "brown cow";
        List<Query> features = Arrays.asList(new Query[] {new TermQuery(new Term("field",  userQuery.split(" ")[0])),
                                                          new PhraseQuery("field", userQuery.split(" "))});
        checkModelWithFeatures(features);


    }

    public void testOnRewrittenQueries() throws IOException {
        String userQuery = "brown cow";

        Term[] termsToBlend = new Term[]{new Term("field",  userQuery.split(" ")[0])};

        Query blended = BlendedTermQuery.booleanBlendedQuery(termsToBlend, false);
        List<Query> features = Arrays.asList(new Query[] {new TermQuery(new Term("field",  userQuery.split(" ")[0])), blended});

        checkModelWithFeatures(features);
    }


    @After
    public void closeStuff() throws IOException {
        indexReaderUnderTest.close();
        indexWriterUnderTest.close();
        dirUnderTest.close();
        // Ranklib's singleton instance
        MyThreadPool.getInstance().shutdown();
    }



}
