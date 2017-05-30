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
