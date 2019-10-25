package com.o19s.es.ltr.ranker.normalizer;

import org.apache.lucene.util.LuceneTestCase;
import org.hamcrest.CoreMatchers;

public class NormalizersTests extends LuceneTestCase {

    public void testGet() {
        assertEquals(Normalizers.get(Normalizers.SIGMOID_NORMALIZER_NAME).getClass(), Normalizers.SigmoidNormalizer.class);
        assertEquals(Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME).getClass(), Normalizers.NoopNormalizer.class);
    }

    public void testInvalidName() {
        assertThat(expectThrows(IllegalArgumentException.class, () -> Normalizers.get("not_normalizer")).getMessage(),
                CoreMatchers.containsString("is not a valid Normalizer"));
    }

    public void testExists() {
        assertTrue(Normalizers.exists(Normalizers.NOOP_NORMALIZER_NAME));
        assertTrue(Normalizers.exists("sigmoid"));
        assertFalse(Normalizers.exists("not_normalizer"));
    }

    public void testNormalize() {
        assertEquals(Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME).normalize(0.2f), 0.2f, Math.ulp(0.2f));
        assertEquals(Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME).normalize(-0.5f), -0.5f, Math.ulp(-0.5f));

        assertEquals(Normalizers.get(Normalizers.SIGMOID_NORMALIZER_NAME).normalize(0.2f), 0.549834f, Math.ulp(0.549834f));
        assertEquals(Normalizers.get(Normalizers.SIGMOID_NORMALIZER_NAME).normalize(-0.5f), 0.37754068f, Math.ulp(0.37754068f));
    }
}
