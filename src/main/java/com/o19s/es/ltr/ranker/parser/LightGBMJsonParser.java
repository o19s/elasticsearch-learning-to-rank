package com.o19s.es.ltr.ranker.parser;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree.Split;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LightGBMJsonParser implements LtrRankerParser {
    public static final String TYPE = "model/lightgbm+json";
    enum MissingType {
        None, Zero, NaN;
    }

    @Override
    public NaiveAdditiveDecisionTree parse(FeatureSet set, String model) {
        RootParserState root;
        List<NaiveAdditiveDecisionTree.Node> trees;
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, model)) {
            trees = RootParserState.parse(parser, set).toNodes(set);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot parse model", e);
        }
        float[] weights = new float[trees.size()];
        Arrays.fill(weights, 1.F);
        return new NaiveAdditiveDecisionTree(trees.toArray(new NaiveAdditiveDecisionTree.Node[0]), weights, set.size());
    }

    private static class SplitParserState {
        private static final ObjectParser<SplitParserState, FeatureSet> PARSER;
        static {
            PARSER = new ObjectParser<>("split", SplitParserState::new);

            PARSER.declareBoolean(SplitParserState::setDefaultLeft, new ParseField("default_left"));
            PARSER.declareString(SplitParserState::setMissingType, new ParseField("missing_type"));
            PARSER.declareFloat((p, s) -> {}, new ParseField("split_gain"));
            PARSER.declareInt((p, s) -> {}, new ParseField("internal_value"));
            PARSER.declareInt((p, s) -> {}, new ParseField("internal_count"));
            PARSER.declareString(SplitParserState::setDecisionType, new ParseField("decision_type"));
            PARSER.declareInt((p, s) -> {}, new ParseField("split_index"));
            PARSER.declareFloat(SplitParserState::setThreshold, new ParseField("threshold"));
            PARSER.declareInt(SplitParserState::setSplitFeature, new ParseField("split_feature"));
            PARSER.declareObject(SplitParserState::setLeftChild, SplitParserState::parse, new ParseField("left_child"));
            PARSER.declareObject(SplitParserState::setRightChild, SplitParserState::parse, new ParseField("right_child"));

            PARSER.declareFloat(SplitParserState::setLeafValue, new ParseField("leaf_value"));
            PARSER.declareInt((p, s) -> {}, new ParseField("leaf_count"));
            PARSER.declareInt((p, s) -> {}, new ParseField("leaf_index"));
        }

        // Properties found in a split
        private Boolean defaultLeft;
        private MissingType missingType;
        private String decisionType;
        private float threshold = Float.NaN;
        private int splitFeature = -1;
        private SplitParserState rightChild;
        private SplitParserState leftChild;

        // Properties found in a leaf
        private Float leafValue;

        public static SplitParserState parse(XContentParser parser, FeatureSet set) {
            SplitParserState split = PARSER.apply(parser, set);
            if (split.isSplit()) {
                if (!split.hasAllSplitFields()) {
                    throw new ParsingException(parser.getTokenLocation(), "This split does not have all the required fields");
                }
                if (!"<=".equals(split.decisionType)) {
                    throw new ParsingException(parser.getTokenLocation(), "Only the <= decision_type is supported");
                }
                if (!split.defaultLeft) {
                    throw new ParsingException(parser.getTokenLocation(), "default_left must be true");
                }
            }
            return split;
        }

        // This is the isZero check used in lightgbm.
        static final float ZERO_THRESHOLD = 1e-35F;

        static boolean isZero(float fval) {
            return fval > -ZERO_THRESHOLD && fval <= ZERO_THRESHOLD;
        }

        // See lightgbm src/io/tree.cpp Tree::NumericalDecisionIfElse
        // TODO: NaN's are invalid lucene scores, should we even check?
        static private final Split.Comparer COMPARER_ZERO_DEFAULT_LEFT = (threshold, fval) -> fval <= threshold || isZero(fval) || Float.isNaN(fval);
        static private final Split.Comparer COMPARER_ZERO_DEFAULT_RIGHT = (threshold, fval) -> fval <= threshold && !isZero(fval) && !Float.isNaN(fval);
        static private final Split.Comparer COMPARER_DEFAULT_LEFT = (threshold, fval) -> fval <= threshold || Float.isNaN(fval);
        static private final Split.Comparer COMPARER_DEFAULT_RIGHT = (threshold, fval) -> fval <= threshold && !Float.isNaN(fval);
        static private final Split.Comparer COMPARER_SIMPLE = (threshold, fval) -> fval <= threshold;

        public Split.Comparer numericalDecision() {
            if (MissingType.None.equals(missingType) || (MissingType.Zero.equals(missingType) && defaultLeft && ZERO_THRESHOLD < threshold)) {
                return COMPARER_SIMPLE;
            }
            if (MissingType.Zero.equals(missingType)) {
                if (defaultLeft) {
                    return COMPARER_ZERO_DEFAULT_LEFT;
                } else {
                    return COMPARER_ZERO_DEFAULT_RIGHT;
                }
            }
            if (defaultLeft) {
                return COMPARER_DEFAULT_LEFT;
            } else {
                return COMPARER_DEFAULT_RIGHT;
            }
        }

        // Setters for all parsed fields

        void setDefaultLeft(boolean defaultLeft) {
            this.defaultLeft = defaultLeft;
        }

        void setMissingType(String missingType) {
            setMissingType(MissingType.valueOf(missingType));
        }

        void setMissingType(MissingType missingType) {
            this.missingType = missingType;
        }

        void setDecisionType(String decisionType) {
            this.decisionType = decisionType;
        }

        void setThreshold(float threshold) {
            this.threshold = threshold;
        }

        void setSplitFeature(int splitFeature) {
            this.splitFeature = splitFeature;
        }

        void setRightChild(SplitParserState child) {
            rightChild = child;
        }

        void setLeftChild(SplitParserState child) {
            leftChild = child;
        }

        void setLeafValue(float leafValue) {
            this.leafValue = leafValue;
        }

        // post-parse validation

        boolean isSplit() {
            return leafValue == null;
        }

        boolean hasAllSplitFields() {
            return defaultLeft != null && missingType != null && decisionType != null && !Float.isNaN(threshold)
                    && splitFeature >= 0 && rightChild != null && leftChild != null;
        }

        NaiveAdditiveDecisionTree.Node toNode(List<String> featureNames, FeatureSet set) {
            if (isSplit()) {
                String featureName = featureNames.get(splitFeature);
                if (!set.hasFeature(featureName)) {
                    // Can't throw parsing exceptions anymore...
                    throw new RuntimeException("TODO");
                }
                int ordinal = set.featureOrdinal(featureName);
                NaiveAdditiveDecisionTree.Node left = leftChild.toNode(featureNames, set);
                NaiveAdditiveDecisionTree.Node right = rightChild.toNode(featureNames, set);
                return new Split(left, right, set.featureOrdinal(featureName), threshold, numericalDecision());

            } else {
                return new NaiveAdditiveDecisionTree.Leaf(leafValue);
            }
        }
    }

    private static class TreeParserState {
        private static final ObjectParser<TreeParserState, FeatureSet> PARSER;
        static {
            PARSER = new ObjectParser<>("tree", TreeParserState::new);
            PARSER.declareInt((p, c) -> {}, new ParseField("num_cat"));
            PARSER.declareInt((p, c) -> {}, new ParseField("tree_index"));
            PARSER.declareInt((p, c) -> {}, new ParseField("num_leaves"));
            PARSER.declareInt((p, c) -> {}, new ParseField("shrinkage"));
            PARSER.declareObject(TreeParserState::setTreeStructure, SplitParserState::parse, new ParseField("tree_structure"));
        }

        private SplitParserState treeStructure;

        public static TreeParserState parse(XContentParser parser, FeatureSet set) {
            TreeParserState tree = PARSER.apply(parser, set);
            if (tree.treeStructure == null) {
                throw new ParsingException(parser.getTokenLocation(), "The tree_structure field must not be missing");
            }
            return tree;
        }

        void setTreeStructure(SplitParserState treeStructure) {
            this.treeStructure = treeStructure;
        }
    }

    private static class RootParserState {
        private static final ObjectParser<RootParserState, FeatureSet> PARSER;
        static {
            PARSER = new ObjectParser<>("root", RootParserState::new);
            PARSER.declareString(RootParserState::setVersion, new ParseField("version"));
            PARSER.declareStringArray(RootParserState::setFeatureNames, new ParseField("feature_names"));
            PARSER.declareObjectArray(RootParserState::setTreeInfo, TreeParserState::parse, new ParseField("tree_info"));
            PARSER.declareInt((p, c) -> {}, new ParseField("num_class"));
            PARSER.declareInt((p, c) -> {}, new ParseField("label_index"));
            PARSER.declareInt((p, c) -> {}, new ParseField("num_tree_per_iteration"));
            PARSER.declareString((p, c) -> {}, new ParseField("name"));
            PARSER.declareInt((p, c) -> {}, new ParseField("max_feature_idx"));
        }

        private String version;
        private List<String> featureNames;
        private List<TreeParserState> treeInfo;

        public static RootParserState parse(XContentParser parser, FeatureSet set) {
            RootParserState root = PARSER.apply(parser, set);
            if (!"v2".equals(root.version)) {
                throw new ParsingException(parser.getTokenLocation(), "Only version v2 is supported");
            }
            if (root.featureNames == null || root.featureNames.size() == 0) {
                throw new ParsingException(parser.getTokenLocation(), "feature_names must not be empty");
            }
            if (!root.featureNames.stream().allMatch(set::hasFeature)) {
                String unknownFeatures = root.featureNames.stream()
                        .filter(n -> !set.hasFeature(n))
                        .collect(Collectors.joining(", ", "Unknown feature names: ", ""));
                throw new ParsingException(parser.getTokenLocation(), unknownFeatures);
            }
            if (root.treeInfo == null) {
                throw new ParsingException(parser.getTokenLocation(), "The tree_info field must not be missing");
            }
            return root;
        }

        void setVersion(String version) {
            this.version = version;
        }

        void setFeatureNames(List<String> featureNames) {
            this.featureNames = featureNames;
        }

        void setTreeInfo(List<TreeParserState> treeInfo) {
            this.treeInfo = treeInfo;
        }

        List<NaiveAdditiveDecisionTree.Node> toNodes(FeatureSet set) {
            List<NaiveAdditiveDecisionTree.Node> trees = new ArrayList<>();
            for (TreeParserState tree : treeInfo) {
                trees.add(tree.treeStructure.toNode(featureNames, set));
            }
            return trees;
        }
    }
}
