package com.o19s.es.ltr.ranker.normalizer;

import org.apache.lucene.search.Explanation;

public class NoOpNormalizer implements Normalizer {
    @Override
    public float normalize(float val) {
        return val;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NoOpNormalizer)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 31*12345;
    }
}
