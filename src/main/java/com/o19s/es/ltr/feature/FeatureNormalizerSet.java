package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.ranker.normalizer.Normalizer;

public interface FeatureNormalizerSet  {

    /**
     * Retrieve the feature normalizer for an ord
     *
     * @param featureOrd - what 0-based feature's norm do we need?
     *
     * @return
     *  a Normalizer for this ord (NoOpNormalizer if the featureOrd is not known)
     */
    Normalizer getNomalizer(int featureOrd);

    /**
     * Return which features are normalized for convenient access
     * to getNormalizer - so you don't have to check every possible ord and
     * just in case the vector is not dense. Precomputed as a simple int array
     * for performance
     *
     * @return
     *  an int array containing the ord of features that will have normalization performed
     *  (empty array if no normalization will occur)
     *  getNormalizer of an ord not in this array should return NoOpNormalizer
     */
    int[] getNormalizedOrds();
}
