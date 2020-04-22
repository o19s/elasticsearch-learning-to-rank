/*
    4/22/2020: DLW - Trying to take better notes of my progress.  This sets up a new query builder
    that utilizes a query and an expression string.

    I may need to update how the expression is parsed. Treating it as a string in this layer for now.

    TODO:
    - Replace query object with a list of terms, sticking with query now as I'm building off the
    explorer query code with plans of modifying later.
 */
package com.o19s.es.termstat;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.Objects;

public class TermStatQueryBuilder extends AbstractQueryBuilder<TermStatQueryBuilder> implements NamedWriteable {
    public static final String NAME = "term_stat";

    private static final ParseField EXPR_NAME = new ParseField("expr");
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
        declareStandardFields(PARSER);
    }

    private QueryBuilder query;
    private String expr; // TODO: This may need to change to an expression

    public TermStatQueryBuilder() {
    }

    public TermStatQueryBuilder(StreamInput in) throws IOException {
        super(in);
        query = in.readNamedWriteable(QueryBuilder.class);
        expr = in.readString();
    }

    public static TermStatQueryBuilder fromXContent(XContentParser parser) throws IOException {
        final TermStatQueryBuilder builder;

        try {
            builder = PARSER.parse(parser, null);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }

        if (builder.query == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + QUERY_NAME + "] is mandatary");
        }
        if (builder.expr == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + EXPR_NAME + "] is mandatary");
        }
        return builder;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(query);
        out.writeString(expr);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        printBoostAndQueryName(builder);
        builder.field(QUERY_NAME.getPreferredName(), query);
        builder.field(EXPR_NAME.getPreferredName(), expr);
        builder.endObject();
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        return new TermStatQuery(query.toQuery(context), expr);
    }

    @Override
    protected int doHashCode() { return Objects.hash(query, expr);}

    @Override
    protected boolean doEquals(TermStatQueryBuilder other) {
        return Objects.equals(query, other.query)
                && Objects.equals(expr, other.expr);
    }

    @Override
    public String getWriteableName() { return NAME; }

    public QueryBuilder query() { return query; }

    public TermStatQueryBuilder query(QueryBuilder query) {
        this.query = query;
        return this;
    }

    public String expr() {
        return expr;
    }

    public TermStatQueryBuilder expr(String expr) {
        this.expr = expr;
        return this;
    }



}
