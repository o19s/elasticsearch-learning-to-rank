/*
 * Copyright [2017] OpenSource Connections
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
package com.o19s.es.ltr.ranker.parser.json.tree;

import ciir.umass.edu.learning.tree.RFRanker;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import com.o19s.es.ltr.ranker.parser.json.FeatureRegister;
import com.o19s.es.ltr.ranker.parser.json.Rankerable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Created by doug on 5/26/17.
 */
public class ParsedEnsemble implements Rankerable {
    public static final String NAME = "ensemble-parser";
    private static final ObjectParser<ParsedEnsemble, ParsedEnsemble.Context> PARSER;


    static {
        PARSER = new ObjectParser<>(NAME, ParsedEnsemble::new);
        PARSER.declareObjectArray(
                ParsedEnsemble::trees,
                (xContent, context) -> context.parseTree(xContent),
                new ParseField("ensemble")
        );


    }


    public void trees(List<ParsedTree> splits) {
        _trees = Objects.requireNonNull(splits);
    }

    public List<ParsedTree> trees() {
        return _trees;
    }

    public static class Context {

        ParsedTree parseTree(XContentParser xContent) throws IOException {
            return ParsedTree.parse(xContent);
        }

    }

    // Parse
    // {
    //    "ensemble": [
    //       {
    //         "weight": 0.5,
    //         "id": "1"
      //       "split": {
    //            "threshold": 252,
    //            "feature": "foo",
    //            "lhs": {
    //               "output": 224.0
    //             }
    //             "rhs": {
    //                "split": { /* another tree */ }
      //           ]
    //          },
    //       {
    //          ...
    //       }
    //    ]
    // }
    public static ParsedEnsemble parse(XContentParser xContent) throws IOException {
        return PARSER.parse(xContent, new Context());
    }



    @Override
    public LtrRanker toRanker(FeatureRegister register) {
        NaiveAdditiveDecisionTree.Node[] trees = new NaiveAdditiveDecisionTree.Node[trees().size()];
        float weights[] = new float[trees().size()];
        int i = 0;
        for (ParsedTree tree: trees()) {
            weights[i] = (float)tree.weight();
            trees[i] = toNode(tree.root(), register);
            i++;
        }
        LtrRanker rVal = new NaiveAdditiveDecisionTree(trees, weights, register.numFeaturesUsed());
        return rVal;
    }


    private static NaiveAdditiveDecisionTree.Node toNode(ParsedSplit sp, FeatureRegister register) {

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



    private List<ParsedTree> _trees;

}
