package com.o19s.es.ltr;

import ciir.umass.edu.learning.Ranker;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.*;

/**
 * Created by doug on 12/24/16.
 *  modeled largely after DisjunctionMaxQuery
 */
public class LtrQuery extends Query  {

    /* The subqueries */
    private final Query[] _features;
    private final Ranker _rankModel;

    public LtrQuery(Collection<Query> features, Ranker rankModel) {
        this._rankModel = rankModel;
        Objects.requireNonNull(features, "Collection of Querys must not be null");

        this._features = features.toArray(new Query[features.size()]);
    }


    /**
     * @return the disjuncts.
     */
    public List<Query> getFeatures() {
        return Collections.unmodifiableList(Arrays.asList(_features));
    }


    @Override
    public String toString(String field) {
        return null;
    }

    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) &&
                equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(LtrQuery other) {
        return Arrays.equals(_features, other._features) && _rankModel.equals(other._rankModel);
    }


    @Override
    public int hashCode() {
        int h = classHash();
        h = 31 * h + Arrays.hashCode(_features);
        h = 31 * h + _rankModel.hashCode();
        return h;
    }


    /** Create the Weight used to score us */
    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        return new LtrQuery.LtrWeight(searcher, needsScores);
    }


    // ************************************************************************//
    // Weight: Modeled on
    protected class LtrWeight extends Weight {

        protected final ArrayList<Weight> weights = new ArrayList<>();  // The Weight's for our subqueries, in 1-1 correspondence with disjuncts
        private final boolean needsScores;

        protected LtrWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
            super(LtrQuery.this);
            for (Query feature : _features) {
                weights.add(searcher.createWeight(feature, needsScores));
            }
            this.needsScores = needsScores;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            for (Weight weight : weights) {
                weight.extractTerms(terms);
            }
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            // TODO
            return null;
        }

        @Override
        public float getValueForNormalization() throws IOException {
            return 0;
        }

        @Override
        public void normalize(float norm, float boost) {
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
            List<Scorer> scorers = new ArrayList<>();
            for (Weight w : weights) {
                // we will advance() subscorers
                Scorer subScorer = w.scorer(context);
                if (subScorer != null) {
                    scorers.add(subScorer);
                }
            }
            if (scorers.isEmpty()) {
                // no sub-scorers had any documents
                return null;
            } else {
                return new LtrScorer(this, scorers, needsScores, context, _rankModel);
            }
        }
    }



        /** Prettyprint us.
         * @param field the field to which we are applied
         * @return a string that shows what we do, of the form "(disjunct1 | disjunct2 | ... | disjunctn)^boost"
         */
    // TODO
//    @Override
//    public String toString(String field) {
//        StringBuilder buffer = new StringBuilder();
//        buffer.append("(");
//        for (int i = 0 ; i < disjuncts.length; i++) {
//            Query subquery = disjuncts[i];
//            if (subquery instanceof BooleanQuery) {   // wrap sub-bools in parens
//                buffer.append("(");
//                buffer.append(subquery.toString(field));
//                buffer.append(")");
//            }
//            else buffer.append(subquery.toString(field));
//            if (i != disjuncts.length-1) buffer.append(" | ");
//        }
//        buffer.append(")");
//        if (tieBreakerMultiplier != 0.0f) {
//            buffer.append("~");
//            buffer.append(tieBreakerMultiplier);
//        }
//        return buffer.toString();
//    }


}
