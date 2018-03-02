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

package com.o19s.es.ltr.rescore;

import com.o19s.es.ltr.query.LtrQueryBuilder;
import com.o19s.es.ltr.query.Normalizer;
import com.o19s.es.ltr.query.RankerQuery;
import com.o19s.es.ltr.query.StoredLtrQueryBuilder;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.search.rescore.RescoreContext;
import org.elasticsearch.search.rescore.RescorerBuilder;

import java.io.IOException;
import java.util.Objects;

public class LtrRescoreBuilder extends RescorerBuilder<LtrRescoreBuilder> {
    public static final ParseField NAME = new ParseField("ltr_rescore");
    static ObjectParser<LtrRescoreBuilder, Void> PARSER = new ObjectParser<>(NAME.getPreferredName(), LtrRescoreBuilder::new);
    private static final ParseField QUERY_NORMALIZER = new ParseField("query_normalizer");
    private static final ParseField RESCORE_QUERY_NORMALIZER = new ParseField("rescore_query_normalizer");
    private static final ParseField QUERY_WEIGHT = new ParseField("query_weight");
    private static final ParseField RESCORE_QUERY_WEIGHT = new ParseField("rescore_query_weight");
    private static final ParseField RESCORE_QUERY = new ParseField("ltr_query");
    private static final ParseField SCORE_MODE = new ParseField("score_mode");
    private static final ParseField SCORING_BATCH_SIZE = new ParseField("scoring_batch_size");
    private static final LtrRescorer RESCORER = new LtrRescorer();

    private Normalizer queryNormalizer = Normalizer.NOOP;
    private Normalizer rescoreQueryNormalizer = Normalizer.NOOP;
    private double queryWeight = 1F;
    private double rescoreQueryWeight = 1F;
    private LtrRescorer.LtrRescoreMode scoreMode = LtrRescorer.LtrRescoreMode.Total;
    private QueryBuilder query;
    private int scoringBatchSize = -1;

    static {
        PARSER.declareObject(LtrRescoreBuilder::setQueryNormalizer, Normalizer::parseBaseNormalizer, QUERY_NORMALIZER);
        PARSER.declareObject(LtrRescoreBuilder::setRescoreQueryNormalizer, Normalizer::parseBaseNormalizer, RESCORE_QUERY_NORMALIZER);
        PARSER.declareDouble(LtrRescoreBuilder::setQueryWeight, QUERY_WEIGHT);
        PARSER.declareDouble(LtrRescoreBuilder::setRescoreQueryWeight, RESCORE_QUERY_WEIGHT);
        PARSER.declareObject(LtrRescoreBuilder::setQuery, (p,c) -> AbstractQueryBuilder.parseInnerQueryBuilder(p), RESCORE_QUERY);
        PARSER.declareString((ltr, scoremode) -> ltr.scoreMode = LtrRescorer.LtrRescoreMode.fromString(scoremode), SCORE_MODE);
        PARSER.declareInt(LtrRescoreBuilder::setScoringBatchSize, SCORING_BATCH_SIZE);
    }

    public LtrRescoreBuilder() {
    }

    public LtrRescoreBuilder(StreamInput in) throws IOException {
        super(in);
        queryNormalizer = in.readNamedWriteable(Normalizer.class);
        rescoreQueryNormalizer = in.readNamedWriteable(Normalizer.class);
        queryWeight = in.readDouble();
        rescoreQueryWeight = in.readDouble();
        scoreMode = LtrRescorer.LtrRescoreMode.readFromStream(in);
        query = in.readNamedWriteable(QueryBuilder.class);
        assert query instanceof LtrQueryBuilder || query instanceof StoredLtrQueryBuilder;
        scoringBatchSize = in.readInt();
    }

    public static LtrRescoreBuilder parse(XContentParser parser) throws IOException {
        try {
            return PARSER.parse(parser, null);
        } catch(IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(queryNormalizer);
        out.writeNamedWriteable(rescoreQueryNormalizer);
        out.writeDouble(queryWeight);
        out.writeDouble(rescoreQueryWeight);
        scoreMode.writeTo(out);
        out.writeNamedWriteable(query);
        out.writeInt(scoringBatchSize);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME.getPreferredName());
        if (queryNormalizer != Normalizer.NOOP) {
            builder.field(QUERY_NORMALIZER.getPreferredName(), queryNormalizer);
        }
        if (rescoreQueryNormalizer != Normalizer.NOOP) {
            builder.field(RESCORE_QUERY_NORMALIZER.getPreferredName(), rescoreQueryNormalizer);
        }
        if (queryWeight != 1F) {
            builder.field(QUERY_WEIGHT.getPreferredName(), queryWeight);
        }
        if (rescoreQueryWeight != 1F) {
            builder.field(RESCORE_QUERY_WEIGHT.getPreferredName(), rescoreQueryWeight);
        }
        builder.field(RESCORE_QUERY.getPreferredName(), query);
        if (scoringBatchSize != -1) {
            builder.field(SCORING_BATCH_SIZE.getPreferredName(), scoringBatchSize);
        }
        if (scoreMode != LtrRescorer.LtrRescoreMode.Total) {
            builder.field(SCORE_MODE.getPreferredName(), scoreMode.toString());
        }
        builder.endObject();
    }

    @Override
    protected RescoreContext innerBuildContext(int windowSize, QueryShardContext context) throws IOException {
        LtrRescorer.LtrRescoreContext ctx = new LtrRescorer.LtrRescoreContext(windowSize, RESCORER);
        ctx.setBatchSize(this.scoringBatchSize);
        ctx.setQueryWeight(this.queryWeight);
        ctx.setRescoreQueryWeight(this.rescoreQueryWeight);
        ctx.setQueryNormalizer(this.queryNormalizer);
        ctx.setRescoreQueryNormalizer(this.rescoreQueryNormalizer);
        ctx.setQuery((RankerQuery) this.query.toQuery(context));
        ctx.setScoreMode(this.scoreMode);
        return ctx;
    }

    /**
     * Returns the name of the writeable object
     */
    @Override
    public String getWriteableName() {
        return null;
    }

    @Override
    public RescorerBuilder<LtrRescoreBuilder> rewrite(QueryRewriteContext ctx) throws IOException {
        QueryBuilder rewrite = query.rewrite(ctx);
        if (rewrite == query) {
            return this;
        }
        LtrRescoreBuilder ltrRescoreBuilder = new LtrRescoreBuilder();
        ltrRescoreBuilder.query = rewrite;
        ltrRescoreBuilder.queryNormalizer = queryNormalizer;
        ltrRescoreBuilder.rescoreQueryNormalizer = rescoreQueryNormalizer;
        ltrRescoreBuilder.queryWeight = queryWeight;
        ltrRescoreBuilder.rescoreQueryWeight = rescoreQueryWeight;
        ltrRescoreBuilder.scoringBatchSize = scoringBatchSize;
        ltrRescoreBuilder.scoreMode = scoreMode;
        ltrRescoreBuilder.windowSize = windowSize;
        return ltrRescoreBuilder;

    }

    public LtrRescoreBuilder setQuery(QueryBuilder query) {
        this.query = query;
        return this;
    }

    public Normalizer getQueryNormalizer() {
        return queryNormalizer;
    }

    public LtrRescoreBuilder setQueryNormalizer(Normalizer queryNormalizer) {
        this.queryNormalizer = Objects.requireNonNull(queryNormalizer);
        return this;
    }

    public Normalizer getRescoreQueryNormalizer() {
        return rescoreQueryNormalizer;
    }

    public LtrRescoreBuilder setRescoreQueryNormalizer(Normalizer rescoreQueryNormalizer) {
        this.rescoreQueryNormalizer = Objects.requireNonNull(rescoreQueryNormalizer);
        return this;
    }

    public double getQueryWeight() {
        return queryWeight;
    }

    public LtrRescoreBuilder setQueryWeight(double queryWeight) {
        this.queryWeight = queryWeight;
        return this;
    }

    public double getRescoreQueryWeight() {
        return rescoreQueryWeight;
    }

    public LtrRescoreBuilder setRescoreQueryWeight(double rescoreQueryWeight) {
        this.rescoreQueryWeight = rescoreQueryWeight;
        return this;
    }

    public LtrRescorer.LtrRescoreMode getScoreMode() {
        return scoreMode;
    }

    public LtrRescoreBuilder setScoreMode(LtrRescorer.LtrRescoreMode scoreMode) {
        this.scoreMode = Objects.requireNonNull(scoreMode);
        return this;
    }

    public QueryBuilder getQuery() {
        return query;
    }

    public int getScoringBatchSize() {
        return scoringBatchSize;
    }

    public LtrRescoreBuilder setScoringBatchSize(int scoringBatchSize) {
        this.scoringBatchSize = scoringBatchSize;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        LtrRescoreBuilder builder = (LtrRescoreBuilder) o;
        return Double.compare(builder.queryWeight, queryWeight) == 0 &&
                Double.compare(builder.rescoreQueryWeight, rescoreQueryWeight) == 0 &&
                scoringBatchSize == builder.scoringBatchSize &&
                Objects.equals(queryNormalizer, builder.queryNormalizer) &&
                Objects.equals(rescoreQueryNormalizer, builder.rescoreQueryNormalizer) &&
                scoreMode == builder.scoreMode &&
                Objects.equals(query, builder.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), queryNormalizer, rescoreQueryNormalizer, queryWeight,
                rescoreQueryWeight, scoreMode, query, scoringBatchSize);
    }
}
