/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.ranker.parser;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.json.JsonXContent;

import java.io.IOException;

import static org.elasticsearch.xcontent.NamedXContentRegistry.EMPTY;

public class LinearRankerParser implements LtrRankerParser {
    public static final String TYPE = "model/linear";

    @Override
    public LinearRanker parse(FeatureSet set, String model) {
        try (XContentParser parser = JsonXContent.jsonXContent.createParser(EMPTY,
                LoggingDeprecationHandler.INSTANCE, model)
        ) {
            return parse(parser, set);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private LinearRanker parse(XContentParser parser, FeatureSet set) throws IOException {
        float[] weights = new float[set.size()];
        if (parser.nextToken() != XContentParser.Token.START_OBJECT) {
            throw new ParsingException(parser.getTokenLocation(), "Expected start object but found [" + parser.currentToken() +"]");
        }
        while (parser.nextToken() == XContentParser.Token.FIELD_NAME) {
            String fname = parser.currentName();
            if (!set.hasFeature(fname)) {
                throw new ParsingException(parser.getTokenLocation(), "Feature [" + fname + "] is unknown.");
            }
            if (parser.nextToken() != XContentParser.Token.VALUE_NUMBER) {
                throw new ParsingException(parser.getTokenLocation(), "Expected a float but found [" + parser.currentToken() +"]");
            }
            weights[set.featureOrdinal(fname)] = parser.floatValue();
        }
        assert parser.currentToken() == XContentParser.Token.END_OBJECT;
        return new LinearRanker(weights);
    }
}
