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

public class XGBoostJsonParserV2 implements LtrRankerParser {

    public static final String TYPE = "model/xgboost+json+v2";

    private static final Integer MISSING_NODE_ID = Integer.MAX_VALUE;

    @Override
    public NaiveAdditiveDecisionTree parse(FeatureSet set, String model) {
        XGBoostJsonParserV2.XGBoostDefinition modelDefinition;
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(XContentParserConfiguration.EMPTY,
                model)
        ) {
            modelDefinition = XGBoostJsonParserV2.XGBoostDefinition.parse(parser, set);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot parse model", e);
        }

        NaiveAdditiveDecisionTree.Node[] trees = modelDefinition.getLearner().getTrees(set);
        float[] weights = new float[trees.length];
        Arrays.fill(weights, 1F);
        return new NaiveAdditiveDecisionTree(trees, weights, set.size(), modelDefinition.getLearner().getObjective().getNormalizer());
    }

    private static class XGBoostDefinition {
        private static final ObjectParser<XGBoostJsonParserV2.XGBoostDefinition, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_definition", true, XGBoostJsonParserV2.XGBoostDefinition::new);
            PARSER.declareObject(XGBoostJsonParserV2.XGBoostDefinition::setLearner, XGBoostJsonParserV2.XGBoostLearner::parse, new ParseField("learner"));
            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostDefinition::setVersion, new ParseField("version"));
        }

        public static XGBoostJsonParserV2.XGBoostDefinition parse(XContentParser parser, FeatureSet set) throws IOException {
            XGBoostJsonParserV2.XGBoostDefinition definition;
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
            } else {
                throw new ParsingException(parser.getTokenLocation(), "Expected [START_ARRAY] or [START_OBJECT] but got ["
                        + startToken + "]");
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

    static class XGBoostObjective {
        private String name;

        private static final ObjectParser<XGBoostJsonParserV2.XGBoostObjective, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_objective", true, XGBoostJsonParserV2.XGBoostObjective::new);
            PARSER.declareString(XGBoostJsonParserV2.XGBoostObjective::setName, new ParseField("name"));
        }

        public static XGBoostJsonParserV2.XGBoostObjective parse(XContentParser parser, FeatureSet set) throws IOException {
            return PARSER.apply(parser, set);
        }

        public XGBoostObjective() {
        }

        public XGBoostObjective(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        Normalizer getNormalizer() {
            switch (this.name) {
                case "binary:logitraw", "rank:ndcg", "rank:map", "rank:pairwise", "reg:linear" -> {
                    return Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME);
                }
                case "binary:logistic", "reg:logistic" -> {
                    return Normalizers.get(Normalizers.SIGMOID_NORMALIZER_NAME);
                }
                default ->
                        throw new IllegalArgumentException("Objective [" + name + "] is not a valid XGBoost objective");
            }
        }
    }

    static class XGBoostGradientBooster {
        private XGBoostModel model;

        private static final ObjectParser<XGBoostJsonParserV2.XGBoostGradientBooster, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_gradient_booster", true, XGBoostJsonParserV2.XGBoostGradientBooster::new);
            PARSER.declareObject(XGBoostJsonParserV2.XGBoostGradientBooster::setModel, XGBoostJsonParserV2.XGBoostModel::parse, new ParseField("model"));
        }

        public static XGBoostJsonParserV2.XGBoostGradientBooster parse(XContentParser parser, FeatureSet set) throws IOException {
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
        private List<XGBoostTree> trees;

        private static final ObjectParser<XGBoostJsonParserV2.XGBoostModel, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_model", true, XGBoostJsonParserV2.XGBoostModel::new);
            PARSER.declareObjectArray(XGBoostJsonParserV2.XGBoostModel::setTrees, XGBoostJsonParserV2.XGBoostTree::parse, new ParseField("trees"));
        }

        public static XGBoostJsonParserV2.XGBoostModel parse(XContentParser parser, FeatureSet set) throws IOException {
            return PARSER.apply(parser, set);
        }

        public XGBoostModel() {
        }

        public List<XGBoostTree> getTrees() {
            return trees;
        }

        public void setTrees(List<XGBoostTree> trees) {
            this.trees = trees;
        }
    }

    static class XGBoostLearner {

        //        private int numOutputGroup;
//        int numFeature;
//        float baseScore;
        private List<Integer> treeInfo;
        private XGBoostGradientBooster gradientBooster;
        private XGBoostObjective objective;

        private static final ObjectParser<XGBoostJsonParserV2.XGBoostLearner, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_learner", true, XGBoostJsonParserV2.XGBoostLearner::new);
            PARSER.declareObject(XGBoostJsonParserV2.XGBoostLearner::setObjective, XGBoostJsonParserV2.XGBoostObjective::parse, new ParseField("objective"));
            PARSER.declareObject(XGBoostJsonParserV2.XGBoostLearner::setGradientBooster, XGBoostJsonParserV2.XGBoostGradientBooster::parse, new ParseField("gradient_booster"));
            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostLearner::setTreeInfo, new ParseField("tree_info"));
        }

        public static XGBoostJsonParserV2.XGBoostLearner parse(XContentParser parser, FeatureSet set) throws IOException {
            return PARSER.apply(parser, set);
        }

        XGBoostLearner() {
        }

        NaiveAdditiveDecisionTree.Node[] getTrees(FeatureSet set) {
            List<XGBoostTree> parsedTrees = this.getGradientBooster().getModel().getTrees();
            NaiveAdditiveDecisionTree.Node[] trees = new NaiveAdditiveDecisionTree.Node[parsedTrees.size()];
            ListIterator<XGBoostJsonParserV2.XGBoostTree> it = parsedTrees.listIterator();
            while (it.hasNext()) {
                trees[it.nextIndex()] = it.next().asLibTree();
            }
            return trees;
        }


        public XGBoostObjective getObjective() {
            return objective;
        }

        public void setObjective(XGBoostObjective objective) {
            this.objective = objective;
        }

        public List<Integer> getTreeInfo() {
            return treeInfo;
        }

        public void setTreeInfo(List<Integer> treeInfo) {
            this.treeInfo = treeInfo;
        }

        public XGBoostGradientBooster getGradientBooster() {
            return gradientBooster;
        }

        public void setGradientBooster(XGBoostGradientBooster gradientBooster) {
            this.gradientBooster = gradientBooster;
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

        private static final ObjectParser<XGBoostJsonParserV2.XGBoostTree, FeatureSet> PARSER;

        static {
            PARSER = new ObjectParser<>("xgboost_tree", true, XGBoostJsonParserV2.XGBoostTree::new);
            PARSER.declareInt(XGBoostJsonParserV2.XGBoostTree::setTreeId, new ParseField("id"));
            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostTree::setLeftChildren, new ParseField("left_children"));
            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostTree::setRightChildren, new ParseField("right_children"));
            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostTree::setParents, new ParseField("parents"));
            PARSER.declareFloatArray(XGBoostJsonParserV2.XGBoostTree::setSplitConditions, new ParseField("split_conditions"));
            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostTree::setSplitIndices, new ParseField("split_indices"));
            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostTree::setDefaultLeft, new ParseField("default_left"));
            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostTree::setSplitTypes, new ParseField("split_type"));
//            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostTree::setCatSegments, new ParseField("categories_segments"));
//            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostTree::setCatSizes, new ParseField("categories_sizes"));
//            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostTree::setCatNodes, new ParseField("categories_nodes"));
//            PARSER.declareIntArray(XGBoostJsonParserV2.XGBoostTree::setCats, new ParseField("categories"));
            PARSER.declareFloatArray(XGBoostJsonParserV2.XGBoostTree::setBaseWeights, new ParseField("base_weights"));
        }

        public static XGBoostJsonParserV2.XGBoostTree parse(XContentParser parser, FeatureSet set) throws IOException {
            return PARSER.apply(parser, set);
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

        public NaiveAdditiveDecisionTree.Node asLibTree() {
            return this.asLibTree(0);
        }

        private boolean isSplit(Integer nodeId) {
            return leftChildren.get(nodeId) != -1 && rightChildren.get(nodeId) != -1;
        }

        private NaiveAdditiveDecisionTree.Node asLibTree(Integer nodeId) {
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
    }
}
