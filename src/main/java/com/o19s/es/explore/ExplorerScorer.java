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

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;

public class ExplorerScorer extends Scorer {
    private final Scorer subScorer;
    private final String type;

    protected ExplorerScorer(Weight weight, String type, Scorer subScorer) {
        super(weight);
        this.type = type;
        this.subScorer = subScorer;
    }

    @Override
    public float score() throws IOException {
        StatisticsHelper tf_stats = new StatisticsHelper();

        // Grab freq from subscorer, or the children if available
        if(subScorer.getChildren().size() > 0) {
            for(ChildScorable child : subScorer.getChildren()) {
                assert child.child instanceof PostingsExplorerQuery.PostingsExplorerScorer;
                if(child.child.docID() == docID()) {
                    tf_stats.add(child.child.score());
                }
            }
        } else {
            assert subScorer instanceof PostingsExplorerQuery.PostingsExplorerScorer;
            assert subScorer.docID() == docID();
            tf_stats.add(subScorer.score());
        }

        float retval;
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
    public DocIdSetIterator iterator() {
        return subScorer.iterator();
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
