package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper.AggrType;

import com.o19s.es.ltr.utils.Scripting;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.*;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

public class TermStatQueryBuilder extends AbstractQueryBuilder<TermStatQueryBuilder> implements NamedWriteable {
    public static final String NAME = "term_stat";

    private static final ParseField EXPR_NAME = new ParseField("expr");
    private static final ParseField AGGR_NAME = new ParseField("aggr");
    private static final ParseField POS_AGGR_NAME = new ParseField("pos_aggr");
    private static final ParseField QUERY_NAME = new ParseField("query");

    private static final ObjectParser<TermStatQueryBuilder, Void> PARSER;

    static {
        PARSER = new ObjectParser<>(NAME, TermStatQueryBuilder::new);
        PARSER.declareObject(
                TermStatQueryBuilder::query,
                (parser, context) -> parseInnerQueryBuilder(parser),
                QUERY_NAME
        );
        PARSER.declareString(TermStatQueryBuilder::expr, EXPR_NAME);
        PARSER.declareString(TermStatQueryBuilder::aggr, AGGR_NAME);
        PARSER.declareString(TermStatQueryBuilder::posAggr, POS_AGGR_NAME);
        declareStandardFields(PARSER);
    }

    private String expr;
    private String aggr;
    private String pos_aggr;
    private QueryBuilder query;

    public TermStatQueryBuilder() {
    }

    public TermStatQueryBuilder(StreamInput in) throws IOException {
        super(in);
        expr = in.readString();
        aggr = in.readString();
        pos_aggr = in.readString();
        query = in.readNamedWriteable(QueryBuilder.class);
    }

    public static TermStatQueryBuilder fromXContent(XContentParser parser) throws IOException {
        final TermStatQueryBuilder builder;

        try {
            builder = PARSER.parse(parser, null);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }

        if (builder.expr == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + EXPR_NAME + "] is mandatary");
        }

        if (builder.query == null) {
            throw new ParsingException(parser.getTokenLocation(), "Query is manadatory");
        }

        // Default aggr to mean if none specified
        if (builder.aggr == null) {
            builder.aggr(AggrType.AVG.getType());
        }

        // Default pos_aggr to mean if none specified
        if (builder.pos_aggr == null) {
            builder.posAggr(AggrType.AVG.getType());
        }

        return builder;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(expr);
        out.writeString(aggr);
        out.writeString(pos_aggr);
        out.writeNamedWriteable(query);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        printBoostAndQueryName(builder);
        builder.field(EXPR_NAME.getPreferredName(), expr);
        builder.field(AGGR_NAME.getPreferredName(), aggr);
        builder.field(POS_AGGR_NAME.getPreferredName(), pos_aggr);

        builder.field(QUERY_NAME.getPreferredName(), query);

        builder.endObject();
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        Expression compiledExpression = (Expression) Scripting.compile(expr);
        AggrType aggrType = AggrType.valueOf(aggr.toUpperCase(Locale.getDefault()));
        AggrType posAggrType = AggrType.valueOf(pos_aggr.toUpperCase(Locale.getDefault()));
        return new TermStatQuery(compiledExpression, aggrType, posAggrType, query.toQuery(context));
    }

    @Override
    protected QueryBuilder doRewrite(QueryRewriteContext queryRewriteContext) throws IOException {
        if (queryRewriteContext != null) {

            TermStatQueryBuilder rewritten = new TermStatQueryBuilder();
            rewritten.aggr(aggr);
            rewritten.expr(expr);
            rewritten.posAggr(pos_aggr);
            rewritten.query = Rewriteable.rewrite(query, queryRewriteContext);
            rewritten.boost(boost());
            rewritten.queryName(queryName());

            if (!rewritten.equals(this)) {
                return rewritten;
            }
        }
        return super.doRewrite(queryRewriteContext);
    }

    @Override
    protected int doHashCode() { return Objects.hash(expr, aggr, pos_aggr, query);}

    @Override
    protected boolean doEquals(TermStatQueryBuilder other) {
        return Objects.equals(expr, other.expr)
                && Objects.equals(aggr, other.aggr)
                && Objects.equals(pos_aggr, other.pos_aggr)
                && Objects.equals(query, other.query);
    }

    @Override
    public String getWriteableName() { return NAME; }


    public String expr() {
        return expr;
    }

    public TermStatQueryBuilder expr(String expr) {
        this.expr = expr;
        return this;
    }

    public String aggr() {
        return aggr;
    }

    public TermStatQueryBuilder aggr(String aggr) {
        this.aggr = aggr;
        return this;
    }

    public String posAggr() {
        return pos_aggr;
    }

    public TermStatQueryBuilder posAggr(String pos_aggr) {
        this.pos_aggr = pos_aggr;
        return this;
    }

    public QueryBuilder query() { return query; }
    public TermStatQueryBuilder query(QueryBuilder query) {
        this.query = query;
        return this;
    }
}
