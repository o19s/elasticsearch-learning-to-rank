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
