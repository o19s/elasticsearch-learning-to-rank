package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.normalizer.Normalizer;

/**
 * Parsed feature norm from model definition
 */
public interface FeatureNormDefinition extends StorableElement {

    /**
     * @return
     *  Construct the feature norm associated with this definitino
     */
    Normalizer createFeatureNorm();

    /**
     * @return
     *  The feature name associated with this normalizer to
     *  later associate with an ord
     */
    String featureName();

    /**
     * @return
     *  A type of normalizer
     */
    StoredFeatureNormalizers.Type normType();
}
