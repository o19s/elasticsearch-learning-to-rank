/*
 * Copyright [2017] Dan Worley
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
 *
 */
package com.o19s.es.explore;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.SearchExtBuilder;

import java.io.IOException;
import java.util.Objects;

/**
 * Created by Daniel on 6/15/2017.
 */
public class ExplorerExtBuilder extends SearchExtBuilder {
    public static final String NAME = "match_explorer";

    // The parse fields available for this object
    private static final ParseField ENABLED_FIELD = new ParseField("enabled");
    private static final ParseField FIELD_FIELD = new ParseField("field");
    private static final ParseField STATS_FIELD = new ParseField("stats");

    // Variables to track state inside this extension
    private boolean b_enabled;
    private String field;
    private String stats;

    public ExplorerExtBuilder() {
        this.b_enabled = false;
        this.field = "";
        this.stats = "";
    }

    public ExplorerExtBuilder(StreamInput in) throws IOException {
        this.b_enabled = in.readBoolean();
        this.field = in.readString();
        this.stats = in.readString();
    }

    public boolean isEnabled() { return this.b_enabled; }
    public String getField() { return this.field; }
    public String getStats() { return this.stats; }

    public static ExplorerExtBuilder fromXContent(XContentParser parser) throws IOException {
        boolean enabled = false;
        String field = "";
        String stats = "";

        String currentFieldName = null;
        XContentParser.Token token;

        while((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            // No arrays yet, but maybe in the future?
            } else if(token == XContentParser.Token.START_ARRAY) {

            } else if(token.isValue()) {
                if(ENABLED_FIELD.match(currentFieldName)) {
                    enabled = parser.booleanValue();
                } else if(FIELD_FIELD.match(currentFieldName)) {
                    field = parser.text();
                } else if(STATS_FIELD.match(currentFieldName)) {
                    stats = parser.text();
                }
            }
        }

        ExplorerExtBuilder builder = new ExplorerExtBuilder();
        builder.b_enabled = enabled;
        builder.field = field;
        builder.stats = stats;

        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(this.b_enabled);
        out.writeString(this.field);
        out.writeString(this.stats);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ExplorerExtBuilder that = (ExplorerExtBuilder) o;
        return (b_enabled == that.b_enabled)
                && Objects.equals(field, that.field)
                && Objects.equals(stats, that.stats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(b_enabled, field, stats);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);

        builder.field(ENABLED_FIELD.getPreferredName(), b_enabled);
        builder.field(FIELD_FIELD.getPreferredName(), field);
        builder.field(STATS_FIELD.getPreferredName(), stats);

        builder.endObject();

        return builder;
    }
}
