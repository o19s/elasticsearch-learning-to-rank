/*
 * Copyright [2017] Doug Turnbull, Wikimedia Foundation
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
 */

package com.o19s.es.ltr.query;

import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.LtrModel;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.index.query.QueryShardContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lucene query designed to apply a ranking model provided by {@link LtrRanker}
 * This query is not designed for retrieval, in other words it will score
 * all the docs in the index and thus must be used either in a rescore phase
 * or within a BooleanQuery and an appropriate filter clause.
 */
public class RankerQuery extends Query {
    private final List<Query> queries;
    private final FeatureSet features;
    private final LtrRanker ranker;

    private RankerQuery(List<Query> queries, FeatureSet features, LtrRanker ranker) {
        this.queries = Objects.requireNonNull(queries);
        this.features = Objects.requireNonNull(features);
        this.ranker = Objects.requireNonNull(ranker);
    }

    /**
     * Build a RankerQuery based on a prebuilt model.
     * Prebuilt models are not parametrized as they contain only {@link com.o19s.es.ltr.feature.PrebuiltFeature}
     *
     * @param model a prebuilt model
     * @return the lucene query
     */
    public static RankerQuery build(PrebuiltLtrModel model) {
        return build(model.ranker(), model.featureSet(), null, Collections.emptyMap());
    }

    /**
     * Build a RankerQuery.
     *
     * @param model The model
     * @param context the context used to parse features into lucene queries
     * @param params the query params
     * @return the lucene query
     */
    public static RankerQuery build(LtrModel model, QueryShardContext context, Map<String, Object> params) {
        return build(model.ranker(), model.featureSet(), context, params);
    }

    private static RankerQuery build(LtrRanker ranker, FeatureSet features, QueryShardContext context, Map<String, Object> params) {
        List<Query> queries = features.toQueries(context, params);
        return new RankerQuery(queries, features, ranker);
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        List<Query> rewrittenQueries = new ArrayList<>(queries.size());
        boolean rewritten = false;
        for (Query query : queries) {
            Query rewrittenQuery = query.rewrite(reader);
            rewritten |= rewrittenQuery != query;
            rewrittenQueries.add(rewrittenQuery);
        }
        return rewritten ? new RankerQuery(rewrittenQueries, features, ranker) : this;
    }

    @Override
    public boolean equals(Object obj) {
        // This query should never be cached
        if (this == obj) {
            return true;
        }
        if (!sameClassAs(obj)) {
            return false;
        }
        RankerQuery that = (RankerQuery) obj;
        return Objects.deepEquals(queries, that.queries) &&
                Objects.deepEquals(features, that.features) &&
                Objects.equals(ranker, that.ranker);
    }

    Stream<Query> stream() {
        return queries.stream();
    }

    @Override
    public int hashCode() {
        return 31 * classHash() + Objects.hash(features, queries, ranker);
    }

    @Override
    public String toString(String field) {
        return "rankerquery:"+field;
    }

    /**
     * Return feature at ordinal
     */
    Feature getFeature(int ordinal) {
        return features.feature(ordinal);
    }

    /**
     * The ranker used by this query
     */
    LtrRanker ranker() {
        return ranker;
    }

    @Override
    public RankerWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        List<Weight> weights = new ArrayList<>(queries.size());
        for (Query q : queries) {
            weights.add(searcher.createWeight(q, needsScores));
        }
        return new RankerWeight(weights);
    }

    public class RankerWeight extends Weight {
        private final List<Weight> weights;

        RankerWeight(List<Weight> weights) {
            super(RankerQuery.this);
            assert weights instanceof RandomAccess;
            this.weights = weights;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            for (Weight w : weights) {
                w.extractTerms(terms);
            }
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            List<Explanation> subs = new ArrayList<>(weights.size());

            LtrRanker.FeatureVector d = ranker.newFeatureVector(null);
            int ordinal = -1;
            for (Weight weight : weights) {
                ordinal++;
                Explanation explain = weight.explain(context, doc);
                String featureString = "Feature " + Integer.toString(ordinal);
                if (features.feature(ordinal).name() != null) {
                    featureString += "(" + features.feature(ordinal).name() + ")";
                }
                featureString += ":";
                float featureVal = 0.0f;
                if (!explain.isMatch()) {
                    subs.add(Explanation.noMatch(featureString + " [no match, default value 0.0 used]"));
                }
                else {
                    subs.add(Explanation.match(explain.getValue(), featureString, explain));
                    featureVal = explain.getValue();
                }
                d.setFeatureScore(ordinal, featureVal);
            }
            float modelScore = ranker.score(d);
            return Explanation.match(modelScore, " LtrModel: " + ranker.name() + " using features:", subs);
        }

        @Override
        public float getValueForNormalization() throws IOException {
            // disabled in future lucene version, see #normalize(float, float)
            return 1F;
        }

        @Override
        public void normalize(float norm, float boost) {
            // Ignore top-level boost & norm
            // We must make sure that the scores from the sub scorers
            // are not affected by parent queries because rankers using thresholds
            // may produce inconsistent results.
            // It breaks lucene contract but in general this query is meant
            // to be used as the top level query of a rescore query where
            // resulting score can still be controlled with the rescore_weight param.
            // One possibility would be to store the boost value and apply it
            // on the resulting score.
            // Logging feature scores may be impossible when feature queries
            // are run and logged individually (_msearch approach) and the similatity
            // used is affected by queryNorm (ClassicSimilarity)
            for (Weight w : weights) {
                w.normalize(1F, 1F);
            }
        }

        @Override
        public RankerScorer scorer(LeafReaderContext context) throws IOException {
            List<Scorer> scorers = new ArrayList<>(weights.size());
            List<DocIdSetIterator> subIterators = new ArrayList<>(weights.size());
            for (Weight weight : weights) {
                Scorer scorer = weight.scorer(context);
                if (scorer == null) {
                    scorer = new NoopScorer(this, context.reader().maxDoc());
                }
                scorers.add(scorer);
                subIterators.add(scorer.iterator());
            }

            NaiveDisjunctionDISI rankerIterator = new NaiveDisjunctionDISI(DocIdSetIterator.all(context.reader().maxDoc()), subIterators);
            return new RankerScorer(scorers, rankerIterator);
        }

        class RankerScorer extends Scorer {
            /**
             * NOTE: Switch to ChildScorer and {@link #getChildren()} if it appears
             * to be useful for logging
             */
            private final List<Scorer> scorers;
            private final NaiveDisjunctionDISI iterator;
            private LtrRanker.FeatureVector featureVector;

            RankerScorer(List<Scorer> scorers, NaiveDisjunctionDISI iterator) {
                super(RankerWeight.this);
                this.scorers = scorers;
                this.iterator = iterator;
            }

            @Override
            public int docID() {
                return iterator.docID();
            }

            @Override
            public float score() throws IOException {
                featureVector = ranker.newFeatureVector(featureVector);
                int ordinal = -1;
                // a DisiPriorityQueue could help to avoid
                // looping on all scorers
                for (Scorer scorer : scorers) {
                    ordinal++;
                    // FIXME: Probably inefficient, again we loop over all scorers..
                    if (scorer.docID() == docID()) {
                        // XXX: bold assumption that all models are dense
                        // do we need a some indirection to infer the featureId?
                        featureVector.setFeatureScore(ordinal, scorer.score());
                    }
                }
                return ranker.score(featureVector);
            }

            @Override
            public int freq() throws IOException {
                return scorers.size();
            }

            @Override
            public DocIdSetIterator iterator() {
                return iterator;
            }
        }
    }

    /**
     * Driven by a main iterator and tries to maintain a list of sub iterators
     * Mostly needed to avoid calling {@link Scorer#iterator()} to directly advance
     * from {@link RankerWeight.RankerScorer#score()} as some Scorer implementations
     * will instantiate new objects every time iterator() is called.
     * NOTE: consider using {@link org.apache.lucene.search.DisiPriorityQueue}?
     */
    static class NaiveDisjunctionDISI extends DocIdSetIterator {
        private final DocIdSetIterator main;
        private final List<DocIdSetIterator> subIterators;

        NaiveDisjunctionDISI(DocIdSetIterator main, List<DocIdSetIterator> subIterators) {
            this.main = main;
            this.subIterators = subIterators;
        }

        @Override
        public int docID() {
            return main.docID();
        }

        @Override
        public int nextDoc() throws IOException {
            int doc = main.nextDoc();
            advanceSubIterators(doc);
            return doc;
        }

        @Override
        public int advance(int target) throws IOException {
            int docId = main.advance(target);
            advanceSubIterators(docId);
            return docId;
        }

        private void advanceSubIterators(int target) throws IOException {
            if (target == NO_MORE_DOCS) {
                return;
            }
            for (DocIdSetIterator iterator: subIterators) {
                // FIXME: Probably inefficient
                if (iterator.docID() < target) {
                    iterator.advance(target);
                }
            }
        }

        @Override
        public long cost() {
            return main.cost();
        }
    }
}
