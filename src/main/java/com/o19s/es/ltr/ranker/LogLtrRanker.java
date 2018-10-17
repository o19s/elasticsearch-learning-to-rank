/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.ranker;

public class LogLtrRanker implements LtrRanker {
    private final LogConsumer logger;
    private final LtrRanker ranker;

    public LogLtrRanker(LtrRanker ranker, LogConsumer consumer) {
        this.ranker = ranker;
        this.logger = consumer;
    }

    public LogLtrRanker(LogConsumer consumer, int modelSize) {
        this.ranker = new NullRanker(modelSize);
        this.logger = consumer;
    }

    @Override
    public String name() {
        return "log(" + ranker.name() + ")";
    }

    @Override
    public FeatureVector newFeatureVector(FeatureVector reuse) {
        final VectorWrapper wrapper;
        if (reuse == null) {
            wrapper = new VectorWrapper(logger);
        } else {
            assert reuse instanceof VectorWrapper;
            wrapper = (VectorWrapper) reuse;
        }
        wrapper.reset(ranker);
        return wrapper;
    }

    @Override
    public float score(FeatureVector point) {
        assert point instanceof VectorWrapper;
        return ranker.score(((VectorWrapper) point).inner);
    }

    private static class VectorWrapper implements FeatureVector {
        private FeatureVector inner;
        private final LogConsumer logger;

        VectorWrapper(LogConsumer consumer) {
            this.logger = consumer;
        }

        @Override
        public void setFeatureScore(int featureId, float score) {
            inner.setFeatureScore(featureId, score);
            logger.accept(featureId, score);
        }

        @Override
        public float getFeatureScore(int featureId) {
            return inner.getFeatureScore(featureId);
        }

        void reset(LtrRanker ranker) {
            this.inner = ranker.newFeatureVector(inner);
            logger.reset();
        }
    }

    @FunctionalInterface
    public interface LogConsumer {
        void accept(int featureOrdinal, float score);
        default void reset() {}
    }
}
