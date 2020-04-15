package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContent;

import java.io.IOException;

/**
 * Parsed feature norm from model definition
 */
public interface FeatureNormDefinition extends ToXContent {

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

    /**
     * Serialize to a StreamOutput
     * @param out
     *  where the ftr norm defn will be serialized
     */
    void writeTo(StreamOutput out) throws IOException;
}
