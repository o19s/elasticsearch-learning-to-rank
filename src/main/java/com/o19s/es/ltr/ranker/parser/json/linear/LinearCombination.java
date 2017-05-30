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

import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import com.o19s.es.ltr.ranker.parser.json.FeatureRegister;
import com.o19s.es.ltr.ranker.parser.json.Rankerable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;

/**
 * Created by doug on 5/29/17.
 */
public class LinearCombination implements Rankerable {
    private static String NAME = "ltr-parser-linear-model";
    private static final ObjectParser<LinearCombination, LinearCombination.Context> PARSER;


    static {
        PARSER = new ObjectParser<>(NAME, LinearCombination::new);

        PARSER.declareDouble(LinearCombination::yIntercept, new ParseField("y-intercept"));
        PARSER.declareObjectArray(LinearCombination::featureWeights,
                (xContent, context) -> context.parseWeightedFeature(xContent),
                new ParseField("weights"));
    }

    @Override
    public LtrRanker toRanker(FeatureRegister register) {

        float[] packedWeights = new float[register.numFeaturesAvail()];
        for (FeatureWeight  weight: _featureWeights) {
            int featureOrd = register.useFeature(weight.feature());
            packedWeights[featureOrd] = (float) weight.weight();
        }

        LinearRanker ranker = new LinearRanker(packedWeights);
        ranker.intercept((float) _yIntercept);
        return ranker;
    }

    public static class Context {

        public FeatureWeight parseWeightedFeature(XContentParser xContent) throws IOException {
            return FeatureWeight.parse(xContent);
        }

    }

    public static LinearCombination parse(XContentParser xContent) throws IOException {
        return PARSER.parse(xContent, new Context());
    }

    public double yIntercept() {return _yIntercept;}
    public void yIntercept(double yIntercept) {_yIntercept = yIntercept;}

    public List<FeatureWeight> featureWeights() {return _featureWeights;}
    public void featureWeights(List<FeatureWeight> weights) {_featureWeights = weights;}


    private double _yIntercept;
    List<FeatureWeight> _featureWeights;
}
