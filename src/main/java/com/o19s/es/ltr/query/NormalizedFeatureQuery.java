package com.o19s.es.ltr.query;

import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.Set;

/**
 * Wrap another Query and normalize it's score using provided Normalizer
 */
public class NormalizedFeatureQuery extends Query {

    private final Query wrapped;
    private final Normalizer ftrNorm;

    public NormalizedFeatureQuery(Query wrapped, Normalizer ftrNorm) {
        this.wrapped = wrapped;
        this.ftrNorm = ftrNorm;
    }

    @Override
    public String toString(String field) {
        return "Query Normalized by (" + ftrNorm.toString() + ") - " + wrapped.toString();
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        Weight wrappedWeight = wrapped.createWeight(searcher, scoreMode, boost);
        return new NormalizedFeatureWeight(this, wrappedWeight, ftrNorm);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof NormalizedFeatureQuery)) {
            return false;
        }

        NormalizedFeatureQuery that = (NormalizedFeatureQuery)obj;
        if (!that.wrapped.equals(this.wrapped)) {
            return false;
        }
        if (!that.ftrNorm.equals(this.ftrNorm)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return this.wrapped.hashCode() * 31 + this.ftrNorm.hashCode();
    }

    public static class NormalizedFeatureWeight extends Weight {

        private final Weight wrapped;
        private final Normalizer ftrNorm;

        /**
         * Sole constructor, typically invoked by sub-classes.
         *
         * @param query the parent query
         */
        protected NormalizedFeatureWeight(org.apache.lucene.search.Query query,
                                          Weight wrapped, Normalizer ftrNorm) {
            super(query);
            this.wrapped = wrapped;
            this.ftrNorm = ftrNorm;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            this.wrapped.extractTerms(terms);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            return ftrNorm.explain(this.wrapped.explain(context, doc));
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
            Scorer wrappedScorer = wrapped.scorer(context);
            return new NormalizedFeatureScorer(this, wrappedScorer, ftrNorm);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return wrapped.isCacheable(ctx);
        }
    }

    public static class NormalizedFeatureScorer extends Scorer {

        private final Scorer wrapped;
        private final Normalizer ftrNorm;

        /**
         * Constructs a Scorer
         *
         * @param weight The scorers <code>Weight</code>.
         */
        protected NormalizedFeatureScorer(Weight weight, Scorer wrapped, Normalizer ftrNorm) {
            super(weight);
            this.wrapped = wrapped;
            this.ftrNorm = ftrNorm;
        }

        @Override
        public DocIdSetIterator iterator() {
            return wrapped.iterator();
        }

        @Override
        public float getMaxScore(int upTo) throws IOException {
            return wrapped.getMaxScore(upTo);
        }

        @Override
        public float score() throws IOException {
            return ftrNorm.normalize(wrapped.score());
        }

        @Override
        public int docID() {
            return wrapped.docID();
        }
    }
}
