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

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.LtrModel;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.ranker.LogLtrRanker;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.NullRanker;
import com.o19s.es.ltr.utils.Suppliers;
import com.o19s.es.ltr.utils.Suppliers.MutableSupplier;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DisiPriorityQueue;
import org.apache.lucene.search.DisiWrapper;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private final Map<Integer, float[]> featureScoreCache;

    private RankerQuery(List<Query> queries, FeatureSet features, LtrRanker ranker,
                        Map<Integer, float[]> featureScoreCache) {
        this.queries = Objects.requireNonNull(queries);
        this.features = Objects.requireNonNull(features);
        this.ranker = Objects.requireNonNull(ranker);
        this.featureScoreCache = featureScoreCache;
    }

    /**
     * Build a RankerQuery based on a prebuilt model.
     * Prebuilt models are not parametrized as they contain only {@link com.o19s.es.ltr.feature.PrebuiltFeature}
     *
     * @param model a prebuilt model
     * @return the lucene query
     */
    public static RankerQuery build(PrebuiltLtrModel model) {
        return build(model.ranker(), model.featureSet(),
                new LtrQueryContext(null, Collections.emptySet()), Collections.emptyMap(), false);
    }

    /**
     * Build a RankerQuery.
     *
     * @param model   The model
     * @param context the context used to parse features into lucene queries
     * @param params  the query params
     * @return the lucene query
     */
    public static RankerQuery build(LtrModel model, LtrQueryContext context, Map<String, Object> params,
                                    Boolean featureScoreCacheFlag) {
        return build(model.ranker(), model.featureSet(), context, params, featureScoreCacheFlag);
    }

    private static RankerQuery build(LtrRanker ranker, FeatureSet features,
                                     LtrQueryContext context, Map<String, Object> params, Boolean featureScoreCacheFlag) {
        List<Query> queries = features.toQueries(context, params);
        Map<Integer, float[]> featureScoreCache = null;
        if (null != featureScoreCacheFlag && featureScoreCacheFlag) {
            featureScoreCache = new HashMap<>();
        }
        return new RankerQuery(queries, features, ranker, featureScoreCache);
    }

    public static RankerQuery buildLogQuery(LogLtrRanker.LogConsumer consumer, FeatureSet features,
                                            LtrQueryContext context, Map<String, Object> params) {
        List<Query> queries = features.toQueries(context, params);
        return new RankerQuery(queries, features,
                new LogLtrRanker(consumer, features.size()), null);
    }

    public RankerQuery toLoggerQuery(LogLtrRanker.LogConsumer consumer) {
        NullRanker newRanker = new NullRanker(features.size());
        return new RankerQuery(queries, features, new LogLtrRanker(newRanker, consumer), featureScoreCache);
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
        return rewritten ? new RankerQuery(rewrittenQueries, features, ranker, featureScoreCache) : this;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
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
        return "rankerquery:" + field;
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

    public FeatureSet featureSet() {
        return features;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        if (!scoreMode.needsScores()) {
            // If scores are not needed simply return a constant score on all docs
            return new ConstantScoreWeight(this, boost) {
                @Override
                public Scorer scorer(LeafReaderContext context) throws IOException {
                    return new ConstantScoreScorer(this, score(),
                            scoreMode, DocIdSetIterator.all(context.reader().maxDoc()));
                }

                @Override
                public boolean isCacheable(LeafReaderContext ctx) {
                    return false;
                }
            };
        }

        List<Weight> weights = new ArrayList<>(queries.size());
        // XXX: this is not thread safe and may run into extremely weird issues
        // if the searcher uses the parallel collector
        // Hopefully elastic never runs
        MutableSupplier<LtrRanker.FeatureVector> vectorSupplier = new Suppliers.FeatureVectorSupplier();
        FVLtrRankerWrapper ltrRankerWrapper = new FVLtrRankerWrapper(ranker, vectorSupplier);
        LtrRewriteContext context = new LtrRewriteContext(ranker, vectorSupplier);
        for (Query q : queries) {
            if (q instanceof LtrRewritableQuery) {
                q = ((LtrRewritableQuery) q).ltrRewrite(context);
            }
            weights.add(searcher.createWeight(q, ScoreMode.COMPLETE, boost));
        }
        return new RankerWeight(this, weights, ltrRankerWrapper, features, featureScoreCache);
    }

    public static class RankerWeight extends Weight {
        private final List<Weight> weights;
        private final FVLtrRankerWrapper ranker;
        private final FeatureSet features;
        private final Map<Integer, float[]> featureScoreCache;

        RankerWeight(RankerQuery query, List<Weight> weights, FVLtrRankerWrapper ranker, FeatureSet features,
                     Map<Integer, float[]> featureScoreCache) {
            super(query);
            assert weights instanceof RandomAccess;
            this.weights = weights;
            this.ranker = Objects.requireNonNull(ranker);
            this.features = Objects.requireNonNull(features);
            this.featureScoreCache = featureScoreCache;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return false;
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
                final Explanation explain;
                explain = weight.explain(context, doc);
                String featureString = "Feature " + Integer.toString(ordinal);
                if (features.feature(ordinal).name() != null) {
                    featureString += "(" + features.feature(ordinal).name() + ")";
                }
                featureString += ":";
                if (!explain.isMatch()) {
                    subs.add(Explanation.noMatch(featureString + " [no match, default value 0.0 used]"));
                } else {
                    subs.add(Explanation.match(explain.getValue(), featureString, explain));
                    d.setFeatureScore(ordinal, explain.getValue().floatValue());
                }
            }
            float modelScore = ranker.score(d);
            return Explanation.match(modelScore, " LtrModel: " + ranker.name() + " using features:", subs);
        }

        @Override
        public RankerScorer scorer(LeafReaderContext context) throws IOException {
            List<Scorer> scorers = new ArrayList<>(weights.size());
            DisiPriorityQueue disiPriorityQueue = new DisiPriorityQueue(weights.size());
            for (Weight weight : weights) {
                Scorer scorer = weight.scorer(context);
                if (scorer == null) {
                    scorer = new NoopScorer(this, DocIdSetIterator.empty());
                }
                scorers.add(scorer);
                disiPriorityQueue.add(new DisiWrapper(scorer));
            }

            DisjunctionDISI rankerIterator = new DisjunctionDISI(
                    DocIdSetIterator.all(context.reader().maxDoc()), disiPriorityQueue, context.docBase, featureScoreCache);
            return new RankerScorer(scorers, rankerIterator, ranker, context.docBase, featureScoreCache);
        }

        class RankerScorer extends Scorer {
            /**
             * NOTE: Switch to ChildScorer and {@link #getChildren()} if it appears
             * to be useful for logging
             */
            private final List<Scorer> scorers;
            private final DisjunctionDISI iterator;
            private final FVLtrRankerWrapper ranker;
            private LtrRanker.FeatureVector fv;
            private final int docBase;
            private final Map<Integer, float[]> featureScoreCache;

            RankerScorer(List<Scorer> scorers, DisjunctionDISI iterator, FVLtrRankerWrapper ranker,
                         int docBase, Map<Integer, float[]> featureScoreCache) {
                super(RankerWeight.this);
                this.scorers = scorers;
                this.iterator = iterator;
                this.ranker = ranker;
                this.docBase = docBase;
                this.featureScoreCache = featureScoreCache;
            }

            @Override
            public int docID() {
                return iterator.docID();
            }

            @Override
            public float score() throws IOException {
                fv = ranker.newFeatureVector(fv);
                if (featureScoreCache == null) {  // Cache disabled
                    int ordinal = -1;
                    // a DisiPriorityQueue could help to avoid
                    // looping on all scorers
                    for (Scorer scorer : scorers) {
                        ordinal++;
                        // FIXME: Probably inefficient, again we loop over all scorers..
                        if (scorer.docID() == docID()) {
                            // XXX: bold assumption that all models are dense
                            // do we need a some indirection to infer the featureId?
                            fv.setFeatureScore(ordinal, scorer.score());
                        }
                    }
                } else {
                    int perShardDocId = docBase + docID();
                    if (featureScoreCache.containsKey(perShardDocId)) {  // Cache hit
                        float[] featureScores = featureScoreCache.get(perShardDocId);
                        int ordinal = -1;
                        for (float score : featureScores) {
                            ordinal++;
                            if (!Float.isNaN(score)) {
                                fv.setFeatureScore(ordinal, score);
                            }
                        }
                    } else {  // Cache miss
                        int ordinal = -1;
                        float[] featureScores = new float[scorers.size()];
                        for (Scorer scorer : scorers) {
                            ordinal++;
                            float score = Float.NaN;
                            if (scorer.docID() == docID()) {
                                score = scorer.score();
                                fv.setFeatureScore(ordinal, score);
                            }
                            featureScores[ordinal] = score;
                        }
                        featureScoreCache.put(perShardDocId, featureScores);
                    }
                }
                return ranker.score(fv);
            }

//            @Override
//            public int freq() throws IOException {
//                return scorers.size();
//            }

            @Override
            public DocIdSetIterator iterator() {
                return iterator;
            }

            /**
             * Return the maximum score that documents between the last {@code target}
             * that this iterator was {@link #advanceShallow(int) shallow-advanced} to
             * included and {@code upTo} included.
             */
            @Override
            public float getMaxScore(int upTo) throws IOException {
                return Float.POSITIVE_INFINITY;
            }
        }
    }

    /**
     * Driven by a main iterator and tries to maintain a list of sub iterators
     * Mostly needed to avoid calling {@link Scorer#iterator()} to directly advance
     * from {@link RankerWeight.RankerScorer#score()} as some Scorer implementations
     * will instantiate new objects every time iterator() is called.
     */
    static class DisjunctionDISI extends DocIdSetIterator {
        private final DocIdSetIterator main;
        private final DisiPriorityQueue subIteratorsPriorityQueue;
        private final int docBase;
        private final Map<Integer, float[]> featureScoreCache;

        DisjunctionDISI(DocIdSetIterator main, DisiPriorityQueue subIteratorsPriorityQueue, int docBase,
                        Map<Integer, float[]> featureScoreCache) {
            this.main = main;
            this.subIteratorsPriorityQueue = subIteratorsPriorityQueue;
            this.docBase = docBase;
            this.featureScoreCache = featureScoreCache;
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
            if (featureScoreCache != null && featureScoreCache.containsKey(docBase + target)) {
                return docId;  // Cache hit. No need to advance sub iterators
            }
            advanceSubIterators(docId);
            return docId;
        }

        private void advanceSubIterators(int target) throws IOException {
            if (target == NO_MORE_DOCS) {
                return;
            }
            DisiWrapper top = subIteratorsPriorityQueue.top();
            while (top.doc < target) {
                top.doc = top.iterator.advance(target);
                top = subIteratorsPriorityQueue.updateTop();
            }
        }

        @Override
        public long cost() {
            return main.cost();
        }
    }

    static class FVLtrRankerWrapper implements LtrRanker {
        private final LtrRanker wrapped;
        private final MutableSupplier<FeatureVector> vectorSupplier;

        FVLtrRankerWrapper(LtrRanker wrapped, MutableSupplier<FeatureVector> vectorSupplier) {
            this.wrapped = Objects.requireNonNull(wrapped);
            this.vectorSupplier = Objects.requireNonNull(vectorSupplier);
        }

        @Override
        public String name() {
            return wrapped.name();
        }

        @Override
        public FeatureVector newFeatureVector(FeatureVector reuse) {
            FeatureVector fv = wrapped.newFeatureVector(reuse);
            vectorSupplier.set(fv);
            return fv;
        }

        @Override
        public float score(FeatureVector point) {
            return wrapped.score(point);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FVLtrRankerWrapper that = (FVLtrRankerWrapper) o;
            return Objects.equals(wrapped, that.wrapped) &&
                    Objects.equals(vectorSupplier, that.vectorSupplier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(wrapped, vectorSupplier);
        }
    }
}
