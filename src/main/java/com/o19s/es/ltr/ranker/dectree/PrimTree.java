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

package com.o19s.es.ltr.ranker.dectree;

import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.DenseLtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree.Leaf;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree.Node;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree.Split;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import static org.apache.lucene.util.RamUsageEstimator.sizeOf;

/**
 * Dec tree using only array of primitives
 * Inspired from https://github.com/staples-sparx/Sequoia but optimized
 * for binary trees (we only store right offsets, left offsets are always adjacent)
 *
 * Size in mem is significantly lower than the naive implementation
 * when the number of nodes is not ridiculously large it should faster
 * if it becomes too large because we access 3 giant arrays it's likely
 * to cause cache invalidation.
 */
public class PrimTree extends DenseLtrRanker implements Accountable {
    private static final long BASE_RAM_USAGE = RamUsageEstimator.shallowSizeOf(PrimTree.class);

    /**
     * key: sequence
     * value: index in thresholdsOrOutput
     * size: number of trees
     */
    private final int[] roots;

    /**
     * Flat list of thresholds and outputs
     * size: total number of splits + leaves
     */
    private final float[] thresholdsOrOutput;

    /**
     * key: index in thresholdsOrOutput
     * value: feature ordinal or -1 for an output
     * size: total number of splits + leaves
     * NOTE: we use a short because we currently allow only 10000 features max
     */
    private final short[] features;

    /**
     * lefts offsets
     * key: index in thresholdsOrOutput
     * value: index in thresholdsOrOutput
     * size: total number of splits + leaves
     */
    private final int[] rights;

    private final int nFeat;

    public PrimTree(int[] roots, float[] thresholdsOrOutput, short[] features, int[] rights, int nFeat) {
        this.roots = roots;
        this.thresholdsOrOutput = thresholdsOrOutput;
        this.features = features;
        this.rights = rights;
        this.nFeat = nFeat;
    }

    @Override
    public float score(DenseFeatureVector denseVect) {
        float score = 0F;
        for (int n : roots) {
            while (true) {
                short feat = features[n];
                float threshOrOutput = thresholdsOrOutput[n];
                if (feat < 0) {
                    score += threshOrOutput;
                    break;
                }
                if (threshOrOutput > denseVect.scores[feat]) {
                    // left is always adjacent
                    n++;
                } else {
                    n = rights[n];
                }
            }
        }
        return score;
    }

    /**
     * LtrRanker name
     */
    @Override
    public String name() {
        return "prim_additive_decision_tree";
    }

    /**
     * The number of features supported by this ranker
     */
    @Override
    protected int size() {
        return nFeat;
    }

    /**
     * Return the memory usage of this object in bytes. Negative values are illegal.
     */
    @Override
    public long ramBytesUsed() {
        return BASE_RAM_USAGE +
                sizeOf(this.roots) +
                sizeOf(this.thresholdsOrOutput) +
                sizeOf(this.features) +
                sizeOf(this.rights);
    }

    static class BuildState {
        private float[] thresholdsOrOutput;
        private short[] features;
        private int[] rights;
        private final int[] roots;
        private final Node[] trees;
        private final int nFeat;
        private int currentOffset;
        private int currentTree;

        BuildState(Node[] trees, int nFeat) {
            this.trees = trees;
            this.nFeat = nFeat;
            this.roots = new int[trees.length];
        }

        private int countSplitsAndLeaves(Node n) {
            if (n.isLeaf()) {
                return 1;
            }
            int left = countSplitsAndLeaves(((Split) n).left());
            int right = countSplitsAndLeaves(((Split) n).right());
            int total = Math.addExact(left, right);
            return Math.addExact(total, 1);
        }

        PrimTree build() {
            int total = 0;
            for (int i = 0; i < trees.length; i++) {
                roots[i] = total;
                int treeSize = countSplitsAndLeaves(trees[i]);
                total = Math.addExact(treeSize, total);
            }
            thresholdsOrOutput = new float[total];
            features = new short[total];
            rights = new int[total];
            flatten();
            return new PrimTree(roots, thresholdsOrOutput, features, rights, nFeat);
        }

        public void flatten() {
            currentOffset = 0;
            for (currentTree = 0; currentTree < trees.length; currentTree++) {
                assert roots[currentTree] == currentOffset;
                flattenNodes(trees[currentTree]);
            }
        }

        public void flattenNodes(Node n) {
            if (n.isLeaf()) {
                features[currentOffset] = -1;
                rights[currentOffset] = -1;
                thresholdsOrOutput[currentOffset] = ((Leaf) n).output();
                currentOffset++;
            } else {
                Split s = (Split) n;
                assert s.feature() <= Short.MAX_VALUE;
                assert s.feature() >= 0;
                features[currentOffset] = (short) s.feature();
                thresholdsOrOutput[currentOffset] = s.threshold();
                final int myOffset = currentOffset;
                currentOffset++;
                flattenNodes(s.left());
                rights[myOffset] = currentOffset;
                flattenNodes(s.right());
            }
        }
    }
}
