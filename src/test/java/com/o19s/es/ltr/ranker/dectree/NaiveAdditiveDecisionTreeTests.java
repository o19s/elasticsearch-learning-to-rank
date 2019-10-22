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

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.PrebuiltFeature;
import com.o19s.es.ltr.feature.PrebuiltFeatureSet;
import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.linear.LinearRankerTests;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import com.o19s.es.ltr.ranker.normalizer.Normalizers;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_REF;
import static org.apache.lucene.util.TestUtil.nextInt;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.AllOf.allOf;

public class NaiveAdditiveDecisionTreeTests extends LuceneTestCase {
    static final Logger LOG = LogManager.getLogger(NaiveAdditiveDecisionTreeTests.class);
    public void testName() {
        NaiveAdditiveDecisionTree dectree = new NaiveAdditiveDecisionTree(new NaiveAdditiveDecisionTree.Node[0],
                new float[0], 0, Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME));
        assertEquals("naive_additive_decision_tree", dectree.name());
    }

    public void testScore() throws IOException {
        NaiveAdditiveDecisionTree ranker = parseTreeModel("simple_tree.txt", Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME));
        LtrRanker.FeatureVector vector = ranker.newFeatureVector(null);
        vector.setFeatureScore(0, 1);
        vector.setFeatureScore(1, 2);
        vector.setFeatureScore(2, 3);

        float expected = 1.2F*3.4F + 3.2F*2.8F;
        assertEquals(expected, ranker.score(vector), Math.ulp(expected));
    }

    public void testSigmoidScore() throws IOException {
        NaiveAdditiveDecisionTree ranker = parseTreeModel("simple_tree.txt", Normalizers.get(Normalizers.SIGMOID_NORMALIZER_NAME));
        LtrRanker.FeatureVector vector = ranker.newFeatureVector(null);
        vector.setFeatureScore(0, 1);
        vector.setFeatureScore(1, 2);
        vector.setFeatureScore(2, 3);

        float expected = 1.2F*3.4F + 3.2F*2.8F;
        expected = (float) (1 / (1 + Math.exp(-expected)));
        assertEquals(expected, ranker.score(vector), Math.ulp(expected));
    }

    public void testPerfAndRobustness() {
        SimpleCountRandomTreeGeneratorStatsCollector counts = new SimpleCountRandomTreeGeneratorStatsCollector();
        NaiveAdditiveDecisionTree ranker = generateRandomDecTree(100, 1000,
                100, 1000,
                5, 50, counts);

        DenseFeatureVector vector = ranker.newFeatureVector(null);
        int nPass = TestUtil.nextInt(random(), 10, 8916);
        LinearRankerTests.fillRandomWeights(vector.scores);
        ranker.score(vector); // warmup

        long time = -System.currentTimeMillis();
        for (int i = 0; i < nPass; i++) {
            vector = ranker.newFeatureVector(vector);
            LinearRankerTests.fillRandomWeights(vector.scores);
            ranker.score(vector);
        }
        time += System.currentTimeMillis();
        LOG.info("Scored {} docs with {} trees/{} features within {}ms ({} ms/doc), " +
                        "{} nodes ({} splits & {} leaves) ",
                nPass, counts.trees.get(), ranker.size(), time, (float) time / (float) nPass,
                counts.nodes.get(), counts.splits.get(), counts.leaves.get());
    }

    public void testRamSize() {
        SimpleCountRandomTreeGeneratorStatsCollector counts = new SimpleCountRandomTreeGeneratorStatsCollector();
        NaiveAdditiveDecisionTree ranker = generateRandomDecTree(100, 1000,
                100, 1000,
                5, 50, counts);
        long actualSize = ranker.ramBytesUsed();
        long expectedApprox = counts.splits.get() * (NUM_BYTES_OBJECT_HEADER + Float.BYTES + NUM_BYTES_OBJECT_REF * 2);
        expectedApprox += counts.leaves.get() * (NUM_BYTES_ARRAY_HEADER + NUM_BYTES_OBJECT_HEADER + Float.BYTES);
        expectedApprox += ranker.size() * Float.BYTES + NUM_BYTES_ARRAY_HEADER;
        assertThat(actualSize, allOf(
                greaterThan((long) (expectedApprox*0.66F)),
                lessThan((long) (expectedApprox*1.33F))));
    }

    public static NaiveAdditiveDecisionTree generateRandomDecTree(int minFeatures, int maxFeatures, int minTrees,
                                                                  int maxTrees, int minDepth, int maxDepth,
                                                                  RandomTreeGeneratorStatsCollector collector) {
        int nFeat = nextInt(random(), minFeatures, maxFeatures);
        int nbTrees = nextInt(random(), minTrees, maxTrees);
        return generateRandomDecTree(nFeat, nbTrees, minDepth, maxDepth, collector);
    }

    public static NaiveAdditiveDecisionTree generateRandomDecTree(int nbFeatures, int nbTree, int minDepth,
                                                                  int maxDepth, RandomTreeGeneratorStatsCollector collector) {
        NaiveAdditiveDecisionTree.Node[] trees = new NaiveAdditiveDecisionTree.Node[nbTree];
        float[] weights = LinearRankerTests.generateRandomWeights(nbTree);
        for (int i = 0; i < nbTree; i++) {
            if (collector != null) {
                collector.newTree();
            }
            trees[i] = new RandomTreeGenerator(nbFeatures, minDepth, maxDepth, collector).genTree();
        }
        return new NaiveAdditiveDecisionTree(trees, weights, nbFeatures, Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME));
    }

    public void testSize() {
        NaiveAdditiveDecisionTree ranker = new NaiveAdditiveDecisionTree(new NaiveAdditiveDecisionTree.Node[0],
                new float[0], 3, Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME));
        assertEquals(ranker.size(), 3);
    }

    private NaiveAdditiveDecisionTree parseTreeModel(String textRes, Normalizer normalizer) throws IOException {
        List<PrebuiltFeature> features = new ArrayList<>(3);
        features.add(new PrebuiltFeature("feature1", new MatchAllDocsQuery()));
        features.add(new PrebuiltFeature("feature2", new MatchAllDocsQuery()));
        features.add(new PrebuiltFeature("feature3", new MatchAllDocsQuery()));
        FeatureSet set = new PrebuiltFeatureSet("my_set", features);

        TreeTextParser parser = new TreeTextParser(this.getClass().getResourceAsStream(textRes), set);
        List<TreeAndWeight> treesAndWeight = parser.parseTrees();
        float[] weights = new float[treesAndWeight.size()];
        NaiveAdditiveDecisionTree.Node[] trees = new NaiveAdditiveDecisionTree.Node[treesAndWeight.size()];
        for(int i = 0; i < treesAndWeight.size(); i++) {
            weights[i] = treesAndWeight.get(i).weight;
            trees[i] = treesAndWeight.get(i).tree;
        }
        return new NaiveAdditiveDecisionTree(trees, weights, set.size(), normalizer);
    }

    private static class TreeTextParser {
        FeatureSet set;
        Iterator<String> lines;
        private TreeTextParser(InputStream is, FeatureSet set) throws IOException {
            List<String> lines = new ArrayList<>();
            try(BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))) {
                String line;
                while ((line = br.readLine()) != null) {
                    lines.add(line);
                }
            }
            this.set = set;
            this.lines = lines.iterator();
        }
        private List<TreeAndWeight> parseTrees() {
            List<TreeAndWeight> trees = new ArrayList<>();

            while(lines.hasNext()) {
                String line = lines.next();
                if(line.startsWith("- tree")) {
                    TreeAndWeight tree = new TreeAndWeight();
                    tree.weight = extractLastFloat(line);
                    tree.tree = parseTree();
                    trees.add(tree);
                }
            }
            return trees;
        }

        NaiveAdditiveDecisionTree.Node parseTree() {
            String line;
            do {
                if (!lines.hasNext()) {
                    throw new IllegalArgumentException("Invalid tree");
                }
                line = lines.next();
            } while(line.startsWith("#"));

            if (line.contains("- output")) {
                return new NaiveAdditiveDecisionTree.Leaf(extractLastFloat(line));
            } else if(line.contains("- split")) {
                String featName = line.split(":")[1];
                int ord = set.featureOrdinal(featName);
                if (ord < 0 || ord > set.size()) {
                    throw new IllegalArgumentException("Unknown feature " + featName);
                }
                float threshold = extractLastFloat(line);
                NaiveAdditiveDecisionTree.Node right = parseTree();
                NaiveAdditiveDecisionTree.Node left = parseTree();

                return new NaiveAdditiveDecisionTree.Split(left, right,
                        ord, threshold);
            } else {
                throw new IllegalArgumentException("Invalid tree");
            }
        }

        float extractLastFloat(String line) {
            Pattern p = Pattern.compile(".*:([0-9.]+)$");
            Matcher m = p.matcher(line);
            if(m.find()) {
                return Float.parseFloat(m.group(1));
            }
            throw new IllegalArgumentException("Cannot extract float from " + line);
        }
    }

    private static class TreeAndWeight {
        private NaiveAdditiveDecisionTree.Node tree;
        private float weight;
    }

    public static class RandomTreeGenerator {
        private final int maxDepth;
        private final int minDepth;
        private final RandomTreeGeneratorStatsCollector statsCollector;
        private final Supplier<Integer> featureGen;
        private final Supplier<Float> outputGenerator;
        private final Function<Integer, Float> thresholdGenerator;
        private final Supplier<Boolean> leafDecider;

        public RandomTreeGenerator(int maxFeat, int minDepth, int maxDepth, RandomTreeGeneratorStatsCollector collector) {
            this.minDepth = minDepth;
            this.maxDepth = maxDepth;
            this.statsCollector = collector != null ? collector : RandomTreeGeneratorStatsCollector.NULL;
            featureGen = () -> nextInt(random(), 0, maxFeat-1);
            outputGenerator = () ->
                (random().nextBoolean() ? 1F : -1F) *
                        ((float)nextInt(random(), 0, 1000) / (float)nextInt(random(), 1, 1000));
            thresholdGenerator = (feat) ->
                    (random().nextBoolean() ? 1F : -1F) *
                            ((float)nextInt(random(), 0, 1000) / (float)nextInt(random(), 1, 1000));
            leafDecider = () -> random().nextBoolean();

        }

        NaiveAdditiveDecisionTree.Node genTree() {
            return newNode(0);
        }

        NaiveAdditiveDecisionTree.Node newNode(int depth) {
            statsCollector.newNode();
            if (depth>=maxDepth) {
                return newLeaf(depth);
            } else if (depth <= minDepth) {
                return newSplit(depth);
            } else {
                return leafDecider.get() ? newLeaf(depth) : newNode(depth);
            }
        }

        private NaiveAdditiveDecisionTree.Node newSplit(int depth) {
            depth++;
            int feature = featureGen.get();
            float thresh = thresholdGenerator.apply(feature);
            statsCollector.newSplit(depth, feature, thresh);
            return new NaiveAdditiveDecisionTree.Split(newNode(depth), newNode(depth), feature, thresh);
        }

        private NaiveAdditiveDecisionTree.Node newLeaf(int depth) {
            float output = outputGenerator.get();
            statsCollector.newLeaf(depth, output);
            return new NaiveAdditiveDecisionTree.Leaf(output);
        }
    }

    public interface RandomTreeGeneratorStatsCollector {
        RandomTreeGeneratorStatsCollector NULL = new RandomTreeGeneratorStatsCollector() {};
        default void newSplit(int depth, int feature, float thresh) {}
        default void newLeaf(int depth, float output) {}
        default void newNode() {}
        default void newTree() {}
    }

    public static class SimpleCountRandomTreeGeneratorStatsCollector implements RandomTreeGeneratorStatsCollector {
        private final AtomicInteger splits = new AtomicInteger();
        private final AtomicInteger leaves = new AtomicInteger();
        private final AtomicInteger nodes = new AtomicInteger();
        private final AtomicInteger trees = new AtomicInteger();

        @Override
        public void newSplit(int depth, int feature, float thresh) {
            splits.incrementAndGet();
        }

        @Override
        public void newLeaf(int depth, float output) {
            leaves.incrementAndGet();
        }

        @Override
        public void newNode() {
            nodes.incrementAndGet();
        }

        @Override
        public void newTree() {
            trees.incrementAndGet();
        }
    }
}