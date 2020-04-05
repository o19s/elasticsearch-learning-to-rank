package com.o19s.es.ltr.ranker.normalizer;

public class NoOpNormalizer implements Normalizer {
    @Override
    public float normalize(float val) {
        return val;
    }
}
