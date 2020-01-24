/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.query;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.expressions.Bindings;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class DerivedExpressionQuery extends Query implements LtrRewritableQuery {
    private final FeatureSet features;
    private final Expression expression;
    private final Map<String, Double> queryParamValues;

    public DerivedExpressionQuery(FeatureSet features, Expression expr, Map<String, Double> queryParamValues) {
        this.features = features;
        this.expression = expr;
        this.queryParamValues = queryParamValues;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!sameClassAs(obj)) {
            return false;
        }
        DerivedExpressionQuery that = (DerivedExpressionQuery) obj;
        return Objects.deepEquals(expression, that.expression)
                && Objects.deepEquals(features, that.features)
                && Objects.deepEquals(queryParamValues, that.queryParamValues);
    }

    @Override
    public Query ltrRewrite(LtrRewriteContext context) {
        return new FVDerivedExpressionQuery(this, context.getFeatureVectorSupplier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, features, queryParamValues);
    }

    @Override
    public String toString(String field) {
        return (field != null ? field : "") + ":fv_query(" + expression.sourceText + ")";
    }

    static final class FVDerivedExpressionQuery extends Query {
        private final DerivedExpressionQuery query;
        private final Supplier<LtrRanker.FeatureVector> fvSupplier;

        FVDerivedExpressionQuery(DerivedExpressionQuery query, Supplier<LtrRanker.FeatureVector> fvSupplier) {
            this.query = query;
            this.fvSupplier = fvSupplier;
        }

        @Override
        public String toString(String field) {
            return query.toString();
        }

        @Override
        public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
            if (!scoreMode.needsScores()) {
                // If scores are not needed simply return a constant score on all docs
                return new ConstantScoreWeight(this.query, boost) {
                    @Override
                    public boolean isCacheable(LeafReaderContext ctx) {
                        return true;
                    }

                    @Override
                    public Scorer scorer(LeafReaderContext context) throws IOException {
                        return new ConstantScoreScorer(this, score(),
                            scoreMode, DocIdSetIterator.all(context.reader().maxDoc()));
                    }
                };
            }

            return new FVWeight(this);
        }

        @Override
        public boolean equals(Object obj) {
            assert false;
            // Should not be called as it is likely an indication that it'll be cached but should not...
            return sameClassAs(obj) &&
                    Objects.equals(this.query, ((FVDerivedExpressionQuery)obj).query) &&
                    Objects.equals(this.fvSupplier, ((FVDerivedExpressionQuery)obj).fvSupplier);
        }

        @Override
        public int hashCode() {
            assert false;
            // Should not be called as it is likely an indication that it'll be cached but should not...
            return Objects.hash(classHash(), query, fvSupplier);
        }
    }

    static class FVWeight extends Weight {
        private final FeatureSet features;
        private final Expression expression;
        private final Supplier<LtrRanker.FeatureVector> vectorSupplier;
        private final Map<String, Double> queryParamValues;

        FVWeight(FVDerivedExpressionQuery query) {
            super(query.query);
            features = query.query.features;
            expression = query.query.expression;
            queryParamValues = query.query.queryParamValues;
            vectorSupplier = query.fvSupplier;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            // No-op
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
            Bindings bindings = new Bindings(){
                @Override
                public DoubleValuesSource getDoubleValuesSource(String name) {
                    Double queryParamValue  = queryParamValues.get(name);
                    if (queryParamValue != null) {
                        return DoubleValuesSource.constant(queryParamValue);
                    }
                    return new FVDoubleValuesSource(vectorSupplier, features.featureOrdinal(name));
                }
            };

            DocIdSetIterator iterator = DocIdSetIterator.all(context.reader().maxDoc());
            DoubleValuesSource src = expression.getDoubleValuesSource(bindings);
            DoubleValues values = src.getValues(context, null);

            return new DValScorer(this, iterator, values);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Bindings bindings = new Bindings(){
                @Override
                public DoubleValuesSource getDoubleValuesSource(String name) {
                    return new FVDoubleValuesSource(vectorSupplier, features.featureOrdinal(name));
                }
            };

            DoubleValuesSource src = expression.getDoubleValuesSource(bindings);
            DoubleValues values = src.getValues(context, null);
            values.advanceExact(doc);
            return Explanation.match((float) values.doubleValue(), "Evaluation of derived expression: " + expression.sourceText);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return false;
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

    static class FVDoubleValuesSource extends DoubleValuesSource {
        private final int ordinal;
        private final Supplier<LtrRanker.FeatureVector> vectorSupplier;

        FVDoubleValuesSource(Supplier<LtrRanker.FeatureVector> vectorSupplier, int ordinal) {
            this.vectorSupplier = vectorSupplier;
            this.ordinal = ordinal;
        }

        @Override
        public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
            return new DoubleValues() {
                @Override
                public double doubleValue() throws IOException {
                    assert vectorSupplier.get() != null;
                    return vectorSupplier.get().getFeatureScore(ordinal);
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

        @Override
        public DoubleValuesSource rewrite(IndexSearcher reader) throws IOException {
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FVDoubleValuesSource that = (FVDoubleValuesSource) o;
            return ordinal == that.ordinal &&
                    Objects.equals(vectorSupplier, that.vectorSupplier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ordinal, vectorSupplier);
        }

        @Override
        public String toString() {
            return "FVDoubleValuesSource{" +
                    "ordinal=" + ordinal +
                    ", vectorSupplier=" + vectorSupplier +
                    '}';
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return false;
        }
    }
}
