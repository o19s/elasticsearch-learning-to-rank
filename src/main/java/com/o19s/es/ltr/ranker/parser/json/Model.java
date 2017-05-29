package com.o19s.es.ltr.ranker.parser.json;

import ciir.umass.edu.learning.tree.RFRanker;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.parser.json.tree.ParsedEnsemble;
import com.o19s.es.ltr.ranker.parser.json.tree.ParsedForest;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;

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

        PARSER.declareObject(Model::forestModel,
                (xContent, context) -> context.parseRandomForest(xContent),
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
        mart = model;
    }

    private ParsedForest forest;
    private ParsedEnsemble mart;

    LtrRanker toModel() {
        return null;
    }



}
