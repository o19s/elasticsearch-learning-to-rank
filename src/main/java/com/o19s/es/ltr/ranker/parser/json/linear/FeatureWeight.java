package com.o19s.es.ltr.ranker.parser.json.linear;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;

/**
 * Created by doug on 5/29/17.
 */
public class FeatureWeight {

    private static String NAME = "ltr-parser-linear-weight";
    private static final ObjectParser<FeatureWeight, FeatureWeight.Context> PARSER;


    static {
        PARSER = new ObjectParser<>(NAME, FeatureWeight::new);
        PARSER.declareString(FeatureWeight::feature, new ParseField("feature"));
        PARSER.declareDouble(FeatureWeight::weight, new ParseField("weight"));
    }

    public static class Context {

    }

    public void feature(String name) {_feature = name;}

    public String feature() {return _feature;}

    public void weight(double weight) {_weight = weight;}

    public double weight() {return _weight;}


    public static FeatureWeight parse(XContentParser xContent) throws IOException {
        return PARSER.parse(xContent, null);
    }

    private String _feature;
    private double _weight;

}
