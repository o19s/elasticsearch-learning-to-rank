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

import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.elasticsearch.common.logging.Loggers;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ExplorerScorer extends Scorer {
    private Scorer subscorer;
    private String field, type;
    private LeafReaderContext context;

    protected ExplorerScorer(Weight weight, LeafReaderContext context, String field, String type, Scorer subscorer) {
        super(weight);
        this.context = context;
        this.field = field;
        this.type = type;
        this.subscorer = subscorer;
    }

    @Override
    public float score() throws IOException {
        Set<Term> terms = new HashSet<Term>();
        weight.extractTerms(terms);

        // No stats if no terms
        if(terms.size() == 0) return 0.0f;

        Terms termVector = context.reader().getTermVector(docID(), field);

        // No stats are available if no term vectors...
        if(termVector == null) return 0.0f;

        TermsEnum itr = termVector.iterator();
        PostingsEnum postings = null;

        ClassicSimilarity sim = new ClassicSimilarity();
        StatisticsHelper tf_stats = new StatisticsHelper();
        StatisticsHelper idf_stats = new StatisticsHelper();

        for (Term term : terms) {
            boolean found = itr.seekExact(term.bytes());

            if(found) {
                postings = itr.postings(postings, PostingsEnum.FREQS);
                postings.nextDoc();
                tf_stats.add(postings.freq());
                idf_stats.add(sim.idf(context.reader().docFreq(term), context.reader().numDocs()));
            } else {
                tf_stats.add(0);
                idf_stats.add(0);
            }
        }

        float retval = 0.0f;
        switch(type) {
            case("sum_raw_tf"):
                retval = tf_stats.getSum();
                break;
            case("mean_raw_tf"):
                retval = tf_stats.getMean();
                break;
            case("max_raw_tf"):
                retval = tf_stats.getMax();
                break;
            case("min_raw_tf"):
                retval = tf_stats.getMin();
                break;
            case("stddev_raw_tf"):
                retval = tf_stats.getStdDev();
                break;
            case("sum_classic_idf"):
                retval = idf_stats.getSum();
                break;
            case("mean_classic_idf"):
                retval = idf_stats.getMean();
                break;
            case("max_classic_idf"):
                retval = idf_stats.getMax();
                break;
            case("min_classic_idf"):
                retval = idf_stats.getMin();
                break;
            case("stddev_classic_idf"):
                retval = idf_stats.getStdDev();
                break;
        }

        return retval;
    }

    @Override
    public int docID() {
        return subscorer.docID();
    }

    @Override
    public int freq() throws IOException {
        return subscorer.freq();
    }

    @Override
    public DocIdSetIterator iterator() {
        return subscorer.iterator();
    }
}
