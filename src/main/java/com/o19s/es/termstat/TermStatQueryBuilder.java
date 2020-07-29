package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper.AggrType;

import com.o19s.es.ltr.utils.Scripting;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class TermStatQueryBuilder extends AbstractQueryBuilder<TermStatQueryBuilder> implements NamedWriteable {
    public static final String NAME = "term_stat";

    private static final ParseField EXPR_NAME = new ParseField("expr");
    private static final ParseField AGGR_NAME = new ParseField("aggr");
    private static final ParseField POS_AGGR_NAME = new ParseField("pos_aggr");
    private static final ParseField TERMS_NAME = new ParseField("terms");

    private String expr;
    private AggrType aggr;
    private AggrType pos_aggr;
    private Set<Term> terms;

    public TermStatQueryBuilder() {
    }

    public TermStatQueryBuilder(StreamInput in) throws IOException {
        super(in);
        expr = in.readString();
        aggr = AggrType.valueOf(in.readString().toUpperCase(Locale.getDefault()));
        pos_aggr = AggrType.valueOf(in.readString().toUpperCase(Locale.getDefault()));

        // Read in all terms
        terms = new HashSet<Term>();
        while (in.available() > 0) {
            String field = in.readString();
            String text = in.readString();
            terms.add(new Term(field, text));
        }
    }

    public static TermStatQueryBuilder fromXContent(XContentParser parser) throws IOException {
        float boost = 0.0f;

        String expr = null;
        AggrType aggr = null;
        AggrType pos_aggr = null;
        String queryName = null;
        Set<Term> terms = new HashSet<>();

        String currentFieldName = null;
        XContentParser.Token token;
        // TODO: Can we just use the simpler ObjectParser method here? Revisit after playing with the terms DSL
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            // Read in current field name inside the main object
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            // Parse an object
            } else if (token == XContentParser.Token.START_OBJECT) {
                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (TERMS_NAME.match(currentFieldName, parser.getDeprecationHandler())) {
                        try {
                            String fieldName = parser.currentName();
                            parser.nextToken();
                            String termText = parser.text();
                            terms.add(new Term(fieldName, termText));
                        } catch (Exception ex) {
                            throw new ParsingException(parser.getTokenLocation(),
                                    "[" + NAME + "] unknown token [" + token + "] after [" + currentFieldName + "]");
                        }
                    } else {
                        throw new ParsingException(parser.getTokenLocation(),
                                "[" + NAME + "] unknown token [" + token + "] after [" + currentFieldName + "]");
                    }
                }
            // Parse out value strings
            } else if (token == XContentParser.Token.VALUE_STRING) {
                if (EXPR_NAME.match(currentFieldName, parser.getDeprecationHandler())) {
                    expr = parser.text();
                } else if(AGGR_NAME.match(currentFieldName, parser.getDeprecationHandler())) {
                    aggr = AggrType.valueOf(parser.text().toUpperCase(Locale.getDefault()));
                } else if(POS_AGGR_NAME.match(currentFieldName, parser.getDeprecationHandler())) {
                    pos_aggr = AggrType.valueOf(parser.text().toUpperCase(Locale.getDefault()));
                } else if(AbstractQueryBuilder.NAME_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    queryName = parser.text();
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                            "[" + NAME + "] unknown token [" + token + "] after [" + currentFieldName + "]");
                }
            } else if (token == XContentParser.Token.VALUE_NUMBER) {
                if (AbstractQueryBuilder.BOOST_FIELD.match(currentFieldName, parser.getDeprecationHandler())) {
                    boost = parser.floatValue();
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                            "[" + NAME + "] unknown token [" + token + "] after [" + currentFieldName + "]");
                }
            }
        }

        TermStatQueryBuilder builder = new TermStatQueryBuilder();
        builder
            .expr(expr)
            .aggr(aggr)
            .posAggr(pos_aggr)
            .terms(terms)
            .queryName(queryName)
            .boost(boost);


        if (builder.expr == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + EXPR_NAME + "] is mandatary");
        }

        if (builder.terms == null) {
            throw new ParsingException(parser.getTokenLocation(), "Term listing is manadatory");
        }

        // Default aggr to mean if none specified
        if (builder.aggr == null) {
            builder.aggr(AggrType.AVG);
        }

        // Default pos_aggr to mean if none specified
        if (builder.pos_aggr == null) {
            builder.posAggr(AggrType.AVG);
        }

        return builder;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(expr);
        out.writeString(aggr.getType());
        out.writeString(pos_aggr.getType());

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
        Expression compiledExpression = (Expression) Scripting.compile(expr);

        return new TermStatQuery(compiledExpression, aggr, pos_aggr, terms);
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

    public AggrType aggr() {
        return aggr;
    }

    public TermStatQueryBuilder aggr(AggrType aggr) {
        this.aggr = aggr;
        return this;
    }

    public AggrType posAggr() {
        return pos_aggr;
    }

    public TermStatQueryBuilder posAggr(AggrType pos_aggr) {
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
