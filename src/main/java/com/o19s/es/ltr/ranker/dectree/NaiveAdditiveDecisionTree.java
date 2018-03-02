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

package com.o19s.es.ltr.ranker.dectree;

import com.o19s.es.ltr.ranker.BulkLtrRanker;
import com.o19s.es.ltr.ranker.DenseFeatureMatrix;
import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.DenseLtrRanker;
import com.o19s.es.ltr.ranker.FeatureMatrix;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import java.util.Objects;

/**
 * Naive implementation of additive decision tree.
 * May be slow when the number of trees and tree complexity if high comparatively to the number of features.
 */
public class NaiveAdditiveDecisionTree extends DenseLtrRanker implements Accountable, BulkLtrRanker {
    private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(Split.class);

    private final Node[] trees;
    private final float[] weights;
    private final int modelSize;

    /**
     * TODO: Constructor for these classes are strict and not really
     * designed for a fluent building process. We might consider
     * changing this according to model parsers we implement.
     *
     * @param trees an array of trees
     * @param weights the respective weights
     * @param modelSize the modelSize in number of feature used
     */
    public NaiveAdditiveDecisionTree(Node[] trees, float[] weights, int modelSize) {
        assert trees.length == weights.length;
        this.trees = trees;
        this.weights = weights;
        this.modelSize = modelSize;
    }

    @Override
    public String name() {
        return "naive_additive_decision_tree";
    }

    @Override
    protected float score(DenseFeatureVector vector) {
        return this.score(vector.scores);
    }

    private float score(float[] scores) {
        float sum = 0;
        for (int i = 0; i < trees.length; i++) {
            sum += weights[i] * trees[i].eval(scores);
        }
        return sum;
    }

    @Override
    protected int size() {
        return modelSize;
    }

    /**
     * Return the memory usage of this object in bytes. Negative values are illegal.
     */
    @Override
    public long ramBytesUsed() {
        return BASE_RAM_USED + RamUsageEstimator.sizeOf(weights)
                + RamUsageEstimator.sizeOf(trees);
    }

    /**
     * A new feature matrix that can hold nfeature*nDocs floats
     *
     * @param reuse a reusable matrix
     * @param nDocs number of docs in the matrix
     * @return the matrix
     */
    @Override
    public DenseFeatureMatrix newMatrix(FeatureMatrix reuse, int nDocs) {
        if (reuse == null) {
            return new DenseFeatureMatrix(nDocs, size());
        }
        assert reuse instanceof DenseFeatureMatrix;
        DenseFeatureMatrix dmatrix = (DenseFeatureMatrix) reuse;
        dmatrix.reset();
        return dmatrix;
    }

    float[] scores = null;

    /**
     * Compute the scores using the values stored in the matrix
     *
     * @param matrix   the input matrix with feature values populated
     * @param from     score from this base
     * @param to       score up to this doc
     * @param consumer callback to receive scores
     */
    @Override
    public void bulkScore(FeatureMatrix matrix, int from, int to, BulkScoreConsumer consumer) {
        assert matrix instanceof DenseFeatureMatrix;
        DenseFeatureMatrix dmatrix = (DenseFeatureMatrix) matrix;
        for (int i = from;  i < to; i++) {
            consumer.consume(i, score(dmatrix.scores[i]));
        }
    }

    public interface Node extends Accountable {
         boolean isLeaf();
         float eval(float[] scores);
    }

    public static class Split implements Node {
        private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(Split.class);
        private final Node left;
        private final Node right;
        private final int feature;
        private final float threshold;

        public Split(Node left, Node right, int feature, float threshold) {
            this.left = Objects.requireNonNull(left);
            this.right = Objects.requireNonNull(right);
            this.feature = feature;
            this.threshold = threshold;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public float eval(float[] scores) {
            Node n = this;
            while (!n.isLeaf()) {
                assert n instanceof Split;
                Split s = (Split) n;
                if (s.threshold > scores[s.feature]) {
                    n = s.left;
                } else {
                    n = s.right;
                }
            }
            assert n instanceof Leaf;
            return n.eval(scores);
        }

        /**
         * Return the memory usage of this object in bytes. Negative values are illegal.
         */
        @Override
        public long ramBytesUsed() {
            return BASE_RAM_USED + left.ramBytesUsed() + right.ramBytesUsed();
        }
    }

    public static class Leaf implements Node {
        private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(Split.class);

        private final float output;

        public Leaf(float output) {
            this.output = output;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public float eval(float[] scores) {
            return output;
        }

        /**
         * Return the memory usage of this object in bytes. Negative values are illegal.
         */
        @Override
        public long ramBytesUsed() {
            return BASE_RAM_USED;
        }
    }
}
