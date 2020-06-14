/*
    4/22/2020: DLW - Trying to take better notes of my progress.  This sets up a new query builder
    that utilizes a query and an expression string.

    I may need to update how the expression is parsed. Treating it as a string in this layer for now.

    TODO:
    - Replace query object with a list of terms, sticking with query now as I'm building off the
    explorer query code with plans of modifying later.
 */
package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper;
import org.apache.lucene.index.Term;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class TermStatQueryBuilder extends AbstractQueryBuilder<TermStatQueryBuilder> implements NamedWriteable {
    public static final String NAME = "term_stat";

    private static final ParseField EXPR_NAME = new ParseField("expr");
    private static final ParseField AGGR_NAME = new ParseField("aggr");
    private static final ParseField POS_AGGR_NAME = new ParseField("pos_aggr");
    private static final ParseField TERMS_NAME = new ParseField("terms");

    private String expr;
    private String aggr;
    private String pos_aggr;
    private Set<Term> terms;

    public TermStatQueryBuilder() {
    }

    public TermStatQueryBuilder(StreamInput in) throws IOException {
        super(in);
        expr = in.readString();
        aggr = in.readString();
        pos_aggr = in.readString();

        // Read in all terms
        terms = new HashSet<Term>();
        while (in.available() > 0) {
            String field = in.readString();
            String text = in.readString();
            terms.add(new Term(field, text));
        }
    }

    public static TermStatQueryBuilder fromXContent(XContentParser parser) throws IOException {
        String expr = null;
        String aggr = null;
        String pos_aggr = null;
        Set<Term> terms = null;

        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT) {
                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (EXPR_NAME.match(currentFieldName, parser.getDeprecationHandler())) {
                        expr = parser.text();
                    } else if(AGGR_NAME.match(currentFieldName, parser.getDeprecationHandler())) {
                        aggr = parser.text();
                    } else if(POS_AGGR_NAME.match(currentFieldName, parser.getDeprecationHandler())) {
                        pos_aggr = parser.text();
                    } else if (TERMS_NAME.match(currentFieldName, parser.getDeprecationHandler())) {
                        // TODO: How to avoid DRY when parsing terms...
                        // terms = TermParser.parseTerms(parser); // would this work?
                    }
                }
            }
        }

        TermStatQueryBuilder builder = new TermStatQueryBuilder();
        builder
            .expr(expr)
            .aggr(aggr)
            .posAggr(aggr)
            .terms(terms);


        if (builder.expr == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + EXPR_NAME + "] is mandatary");
        }

        if (builder.terms == null) {
            throw new ParsingException(parser.getTokenLocation(), "Term listing is manadatory");
        }

        // Default aggr to mean if none specified
        if (builder.aggr == null) {
            builder.aggr(StatisticsHelper.AggrTypes.AVG.toString().toLowerCase());
        }

        // Default pos_aggr to mean if none specified
        if (builder.pos_aggr == null) {
            builder.posAggr(StatisticsHelper.AggrTypes.AVG.toString().toLowerCase());
        }

        return builder;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(expr);
        out.writeString(aggr);
        out.writeString(pos_aggr);

        // Output all terms
        for (Term t : terms) {
            out.writeString(t.field());
            out.writeString(t.text());
        }
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(NAME);
        printBoostAndQueryName(builder);
        builder.field(EXPR_NAME.getPreferredName(), expr);
        builder.field(AGGR_NAME.getPreferredName(), aggr);
        builder.field(POS_AGGR_NAME.getPreferredName(), pos_aggr);

        // Spit out all terms
        builder.startObject(TERMS_NAME.getPreferredName());
        for (Term t : terms) {
            builder.field(t.field(), t.text());
        }
        builder.endObject();

        builder.endObject();
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        return new TermStatQuery(expr, aggr, pos_aggr, terms);
    }

    @Override
    protected int doHashCode() { return Objects.hash(expr, aggr, pos_aggr, terms);}

    @Override
    protected boolean doEquals(TermStatQueryBuilder other) {
        return Objects.equals(expr, other.expr)
                && Objects.equals(aggr, other.aggr)
                && Objects.equals(pos_aggr, other.pos_aggr)
                && Objects.equals(terms, other.terms);
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

    public Set<Term> terms() {
        return terms;
    }

    public TermStatQueryBuilder terms(Set<Term> terms) {
        this.terms = terms;
        return this;
    }



}
