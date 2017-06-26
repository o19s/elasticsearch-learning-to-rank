/*
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
package com.o19s.es.explore;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

public class ExplorerQuery extends Query {
    private final Query query;
    private final String field, type;

    public ExplorerQuery(Query query, String field, String type) {
        this.query = query;
        this.field = field;
        this.type = type;
    }

    public Query getQuery() { return this.query; }
    public String getField() { return this.field; }
    public String getType() { return this.type; }

    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) &&
                equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(ExplorerQuery other) {
        return Objects.equals(query, other.query)
                && Objects.equals(field, other.field)
                && Objects.equals(type, other.type);
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        Query rewritten = query.rewrite(reader);
        if(rewritten != query) {
            return new ExplorerQuery(rewritten, field, type);
        }
        return query;
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, field, type);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        return new ExplorerQuery.ExplorerWeight(searcher, needsScores);
    }

    protected class ExplorerWeight extends Weight {
        protected final Weight weight;

        protected ExplorerWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
            super(ExplorerQuery.this);
            weight = searcher.createWeight(ExplorerQuery.this, false);
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            weight.extractTerms(terms);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = scorer(context);

            if (scorer != null) {
                int newDoc = scorer.iterator().advance(doc);
                if (newDoc == doc) {
                    return Explanation.match(
                            scorer.score(),
                            "Stat Score: " + type + " of " + field);
                }
            }
            return Explanation.noMatch("no matching term");
        }

        @Override
        public float getValueForNormalization() throws IOException {
            return 1.0f;
        }

        @Override
        public void normalize(float norm, float boost) {

        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
            Scorer subscorer = weight.scorer(context);
            return new ExplorerScorer(weight, context, field, type, subscorer);
        }
    }

    public String toString(String field) {
        return query.toString();
    };



}
