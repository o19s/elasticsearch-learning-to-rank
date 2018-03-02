package com.o19s.es.ltr.ranker;

import org.apache.lucene.util.LuceneTestCase;

public class DenseFeatureMatrixTests extends LuceneTestCase {
    public void testGetSetFeatureScoreForDoc() {
        int nDocs = random().nextInt(20) + 1;
        int nFeat = random().nextInt(20) + 1;
        DenseFeatureMatrix matrix = new DenseFeatureMatrix(nDocs, nFeat);
        float featValue = random().nextFloat();

        int doc = random().nextInt(nDocs);
        int feat = random().nextInt(nFeat);

        matrix.setFeatureScoreForDoc(doc, feat, featValue);
        assertEquals(featValue, matrix.getFeatureScoreForDoc(doc, feat), Math.ulp(featValue));
        assertEquals(featValue, matrix.scores[doc][feat], Math.ulp(featValue));
    }

    public void testDocSize() {
        int nDocs = random().nextInt(20) + 1;
        int nFeat = random().nextInt(20) + 1;
        DenseFeatureMatrix matrix = new DenseFeatureMatrix(nDocs, nFeat);
        assertEquals(nDocs, matrix.docSize());
        assertEquals(nDocs, matrix.scores.length);
        assertEquals(nFeat, matrix.scores[0].length);
    }

    public void testReset() {
        int nDocs = random().nextInt(20) + 1;
        int nFeat = random().nextInt(20) + 1;
        DenseFeatureMatrix matrix = new DenseFeatureMatrix(nDocs, nFeat);

        float featValue = random().nextFloat() + 1F;
        int doc = random().nextInt(nDocs);
        int feat = random().nextInt(nFeat);

        matrix.setFeatureScoreForDoc(doc, feat, featValue);
        assertNotEquals(matrix.getFeatureScoreForDoc(doc, feat), 0F);
        matrix.reset();
        assertEquals(matrix.getFeatureScoreForDoc(doc, feat), 0F, Math.ulp(0F));
    }
}