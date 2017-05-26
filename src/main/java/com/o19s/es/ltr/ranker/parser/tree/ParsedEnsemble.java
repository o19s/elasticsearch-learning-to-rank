package com.o19s.es.ltr.ranker.parser.tree;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;

/**
 * Created by doug on 5/26/17.
 */
public class ParsedEnsemble {
    public static final String NAME = "ensemble-parser";
    private static final ObjectParser<ParsedEnsemble, ParsedEnsemble.Context> PARSER;


    private List<ParsedSplit> _splits;
    static {
        PARSER = new ObjectParser<>(NAME, ParsedEnsemble::new);
        PARSER.declareObjectArray(
                ParsedEnsemble::splits,
                (xContent, context) -> context.parseSplit(xContent),
                new ParseField("ensemble")
        );


    }


    public void splits(List<ParsedSplit> splits) {
        _splits = splits;
    }

    public List<ParsedSplit> splits() {
        return _splits;
    }

    public static class Context {

        ParsedSplit parseSplit(XContentParser xContent) throws IOException {
            return ParsedSplit.parse(xContent);
        }

    }

    // Parse
    // {
    //    "ensemble": [
    //       {
    //          "threshold": 252,
    //          "feature": "foo",
    //          "splits": [
    //              {"output": 224.0}
    //              {"split": { /* another tree */ }}
    //           ]
    //        },
    //       {
    //          "threshold": ...
    //          ...
    //       }
    //    ]
    // }
    public static ParsedEnsemble parseEnsemble(XContentParser xContent) throws IOException {
        return PARSER.parse(xContent, new Context());
    }
}
