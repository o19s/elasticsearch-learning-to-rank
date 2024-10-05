package com.o19s.es.ltr.ranker.parser;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import com.o19s.es.ltr.ranker.normalizer.Normalizers;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.xcontent.*;
import org.elasticsearch.xcontent.json.JsonXContent;

import java.io.IOException;
import java.util.*;

public class XGBoostRawJsonParser implements LtrRankerParser {

    public static final String TYPE = "model/xgboost+json+raw";

    private static final Integer MISSING_NODE_ID = Integer.MAX_VALUE;

    @Override
    public NaiveAdditiveDecisionTree parse(FeatureSet set, String model) {
        XGBoostRawJsonParser.XGBoostDefinition modelDefinition;
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(XContentParserConfiguration.EMPTY,
                model)
        ) {
            modelDefinition = XGBoostRawJsonParser.XGBoostDefinition.parse(parser, set);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot parse model", e);
        }

        NaiveAdditiveDecisionTree.Node[] trees = modelDefinition.getLearner().getTrees(set);
        float[] weights = new float[trees.length];
        Arrays.fill(weights, 1F);
        return new NaiveAdditiveDecisionTree(trees, weights, set.size(), modelDefinition.getLearner().getObjective().getNormalizer());
    }

    private static class XGBoostDefinition {
        private static final ObjectParser<XGBoostRawJsonParser.XGBoostDefinition, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_definition", true, XGBoostRawJsonParser.XGBoostDefinition::new);
            PARSER.declareObject(XGBoostRawJsonParser.XGBoostDefinition::setLearner, XGBoostRawJsonParser.XGBoostLearner::parse, new ParseField("learner"));
            PARSER.declareIntArray(XGBoostRawJsonParser.XGBoostDefinition::setVersion, new ParseField("version"));
        }

        public static XGBoostRawJsonParser.XGBoostDefinition parse(XContentParser parser, FeatureSet set) throws IOException {
            XGBoostRawJsonParser.XGBoostDefinition definition;
            XContentParser.Token startToken = parser.nextToken();

            if (startToken == XContentParser.Token.START_OBJECT) {
                try {
                    definition = PARSER.apply(parser, set);
                } catch (XContentParseException e) {
                    throw new ParsingException(parser.getTokenLocation(), "Unable to parse XGBoost object", e);
                }
                if (definition.learner == null) {
                    throw new ParsingException(parser.getTokenLocation(), "XGBoost model missing required field [learner]");
                }
                List<String> unknownFeatures = new ArrayList<>();
                for (String modelFeatureName : definition.learner.featureNames) {
                    if (!set.hasFeature(modelFeatureName)) {
                        unknownFeatures.add(modelFeatureName);
                    }
                }
                if (!unknownFeatures.isEmpty()) {
                    throw new ParsingException(parser.getTokenLocation(), "Unknown features in model: [" + String.join(", ", unknownFeatures) + "]");
                }
            } else {
                throw new ParsingException(parser.getTokenLocation(), "Expected [START_OBJECT] but got [" + startToken + "]");
            }
            return definition;
        }

        private XGBoostLearner learner;

        public XGBoostLearner getLearner() {
            return learner;
        }

        public void setLearner(XGBoostLearner learner) {
            this.learner = learner;
        }

        private List<Integer> version;

        public List<Integer> getVersion() {
            return version;
        }

        public void setVersion(List<Integer> version) {
            this.version = version;
        }
    }

    static class XGBoostLearner {

        private List<String> featureNames;
        private List<String> featureTypes;
        private XGBoostGradientBooster gradientBooster;
        private XGBoostObjective objective;

        private static final ObjectParser<XGBoostRawJsonParser.XGBoostLearner, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_learner", true, XGBoostRawJsonParser.XGBoostLearner::new);
            PARSER.declareObject(XGBoostRawJsonParser.XGBoostLearner::setObjective, XGBoostRawJsonParser.XGBoostObjective::parse, new ParseField("objective"));
            PARSER.declareObject(XGBoostRawJsonParser.XGBoostLearner::setGradientBooster, XGBoostRawJsonParser.XGBoostGradientBooster::parse, new ParseField("gradient_booster"));
            PARSER.declareStringArray(XGBoostRawJsonParser.XGBoostLearner::setFeatureNames, new ParseField("feature_names"));
            PARSER.declareStringArray(XGBoostRawJsonParser.XGBoostLearner::setFeatureTypes, new ParseField("feature_types"));
        }

        private void setFeatureTypes(List<String> featureTypes) {
            this.featureTypes = featureTypes;
        }

        private void setFeatureNames(List<String> featureNames) {
            this.featureNames = featureNames;
        }

        public static XGBoostRawJsonParser.XGBoostLearner parse(XContentParser parser, FeatureSet set) throws IOException {
            return PARSER.apply(parser, set);
        }

        XGBoostLearner() {
        }

        NaiveAdditiveDecisionTree.Node[] getTrees(FeatureSet set) {
            return this.getGradientBooster().getModel().getTrees();
        }


        public XGBoostObjective getObjective() {
            return objective;
        }

        public void setObjective(XGBoostObjective objective) {
            this.objective = objective;
        }

        public XGBoostGradientBooster getGradientBooster() {
            return gradientBooster;
        }

        public void setGradientBooster(XGBoostGradientBooster gradientBooster) {
            this.gradientBooster = gradientBooster;
        }
    }

    static class XGBoostGradientBooster {
        private XGBoostModel model;

        private static final ObjectParser<XGBoostRawJsonParser.XGBoostGradientBooster, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_gradient_booster", true, XGBoostRawJsonParser.XGBoostGradientBooster::new);
            PARSER.declareObject(XGBoostRawJsonParser.XGBoostGradientBooster::setModel, XGBoostRawJsonParser.XGBoostModel::parse, new ParseField("model"));
        }

        public static XGBoostRawJsonParser.XGBoostGradientBooster parse(XContentParser parser, FeatureSet set) throws IOException {
            return PARSER.apply(parser, set);
        }

        public XGBoostGradientBooster() {
        }

        public XGBoostModel getModel() {
            return model;
        }

        public void setModel(XGBoostModel model) {
            this.model = model;
        }
    }

    static class XGBoostModel {
        private NaiveAdditiveDecisionTree.Node[] trees;
        private List<Integer> treeInfo;

        private static final ObjectParser<XGBoostRawJsonParser.XGBoostModel, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_model", true, XGBoostRawJsonParser.XGBoostModel::new);
            PARSER.declareObjectArray(XGBoostRawJsonParser.XGBoostModel::setTrees, XGBoostRawJsonParser.XGBoostTree::parse, new ParseField("trees"));
            PARSER.declareIntArray(XGBoostRawJsonParser.XGBoostModel::setTreeInfo, new ParseField("tree_info"));
        }

        public List<Integer> getTreeInfo() {
            return treeInfo;
        }

        public void setTreeInfo(List<Integer> treeInfo) {
            this.treeInfo = treeInfo;
        }

        public static XGBoostRawJsonParser.XGBoostModel parse(XContentParser parser, FeatureSet set) throws IOException {
            try {
                return PARSER.apply(parser, set);
            } catch (IllegalArgumentException e) {
                throw new ParsingException(parser.getTokenLocation(), e.getMessage(), e);
            }
        }

        public XGBoostModel() {
        }

        public NaiveAdditiveDecisionTree.Node[] getTrees() {
            return trees;
        }

        public void setTrees(List<XGBoostTree> parsedTrees) {
            NaiveAdditiveDecisionTree.Node[] trees = new NaiveAdditiveDecisionTree.Node[parsedTrees.size()];
            ListIterator<XGBoostRawJsonParser.XGBoostTree> it = parsedTrees.listIterator();
            while (it.hasNext()) {
                trees[it.nextIndex()] = it.next().getRootNode();
            }
            this.trees = trees;
        }
    }

    static class XGBoostObjective {
        private Normalizer normalizer;

        private static final ObjectParser<XGBoostRawJsonParser.XGBoostObjective, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_objective", true, XGBoostRawJsonParser.XGBoostObjective::new);
            PARSER.declareString(XGBoostRawJsonParser.XGBoostObjective::setName, new ParseField("name"));
        }

        public static XGBoostRawJsonParser.XGBoostObjective parse(XContentParser parser, FeatureSet set) throws IOException {
            return PARSER.apply(parser, set);
        }

        public XGBoostObjective() {
        }


        public void setName(String name) {
            switch (name) {
                case "binary:logitraw", "rank:ndcg", "rank:map", "rank:pairwise", "reg:linear" ->
                        this.normalizer = Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME);
                case "binary:logistic", "reg:logistic" ->
                        this.normalizer = Normalizers.get(Normalizers.SIGMOID_NORMALIZER_NAME);
                default ->
                        throw new IllegalArgumentException("Objective [" + name + "] is not a valid XGBoost objective");
            }
        }

        Normalizer getNormalizer() {
            return this.normalizer;
        }
    }

    static class XGBoostTree {
        private Integer treeId;
        private List<Integer> leftChildren;
        private List<Integer> rightChildren;
        private List<Integer> parents;
        private List<Float> splitConditions;
        private List<Integer> splitIndices;
        private List<Integer> defaultLeft;
        private List<Integer> splitTypes;
        private List<Float> baseWeights;

        private NaiveAdditiveDecisionTree.Node rootNode;

        private static final ObjectParser<XGBoostRawJsonParser.XGBoostTree, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_tree", true, XGBoostRawJsonParser.XGBoostTree::new);
            PARSER.declareInt(XGBoostRawJsonParser.XGBoostTree::setTreeId, new ParseField("id"));
            PARSER.declareIntArray(XGBoostRawJsonParser.XGBoostTree::setLeftChildren, new ParseField("left_children"));
            PARSER.declareIntArray(XGBoostRawJsonParser.XGBoostTree::setRightChildren, new ParseField("right_children"));
            PARSER.declareIntArray(XGBoostRawJsonParser.XGBoostTree::setParents, new ParseField("parents"));
            PARSER.declareFloatArray(XGBoostRawJsonParser.XGBoostTree::setSplitConditions, new ParseField("split_conditions"));
            PARSER.declareIntArray(XGBoostRawJsonParser.XGBoostTree::setSplitIndices, new ParseField("split_indices"));
            PARSER.declareIntArray(XGBoostRawJsonParser.XGBoostTree::setDefaultLeft, new ParseField("default_left"));
            PARSER.declareIntArray(XGBoostRawJsonParser.XGBoostTree::setSplitTypes, new ParseField("split_type"));
            PARSER.declareFloatArray(XGBoostRawJsonParser.XGBoostTree::setBaseWeights, new ParseField("base_weights"));
        }

        public static XGBoostRawJsonParser.XGBoostTree parse(XContentParser parser, FeatureSet set) throws IOException {
            XGBoostRawJsonParser.XGBoostTree tree = PARSER.apply(parser, set);
            tree.rootNode = tree.asLibTree(0);
            return tree;
        }

        public Integer getTreeId() {
            return treeId;
        }

        public void setTreeId(Integer treeId) {
            this.treeId = treeId;
        }

        public List<Integer> getLeftChildren() {
            return leftChildren;
        }

        public void setLeftChildren(List<Integer> leftChildren) {
            this.leftChildren = leftChildren;
        }

        public List<Integer> getRightChildren() {
            return rightChildren;
        }

        public void setRightChildren(List<Integer> rightChildren) {
            this.rightChildren = rightChildren;
        }

        public List<Integer> getParents() {
            return parents;
        }

        public void setParents(List<Integer> parents) {
            this.parents = parents;
        }

        public List<Float> getSplitConditions() {
            return splitConditions;
        }

        public void setSplitConditions(List<Float> splitConditions) {
            this.splitConditions = splitConditions;
        }

        public List<Integer> getSplitIndices() {
            return splitIndices;
        }

        public void setSplitIndices(List<Integer> splitIndices) {
            this.splitIndices = splitIndices;
        }

        public List<Integer> getDefaultLeft() {
            return defaultLeft;
        }

        public void setDefaultLeft(List<Integer> defaultLeft) {
            this.defaultLeft = defaultLeft;
        }

        public List<Integer> getSplitTypes() {
            return splitTypes;
        }

        public void setSplitTypes(List<Integer> splitTypes) {
            this.splitTypes = splitTypes;
        }

        private boolean isSplit(Integer nodeId) {
            return leftChildren.get(nodeId) != -1 && rightChildren.get(nodeId) != -1;
        }

        private NaiveAdditiveDecisionTree.Node asLibTree(Integer nodeId) {
            if (nodeId >= leftChildren.size()) {
                throw new IllegalArgumentException("Node ID [" + nodeId + "] is invalid");
            }
            if (nodeId >= rightChildren.size()) {
                throw new IllegalArgumentException("Node ID [" + nodeId + "] is invalid");
            }

            if (isSplit(nodeId)) {
                return new NaiveAdditiveDecisionTree.Split(asLibTree(leftChildren.get(nodeId)), asLibTree(rightChildren.get(nodeId)),
                        splitIndices.get(nodeId), splitConditions.get(nodeId), splitIndices.get(nodeId), MISSING_NODE_ID);
            } else {
                return new NaiveAdditiveDecisionTree.Leaf(baseWeights.get(nodeId));
            }
        }

        public List<Float> getBaseWeights() {
            return baseWeights;
        }

        public void setBaseWeights(List<Float> baseWeights) {
            this.baseWeights = baseWeights;
        }

        public NaiveAdditiveDecisionTree.Node getRootNode() {
            return rootNode;
        }
    }
}
