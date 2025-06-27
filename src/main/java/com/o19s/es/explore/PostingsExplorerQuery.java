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

package com.o19s.es.explore;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.IOSupplier;
import org.elasticsearch.core.CheckedFunction;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.DocIdSetIterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class PostingsExplorerQuery extends Query {
    private final Term term;
    private final Type type;

    PostingsExplorerQuery(Term term, Type type) {
        this.term = Objects.requireNonNull(term);
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public String toString(String field) {
        StringBuilder buffer = new StringBuilder("postings_explorer(");
        buffer.append(type.name()).append(", ");
        if (!this.term.field().equals(field)) {
            buffer.append(this.term.field());
            buffer.append(":");
        }

        buffer.append(this.term.text());
        buffer.append(")");
        return buffer.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return this.sameClassAs(obj)
                && this.term.equals(((PostingsExplorerQuery) obj).term)
                && this.type.equals(((PostingsExplorerQuery) obj).type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classHash(), this.term, this.type);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
            throws IOException {
        assert scoreMode.needsScores() : "Should not be used in filtering mode";
        return new PostingsExplorerWeight(this, this.term, TermStates.build(searcher, this.term,
                scoreMode.needsScores()),
                this.type);
    }

    /**
     * Will eventually allow implementing more explorer techniques (e.g. some stats
     * on positions)
     */
    enum Type implements CheckedFunction<TermsEnum, Scorer, IOException> {
        // Extract TF from the postings
        TF((terms) -> new TFScorer(terms.postings(null, PostingsEnum.FREQS))),
        TP((terms) -> new TPScorer(terms.postings(null, PostingsEnum.POSITIONS)));

        private final CheckedFunction<TermsEnum, Scorer, IOException> func;

        Type(CheckedFunction<TermsEnum, Scorer, IOException> func) {
            this.func = func;
        }

        @Override
        public Scorer apply(TermsEnum termsEnum) throws IOException {
            return func.apply(termsEnum);
        }
    }

    static class PostingsExplorerWeight extends Weight {
        private final Term term;
        private final TermStates termStates;
        private final Type type;

        PostingsExplorerWeight(Query query, Term term, TermStates termStates, Type type) {
            super(query);
            this.term = term;
            this.termStates = termStates;
            this.type = type;
        }

        public void extractTerms(Set<Term> terms) {
            terms.add(term);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = this.scorer(context);
            int newDoc = scorer.iterator().advance(doc);
            if (newDoc == doc) {
                return Explanation
                        .match(scorer.score(), "weight(" + this.getQuery() + " in doc " + newDoc + ")");
            }
            return Explanation.noMatch("no matching term");
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }

        @Override
        public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
            assert this.termStates != null && this.termStates
                    .wasBuiltFor(ReaderUtil.getTopLevelContext(context));
            IOSupplier<TermState> stateSupplier = this.termStates.get(context);
            if (stateSupplier == null) {
                return null;
            } else {
                TermState state = stateSupplier.get();
                if (state == null) {
                    return new Weight.DefaultScorerSupplier(null);
                }
                TermsEnum terms = context.reader().terms(this.term.field()).iterator();
                terms.seekExact(this.term.bytes(), state);
                return new Weight.DefaultScorerSupplier(this.type.apply(terms));
            }
        }
    }

    public abstract static class PostingsExplorerScorer extends Scorer {
        final PostingsEnum postingsEnum;
        protected String typeConditional;

        PostingsExplorerScorer(PostingsEnum postingsEnum) {
            this.postingsEnum = postingsEnum;
        }

        public void setType(String type) {
            this.typeConditional = type;
        }

        @Override
        public int docID() {
            return this.postingsEnum.docID();
        }

        @Override
        public DocIdSetIterator iterator() {
            return this.postingsEnum;
        }
    }

    static class TFScorer extends PostingsExplorerScorer {
        TFScorer(PostingsEnum postingsEnum) {
            super(postingsEnum);
        }

        @Override
        public float score() throws IOException {
            return this.postingsEnum.freq();
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

    static class TPScorer extends PostingsExplorerScorer {
        TPScorer(PostingsEnum postingsEnum) {
            super(postingsEnum);
        }

        @Override
        public float score() throws IOException {
            if (this.postingsEnum.freq() <= 0) {
                return 0.0f;
            }

            ArrayList<Float> positions = new ArrayList<Float>();
            for (int i = 0; i < this.postingsEnum.freq(); i++) {
                positions.add((float) this.postingsEnum.nextPosition() + 1);
            }

            float retval;
            switch (this.typeConditional) {
                case ("avg_raw_tp"):
                    float sum = 0.0f;
                    for (float position : positions) {
                        sum += position;
                    }
                    retval = sum / positions.size();
                    break;
                case ("max_raw_tp"):
                    retval = Collections.max(positions);
                    break;
                case ("min_raw_tp"):
                    retval = Collections.min(positions);
                    break;
                default:
                    retval = 0.0f;
            }

            return retval;
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

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(this.term.field()) != false) {
            visitor.consumeTerms(this, term);
        }
    }
}
