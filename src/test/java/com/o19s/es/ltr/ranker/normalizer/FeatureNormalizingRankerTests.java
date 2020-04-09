package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.feature.FeatureNormalizerSet;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import org.apache.lucene.util.LuceneTestCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FeatureNormalizingRankerTests extends LuceneTestCase {

    private FeatureNormalizerSet getMorkFtrNormSet(int[] activeOrds) {
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

            @Override
            public int[] getNormalizedOrds() {
                return activeOrds;
            }
        };

        return add1NormSet;
    }

    private float[] castToFloatArr(double[] x) {
        float[] floats = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            floats[i] = (float)x[i];
        }
        return floats;
    }

    public void testFeatureNormalization() {

        int vectorSize = 5;

        Set<Integer> activeOrds = new HashSet<Integer>();

        for (int i = 0; i < vectorSize; i++) {
            if (random().nextBoolean()) {
                activeOrds.add(i);
            }
        }

        int[] activeOrdsArr = activeOrds.stream().mapToInt(Integer::intValue).toArray();

        FeatureNormalizerSet add1NormSet = getMorkFtrNormSet(activeOrdsArr);

        float weights[] = castToFloatArr(random().doubles(vectorSize).toArray());

        LtrRanker ranker = new LinearRanker(weights);
        FeatureNormalizingRanker ftrNormRanker = new FeatureNormalizingRanker(ranker, add1NormSet);

        LtrRanker.FeatureVector ftrVect = ftrNormRanker.newFeatureVector(null);

        float values[] = castToFloatArr(random().doubles(vectorSize).toArray());

        float expectedScore = 0;
        for (int i = 0; i < vectorSize; i++) {
            ftrVect.setFeatureScore(i, values[i]);
            if (activeOrds.contains(i)) {
                expectedScore += weights[i] * (values[i] + 1.0f);
            } else {
                expectedScore += weights[i] * (values[i]);
            }
        }

        assertEquals(ftrNormRanker.score(ftrVect), expectedScore, 0.01f);

    }

}
