package com.o19s.es.ltr.ranker.dectree;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an XGBoost model learning objective
 *
 * Depending on the objective, the model prediction may require a transform. Currently only untransformed
 * and logistic (sigmoid) types are implemented.
 * See <a href="https://xgboost.readthedocs.io/en/latest/parameter.html#learning-task-parameters">task params</a>
 */
public class ModelObjective {
    public static final String DEFAULT_OBJECTIVE_TYPE = "reg:linear";
    private static final Map<String, ModelObjective> OBJECTIVES = new HashMap<>();

    static {
        register("binary:logistic", new LogisticObjective());
        register("binary:logitraw", new ModelObjective());
        register("rank:pairwise", new ModelObjective());
        register("reg:linear", new ModelObjective());
        register("reg:logistic", new LogisticObjective());
    }

    public static void register(String name, ModelObjective objective) {
        OBJECTIVES.put(name, objective);
    }

    public static ModelObjective get(String objectiveName) {
        ModelObjective objective = OBJECTIVES.get(objectiveName);
        if (objective == null) {
            throw new IllegalArgumentException(objectiveName + " is not a valid model objective.");
        }
        return objective;
    }

    public static boolean exists(String objectiveName) {
        return OBJECTIVES.containsKey(objectiveName);
    }

    public static ModelObjective defaultObjective() {
        return get(DEFAULT_OBJECTIVE_TYPE);
    }

    public float predTransform(float pred) {
        return pred;
    }

    static class LogisticObjective extends ModelObjective {
        @Override
        public float predTransform(float pred) {
            return sigmoid(pred);
        }

        float sigmoid(float x) {
            return (float) (1 / (1 + Math.exp(-x)));
        }
    }
}
