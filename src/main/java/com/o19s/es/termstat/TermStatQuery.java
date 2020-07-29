package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper;
import com.o19s.es.explore.StatisticsHelper.AggrType;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.index.query.QueryBuilder;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

public class TermStatQuery extends Query {
    private Expression expr;
    private StatisticsHelper.AggrType aggr;
    private StatisticsHelper.AggrType posAggr;
    private Query query;

    public TermStatQuery(Expression expr, AggrType aggr, AggrType posAggr, Query query) {
        this.expr = expr;
        this.aggr = aggr;
        this.posAggr = posAggr;
        this.query = query;
    }


    public Expression getExpr() {
        return this.expr;
    }
    public AggrType getAggr() { return this.aggr; }
    public AggrType getPosAggr() { return this.posAggr; }
    public Query getQuery() { return this.query; }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) &&
                equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(TermStatQuery other) {
        return Objects.equals(expr.sourceText, other.expr.sourceText)
                && Objects.equals(aggr, other.aggr)
                && Objects.equals(posAggr, other.posAggr)
                && Objects.equals(query, other.query);
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        Query rewritten = query.rewrite(reader);

        if (rewritten != query) {
            return new TermStatQuery(expr, aggr, posAggr, rewritten);
        }

        return this;
    }

    @Override
    public int hashCode() { return Objects.hash(expr.sourceText, aggr, posAggr, query); }

    @Override
    public String toString(String field) {
        return null;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
            throws IOException {
        assert scoreMode.needsScores() : "Should not be used in filtering mode";

        return new TermStatWeight(searcher, this, query, scoreMode, aggr, posAggr);
    }

    static class TermStatWeight extends Weight {
        private final Expression expression;
        private IndexSearcher searcher;
        private final Query query;
        private final ScoreMode scoreMode;

        private AggrType aggr;
        private AggrType posAggr;

        TermStatWeight(IndexSearcher searcher, TermStatQuery tsq, Query query, ScoreMode scoreMode, AggrType aggr, AggrType posAggr) {
            super(tsq);
            this.searcher = searcher;
            this.expression = tsq.expr;
            this.query = query;
            this.scoreMode = scoreMode;
            this.aggr = aggr;
            this.posAggr = posAggr;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            // TODO: Play with visitor interface, try to find best place to link up to this call.
            terms.addAll(terms);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = this.scorer(context);
            int newDoc = scorer.iterator().advance(doc);
            if (newDoc == doc) {
                return Explanation
                        .match(scorer.score(), "weight(" + this.expression.sourceText + " in doc " + newDoc + ")");
            }
            return Explanation.noMatch("no matching term");
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
            return new TermStatScorer(this, searcher, context, expression, query, scoreMode, aggr, posAggr);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }
    }
}
