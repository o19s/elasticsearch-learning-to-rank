package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.feature.FeatureNormalizerSet;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import org.apache.lucene.util.LuceneTestCase;

public class FeatureNormalizingRankerTests extends LuceneTestCase {

    public void testFeatureNormalization() {
        FeatureNormalizerSet add1NormSet = new FeatureNormalizerSet() {
            @Override
            public Normalizer getNomalizer(int featureOrd) {
                return new Normalizer() {
                    @Override
                    public float normalize(float val) {
                        return 1.0f + val;
                    }
                };
            }
        };

        float w1 = random().nextFloat();
        float w2 = random().nextFloat();

        LtrRanker ranker = new LinearRanker(new float[]{w1, w2});
        FeatureNormalizingRanker ftrNormRanker = new FeatureNormalizingRanker(ranker, add1NormSet);

        LtrRanker.FeatureVector ftrVect = ftrNormRanker.newFeatureVector(null);

        float x1 = random().nextFloat();
        float x2 = random().nextFloat();

        ftrVect.setFeatureScore(0, x1);
        ftrVect.setFeatureScore(1, x2);

        float expectedScore = ((x1 + 1.0f) * w1) + ((x2 + 1.0f) * w2);

        assertEquals(ftrNormRanker.score(ftrVect), expectedScore, 0.01f);

    }
}
