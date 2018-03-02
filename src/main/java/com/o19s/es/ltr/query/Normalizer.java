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

package com.o19s.es.ltr.query;

import org.apache.lucene.search.Explanation;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public interface Normalizer extends NamedWriteable, ToXContent {
    NoopNormalizer NOOP = new NoopNormalizer();

    double normalize(double score);
    Explanation explain(double sourceScore, Explanation sourceExplanation);

    @Override
    default XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject().field(getWriteableName());
        return doToXContent(builder, params).endObject();
    }

    XContentBuilder doToXContent(XContentBuilder builder, Params params) throws IOException;
    static Normalizer parseBaseNormalizer(XContentParser parser, Void context) throws IOException {
        return parseNormalizer(parser, Normalizer.class, context);
    }

    static UnitIntervalNormalizer parseUnitIntervalNormalizer(XContentParser parser, Void context) throws IOException {
        return parseNormalizer(parser, UnitIntervalNormalizer.class, context);
    }

    static <E extends Normalizer> E parseNormalizer(XContentParser parser, Class<E> clazz, Void context) throws IOException {
        if (parser.currentToken() != XContentParser.Token.START_OBJECT) {
            if (parser.nextToken() != XContentParser.Token.START_OBJECT) {
                throw new ParsingException(parser.getTokenLocation(), "[_na] normalizer malformed, must start with start_object");
            }
        }
        if (parser.nextToken() == XContentParser.Token.END_OBJECT) {
            // we encountered '{}' for a query clause, it used to be supported, deprecated in 5.0 and removed in 6.0
            throw new IllegalArgumentException("normalizer malformed, empty clause found at [" + parser.getTokenLocation() +"]");
        }
        if (parser.currentToken() != XContentParser.Token.FIELD_NAME) {
            throw new ParsingException(parser.getTokenLocation(), "[_na] normalizer malformed, no field after start_object");
        }
        String name = parser.currentName();
        E normalizer = parser.namedObject(clazz, name, null);
        if (parser.currentToken() != XContentParser.Token.END_OBJECT) {
            throw new ParsingException(parser.getTokenLocation(),
                    "[" + name + "] malformed normalizer, expected [END_OBJECT] but found [" + parser.currentToken() + "]");
        }
        if (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            throw new ParsingException(parser.getTokenLocation(),
                    "[" + name + "] malformed normalizer, expected [END_OBJECT] but found [" + parser.currentToken() + "]");
        }
        return normalizer;
    }

    static List<NamedXContentRegistry.Entry> getNamedXContent() {
        return Arrays.asList(
            new NamedXContentRegistry.Entry(Normalizer.class, NoopNormalizer.NAME, NoopNormalizer::parse),
            new NamedXContentRegistry.Entry(Normalizer.class, IntervalNormalizer.NAME, IntervalNormalizer::parse),
            new NamedXContentRegistry.Entry(Normalizer.class, MinMax.NAME, MinMax::parse),
            new NamedXContentRegistry.Entry(Normalizer.class, Saturation.NAME, Saturation::parse),
            new NamedXContentRegistry.Entry(Normalizer.class, Logistic.NAME, Logistic::parse),
            new NamedXContentRegistry.Entry(UnitIntervalNormalizer.class, MinMax.NAME, MinMax::parse),
            new NamedXContentRegistry.Entry(UnitIntervalNormalizer.class, Saturation.NAME, Saturation::parse),
            new NamedXContentRegistry.Entry(UnitIntervalNormalizer.class, Logistic.NAME, Logistic::parse)
        );
    }

    static List<NamedWriteableRegistry.Entry> getNamedWriteables() {
        return Arrays.asList(
                new NamedWriteableRegistry.Entry(Normalizer.class, NoopNormalizer.NAME.getPreferredName(), (s) -> NOOP),
                new NamedWriteableRegistry.Entry(Normalizer.class, IntervalNormalizer.NAME.getPreferredName(), IntervalNormalizer::new),
                new NamedWriteableRegistry.Entry(Normalizer.class, MinMax.NAME.getPreferredName(), MinMax::new),
                new NamedWriteableRegistry.Entry(Normalizer.class, Saturation.NAME.getPreferredName(), Saturation::new),
                new NamedWriteableRegistry.Entry(Normalizer.class, Logistic.NAME.getPreferredName(), Logistic::new),
                new NamedWriteableRegistry.Entry(UnitIntervalNormalizer.class, MinMax.NAME.getPreferredName(), MinMax::new),
                new NamedWriteableRegistry.Entry(UnitIntervalNormalizer.class, Saturation.NAME.getPreferredName(), Saturation::new),
                new NamedWriteableRegistry.Entry(UnitIntervalNormalizer.class, Logistic.NAME.getPreferredName(), Logistic::new)
        );
    }

    /**
     * Marker class to identify normalizers that outputs
     * their scores in the unit interval [0,1]
     */
    interface UnitIntervalNormalizer extends Normalizer {}

    class IntervalNormalizer implements Normalizer {
        private static final ParseField NAME = new ParseField("interval");
        private static final ParseField FROM = new ParseField("from");
        private static final ParseField TO = new ParseField("to");
        private static final ParseField INCLUSIVE = new ParseField("inclusive");
        private static final ParseField NORMALIZER = new ParseField("normalizer");

        static final ConstructingObjectParser<IntervalNormalizer, Void> PARSER = new ConstructingObjectParser<>(NAME.getPreferredName(),
                (o) -> new IntervalNormalizer((double) o[0], (double) o[1],
                        o[3] != null ? (boolean) o[3] : false, (UnitIntervalNormalizer) o[2]));

        static {
            PARSER.declareDouble(ConstructingObjectParser.constructorArg(), FROM);
            PARSER.declareDouble(ConstructingObjectParser.constructorArg(), TO);
            PARSER.declareField(ConstructingObjectParser.constructorArg(),
                    Normalizer::parseUnitIntervalNormalizer, NORMALIZER, ObjectParser.ValueType.OBJECT);
            PARSER.declareBoolean(ConstructingObjectParser.optionalConstructorArg(), INCLUSIVE);
        }
        private final double from;
        private final double to;
        private final boolean inclusive;
        private final double intervalSize;
        private final UnitIntervalNormalizer normalizer;

        public IntervalNormalizer(double from, double to, boolean inclusive, UnitIntervalNormalizer normalizer) {
            this.from = from;
            this.to = to;
            this.inclusive = inclusive;
            if (inclusive) {
                this.intervalSize = to - from;
            } else {
                this.intervalSize = to - from - Math.ulp(Math.abs(to)+Math.abs(from));
            }
            if (this.intervalSize < 0) {
                throw new IllegalArgumentException( "Invalid interval ["+from+","+to+"] given");
            }

            this.normalizer = Objects.requireNonNull(normalizer);
        }

        IntervalNormalizer(StreamInput in) throws IOException {
            this(in.readDouble(), in.readDouble(), in.readBoolean(), in.readNamedWriteable(UnitIntervalNormalizer.class));
        }

        public double normalize(double score) {
            return ((this.intervalSize * this.normalizer.normalize(score)) + from);
        }

        static Normalizer parse(XContentParser parser) throws IOException {
            return PARSER.parse(parser, null);
        }

        @Override
        public Explanation explain(double sourceScore, Explanation sourceExplanation) {
            return Explanation.match((float) normalize(sourceScore),
                    String.format(Locale.ROOT, "interval: [%f, %f" + (this.inclusive ? "]" : ")"), from, to),
                    this.normalizer.explain(sourceScore, sourceExplanation));
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeDouble(this.from);
            out.writeDouble(this.to);
            out.writeBoolean(this.inclusive);
            out.writeNamedWriteable(this.normalizer);
        }

        /**
         * Returns the name of the writeable object
         */
        @Override
        public String getWriteableName() {
            return NAME.getPreferredName();
        }

        @Override
        public XContentBuilder doToXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject()
                    .field(FROM.getPreferredName(), from)
                    .field(TO.getPreferredName(), to)
                    .field(NORMALIZER.getPreferredName(), normalizer);
            if (inclusive) {
                builder.field(INCLUSIVE.getPreferredName(), inclusive);
            }
            return builder.endObject();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IntervalNormalizer that = (IntervalNormalizer) o;
            return Double.compare(that.from, from) == 0 &&
                    Double.compare(that.to, to) == 0 &&
                    inclusive == that.inclusive &&
                    normalizer.equals(that.normalizer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to, inclusive, normalizer);
        }
    }

    class MinMax implements UnitIntervalNormalizer {
        private final double min, max;
        private static final ParseField NAME = new ParseField("minmax");
        private static final ParseField MIN = new ParseField("min");
        private static final ParseField MAX = new ParseField("max");

        static final ConstructingObjectParser<MinMax,Void> PARSER = new ConstructingObjectParser<>(NAME.getPreferredName(),
                (o) -> new MinMax((double) o[0], (double) o[1]));
        static {
            PARSER.declareDouble(ConstructingObjectParser.constructorArg(), MIN);
            PARSER.declareDouble(ConstructingObjectParser.constructorArg(), MAX);
        }

        public MinMax(double min, double max) {
            if (min >= max) {
                throw new IllegalArgumentException("min >= max");
            }
            this.min = min;
            this.max = max;
        }

        MinMax(StreamInput in) throws IOException {
            min = in.readDouble();
            max = in.readDouble();
        }

        @Override
        public double normalize(double score) {
            return ((Math.min(Math.max(score, min), max)- min)/(max - min));
        }

        public Explanation explain(double sourceScore, Explanation sourceExplanation) {
            return Explanation.match((float) normalize(sourceScore),
                    String.format(Locale.ROOT, "minmax: [%f, %f]", min, max),
                    sourceExplanation);
        }

        static MinMax parse(XContentParser parser) throws IOException {
            return PARSER.parse(parser, null);
        }

        /**
         * Returns the name of the writeable object
         */
        @Override
        public String getWriteableName() {
            return NAME.getPreferredName();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeDouble(min);
            out.writeDouble(max);
        }

        @Override
        public XContentBuilder doToXContent(XContentBuilder builder, Params params) throws IOException {
            return builder.startObject()
                    .field(MIN.getPreferredName(), min)
                    .field(MAX.getPreferredName(), max)
                    .endObject();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MinMax minMax = (MinMax) o;
            return Double.compare(minMax.min, min) == 0 &&
                    Double.compare(minMax.max, max) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(min, max);
        }
    }

    class Saturation implements UnitIntervalNormalizer {
        private final double k, ka, a;
        private static final ParseField NAME = new ParseField("saturation");
        private static final ParseField K = new ParseField("k");
        private static final ParseField A = new ParseField("a");

        static final ConstructingObjectParser<Saturation,Void> PARSER = new ConstructingObjectParser<>(NAME.getPreferredName(),
                (o) -> new Saturation((double) o[0], (double) o[1]));
        static {
            PARSER.declareDouble(ConstructingObjectParser.constructorArg(), K);
            PARSER.declareDouble(ConstructingObjectParser.constructorArg(), A);
        }

        public Saturation(double k, double a) {
            if (k <= 0) {
                throw new IllegalArgumentException("k must be strictly positive");
            }
            if (a <= 0) {
                throw new IllegalArgumentException("a must be strictly positive");
            }
            this.k = k;
            this.ka = Math.pow(k, a);
            this.a = a;
        }

        Saturation(StreamInput in) throws IOException {
            this(in.readDouble(), in.readDouble());
        }

        @Override
        public double normalize(double score) {
            double sa = Math.pow(Math.max(score, 0), a);
            return (sa / ( sa + ka ));
        }

        public Explanation explain(double sourceScore, Explanation sourceExplanation) {
            return Explanation.match((float) normalize(sourceScore),
                    String.format(Locale.ROOT, "saturation(k = %f, a = %f)", k, a),
                    sourceExplanation);
        }

        static Saturation parse(XContentParser parser) throws IOException {
            return PARSER.parse(parser, null);
        }

        /**
         * Returns the name of the writeable object
         */
        @Override
        public String getWriteableName() {
            return NAME.getPreferredName();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeDouble(k);
            out.writeDouble(a);
        }

        @Override
        public XContentBuilder doToXContent(XContentBuilder builder, Params params) throws IOException {
            return builder.startObject()
                    .field(K.getPreferredName(), k)
                    .field(A.getPreferredName(), a)
                    .endObject();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Saturation that = (Saturation) o;
            return Double.compare(that.k, k) == 0 &&
                    Double.compare(that.a, a) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(k, a);
        }
    }

    class Logistic implements UnitIntervalNormalizer {
        private final double k, x0;
        private static final ParseField NAME = new ParseField("logistic");
        private static final ParseField K = new ParseField("k");
        private static final ParseField X0 = new ParseField("x0");

        static final ConstructingObjectParser<Logistic,Void> PARSER = new ConstructingObjectParser<>(NAME.getPreferredName(),
                (o) -> new Logistic((double) o[0], (double) o[1]));

        static {
            PARSER.declareDouble(ConstructingObjectParser.constructorArg(), K);
            PARSER.declareDouble(ConstructingObjectParser.constructorArg(), X0);
        }

        public Logistic(double k, double x0) {
            this.k = k;
            this.x0 = x0;
        }

        Logistic(StreamInput in) throws IOException {
            this.k = in.readDouble();
            this.x0 = in.readDouble();
        }

        @Override
        public double normalize(double score) {
            return (1D / (1D + Math.exp(-k * ((score - x0)))));
        }

        public Explanation explain(double sourceScore, Explanation sourceExplanation) {
            return Explanation.match((float) normalize(sourceScore),
                    String.format(Locale.ROOT, "logistic(k = %f, x0 = %f)", k, x0),
                    sourceExplanation);
        }

        static Logistic parse(XContentParser parser) throws IOException {
            return PARSER.parse(parser, null);
        }

        /**
         * Returns the name of the writeable object
         */
        @Override
        public String getWriteableName() {
            return NAME.getPreferredName();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeDouble(k);
            out.writeDouble(x0);
        }

        @Override
        public XContentBuilder doToXContent(XContentBuilder builder, Params params) throws IOException {
            return builder.startObject()
                    .field(K.getPreferredName(), k)
                    .field(X0.getPreferredName(), x0)
                    .endObject();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Logistic logistic = (Logistic) o;
            return Double.compare(logistic.k, k) == 0 &&
                    Double.compare(logistic.x0, x0) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(k, x0);
        }
    }

    class NoopNormalizer implements Normalizer {
        static final ParseField NAME = new ParseField("noop");

        @Override
        public double normalize(double score) {
            return score;
        }

        public Explanation explain(double sourceScore, Explanation sourceExplanation) {
            return Explanation.match((float) normalize(sourceScore),"noop normalizer",
                    sourceExplanation);
        }

        /**
         * Returns the name of the writeable object
         */
        @Override
        public String getWriteableName() {
            return NAME.getPreferredName();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
        }

        static NoopNormalizer parse(XContentParser parser) throws IOException {
            if (parser.nextToken() != XContentParser.Token.START_OBJECT) {
                throw new ParsingException(parser.getTokenLocation(), "Expected START_OBJECT");
            }
            if (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                throw new ParsingException(parser.getTokenLocation(), "Expected END_OBJECT");
            }
            return NOOP;
        }

        @Override
        public XContentBuilder doToXContent(XContentBuilder builder, Params params) throws IOException {
            return builder.startObject().endObject();
        }
    }
}
