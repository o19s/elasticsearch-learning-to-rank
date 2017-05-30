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
package com.o19s.es.ltr.ranker.parser.json;

import ciir.umass.edu.learning.tree.RFRanker;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import com.o19s.es.ltr.ranker.parser.json.linear.LinearCombination;
import com.o19s.es.ltr.ranker.parser.json.tree.ParsedEnsemble;
import com.o19s.es.ltr.ranker.parser.json.tree.ParsedForest;
import com.o19s.es.ltr.ranker.parser.json.tree.ParsedSplit;
import com.o19s.es.ltr.ranker.parser.json.tree.ParsedTree;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by doug on 5/28/17.
 */
public class Model {


    public static final String NAME = "ltr-model-parser";
    private static final ObjectParser<Model, Model.Context> PARSER;

    static {
        PARSER = new ObjectParser<>(NAME, Model::new);

        PARSER.declareObject(Model::model,
                (xContent, context) -> context.parseRandomForest(xContent),
                new ParseField("random-forest"));

        PARSER.declareObject(Model::model,
                (xContent, context) -> context.parseMart(xContent),
                new ParseField("mart"));

        PARSER.declareObject(Model::model,
                (xContent, context) -> context.parseLinear(xContent),
                new ParseField("linear"));
    }

    public static class Context {

        private FeatureRegister _reg;

        public Context(FeatureRegister reg) {
            _reg = reg;
        }

        LtrRanker parseRandomForest(XContentParser xContent) throws IOException {
            return null;
            //return ParsedForest.parse(xContent);
        }

        LtrRanker parseMart(XContentParser xContent) throws IOException {
            return ParsedEnsemble.parse(xContent).toRanker(_reg);
        }

        LtrRanker parseLinear(XContentParser xContent) throws IOException {
            return LinearCombination.parse(xContent).toRanker(_reg);
        }
    }

    public void model(LtrRanker ranker) {_ranker = ranker;}
    public LtrRanker model() {return _ranker;}

    private LtrRanker _ranker;



    public static Model parse(XContentParser xContent, FeatureSet set) throws IOException {
        return PARSER.parse(xContent, new Context(new FeatureRegister(set)));
    }



}
