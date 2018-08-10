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

import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.utils.Suppliers;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * A weight allowing to inject the FeatureVector in use by the RankerQuery
 */
public abstract class FeatureVectorWeight extends Weight {

    private final Suppliers.MutableSupplierInterface<Supplier<LtrRanker.FeatureVector>> vectorSupplier;

    protected FeatureVectorWeight(Query query, Suppliers.MutableSupplierInterface<Supplier<LtrRanker.FeatureVector>> vectorSupplier) {
        super(query);
        this.vectorSupplier = vectorSupplier;
    }

    @Override
    public final Scorer scorer(LeafReaderContext context) throws IOException {
        throw new UnsupportedOperationException("This Weight cannot work outside the RankerQuery context: " +
                "you must call scorer(context, vectorSupplier)");
    }

    @Override
    public Explanation explain(LeafReaderContext context, int doc) throws IOException {
        throw new UnsupportedOperationException("This Weight cannot work outside the RankerQuery context:" +
                "you must call explain(context, vector, doc)");
    }

    public abstract Explanation explain(LeafReaderContext context, LtrRanker.FeatureVector vector, int doc) throws IOException;

    /**
     * @param context        surrent segment
     * @param vectorSupplier supplier of the feature vector (always call get(), it must not be cached)
     * @return The scorer
     */
    public abstract Scorer scorer(LeafReaderContext context, Supplier<LtrRanker.FeatureVector> vectorSupplier) throws IOException;

    public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
        final Scorer scorer = scorer(context, vectorSupplier.get());
        if (scorer == null) {
            return null;
        }
        return new ScorerSupplier() {
            @Override
            public Scorer get(long leadCost) {
                return scorer;
            }

            @Override
            public long cost() {
                return scorer.iterator().cost();
            }
        };
    }

}
