package com.o19s.es.termstat;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.Objects;

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
        Query rewritten = query.rewrite(reader);

        if (rewritten != query) {
            return new TermStatQuery(rewritten, expr);
        }

        return this;
    }

    @Override
    public int hashCode() { return Objects.hash(query, expr); }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
            throws IOException {

        if (!scoreMode.needsScores()) {
            return searcher.createWeight(query, scoreMode, boost);
        }

        // TODO: Constant 1.0 placeholder, build out logic
        return new ConstantScoreWeight(TermStatQuery.this, 1.0f) {
            @Override
            public Explanation explain(LeafReaderContext context, int doc) throws IOException {
                Scorer scorer = scorer(context);
                int newDoc = scorer.iterator().advance(doc);
                assert newDoc == doc; // this is a DocIdSetIterator.all
                return Explanation.match(
                        scorer.score(),
                        "Expression: " + expr);
            }

            @Override
            public Scorer scorer(LeafReaderContext context) throws IOException {
                return new ConstantScoreScorer(this, score(), scoreMode, DocIdSetIterator.all(context.reader().maxDoc()));
            }

            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return true;
            }
        };
    }

    public String toString(String field) {
        return query.toString(field);
    }
}
