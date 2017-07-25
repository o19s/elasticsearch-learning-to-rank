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

import java.nio.ByteBuffer;

import static org.apache.lucene.util.RamUsageEstimator.sizeOf;

/**
 * Implementation based on a binary representation of the tree where all
 * the bits used to evaluate a split are close to each other
 * It could have been stored as a byte[] but off-heap allocation makes it
 * slightly faster.
 *
 * This is attempt to see if packing elements needed to evaluate can give better
 * perf, it does not seem to be the case...
 * It may produce better perf for extremely large trees tho...
 */
public class BinBinTree extends DenseLtrRanker implements Accountable {
    private static final long BASE_RAM_USAGE = RamUsageEstimator.shallowSizeOf(BinBinTree.class);
    private static final int LEAF_SIZE = Short.BYTES + Float.BYTES;
    private static final int SPLIT_SIZE = Short.BYTES + Float.BYTES + Integer.BYTES;

    /**
     * Offset of roots in data
     * size: number of trees
     */
    private final int[] roots;

    /**
     * binary tree in binary format:
     * Splits:
     *  [short  ][float       ][int         ]
     *  [feature][thresholdOrOutput][right offset]
     *  block size: 10
     * Leaves:
     *  [short  ][float       ]
     *  [-1     ][output      ]
     *  block size: 6
     * size : 10 * total splits + 6 * leaves
     */
    private final ByteBuffer data;

    private final int nFeat;

    public BinBinTree(int[] roots, ByteBuffer data, int nFeat) {
        this.roots = roots;
        this.data = data;
        this.nFeat = nFeat;
    }

    @Override
    public float score(DenseFeatureVector denseVect) {
        float score = 0F;
        for (int n : roots) {
            while (true) {
                short feat = data.getShort(n);
                float threshOrOutput = data.getFloat(n+Short.BYTES);
                if (feat < 0) {
                    score += threshOrOutput;
                    break;
                }
                if (threshOrOutput > denseVect.scores[feat]) {
                    // left is always adjacent
                    n += SPLIT_SIZE;
                } else {
                    n = data.getInt(n + Short.BYTES + Float.BYTES);
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
        return "binbin_additive_decision_tree";
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
                this.data.limit();
    }

    static class BuildState {
        private final int[] roots;
        private final Node[] trees;
        private final int nFeat;
        private int currentTree;
        private ByteBuffer buffer;

        BuildState(Node[] trees, int nFeat) {
            this.trees = trees;
            this.nFeat = nFeat;
            this.roots = new int[trees.length];
        }

        BinBinTree build() {
            int total = 0;
            for (int i = 0; i < trees.length; i++) {
                int leaves = trees[i].count(NaiveAdditiveDecisionTree.CountType.Leaves);
                int splits = trees[i].count(NaiveAdditiveDecisionTree.CountType.Splits);
                total = Math.addExact(total, Math.multiplyExact(leaves, LEAF_SIZE));
                total = Math.addExact(total, Math.multiplyExact(splits, SPLIT_SIZE));
            }

            // allocate
            buffer = ByteBuffer.allocateDirect(total);
            flatten();
            return new BinBinTree(roots, buffer, nFeat);
        }

        public void flatten() {
            for (currentTree = 0; currentTree < trees.length; currentTree++) {
                roots[currentTree] = buffer.position();
                flattenNodes(trees[currentTree]);
            }
        }

        public void flattenNodes(Node n) {
            if (n.isLeaf()) {
                buffer.putShort((short)-1);
                buffer.putFloat(((Leaf) n).output());
            } else {
                Split s = (Split) n;
                assert s.feature() <= Short.MAX_VALUE;
                assert s.feature() >= 0;
                buffer.putShort((short) s.feature());
                buffer.putFloat(s.threshold());
                final int myOffset = buffer.position();
                buffer.position(buffer.position() + Integer.BYTES);
                flattenNodes(s.left());
                buffer.putInt(myOffset, buffer.position());
                flattenNodes(s.right());
            }
        }
    }
}
