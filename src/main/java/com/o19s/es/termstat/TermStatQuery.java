package com.o19s.es.termstat;

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
    private final Query query;
    private String expr;

    public TermStatQuery(Query query, String expr) {
        this.query = query;
        this.expr = expr;
    }


    public Query getQuery() {
        return this.query;
    }

    public String getExpr() {
        return this.expr;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) &&
                equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(TermStatQuery other) {
        return Objects.equals(query, other.query)
                && Objects.equals(expr, other.expr);
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        return this;
    }

    @Override
    public int hashCode() { return Objects.hash(query, expr); }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
            throws IOException {
        assert scoreMode.needsScores() : "Should not be used in filtering mode";

        // TODO: Parse terms from a param, basic whitespace tokenizer?
        HashSet<Term> terms = new HashSet<>();
        terms.add(new Term("text", "cow"));


        return new TermStatWeight(searcher, this, terms, scoreMode);
    }

    public String toString(String field) {
        return query.toString(field);
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
            return new TermStatScorer(this, searcher, context, terms, scoreMode);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }
    }
}
