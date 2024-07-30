package com.o19s.es.ltr.ranker.parser;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import com.o19s.es.ltr.ranker.normalizer.Normalizers;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.json.JsonXContent;

import java.io.IOException;
import java.util.*;

public class XGBoostJsonParserV2 implements LtrRankerParser {

    public static final String TYPE = "model/xgboost+json";

    private static final Integer MISSING_NODE_ID = Integer.MAX_VALUE;

    @Override
    public NaiveAdditiveDecisionTree parse(FeatureSet set, String model) {
        XGBoostJsonParserV2.XGBoostDefinition modelDefinition;
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(XContentParserConfiguration.EMPTY,
                model)
        ) {
            modelDefinition = new XGBoostJsonParserV2.XGBoostDefinition(set, parser.map());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse XGBoost object", e);
        }

        NaiveAdditiveDecisionTree.Node[] trees = modelDefinition.getTrees(set);
        float[] weights = new float[trees.length];
        Arrays.fill(weights, 1F);
        return new NaiveAdditiveDecisionTree(trees, weights, set.size(), modelDefinition.normalizer);
    }

    enum SplitType {
        NUMERICAL(0),
        CATEGORICAL(1);

        private final int value;

        SplitType(int value) {
            this.value = value;
        }

        public static SplitType fromValue(int value) {
            for (SplitType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown SplitType value: " + value);
        }
    }

    class Node {
        int nodeid;
        int left;
        int right;
        int parent;
        int splitIdx;
        float splitCond;
        boolean defaultLeft;
        SplitType splitType;
        List<Integer> categories;
        float baseWeight;
        float lossChg;
        float sumHess;

        Node(int nodeid, int left, int right, int parent, int splitIdx, float splitCond, boolean defaultLeft,
             SplitType splitType, List<Integer> categories, float baseWeight, float lossChg, float sumHess) {
            this.nodeid = nodeid;
            this.left = left;
            this.right = right;
            this.parent = parent;
            this.splitIdx = splitIdx;
            this.splitCond = splitCond;
            this.defaultLeft = defaultLeft;
            this.splitType = splitType;
            this.categories = categories;
            this.baseWeight = baseWeight;
            this.lossChg = lossChg;
            this.sumHess = sumHess;
        }
    }

    class Tree {
        int treeId;
        List<Node> nodes;

        Tree(int treeId, List<Node> nodes) {
            this.treeId = treeId;
            this.nodes = nodes;
        }

        float lossChange(int nodeId) {
            return nodes.get(nodeId).lossChg;
        }

        float sumHessian(int nodeId) {
            return nodes.get(nodeId).sumHess;
        }

        float baseWeight(int nodeId) {
            return nodes.get(nodeId).baseWeight;
        }

        int splitIndex(int nodeId) {
            return nodes.get(nodeId).splitIdx;
        }

        float splitCondition(int nodeId) {
            return nodes.get(nodeId).splitCond;
        }

        List<Integer> splitCategories(int nodeId) {
            return nodes.get(nodeId).categories;
        }

        boolean isCategorical(int nodeId) {
            return nodes.get(nodeId).splitType == SplitType.CATEGORICAL;
        }

        boolean isNumerical(int nodeId) {
            return !isCategorical(nodeId);
        }

        int parent(int nodeId) {
            return nodes.get(nodeId).parent;
        }

        int leftChild(int nodeId) {
            return nodes.get(nodeId).left;
        }

        int rightChild(int nodeId) {
            return nodes.get(nodeId).right;
        }

        boolean isLeaf(int nodeId) {
            return nodes.get(nodeId).left == -1 && nodes.get(nodeId).right == -1;
        }

        boolean isSplit(int nodeId) {
            return !this.isLeaf(nodeId);
        }

        boolean isDeleted(int nodeId) {
            return splitIndex(nodeId) == MISSING_NODE_ID;
        }

        NaiveAdditiveDecisionTree.Node toLibNode(int nodeid) {
            if (isSplit(nodeid)) {
                Node node = nodes.get(nodeid);
                return new NaiveAdditiveDecisionTree.Split(toLibNode(node.left), toLibNode(node.right),
                        node.splitIdx, node.splitCond, node.left, MISSING_NODE_ID);
            } else {
                Node node = nodes.get(nodeid);
                return new NaiveAdditiveDecisionTree.Leaf(node.baseWeight);
            }
        }
    }

    class XGBoostDefinition {
        int numOutputGroup;
        int numFeature;
        float baseScore;
        List<Integer> treeInfo;
        List<Tree> trees;
        Normalizer normalizer = Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME);

        XGBoostDefinition(FeatureSet set, Map<String, Object> modelStr) {
            Map<String, String> learnerModelShape = (Map<String, String>) ((Map<String, Object>) modelStr.get("learner")).get("learner_model_param");
            this.numOutputGroup = Integer.parseInt(learnerModelShape.get("num_class"));
            this.numFeature = Integer.parseInt(learnerModelShape.get("num_feature"));
            this.baseScore = Float.parseFloat(learnerModelShape.get("base_score"));

            Map<String, Object> gradientBooster = (Map<String, Object>) ((Map<String, Object>) modelStr.get("learner")).get("gradient_booster");
            this.treeInfo = (List<Integer>) gradientBooster.get("tree_info");
            Map<String, Object> model = (Map<String, Object>) gradientBooster.get("model");
            Map<String, String> modelShape = (Map<String, String>) model.get("gbtree_model_param");

            List<Map<String, Object>> treesObj = (List<Map<String, Object>>) model.get("trees");
            this.trees = new ArrayList<>();
            int numTrees = Integer.parseInt(modelShape.get("num_trees"));

            for (int i = 0; i < numTrees; i++) {
                Map<String, Object> tree = treesObj.get(i);
                int treeId = (int) tree.get("id");

                List<Integer> leftChildren = (List<Integer>) tree.get("left_children");
                List<Integer> rightChildren = (List<Integer>) tree.get("right_children");
                List<Integer> parents = (List<Integer>) tree.get("parents");
                List<Float> splitConditions = ((List<Double>) tree.get("split_conditions")).stream().map(n -> n.floatValue()).toList();
                List<Integer> splitIndices = (List<Integer>) tree.get("split_indices");

                List<Integer> defaultLeft = toIntegers((List<Integer>) tree.get("default_left"));
                List<Integer> splitTypes = toIntegers((List<Integer>) tree.get("split_type"));

                List<Integer> catSegments = (List<Integer>) tree.get("categories_segments");
                List<Integer> catSizes = (List<Integer>) tree.get("categories_sizes");
                List<Integer> catNodes = (List<Integer>) tree.get("categories_nodes");
                List<Integer> cats = (List<Integer>) tree.get("categories");

                int catCnt = 0;
                int lastCatNode = !catNodes.isEmpty() ? catNodes.get(catCnt) : -1;
                List<List<Integer>> nodeCategories = new ArrayList<>();

                for (int nodeId = 0; nodeId < leftChildren.size(); nodeId++) {
                    if (nodeId == lastCatNode) {
                        int beg = catSegments.get(catCnt);
                        int size = catSizes.get(catCnt);
                        int end = beg + size;
                        List<Integer> nodeCats = cats.subList(beg, end);
                        catCnt++;
                        lastCatNode = catCnt < catNodes.size() ? catNodes.get(catCnt) : -1;
                        nodeCategories.add(nodeCats);
                    } else {
                        nodeCategories.add(new ArrayList<>());
                    }
                }

                List<Float> baseWeights = ((List<Double>) tree.get("base_weights")).stream().map(n -> n.floatValue()).toList();
                List<Float> lossChanges = ((List<Double>) tree.get("loss_changes")).stream().map(n -> n.floatValue()).toList();
                List<Float> sumHessian = ((List<Double>) tree.get("sum_hessian")).stream().map(n -> n.floatValue()).toList();

                List<Node> nodes = new ArrayList<>();
                for (int nodeId = 0; nodeId < leftChildren.size(); nodeId++) {
                    nodes.add(new Node(
                            nodeId,
                            leftChildren.get(nodeId),
                            rightChildren.get(nodeId),
                            parents.get(nodeId),
                            splitIndices.get(nodeId),
                            splitConditions.get(nodeId),
                            defaultLeft.get(nodeId) == 1,
                            SplitType.fromValue(splitTypes.get(nodeId)),
                            nodeCategories.get(nodeId),
                            baseWeights.get(nodeId),
                            lossChanges.get(nodeId),
                            sumHessian.get(nodeId)
                    ));
                }

                trees.add(new Tree(treeId, nodes));
            }
        }

        private List<Integer> toIntegers(List<Integer> data) {
            return new ArrayList<>(data);
        }

        NaiveAdditiveDecisionTree.Node[] getTrees(FeatureSet set) {
            NaiveAdditiveDecisionTree.Node[] trees = new NaiveAdditiveDecisionTree.Node[this.trees.size()];
            ListIterator<XGBoostJsonParserV2.Tree> it = this.trees.listIterator();
            while (it.hasNext()) {
                trees[it.nextIndex()] = it.next().toLibNode(0);
            }
            return trees;
        }
    }
}