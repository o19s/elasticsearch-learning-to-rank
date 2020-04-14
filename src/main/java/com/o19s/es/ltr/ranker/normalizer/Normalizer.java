package com.o19s.es.ltr.ranker.normalizer;

import org.apache.lucene.search.Explanation;

/**
 * Interface to normalize the resulting score of a model
 */
public interface Normalizer {
    float normalize(float val);

    default Explanation explain(Explanation wrappedQueryExplain) {
        return wrappedQueryExplain;
    }
}
