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

package com.o19s.es.ltr.logging;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.SearchExtBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class LoggingSearchExtBuilder extends SearchExtBuilder {
    public static final String NAME = "ltr_log";

    private static final ObjectParser<LoggingSearchExtBuilder, Void> PARSER;
    private static final ParseField LOG_SPECS = new ParseField("log_specs");

    static {
        PARSER = new ObjectParser<>(NAME, LoggingSearchExtBuilder::new);
        PARSER.declareObjectArray(LoggingSearchExtBuilder::setLogSpecs, LogSpec::parse, LOG_SPECS);
    }
    private List<LogSpec> logSpecs;

    public LoggingSearchExtBuilder() {}

    public LoggingSearchExtBuilder(StreamInput input) throws IOException {
        logSpecs = input.readList(LogSpec::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeList(logSpecs);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public static LoggingSearchExtBuilder parse(XContentParser parser) throws IOException {
        try {
            LoggingSearchExtBuilder ext = PARSER.parse(parser, null);
            if (ext.logSpecs == null || ext.logSpecs.isEmpty()) {
                throw new ParsingException(parser.getTokenLocation(), "[" + NAME + "] should define at least one [" +
                    LOG_SPECS + "]");
            }
            return ext;
        } catch(IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(LOG_SPECS.getPreferredName(), logSpecs);
        return builder.endObject();
    }

    public Stream<LogSpec> logSpecsStream() {
        return logSpecs.stream();
    }

    private void setLogSpecs(List<LogSpec> logSpecs) {
        this.logSpecs = logSpecs;
    }

    public LoggingSearchExtBuilder addQueryLogging(String name, String namedQuery, boolean missingAsZero) {
        addLogSpec(new LogSpec(name, Objects.requireNonNull(namedQuery), missingAsZero));
        return this;
    }

    public LoggingSearchExtBuilder addRescoreLogging(String name, int rescoreIndex, boolean missingAsZero) {
        addLogSpec(new LogSpec(name, rescoreIndex, missingAsZero));
        return this;
    }

    private void addLogSpec(LogSpec spec) {
        if (logSpecs == null) {
            logSpecs = new ArrayList<>();
        }
        logSpecs.add(spec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getClass(), logSpecs);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof LoggingSearchExtBuilder)) {
            return false;
        }
        LoggingSearchExtBuilder o = (LoggingSearchExtBuilder) obj;
        return Objects.equals(logSpecs, o.logSpecs);
    }

    public static class LogSpec implements Writeable, ToXContentObject {
        private static final ParseField LOGGER_NAME = new ParseField("name");
        private static final ParseField NAMED_QUERY = new ParseField("named_query");
        private static final ParseField RESCORE_INDEX = new ParseField("rescore_index");
        private static final ParseField MISSING_AS_ZERO = new ParseField("missing_as_zero");

        private static final ObjectParser<LogSpec, Void> PARSER;

        static {
            PARSER = new ObjectParser<>("spec", LogSpec::new);
            PARSER.declareString(LogSpec::setLoggerName, LOGGER_NAME);
            PARSER.declareString(LogSpec::setNamedQuery, NAMED_QUERY);
            PARSER.declareInt(LogSpec::setRescoreIndex, RESCORE_INDEX);
            PARSER.declareBoolean(LogSpec::setMissingAsZero, MISSING_AS_ZERO);
        }
        private String loggerName;
        private String namedQuery;
        private Integer rescoreIndex;
        private boolean missingAsZero;

        private LogSpec() {}

        LogSpec(@Nullable String loggerName, String namedQuery, boolean missingAsZero) {
            this.loggerName = loggerName;
            this.namedQuery = Objects.requireNonNull(namedQuery);
            this.missingAsZero = missingAsZero;
        }

        LogSpec(@Nullable String loggerName, int rescoreIndex, boolean missingAsZero) {
            this.loggerName = loggerName;
            this.rescoreIndex = rescoreIndex;
            this.missingAsZero = missingAsZero;
        }

        private LogSpec(StreamInput input) throws IOException {
            loggerName = input.readOptionalString();
            namedQuery = input.readOptionalString();
            rescoreIndex = input.readOptionalVInt();
            missingAsZero = input.readBoolean();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeOptionalString(loggerName);
            out.writeOptionalString(namedQuery);
            out.writeOptionalVInt(rescoreIndex);
            out.writeBoolean(missingAsZero);
        }

        private static LogSpec parse(XContentParser parser, Void context) throws IOException {
            try {
                LogSpec spec = PARSER.parse(parser, null);
                if (spec.namedQuery == null && spec.rescoreIndex == null) {
                    throw new ParsingException(parser.getTokenLocation(), "Either " +
                            "[" + NAMED_QUERY + "] or [" + RESCORE_INDEX + "] must be set.");
                }
                if (spec.rescoreIndex != null && spec.rescoreIndex < 0) {
                    throw new ParsingException(parser.getTokenLocation(), "[" + RESCORE_INDEX + "] must be a non-negative integer.");
                }
                return spec;
            } catch (IllegalArgumentException iae) {
                throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
            }
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            if (loggerName != null) {
                builder.field(LOGGER_NAME.getPreferredName(), loggerName);
            }
            if (namedQuery != null) {
                builder.field(NAMED_QUERY.getPreferredName(), namedQuery);
            } else if (rescoreIndex != null) {
                builder.field(RESCORE_INDEX.getPreferredName(), rescoreIndex);
            }
            if (missingAsZero) {
                builder.field(MISSING_AS_ZERO.getPreferredName(), missingAsZero);
            }
            return builder.endObject();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LogSpec logSpec = (LogSpec) o;

            if (missingAsZero != logSpec.missingAsZero) return false;
            if (loggerName != null ? !loggerName.equals(logSpec.loggerName) : logSpec.loggerName != null) return false;
            if (namedQuery != null ? !namedQuery.equals(logSpec.namedQuery) : logSpec.namedQuery != null) return false;
            return rescoreIndex != null ? rescoreIndex.equals(logSpec.rescoreIndex) : logSpec.rescoreIndex == null;
        }

        @Override
        public int hashCode() {
            int result = loggerName != null ? loggerName.hashCode() : 0;
            result = 31 * result + (namedQuery != null ? namedQuery.hashCode() : 0);
            result = 31 * result + (rescoreIndex != null ? rescoreIndex.hashCode() : 0);
            result = 31 * result + (missingAsZero ? 1 : 0);
            return result;
        }

        public String getNamedQuery() {
            return namedQuery;
        }

        private void setNamedQuery(String namedQuery) {
            this.namedQuery = namedQuery;
        }

        public Integer getRescoreIndex() {
            return rescoreIndex;
        }

        private void setRescoreIndex(Integer rescoreIndex) {
            this.rescoreIndex = rescoreIndex;
        }

        public String getLoggerName() {
            if (loggerName != null) {
                return loggerName;
            }
            return namedQuery != null ? namedQuery : "rescore[" + rescoreIndex + "]";
        }

        private void setLoggerName(String loggerName) {
            this.loggerName = loggerName;
        }

        public boolean isMissingAsZero() {
            return missingAsZero;
        }

        private void setMissingAsZero(boolean missingAsZero) {
            this.missingAsZero = missingAsZero;
        }
    }
}
