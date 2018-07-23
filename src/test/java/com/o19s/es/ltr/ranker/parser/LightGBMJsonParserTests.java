package com.o19s.es.ltr.ranker.parser;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import com.o19s.es.ltr.ranker.linear.LinearRankerTests;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.io.Streams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.o19s.es.ltr.LtrTestUtils.randomFeatureSet;

public class LightGBMJsonParserTests extends LuceneTestCase {
    private final LightGBMJsonParser parser = new LightGBMJsonParser();

    public void testReadLeaf() throws IOException {
        FeatureSet set = randomFeatureSet();
        String featureNames = generateFeatureNames(set);
        String model = generateRoot(generateFeatureNames(set), generateTree(generateLeaf(3.14F)));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        assertEquals(3.14F, tree.score(tree.newFeatureVector(null)), Math.ulp(3.14F));
    }

    public void testReadSimpleSplit() throws IOException {
        FeatureSet set = randomFeatureSet();
        String model = generateRoot(generateFeatureNames(set), generateTree(generateSplit(
                4.321F, 0, generateLeaf(3.14F), generateLeaf(0.234F))));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        LtrRanker.FeatureVector v = tree.newFeatureVector(null);
        v.setFeatureScore(0, 4.320F);
        assertEquals(3.14F, tree.score(v), Math.ulp(3.14F));
        v.setFeatureScore(0, 4.321F);
        assertEquals(3.14F, tree.score(v), Math.ulp(3.14F));
        v.setFeatureScore(0, 4.322F);
        assertEquals(0.234F, tree.score(v), Math.ulp(0.234F));
    }

    public void testFeaturesInCorrectOrder() throws IOException {
        FeatureSet set = randomFeatureSet(2);
        String featureNames = "[\"" + set.feature(1).name() + "\",\"" + set.feature(0).name() + "\"]";
        String model = generateRoot(featureNames, generateTree(
                generateSplit(0F, 0, generateLeaf(-1F), generateLeaf(1F))));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        LtrRanker.FeatureVector v = tree.newFeatureVector(null);
        v.setFeatureScore(0, 10F);
        v.setFeatureScore(1, -10F);
        // splitFeature was set to 0, but that pointed to feature 1 in the featureNames.
        // lightgbm uses approx `fval <= threshold ? left : right`
        // or for our case, -10F <= 0 ? -1F : 1F
        assertEquals(-1F, tree.score(v), Math.ulp(1F));
    }

    private String generateRandomRoot(String featureNames) {
        String[] trees = new String[random().nextInt(20)];
        for (int i = 0; i < trees.length; i++) {
            trees[i] = generateRandomTree();
        }
        return generateRoot(featureNames, trees);
    }

    private String generateRoot(String featureNames, String... trees) {
        return "{\"version\": \"v2\", \"feature_names\": " + featureNames
            + ",\"tree_info\": [" + String.join(",", trees) + "]}";
    }

    private String generateRandomTree() {
        return generateTree(generateRandomSplit());
    }

    private String generateTree(String node) {
        return "{\"tree_structure\":" + node + "}";
    }

    private String generateRandomSplit() {
        String left = random().nextBoolean() ? generateRandomSplit() : generateRandomLeaf();
        String right = random().nextBoolean() ? generateRandomSplit() : generateRandomLeaf();
        return generateRandomSplit(left, right);
    }

    private String generateRandomSplit(String left, String right) {
        return generateSplit(random().nextFloat(), random().nextInt(), left, right);
    }

    private String generateSplit(float threshold, int splitFeature, String left, String right) {
        return "{\"default_left\": true, \"missing_type\": \"None\", \"decision_type\": \"<=\""
            + ",\"threshold\":" + Float.toString(threshold)
            + ",\"split_feature\":" + Integer.toString(splitFeature)
            + ",\"left_child\":" + left
            + ",\"right_child\":" + right
            + "}";
    }

    private String generateRandomLeaf() {
        return generateLeaf(random().nextFloat());
    }

    private String generateLeaf(float value) {
        return "{\"leaf_value\": " + Float.toString(value) + "}";
    }

    private String generateFeatureNames(FeatureSet set) {
        return generateFeatureNames(
                IntStream.range(0, set.size())
                        .mapToObj(i -> set.feature(i).name())
                        .collect(Collectors.toList()));
    }

    private String generateFeatureNames(List<String> featureNames) {
        return "[\"" + String.join("\",\"", featureNames) + "\"]";
    }

    public void testComplexModel() throws IOException {
        String model = readModel("/models/lightgbm-example.json");
        List<StoredFeature> features = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            features.add(LtrTestUtils.randomFeature("feature_" + Integer.toString(i)));
        }
        StoredFeatureSet set = new StoredFeatureSet("set", features);
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        DenseFeatureVector v = tree.newFeatureVector(null);
        assertEquals(v.scores.length, features.size());

        for (int i = random().nextInt(5000) + 1000; i > 0; i--) {
            LinearRankerTests.fillRandomWeights(v.scores);
            assertFalse(Float.isNaN(tree.score(v)));
        }

        // Example values sourced from same model in python Booster.predict(x, raw_score=True)
        Arrays.fill(v.scores, 0F);
        assertAlmostEquals(-0.12437760472589514F, tree.score(v));
        Arrays.fill(v.scores, 1F);
        assertAlmostEquals(0.1487958284969602F, tree.score(v));
        v.scores[0] = 0F;
        assertAlmostEquals(0.190454992666586F, tree.score(v));
        Arrays.fill(v.scores, 1, 6, 0F);
        assertAlmostEquals(0.017346924463503843F, tree.score(v));
    }

    private static void assertAlmostEquals(float expected, float actual) {
        assertEquals(expected, actual, Math.ulp(expected));
    }

    private String readModel(String model) throws IOException {
        try (InputStream is = this.getClass().getResourceAsStream(model)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Streams.copy(is, bos);
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }
}
