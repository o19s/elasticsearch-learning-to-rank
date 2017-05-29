package com.o19s.es.ltr.ranker.parser.json;

import ciir.umass.edu.learning.tree.RFRanker;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import com.o19s.es.ltr.ranker.parser.json.tree.ParsedEnsemble;
import com.o19s.es.ltr.ranker.parser.json.tree.ParsedForest;
import com.o19s.es.ltr.ranker.parser.json.tree.ParsedSplit;
import com.o19s.es.ltr.ranker.parser.json.tree.ParsedTree;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by doug on 5/28/17.
 */
public class Model {


    public static final String NAME = "ltr-model-parser";
    private static final ObjectParser<Model, Model.Context> PARSER;

    static {
        PARSER = new ObjectParser<>(NAME, Model::new);

        PARSER.declareObject(Model::forestModel,
                (xContent, context) -> context.parseRandomForest(xContent),
                new ParseField("random-forest"));

        PARSER.declareObject(Model::martModel,
                (xContent, context) -> context.parseMart(xContent),
                new ParseField("mart"));
    }

    public static class Context {

        ParsedForest parseRandomForest(XContentParser xContent) throws IOException {
            return ParsedForest.parse(xContent);
        }

        ParsedEnsemble parseMart(XContentParser xContent) throws IOException {
            return ParsedEnsemble.parse(xContent);
        }

    }


    public void forestModel(ParsedForest model) {
        forest = model;
    }

    public void martModel(ParsedEnsemble model) {
        _mart = model;
    }

    private ParsedForest forest;
    private ParsedEnsemble _mart;


    public static Model parse(XContentParser xContent) throws IOException {
        return PARSER.parse(xContent, new Context());
    }

    public LtrRanker toModel(FeatureSet set) {
        if (_mart != null) {
            return martToModel(set);
        }
        return null;
    }

    private LtrRanker martToModel(FeatureSet set) {

        if (_mart != null) {
            NaiveAdditiveDecisionTree.Node[] trees = new NaiveAdditiveDecisionTree.Node[_mart.trees().size()];
            float weights[] = new float[_mart.trees().size()];
            int i = 0;
            FeatureRegister register = new FeatureRegister(set);
            for (ParsedTree tree: _mart.trees()) {
                weights[i] = (float)tree.weight();
                trees[i] = toNode(tree.root(), register);
            }
            LtrRanker rVal = new NaiveAdditiveDecisionTree(trees, weights, register.numFeaturesUsed());
            return rVal;
        } else {
            throw new IllegalStateException("LTR Plugin attempting to grab a mart model when none was specified!? Bug?");
        }

    }

    // Used to track which features are used
    private static class FeatureRegister
    {
        FeatureRegister (FeatureSet set) {
            _featureSet = set;
        }

        int useFeature(String featureName) {
            int featureOrd = _featureSet.featureOrdinal(featureName);
            _usedFeatures.add(featureOrd);
            return featureOrd;
        }

        int numFeaturesUsed() {
            return _usedFeatures.size();
        }

        private Set<Integer> _usedFeatures = new HashSet<Integer>();
        private FeatureSet _featureSet;
    }

    private NaiveAdditiveDecisionTree.Node toNode(ParsedSplit sp, FeatureRegister register) {

        if (sp.isLeaf()) {
            return new NaiveAdditiveDecisionTree.Leaf((float) sp.output());
        }
        else {

            int featureOrd = register.useFeature(sp.feature());

            return new NaiveAdditiveDecisionTree.Split( toNode(sp.lhs(), register),
                                                        toNode(sp.rhs(), register),
                                                        featureOrd,
                                                        (float) sp.threshold());
        }
    }



}
