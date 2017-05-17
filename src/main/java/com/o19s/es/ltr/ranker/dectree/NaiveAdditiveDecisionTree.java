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

import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.DenseLtrRanker;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import java.util.Objects;

/**
 * Naive implementation of additive decision tree.
 * May be slow when the number of trees and tree complexity if high comparatively to the number of features.
 */
public class NaiveAdditiveDecisionTree extends DenseLtrRanker implements Accountable {
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
        float sum = 0;
        float[] scores = vector.scores;
        for (int i = 0; i < trees.length; i++) {
            sum += weights[i]*trees[i].eval(scores);
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
                if (s.threshold >= scores[feature]) {
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
