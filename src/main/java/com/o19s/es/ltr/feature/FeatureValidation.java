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

package com.o19s.es.ltr.feature;

import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ObjectParser;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Simple object to store the parameters needed to validate stored elements:
 * - The list of template params to replace
 * - The index to run the query
 */
public class FeatureValidation implements Writeable, ToXContentObject {
    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<FeatureValidation, Void> PARSER = new ConstructingObjectParser<>("feature_validation",
            (Object[] args) -> new FeatureValidation((String) args[0], (Map<String, Object>) args[1]));

    public static final ParseField INDEX = new ParseField("index");

    public static final ParseField PARAMS = new ParseField("params");

    static {
        PARSER.declareString(ConstructingObjectParser.constructorArg(), INDEX);
        PARSER.declareField(ConstructingObjectParser.constructorArg(), XContentParser::map,
                PARAMS, ObjectParser.ValueType.OBJECT);
    }

    private final String index;
    private final Map<String, Object> params;

    public FeatureValidation(String index, Map<String, Object> params) {
        this.index = Objects.requireNonNull(index);
        this.params = Objects.requireNonNull(params);
    }

    public FeatureValidation(StreamInput input) throws IOException {
        this.index = input.readString();
        this.params = input.readMap();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(index);
        out.writeMap(params);
    }

    public String getIndex() {
        return index;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeatureValidation that = (FeatureValidation) o;
        return Objects.equals(index, that.index) &&
                Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, params);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.startObject()
                .field(INDEX.getPreferredName(), index)
                .field(PARAMS.getPreferredName(), this.params)
                .endObject();
    }
}
