package com.o19s.es.termstat;

import com.o19s.es.ltr.utils.Scripting;
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

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class TermStatQuery extends Query {
    private String expr;
    private String aggr;
    private String pos_aggr;
    private Set<Term> terms;

    public TermStatQuery(String expr, String aggr, String pos_aggr, Set<Term> terms) {
        this.expr = expr;
        this.aggr = aggr;
        this.pos_aggr = pos_aggr;
        this.terms = terms;
    }


    public String getExpr() {
        return this.expr;
    }
    public String getAggr() { return this.aggr; }
    public String getPosAggr() { return this.pos_aggr; }
    public Set<Term> getTerms() { return this.terms; }


    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) &&
                equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(TermStatQuery other) {
        return Objects.equals(expr, other.expr)
                && Objects.equals(aggr, other.aggr)
                && Objects.equals(pos_aggr, other.pos_aggr)
                && Objects.equals(terms, other.terms);
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        return this;
    }

    @Override
    public int hashCode() { return Objects.hash(expr, aggr, pos_aggr, terms); }

    @Override
    public String toString(String field) {
        return null;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
            throws IOException {
        assert scoreMode.needsScores() : "Should not be used in filtering mode";

        return new TermStatWeight(searcher, this, terms, scoreMode);
    }

    static class TermStatWeight extends Weight {
        private final String expression;
        private IndexSearcher searcher;
        private final Set<Term> terms;
        private final ScoreMode scoreMode;

        TermStatWeight(IndexSearcher searcher, TermStatQuery query, Set<Term> terms, ScoreMode scoreMode) {
            super(query);
            this.searcher = searcher;
            this.expression = query.expr;
            this.terms = terms;
            this.scoreMode = scoreMode;
        }

        @Override
        public void extractTerms(Set<Term> terms) { terms.addAll(terms); }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = this.scorer(context);
            int newDoc = scorer.iterator().advance(doc);
            if (newDoc == doc) {
                return Explanation
                        .match(scorer.score(), "weight(" + this.expression + " in doc " + newDoc + ")");
            }
            return Explanation.noMatch("no matching term");
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
            Expression compiledExpression = (Expression) Scripting.compile(this.expression);
            return new TermStatScorer(this, searcher, context, compiledExpression, terms, scoreMode);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }
    }
}
