package com.o19s.es.termstat;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.Weight;

import java.io.IOException;
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
        IndexReaderContext context = searcher.getTopReaderContext();
        assert scoreMode.needsScores() : "Should not be used in filtering mode";
        Term term = new Term("text", "cow");
        return new TermStatWeight(searcher, this, term, TermStates.build(context, term, scoreMode.needsScores()));
    }

    public String toString(String field) {
        return query.toString(field);
    }

    static class TermStatWeight extends Weight {
        private IndexSearcher searcher;
        private final Term term;
        private final TermStates termStates;

        TermStatWeight(IndexSearcher searcher, Query query, Term term, TermStates termStates) {
            super(query);
            this.searcher = searcher;
            this.term = term;
            this.termStates = termStates;
        }

        @Override
        public void extractTerms(Set<Term> terms) { terms.add(term); }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = this.scorer(context);
            int newDoc = scorer.iterator().advance(doc);
            if (newDoc == doc) {
                return Explanation
                        .match(scorer.score(), "weight(" + this.getQuery() + " in doc " + newDoc + ")");
            }
            return Explanation.noMatch("no matching term");
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
            assert this.termStates != null && this.termStates
                    .wasBuiltFor(ReaderUtil.getTopLevelContext(context));
            TermState state = this.termStates.get(context);
            if (state == null) {
                return null;
            } else {
                // TODO: Build out arrays of term stats here for multiple terms support?
                TermStates ctx = TermStates.build(searcher.getTopReaderContext(), term, true);
                TermStatistics termStatistics = searcher.termStatistics(term, ctx);

                TermsEnum terms = context.reader().terms(this.term.field()).iterator();
                terms.seekExact(this.term.bytes(), state);
                return new TermScorer(this, terms.postings(null, PostingsEnum.ALL), termStatistics);
            }
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }
    }

    public abstract static class PostingsScorer extends Scorer {
        final PostingsEnum postingsEnum;
        final TermStatistics termStatistics;

        PostingsScorer(Weight weight, PostingsEnum postingsEnum, TermStatistics termStatistics) {
            super(weight);
            this.postingsEnum = postingsEnum;
            this.termStatistics = termStatistics;
        }

        @Override
        public int docID() {
            return this.postingsEnum.docID();
        }

        @Override
        public DocIdSetIterator iterator() {
            return this.postingsEnum;
        }
    }

    static class TermScorer extends PostingsScorer {
        TermScorer(Weight weight, PostingsEnum postingsEnum, TermStatistics termStatistics) {super(weight, postingsEnum, termStatistics); }

        @Override
        public float score() throws IOException {
            return termStatistics.docFreq();
        }

        @Override
        public float getMaxScore(int upTo) throws IOException {
            return Float.POSITIVE_INFINITY;
        }
    }
}
