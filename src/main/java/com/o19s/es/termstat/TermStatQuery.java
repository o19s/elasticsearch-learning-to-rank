package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper;
import com.o19s.es.explore.StatisticsHelper.AggrType;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.elasticsearch.script.DoubleValuesScript;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TermStatQuery extends Query {
    private DoubleValuesScript expr;
    private StatisticsHelper.AggrType aggr;
    private StatisticsHelper.AggrType posAggr;
    private Set<Term> terms;

    public TermStatQuery(DoubleValuesScript expr, AggrType aggr, AggrType posAggr, Set<Term> terms) {
        this.expr = expr;
        this.aggr = aggr;
        this.posAggr = posAggr;
        this.terms = terms;
    }

    public DoubleValuesScript getExpr() {
        return this.expr;
    }

    public AggrType getAggr() {
        return this.aggr;
    }

    public AggrType getPosAggr() {
        return this.posAggr;
    }

    public Set<Term> getTerms() {
        return this.terms;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) &&
                equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(TermStatQuery other) {
        return Objects.equals(expr.sourceText(), other.expr.sourceText())
                && Objects.equals(aggr, other.aggr)
                && Objects.equals(posAggr, other.posAggr)
                && Objects.equals(terms, other.terms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expr.sourceText(), aggr, posAggr, terms);
    }

    @Override
    public String toString(String field) {
        return null;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
            throws IOException {
        assert scoreMode.needsScores() : "Should not be used in filtering mode";

        return new TermStatWeight(searcher, this, terms, scoreMode, aggr, posAggr);
    }

    static class TermStatWeight extends Weight {
        private final DoubleValuesScript expression;
        private final IndexSearcher searcher;
        private final ScoreMode scoreMode;

        private final AggrType aggr;
        private final AggrType posAggr;
        private final Set<Term> terms;
        private final Map<Term, TermStates> termContexts;

        TermStatWeight(IndexSearcher searcher,
                TermStatQuery tsq,
                Set<Term> terms,
                ScoreMode scoreMode,
                AggrType aggr,
                AggrType posAggr) throws IOException {
            super(tsq);
            this.searcher = searcher;
            this.expression = tsq.expr;
            this.terms = terms;
            this.scoreMode = scoreMode;
            this.aggr = aggr;
            this.posAggr = posAggr;
            this.termContexts = new HashMap<>();

            // This is needed for proper DFS_QUERY_THEN_FETCH support
            if (scoreMode.needsScores()) {
                for (Term t : terms) {
                    TermStates ctx = TermStates.build(searcher, t, true);

                    if (ctx != null && ctx.docFreq() > 0) {
                        searcher.collectionStatistics(t.field());
                        searcher.termStatistics(t, ctx.docFreq(), ctx.totalTermFreq());
                    }

                    termContexts.put(t, ctx);
                }
            }
        }

        public void extractTerms(Set<Term> terms) {
            terms.addAll(terms);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = this.scorer(context);
            int newDoc = scorer.iterator().advance(doc);
            if (newDoc == doc) {
                return Explanation
                        .match(scorer.score(), "weight(" + this.expression.sourceText() + " in doc " + newDoc + ")");
            }
            return Explanation.noMatch("no matching term");
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }

        @Override
        public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
            return new Weight.DefaultScorerSupplier(
                    new TermStatScorer(searcher, context, expression, terms, scoreMode, aggr, posAggr, termContexts));
        }
    }

    @Override
    public void visit(QueryVisitor visitor) {
        Term[] acceptedTerms = terms.stream().filter(
                t -> visitor.acceptField(t.field())).toArray(Term[]::new);

        if (acceptedTerms.length > 0) {
            QueryVisitor v = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this);
            v.consumeTerms(this, acceptedTerms);
        }
    }
}
