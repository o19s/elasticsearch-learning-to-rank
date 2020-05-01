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
        IndexReaderContext context = searcher.getTopReaderContext();
        assert scoreMode.needsScores() : "Should not be used in filtering mode";
        Term term = new Term("text", "cow");
        return new TermStatWeight(this, term, TermStates.build(context, term, scoreMode.needsScores()));
    }

    public String toString(String field) {
        return query.toString(field);
    }

    static class TermStatWeight extends Weight {
        private final Term term;
        private final TermStates termStates;

        TermStatWeight(Query query, Term term, TermStates termStates) {
            super(query);
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
                TermsEnum terms = context.reader().terms(this.term.field()).iterator();
                terms.seekExact(this.term.bytes(), state);
                return new TermScorer(this, terms.postings(null, PostingsEnum.ALL));
            }
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }
    }

    public abstract static class PostingsScorer extends Scorer {
        final PostingsEnum postingsEnum;

        PostingsScorer(Weight weight, PostingsEnum postingsEnum) {
            super(weight);
            this.postingsEnum = postingsEnum;
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
        TermScorer(Weight weight, PostingsEnum postingsEnum) {super(weight, postingsEnum); }

        @Override
        public float score() throws IOException {
            return postingsEnum.nextPosition();
        }

        @Override
        public float getMaxScore(int upTo) throws IOException {
            return Float.POSITIVE_INFINITY;
        }
    }
}
