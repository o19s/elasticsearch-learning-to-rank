package com.o19s.es.ltr.ranker.normalizer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Class that manages Normalizer implementations
 */
public class Normalizers {
    private static final Map<String, Normalizer> NORMALIZERS = Collections.unmodifiableMap(new HashMap<String, Normalizer>() {{
        put(NOOP_NORMALIZER_NAME, new NoopNormalizer());
        put(SIGMOID_NORMALIZER_NAME, new SigmoidNormalizer());
    }});
    public static final String NOOP_NORMALIZER_NAME = "noop";
    public static final String SIGMOID_NORMALIZER_NAME = "sigmoid";

    public static Normalizer get(String name) {
        Normalizer normalizer = NORMALIZERS.get(name);
        if (normalizer == null) {
            throw new IllegalArgumentException(name + " is not a valid Normalizer");
        }
        return normalizer;
    }

    public static boolean exists(String name) {
        return NORMALIZERS.containsKey(name);
    }

    static class NoopNormalizer implements Normalizer {
        @Override
        public float normalize(float val) {
            return val;
        }
    }

    static class SigmoidNormalizer implements Normalizer {
        @Override
        public float normalize(float val) {
            return sigmoid(val);
        }

        float sigmoid(float x) {
            return (float) (1 / (1 + Math.exp(-x)));
        }
    }
}
