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

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ExplorerScorer extends Scorer {
    private Scorer subScorer;
    private String type;
    private LeafReaderContext context;

    protected ExplorerScorer(Weight weight, LeafReaderContext context, String type, Scorer subScorer) {
        super(weight);
        this.context = context;
        this.type = type;
        this.subScorer = subScorer;
    }

    @Override
    public float score() throws IOException {
        Set<Term> terms = new HashSet<Term>();
        weight.extractTerms(terms);

        // No stats if no terms
        if(terms.size() == 0) return 0.0f;

        StatisticsHelper tf_stats = new StatisticsHelper();

        // Grab freq from subscorer, or the children if available
        if(subScorer.getChildren().size() > 0) {
            for(ChildScorer child : subScorer.getChildren()) {
                if(child.child.docID() == docID()){
                    tf_stats.add(subScorer.freq());
                }
            }
        } else {
            if(subScorer.docID() == docID()) {
                tf_stats.add(subScorer.freq());
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
            default:
                throw new RuntimeException("Invalid stat type specified.");
        }

        return retval;
    }

    @Override
    public int docID() {
        return subScorer.docID();
    }

    @Override
    public int freq() throws IOException {
        return subScorer.freq();
    }

    @Override
    public DocIdSetIterator iterator() {
        return subScorer.iterator();
    }
}
