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
import com.o19s.es.ltr.ranker.ArrayDataPoint;
import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.util.LuceneTestCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NaiveAdditiveDecisionTreeTests extends LuceneTestCase {
    public void testName() {
        NaiveAdditiveDecisionTree dectree = new NaiveAdditiveDecisionTree(new NaiveAdditiveDecisionTree.Node[0],
                new float[0], 0);
        assertEquals("naive_additive_decision_tree", dectree.name());
    }

    public void testBewDataPoint() {
        NaiveAdditiveDecisionTree dectree = new NaiveAdditiveDecisionTree(new NaiveAdditiveDecisionTree.Node[0],
                new float[0], 2);
        LtrRanker.DataPoint point = dectree.newDataPoint();
        assertEquals(0, point.getFeatureScore(0), Math.ulp(0));
        assertEquals(0, point.getFeatureScore(0), Math.ulp(0));
        assertTrue(point instanceof ArrayDataPoint);
        float[] points = ((ArrayDataPoint) point).scores;
        assertEquals(points.length, 2);
    }

    public void testScore() throws IOException {
        NaiveAdditiveDecisionTree ranker = parseTreeModel("simple_tree.txt");
        LtrRanker.DataPoint point = ranker.newDataPoint();
        point.setFeatureScore(0, 2);
        point.setFeatureScore(1, 2);
        point.setFeatureScore(2, 1);

        float expected = 1.2F*3.4F + 3.2F*2.8F;
        assertEquals(expected, ranker.score(point), Math.ulp(expected));
    }



    public void testSize() {
        NaiveAdditiveDecisionTree ranker = new NaiveAdditiveDecisionTree(new NaiveAdditiveDecisionTree.Node[0],
                new float[0], 3);
        assertEquals(ranker.size(), 3);
    }

    private NaiveAdditiveDecisionTree parseTreeModel(String textRes) throws IOException {
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
        return new NaiveAdditiveDecisionTree(trees, weights, set.size());
    }

    private static class TreeTextParser {
        FeatureSet set;
        Iterator<String> lines;
        private TreeTextParser(InputStream is, FeatureSet set) throws IOException {
            List<String> lines = new ArrayList<>();
            try(BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))) {
                String line = null;
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
}