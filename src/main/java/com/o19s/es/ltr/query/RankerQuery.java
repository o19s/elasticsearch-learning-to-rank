/*
 * Copyright [2017] Wikimedia Foundation
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lucene query designed to apply a ranking model provided by {@link LtrRanker}
 * This query is not designed for retrieval, in other words it will score
 * all the docs in the index and thus must be used either in a rescore phase
 * or within a BooleanQuery and an appropriate filter clause.
 */
public class RankerQuery extends Query {
    private final Query[] queries;
    private final Feature[] features;
    private final LtrRanker ranker;

    private RankerQuery(Query[] queries, Feature[] features, LtrRanker ranker) {
        assert queries.length == features.length;
        this.queries = queries;
        this.features = features;
        this.ranker = ranker;
    }

    /**
     * Build a RankerQuery based on a prebuilt model.
     * Prebuilt models are not parametrized as they contain only {@link com.o19s.es.ltr.feature.PrebuiltFeature}
     *
     * @param model a prebuilt model
     * @return the lucene query
     */
    public static RankerQuery build(PrebuiltLtrModel model) {
        return build(model, null, Collections.emptyMap());
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
        Feature[] features = model.featureSet().features().toArray(new Feature[model.featureSet().size()]);
        return build(model.ranker(), features, context, params);
    }

    private static RankerQuery build(LtrRanker ranker, Feature[] features, QueryShardContext context, Map<String, Object> params) {
        assert features.length >= ranker.size();
        Query[] queries = new Query[features.length];
        for(int i = 0; i < features.length; i++) {
            queries[i] = features[i].doToQuery(context, params);
        }
        return new RankerQuery(queries, features, ranker);
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        Query[] rewrittenQueries = new Query[queries.length];
        boolean rewritten = false;
        for(int i = 0; i < queries.length; i++) {
            rewrittenQueries[i] = queries[i].rewrite(reader);
            rewritten |= rewrittenQueries[i] != queries[i];
        }
        return rewritten ? new RankerQuery(rewrittenQueries, features, ranker) : this;
    }

    public Feature getFeature(int idx) {
        return features[idx];
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof RankerQuery)) {
            return false;
        }
        RankerQuery other = (RankerQuery) obj;
        return Arrays.deepEquals(queries, other.queries)
                && Arrays.deepEquals(features, other.features)
                && Objects.equals(ranker, other.ranker);
    }

    @Override
    public int hashCode() {
        return Objects.hash(features, queries, ranker);
    }

    @Override
    public String toString(String field) {
        return "rankerquery:"+field;
    }

    @Override
    public RankerWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        Weight[] weights = new Weight[queries.length];
        for(int i = 0; i < weights.length; i++) {
            weights[i] = searcher.createWeight(queries[i], needsScores);
        }
        return new RankerWeight(weights);
    }

    public class RankerWeight extends Weight {
        private final Weight[] weights;

        protected RankerWeight(Weight[] weights) {
            super(RankerQuery.this);
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
            List<Explanation> subs = new ArrayList<>(weights.length);

            LtrRanker.DataPoint d = ranker.newDataPoint();
            for (int i = 0; i < weights.length; i++) {
                Weight weight = weights[i];
                Explanation explain = weight.explain(context, doc);
                String featureString = "Feature " + Integer.toString(i);
                if (features[i].getName() != null) {
                    featureString += "(" + features[i].getName() + ")";
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
                d.setFeatureScore(i, featureVal);
            }
            float modelScore = ranker.score(d);
            return Explanation.match(modelScore, " LtrModel: " + ranker.name() + " using features:", subs);
        }

        @Override
        public float getValueForNormalization() throws IOException {
            // XXX: may produce inconsistent scores if featured queries
            // are run individually and a Similarity that implements queryNorm is used.
            // queryNorm will disappear in lucene 7.
            // Should we hack something or warn if this query is being used with ClassicSimilarity?
            float sum = 0.0f;
            for (Weight w : weights) {
                sum += w.getValueForNormalization();
            }
            return sum ;
        }

        @Override
        public void normalize(float norm, float boost) {
            for(Weight w : weights) {
                w.normalize(norm, boost);
            }
        }

        @Override
        public RankerScorer scorer(LeafReaderContext context) throws IOException {
            RankerChildScorer[] scorers = new RankerChildScorer[weights.length];
            DocIdSetIterator[] subIterators = new DocIdSetIterator[weights.length];
            for(int i = 0; i < weights.length; i++) {
                Scorer scorer = weights[i].scorer(context);
                if (scorer == null) {
                    scorer = new NoopScorer(this, context.reader().maxDoc());
                }
                scorers[i] = new RankerChildScorer(scorer, features[i]);
                subIterators[i] = scorer.iterator();
            }
            DocIdSetIterator rankerIterator = new NaiveDisjunctionDISI(DocIdSetIterator.all(context.reader().maxDoc()), subIterators);
            return new RankerScorer(scorers, rankerIterator);
        }

        class RankerScorer extends Scorer {
            private final List<ChildScorer> scorers;
            private final DocIdSetIterator iterator;
            private final float[] scores;
            private final LtrRanker.DataPoint dataPoint;

            RankerScorer(RankerChildScorer[] scorers, DocIdSetIterator iterator) {
                super(RankerWeight.this);
                this.scorers = Arrays.asList(scorers);
                scores = new float[scorers.length];
                this.iterator = iterator;
                dataPoint = ranker.newDataPoint();
            }

            @Override
            public int docID() {
                return iterator.docID();
            }

            @Override
            public Collection<ChildScorer> getChildren() {
                return scorers;
            }

            @Override
            public float score() throws IOException {
                for (int i = 0; i < scorers.size(); i++) {
                    Scorer scorer = scorers.get(i).child;
                    if (scorer.docID() == docID()) {
                        dataPoint.setFeatureScore(i, scorer.score());
                    } else {
                        dataPoint.setFeatureScore(i, 0);
                    }
                }
                return ranker.score(dataPoint);
            }

            @Override
            public int freq() throws IOException {
                return scores.length;
            }

            @Override
            public DocIdSetIterator iterator() {
                return iterator;
            }
        }
    }

    static class RankerChildScorer extends Scorer.ChildScorer {
        private final Feature feature;

        RankerChildScorer(Scorer scorer, Feature feature) {
            super(scorer, feature.getName());
            this.feature = feature;
        }
    }

    /**
     * Driven by a main iterator and tries to maintain a list of sub iterators
     */
    static class NaiveDisjunctionDISI extends DocIdSetIterator {
        private final DocIdSetIterator main;
        private final DocIdSetIterator[] subIterators;

        NaiveDisjunctionDISI(DocIdSetIterator main, DocIdSetIterator[] subIterators) {
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
            for (int i = 0; i < subIterators.length; i++) {
                DocIdSetIterator iterator = subIterators[i];
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
