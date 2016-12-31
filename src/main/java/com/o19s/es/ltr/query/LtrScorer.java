/*
 * Copyright [2016] Doug Turnbull
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
package com.o19s.es.ltr.query;

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.learning.Ranker;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.List;

/**
 * Created by doug on 12/24/16.
 */
public class LtrScorer extends Scorer {

    Ranker _rankModel;
    List<Scorer> _subScorers;
    DocIdSetIterator _allDocsIter;

    protected LtrScorer(Weight weight, List<Scorer> subScorers, boolean needsScores,
                        LeafReaderContext context, Ranker rankModel) {
        super(weight);
        this._rankModel = rankModel;
        _subScorers = subScorers;
        _allDocsIter = DocIdSetIterator.all(context.reader().maxDoc());
    }


    @Override
    public float score() throws IOException {
        DataPoint allScores = new DenseProgramaticDataPoint(_subScorers.size());
        int featureIdx = 1; // RankLib is 1-based
        for (Scorer scorer : _subScorers) {
            if (scorer.docID() < docID()) {
                scorer.iterator().advance(docID());
            }
            float featureVal = 0.0f;
            if (scorer.docID() == docID()) {
                featureVal = scorer.score();
            }
            //System.out.printf("Doc %d, feature %d, val %f\n", docID(), featureIdx, featureVal);
            allScores.setFeatureValue(featureIdx, featureVal);
            featureIdx++;
        }
        float score = (float)_rankModel.eval(allScores);
        //System.out.printf("Doc %d, score %f\n", docID(), score);
        return score;
    }

    @Override
    public int docID() {
        return _allDocsIter.docID();
    }

    @Override
    public int freq() throws IOException {
        return 1;
    }

    @Override
    public DocIdSetIterator iterator() {
        return _allDocsIter;
    }
}
