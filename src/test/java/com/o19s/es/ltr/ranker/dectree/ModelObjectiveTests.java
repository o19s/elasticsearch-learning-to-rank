package com.o19s.es.ltr.ranker.dectree;

import org.apache.lucene.util.LuceneTestCase;
import org.hamcrest.CoreMatchers;

public class ModelObjectiveTests extends LuceneTestCase {
    public void testDefault() {
        assertEquals(ModelObjective.defaultObjective().getClass(), ModelObjective.class);
        assertEquals(ModelObjective.defaultObjective(), ModelObjective.get(ModelObjective.DEFAULT_OBJECTIVE_TYPE));
    }

    public void testGet() {
        assertEquals(ModelObjective.get("binary:logistic").getClass(), ModelObjective.LogisticObjective.class);
        assertEquals(ModelObjective.get("binary:logitraw").getClass(), ModelObjective.class);
        assertEquals(ModelObjective.get("rank:pairwise").getClass(), ModelObjective.class);
        assertEquals(ModelObjective.get("reg:linear").getClass(), ModelObjective.class);
        assertEquals(ModelObjective.get("reg:logistic").getClass(), ModelObjective.LogisticObjective.class);
    }

    public void testInvalidName() {
        assertThat(expectThrows(IllegalArgumentException.class, () -> ModelObjective.get("not_objective")).getMessage(),
                CoreMatchers.containsString("is not a valid model objective"));
    }

    public void testExists() {
        assertTrue(ModelObjective.exists("reg:linear"));
        assertTrue(ModelObjective.exists("reg:logistic"));
        assertFalse(ModelObjective.exists("not_objective"));
    }

    public void testPredTransform() {
        assertEquals(ModelObjective.get("reg:linear").predTransform(0.2f), 0.2f, Math.ulp(0.2f));
        assertEquals(ModelObjective.get("reg:linear").predTransform(-0.5f), -0.5f, Math.ulp(-0.5f));

        assertEquals(ModelObjective.get("reg:logistic").predTransform(0.2f), 0.549834f, Math.ulp(0.549834f));
        assertEquals(ModelObjective.get("reg:logistic").predTransform(-0.5f), 0.37754068f, Math.ulp(0.37754068f));
    }
}
