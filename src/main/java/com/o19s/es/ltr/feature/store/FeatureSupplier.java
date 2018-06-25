package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.LtrRanker;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Supplier;


public class FeatureSupplier extends AbstractMap<String, Float> implements Supplier<LtrRanker.FeatureVector> {
    private Supplier<LtrRanker.FeatureVector> vectorSupplier;
    private final FeatureSet featureSet;

    FeatureSupplier(FeatureSet featureSet) {
        this.featureSet = featureSet;
    }

    @Override
    public LtrRanker.FeatureVector get() {
        return vectorSupplier.get();
    }

    public void set(Supplier<LtrRanker.FeatureVector> supplier) {
        this.vectorSupplier = supplier;
    }

    /**
     * Returns {@code true} if this map contains a mapping for the specified
     * featureName.
     *
     * @param featureName featureName whose presence in this map is to be tested
     * @return {@code true} if this map contains a mapping for the specified
     * featureName
     * @throws ClassCastException if the key is of an inappropriate type for
     *                            this map
     */
    @Override
    public boolean containsKey(Object featureName) {
        return featureSet.hasFeature((String) featureName);
    }

    /**
     * Returns the score to which the specified featureName is mapped,
     * or {@code null} if this map contains no mapping for the featureName.
     *
     * @param featureName the featureName whose associated score is to be returned
     * @return the score to which the specified featureName is mapped, or
     * {@code null} if this map contains no mapping for the key
     */
    @Override
    public Float get(Object featureName) {
        int featureOrdinal;
        try {
            featureOrdinal = featureSet.featureOrdinal((String) featureName);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return vectorSupplier.get().getFeatureScore(featureOrdinal);
    }

    /**
     * Strictly speaking the only methods of this {@code Map} needed are
     * containsKey and get. The remaining methods help fix issues like
     * - deserialization of FEATURE_VECTOR parameter as a Map object
     * - keeps editors like intellij happy when debugging e.g. providing introspection into Map object.
     */

    @Override
    public Set<Entry<String, Float>> entrySet() {
        return new AbstractSet<Entry<String, Float>>() {
            @Override
            public Iterator<Entry<String, Float>> iterator() {
                return new Iterator<Entry<String, Float>>() {
                    private int index;

                    @Override
                    public boolean hasNext() {
                        LtrRanker.FeatureVector featureVector = getFeatureVector();
                        if (featureVector != null) {
                            return index < featureSet.size();
                        }
                        return false;
                    }

                    @Override
                    public Entry<String, Float> next() {
                        LtrRanker.FeatureVector featureVector = getFeatureVector();
                        if (featureVector != null) {
                            float score = featureVector.getFeatureScore(index);
                            String featureName = featureSet.feature(index).name();
                            index++;
                            return new SimpleImmutableEntry<>(featureName, score);
                        }
                        return null;
                    }
                };
            }

            @Override
            public int size() {
                LtrRanker.FeatureVector featureVector = getFeatureVector();
                if (featureVector != null) {
                    return featureSet.size();
                }
                return 0;
            }

            private LtrRanker.FeatureVector getFeatureVector() {
                if (vectorSupplier != null) {
                    return vectorSupplier.get();
                }
                return null;
            }
        };
    }

}

