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

import com.o19s.es.ltr.feature.DerivedFeature;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.LtrModel;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.ranker.LogLtrRanker;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.NullRanker;
import com.o19s.es.ltr.utils.Scripting;
import org.apache.lucene.expressions.Bindings;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.valuesource.DoubleConstValueSource;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
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

    public static RankerQuery buildLogQuery(LogLtrRanker.LogConsumer consumer, FeatureSet features,
                                            QueryShardContext context, Map<String, Object> params) {
        List<Query> queries = features.toQueries(context, params);
        return new RankerQuery(queries, features, new LogLtrRanker(consumer, features.size()));
    }

    public RankerQuery toLoggerQuery(LogLtrRanker.LogConsumer consumer, boolean replaceWithNullRanker) {
        LtrRanker newRanker = ranker;
        if (replaceWithNullRanker && !(ranker instanceof NullRanker)) {
            newRanker = new NullRanker(features.size());
        }
        return new RankerQuery(queries, features, new LogLtrRanker(newRanker, consumer));
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

    public FeatureSet featureSet() {
        return features;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        if (!needsScores) {
            // If scores are not needed simply return a constant score on all docs
            return new ConstantScoreWeight(this) {
                @Override
                public Scorer scorer(LeafReaderContext context) throws IOException {
                    return new ConstantScoreScorer(this, score(), DocIdSetIterator.all(context.reader().maxDoc()));
                }
            };
        }
        List<Weight> weights = new ArrayList<>(queries.size());
        for (Query q : queries) {
            weights.add(searcher.createWeight(q, needsScores));
        }
        return new RankerWeight(weights);
    }

    public class RankerWeight extends Weight {
        private final List<Expression> expressions;
        private final List<Weight> weights;

        RankerWeight(List<Weight> weights) {
            super(RankerQuery.this);
            assert weights instanceof RandomAccess;
            this.weights = weights;

            // Compile expressions for the derived features
            this.expressions = new ArrayList<>(features.derivedFeatures().size());
            for (DerivedFeature df : features.derivedFeatures()) {
                expressions.add((Expression) Scripting.compile(df.expression()));
            }
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

            SimpleBindings bindings = new SimpleBindings();
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
                if (!explain.isMatch()) {
                    subs.add(Explanation.noMatch(featureString + " [no match, default value 0.0 used]"));
                } else {
                    subs.add(Explanation.match(explain.getValue(), featureString, explain));
                    d.setFeatureScore(ordinal, explain.getValue());
                }

                // Add feature binding, bound to name if set, otherwise $[ordinal]
                Feature f = features.feature(ordinal);
                bindings.add(f.name() == null ? ("$" + ordinal) : f.name(),
                        new DoubleConstValueSource(d.getFeatureScore(ordinal)).asDoubleValuesSource());
            }

            for(Expression expr : expressions) {
                ordinal++;

                DoubleValuesSource dvs = expr.getDoubleValuesSource(bindings);
                DoubleValues values = dvs.getValues(null, null);
                values.advanceExact(doc);
                d.setFeatureScore(ordinal, (float) values.doubleValue());
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

            FVHolder holder = new FVHolder();
            Bindings bindings = new Bindings(){
              @Override
              public DoubleValuesSource getDoubleValuesSource(String name) {
                  return new FVDoubleValuesSource(holder, RankerQuery.this.features.featureOrdinal(name));
              }
            };

            for (Weight weight : weights) {
                Scorer scorer = weight.scorer(context);
                if (scorer == null) {
                    scorer = new NoopScorer(this, DocIdSetIterator.empty());
                }
                scorers.add(scorer);
                subIterators.add(scorer.iterator());
            }

            for(Expression expr: expressions) {
                DoubleValuesSource src = expr.getDoubleValuesSource(bindings);
                DoubleValues values = src.getValues(context, null);
                DocIdSetIterator iterator = DocIdSetIterator.all(context.reader().maxDoc());
                scorers.add(new DValScorer(this, iterator, values));
                subIterators.add(iterator);
            }

            NaiveDisjunctionDISI rankerIterator = new NaiveDisjunctionDISI(DocIdSetIterator.all(context.reader().maxDoc()), subIterators);
            return new RankerScorer(scorers, rankerIterator, holder);
        }

        class RankerScorer extends Scorer {
            /**
             * NOTE: Switch to ChildScorer and {@link #getChildren()} if it appears
             * to be useful for logging
             */
            private final List<Scorer> scorers;
            private final NaiveDisjunctionDISI iterator;
            private final FVHolder featureVector;

            RankerScorer(List<Scorer> scorers, NaiveDisjunctionDISI iterator, FVHolder fvholder) {
                super(RankerWeight.this);
                this.scorers = scorers;
                this.iterator = iterator;
                this.featureVector = fvholder;
            }

            @Override
            public int docID() {
                return iterator.docID();
            }

            @Override
            public float score() throws IOException {
                featureVector.vector = ranker.newFeatureVector(featureVector.vector);
                int ordinal = -1;
                // a DisiPriorityQueue could help to avoid
                // looping on all scorers

                for (Scorer scorer : scorers) {
                    ordinal++;
                    // FIXME: Probably inefficient, again we loop over all scorers..
                    if (scorer.docID() == docID()) {
                        float score = scorer.score();
                        // XXX: bold assumption that all models are dense
                        // do we need a some indirection to infer the featureId?
                        featureVector.vector.setFeatureScore(ordinal, score);
                    }
                }

                return ranker.score(featureVector.vector);
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

    static class FVDoubleValuesSource extends DoubleValuesSource {
        private final int ordinal;
        private final FVHolder fvholder;

        FVDoubleValuesSource(FVHolder fvholder, int ordinal) {
            this.fvholder = fvholder;
            this.ordinal = ordinal;
        }

        @Override
        public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
            return new DoubleValues() {
                @Override
                public double doubleValue() throws IOException {
                    assert fvholder.vector != null;
                    return fvholder.vector.getFeatureScore(ordinal);
                }

                @Override
                public boolean advanceExact(int doc) throws IOException {
                    return true;
                }
            };
        }

        /**
         * Return true if document scores are needed to calculate values
         */
        @Override
        public boolean needsScores() {
            return true;
        }
    }

    static class DValScorer extends Scorer {
        private final DocIdSetIterator iterator;
        private final DoubleValues values;

        DValScorer(Weight weight, DocIdSetIterator iterator, DoubleValues values) {
            super(weight);
            this.iterator = iterator;
            this.values = values;
        }

        @Override
        public int docID() {
            return iterator.docID();
        }

        @Override
        public float score() throws IOException {
            values.advanceExact(docID());
            return (float) values.doubleValue();
        }

        /**
         * Returns the freq of this Scorer on the current document
         */
        @Override
        public int freq() throws IOException {
            return 1;
        }

        @Override
        public DocIdSetIterator iterator() {
            return iterator;
        }
    }

    static class FVHolder {
        LtrRanker.FeatureVector vector;
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
