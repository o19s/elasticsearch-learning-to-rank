/*
 * Copyright [2016] Doug Turnbull
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.o19s.es.ltr.query;

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.learning.Ranker;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.Similarity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/**
 * Created by doug on 12/24/16.
 *  modeled largely after DisjunctionMaxQuery
 */
public class LtrQuery extends Query {

    /* The subqueries */
    private final Query[] _features;
    private final Ranker _rankModel;
    private final String[] _featureNames;

    public LtrQuery(Collection<Query> features, Ranker rankModel, Collection<String> featureNames) {
        this._rankModel = rankModel;
        Objects.requireNonNull(features, "Collection of Querys must not be null");

        if (featureNames.size() != features.size()) {
            throw new IllegalArgumentException("Was provided more featureNames than features (or vice-versa!)");
        }

        this._features = features.toArray(new Query[features.size()]);
        this._featureNames = featureNames.toArray(new String[featureNames.size()]);
    }


    /**
     * @return the disjuncts.
     */
    public List<Query> getFeatures() {
        return Collections.unmodifiableList(Arrays.asList(_features));
    }

    public List<String> getFeatureNames()  {
        return Collections.unmodifiableList(Arrays.asList(_featureNames));
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
        return new LtrQuery.LtrWeight(searcher, needsScores, _featureNames);
    }


    // ************************************************************************//
    // Weight: Modeled on
    protected class LtrWeight extends Weight {
        // The Weight's for our subqueries, in 1-1 correspondence with disjuncts
        protected final ArrayList<Weight> weights = new ArrayList<>();

        private final boolean _needsScores;
        private Similarity _similarity;
        private String[] _names;

        protected LtrWeight(IndexSearcher searcher, boolean needsScores, String[] names) throws IOException {
            super(LtrQuery.this);
            for (Query feature : _features) {
                Query rewritten = feature.rewrite(searcher.getIndexReader());
                weights.add(searcher.createWeight(rewritten, needsScores));
            }
            this._names = names;
            this._needsScores = needsScores;
            this._similarity = searcher.getSimilarity(needsScores);
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            for (Weight weight : weights) {
                weight.extractTerms(terms);
            }
        }

        public String getName(int featureIdx) {
            int idx = featureIdx - 1;
            assert(idx >= 0);
            return this._names[idx];
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            // TODO
            List<Explanation> subs = new ArrayList<>();

            DataPoint d = new DenseProgramaticDataPoint(weights.size());
            int featureIdx = 1;
            for (Weight weight: weights) {
                Explanation explain = weight.explain(context, doc);
                String featureString = "Feature " + Integer.toString(featureIdx) + "(" + getName(featureIdx) + "):";
                float featureVal = 0.0f;
                if (!explain.isMatch()) {
                    subs.add(Explanation.noMatch(featureString + "(no match, default value 0.0 used)"));
                }
                else {
                    subs.add(Explanation.match(explain.getValue(), featureString, explain));
                    featureVal = explain.getValue();
                }
                d.setFeatureValue(featureIdx, featureVal);
                featureIdx++;
            }
            float modelScore = (float) _rankModel.eval(d);
            return Explanation.match(modelScore, " Model: " + _rankModel.name() + " using features:", subs);
        }

        @Override
        public float getValueForNormalization() throws IOException {
            // run indexsearcher's normalization procedure directly on each
            // subweight
            for (Weight weight: weights) {
                float valueToNormalize = weight.getValueForNormalization();
                float norm = _similarity.queryNorm(valueToNormalize);
                if (Float.isInfinite(norm) || Float.isNaN(norm)) {
                    norm = 1.0f;
                }
                weight.normalize(norm, 1.0f);

            }
            return 0.0f;
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
                } else {
                    scorers.add(new NoopScorer(w, context.reader().maxDoc()));
                }
            }
            if (scorers.isEmpty()) {
                // no sub-scorers had any documents
                return null;
            } else {
                return new LtrScorer(this, scorers, _needsScores, context, _rankModel);
            }
        }
    }

    public String toString(String field) {
        String rVal = "LTR model: " + _rankModel.name() + "(";
        for (Query query: _features) {
            rVal += _features.toString();
        }
        return rVal + ")";
    };



}
