package com.o19s.es.ltr.ranker.parser.json.tree;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;

/**
 * Created by doug on 5/28/17.
 */
public class ParsedForest {
    public static final String NAME = "forest-parser";
    private static final ObjectParser<ParsedForest, ParsedForest.Context> PARSER;


    private List<ParsedEnsemble> _ensembles;
    static {
        PARSER = new ObjectParser<>(NAME, ParsedForest::new);
        PARSER.declareObjectArray(
                ParsedForest::ensembles,
                (xContent, context) -> context.parseEnsemble(xContent),
                new ParseField("forest")
        );
    }


    public static class Context {

        ParsedEnsemble parseEnsemble(XContentParser xContent) throws IOException {
            return ParsedEnsemble.parse(xContent);
        }

    }

    void ensembles(List<ParsedEnsemble> ensembles) {
        _ensembles = ensembles;
    }

    public List<ParsedEnsemble> ensembles() {
        return _ensembles;
    }

    public static ParsedForest parse(XContentParser xParser) throws IOException {
        return PARSER.parse(xParser, new ParsedForest.Context());
    }
}
