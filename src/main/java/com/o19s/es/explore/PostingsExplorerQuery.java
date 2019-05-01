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

import com.o19s.es.ltr.utils.CheckedBiFunction;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;
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

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
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
        IndexReaderContext context = searcher.getTopReaderContext();
        assert scoreMode.needsScores() : "Should not be used in filtering mode";
        return new PostingsExplorerWeight(this, this.term, TermStates.build(context, this.term,
                scoreMode.needsScores()),
                this.type);
    }

    /**
     * Will eventually allow implementing more explorer techniques (e.g. some stats on positions)
     */
    enum Type implements CheckedBiFunction<Weight, TermsEnum, Scorer, IOException> {
        // Extract TF from the postings
        TF((weight, terms) -> new TFScorer(weight, terms.postings(null, PostingsEnum.FREQS)));

        private final CheckedBiFunction<Weight, TermsEnum, Scorer, IOException> func;

        Type(CheckedBiFunction<Weight, TermsEnum, Scorer, IOException> func) {
            this.func = func;
        }

        @Override
        public Scorer apply(Weight weight, TermsEnum termsEnum) throws IOException {
            return func.apply(weight, termsEnum);
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

        @Override
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
        public Scorer scorer(LeafReaderContext context) throws IOException {
            assert this.termStates != null && this.termStates
                    .wasBuiltFor(ReaderUtil.getTopLevelContext(context));
            TermState state = this.termStates.get(context);
            if (state == null) {
                return null;
            } else {
                TermsEnum terms = context.reader().terms(this.term.field()).iterator();
                terms.seekExact(this.term.bytes(), state);
                return this.type.apply(this, terms);
            }
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }
    }

    public abstract static class PostingsExplorerScorer extends Scorer {
        final PostingsEnum postingsEnum;

        PostingsExplorerScorer(Weight weight, PostingsEnum postingsEnum) {
            super(weight);
            this.postingsEnum = postingsEnum;
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
        TFScorer(Weight weight, PostingsEnum postingsEnum) {
            super(weight, postingsEnum);
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
}
