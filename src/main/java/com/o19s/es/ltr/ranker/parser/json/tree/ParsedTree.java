package com.o19s.es.ltr.ranker.parser.json.tree;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;

/**
 * Created by doug on 5/29/17.
 */
public class ParsedTree {
    public static final String NAME = "tree-ltr-parser";
    private static final ObjectParser<ParsedTree, Context> PARSER;

    static {
        PARSER = new ObjectParser<>(NAME, ParsedTree::new);
        PARSER.declareObject(ParsedTree::root,
                             (xContent, context) -> context.parseRoot(xContent),
                             new ParseField("split"));

        PARSER.declareDouble(ParsedTree::weight,
                             new ParseField("weight"));

        PARSER.declareString(ParsedTree::id,
                             new ParseField("id"));

    }

    public static class Context {
        public ParsedSplit parseRoot(XContentParser xContent) throws IOException {
            return ParsedSplit.parse(xContent);
        }
    }

    public void root(ParsedSplit split) {
        _root = split;
    }

    public ParsedSplit root() {
        return _root;
    }

    public void weight(double val) {
        _weight = val;
    }

    public double weight() {
        return _weight;
    }

    public void id(String id) {
        _id = id;
    }

    public String id() {
        return _id;
    }

    public static ParsedTree parse(XContentParser xContent) throws IOException {
        return PARSER.parse(xContent, new Context());
    }

    private ParsedSplit _root;
    private double _weight;
    private String _id;



}
