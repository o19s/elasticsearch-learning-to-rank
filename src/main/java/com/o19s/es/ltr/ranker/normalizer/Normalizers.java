package com.o19s.es.ltr.ranker.normalizer;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that manages Normalizer implementations
 */
public class Normalizers {
    private static final Map<String, Normalizer> NORMALIZERS = new HashMap<>();
    public static final String DEFAULT_NORMALIZER_NAME = "noop";

    static {
        register("noop", new NoopNormalizer());
        register("sigmoid", new SigmoidNormalizer());
    }

    public static void register(String name, Normalizer normalizer) {
        NORMALIZERS.put(name, normalizer);
    }

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

    public static Normalizer defaultNormalizer() {
        return get(DEFAULT_NORMALIZER_NAME);
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
