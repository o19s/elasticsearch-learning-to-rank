//package com.o19s.es.ltr.ranker.parser;
//
//import com.fasterxml.jackson.core.JsonFactory;
//import com.fasterxml.jackson.core.JsonParser;
//import com.o19s.es.ltr.feature.FeatureSet;
//import com.o19s.es.ltr.ranker.LtrRanker;
//import org.elasticsearch.common.ParseField;
//import org.elasticsearch.common.xcontent.NamedXContentRegistry;
//import org.elasticsearch.common.xcontent.ObjectParser;
//import org.elasticsearch.common.xcontent.XContentParser;
//import org.elasticsearch.common.xcontent.json.JsonXContentParser;
//
//import java.io.IOException;
//
///**
// * Created by doug on 5/26/17.
// */
//public class JsonLtrRankerParser implements LtrRankerParser {
//
//    public static final String NAME = "json-ltr-ranker-parser";
//    //private static final ObjectParser<ParsedScript, ParseContext> PARSER;
//
//
//    static {
//
////
////        PARSER = new ObjectParser<>(NAME, ParsedScript::new);
////        PARSER.declareObject(((parsedScript, fullModel) ->  parsedScript.setModel(fullModel)),
////                (xParser, ctx) -> ctx.parseRandomForest(xParser),
////                new ParseField(("random_forest")));
////
////        PARSER.declareObject(((parsedScript, fullModel) ->  parsedScript.setModel(fullModel)),
////                (xParser, ctx) -> ctx.parseEnsemble(xParser),
////                new ParseField(("mart")));
////
////        PARSER.declareObject(((parsedScript, fullModel) ->  parsedScript.setModel(fullModel)),
////                (xParser, ctx) -> ctx.parseLinear(xParser),
////                new ParseField(("linear")));
////
////
////        //ENSEMBLE_PARSER = new ObjectParser<>(NAME + "_ensemble", )
//
//
////        PARSER.dec
////
////
////        PARSER.declareNamedObjects(
////
////                new ParseField()
////        );
////
////
////        PARSER.declareObjectArray(
////                (ltr, features) -> ltr.features(features),
////                (parser, context) -> context.parseInnerQueryBuilder().get(),
////                new ParseField("features"));
////        PARSER.declareField(
////                (parser, ltr, context) -> ltr.rankerScript(Script.parse(parser, "ranklib")),
////                new ParseField("model"), ObjectParser.ValueType.OBJECT_OR_STRING);
//    }
//
//    public static class ParseContext {
//        Object parseRandomForest(XContentParser xContent) {
//            return null;
//        }
//        Object parseEnsemble(XContentParser xContent) {
//            return null;
//        }
//        Object parseLinear(XContentParser xContent) {
//            return null;
//        }
//
//    }
//
//
//    public static class ParsedScript {
//        public boolean isCompressed;
//
//        public ParsedScript() {
//            isCompressed = false;
//        }
//
//        public void setModel() {
//
//        }
//
//
//        public void setCompressed(boolean isCompressed) {
//        }
//
//        public void parseCompressedModel(String compressed) {
//        }
//
//    }
//
//
//    @Override
//    public LtrRanker parse(FeatureSet set, String model) {
//        JsonFactory jfactory = new JsonFactory();
//        try {
//            JsonParser jParser = jfactory.createParser(model);
//            XContentParser xContent = new JsonXContentParser(NamedXContentRegistry.EMPTY, jParser);
//            PARSER.parse(xContent, new ParseContext());
//
//        } catch (IOException e) {
//            return null;
//        }
//        return null;
//    }
//}
