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

package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.DerivedFeature;
import com.o19s.es.ltr.feature.PrebuiltDerivedFeature;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;

public class StoredDerivedFeature implements Accountable, StorableElement, DerivedFeature {
    private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(StoredDerivedFeature.class);
    public static final String TYPE = "derived_feature";

    private final String name;
    private final String expression;


    private static final ObjectParser<ParsingState, Void> PARSER;

    private static final ParseField NAME = new ParseField("name");
    private static final ParseField EXPR = new ParseField("expr");

    static {
        PARSER = new ObjectParser<>(TYPE, ParsingState::new);
        PARSER.declareString(ParsingState::setName, NAME);
        PARSER.declareString(ParsingState::setExpr, EXPR);
    }

    public StoredDerivedFeature(@Nullable String name, String expression) {
        this.name = name;
        this.expression = expression;
    }

    public StoredDerivedFeature(StreamInput in) throws IOException {
        this(in.readString(), in.readString());
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeString(expression);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME.getPreferredName(), name);
        builder.field(EXPR.getPreferredName(), expression);
        builder.endObject();
        return builder;
    }

    @Override
    public long ramBytesUsed() {
        // rough estimation...
        return BASE_RAM_USED +
                (Character.BYTES * name.length()) + NUM_BYTES_ARRAY_HEADER +
                (Character.BYTES * expression.length()) + NUM_BYTES_ARRAY_HEADER;
    }

    @Override @Nullable
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String expression() { return expression;}

    @Override
    public int hashCode() {
        return Objects.hash(name, expression);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PrebuiltDerivedFeature)) {
            return false;
        }

        StoredDerivedFeature other = (StoredDerivedFeature) o;
        return Objects.equals(name, other.name)
                && Objects.equals(expression, other.expression);
    }

    @Override
    public String toString() {
        return "[" + name + "]: " + expression;
    }

    public static StoredDerivedFeature parse(XContentParser parser) {
        try {
            ParsingState state = PARSER.apply(parser, null);
            if(state.expr == null) {
                throw new ParsingException(parser.getTokenLocation(), "Field [expr] is mandatory");
            }

            return new StoredDerivedFeature(state.name, state.expr);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }


    private static class ParsingState {
        private String name;
        private String expr;

        public void setName(String name) {
            this.name = name;
        }
        public void setExpr(String expr) { this.expr = expr; }
    }
}
