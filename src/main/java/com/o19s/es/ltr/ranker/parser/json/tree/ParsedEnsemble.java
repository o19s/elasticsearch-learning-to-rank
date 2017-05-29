package com.o19s.es.ltr.ranker.parser.json.tree;

import ciir.umass.edu.learning.tree.RFRanker;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Created by doug on 5/26/17.
 */
public class ParsedEnsemble {
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


    // Use directly as a Ranker (ie MART)
    public static LtrRanker toRfRanker(ParsedEnsemble ensemble) {
        //NaiveAdditiveDecisionTree.Split optSplit = new NaiveAdditiveDecisionTree.Split()
        return null;
    }

    private List<ParsedTree> _trees;

}
