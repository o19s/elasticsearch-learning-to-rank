/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.ranker.linear;

import com.o19s.es.ltr.ranker.BulkLtrRanker;
import com.o19s.es.ltr.ranker.DenseFeatureMatrix;
import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.DenseLtrRanker;
import com.o19s.es.ltr.ranker.FeatureMatrix;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import java.util.Arrays;
import java.util.Objects;

/**
 * Simple linear ranker that applies a dot product based
 * on the provided weights array.
 */
public class LinearRanker extends DenseLtrRanker implements BulkLtrRanker, Accountable {
    private static final int BULK_SIZE = 1024;
    private final float[] weights;

    public LinearRanker(float[] weights) {
        this.weights = Objects.requireNonNull(weights);
    }

    @Override
    public String name() {
        return "linear";
    }

    @Override
    protected float score(DenseFeatureVector point) {
        float[] scores = point.scores;
        return score(scores);
    }

    private float score(float[] scores) {
        float score = 0;
        for (int i = 0; i < weights.length; i++) {
            score += weights[i]*scores[i];
        }
        return score;
    }

    @Override
    protected int size() {
        return weights.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LinearRanker ranker = (LinearRanker) o;

        return Arrays.equals(weights, ranker.weights);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(weights);
    }

    /**
     * Return the memory usage of this object in bytes. Negative values are illegal.
     */
    @Override
    public long ramBytesUsed() {
        return RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + RamUsageEstimator.sizeOf(weights);
    }

    @Override
    public FeatureMatrix newMatrix(FeatureMatrix reuse, int maxDocs) {
        DenseFeatureMatrix matrix;
        if (reuse != null) {
            assert reuse instanceof DenseFeatureMatrix;
            matrix = (DenseFeatureMatrix) reuse;
            if (matrix.scores.length < BULK_SIZE && matrix.scores.length < maxDocs) {
                matrix = new DenseFeatureMatrix(Math.min(maxDocs, BULK_SIZE), size());
            } else {
                matrix.reset();
            }
        } else {
            matrix = new DenseFeatureMatrix(Math.min(maxDocs, BULK_SIZE), size());
        }
        return matrix;
    }

    @Override
    public void bulkScore(FeatureMatrix matrix, int from, int to, BulkScoreConsumer consumer) {
        assert matrix instanceof DenseFeatureMatrix;
        DenseFeatureMatrix denseMatrix = (DenseFeatureMatrix) matrix;
        for (int i = from; i < to; i++) {
            float[] scores = denseMatrix.scores[i];
            consumer.consume(i, score(scores));
        }
    }
}
