package com.o19s.es.ltr;

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.learning.DenseDataPoint;
import ciir.umass.edu.learning.Ranker;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DisiWrapper;
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

    protected LtrScorer(Weight weight, List<Scorer> subScorers, boolean needsScores, LeafReaderContext context, Ranker rankModel) {
        super(weight);
        this._rankModel = rankModel;
        _subScorers = subScorers;
        _allDocsIter = DocIdSetIterator.all(context.reader().maxDoc());
    }


    @Override
    public float score() throws IOException {
        DataPoint allScores = new DenseDataPoint("");
        int featureIdx = 0;
        for (Scorer scorer : _subScorers) {
            allScores.setFeatureValue(0, scorer.score());
        }

//        for (DisiWrapper w = topList; w != null; w = w.next) {
//            // Ugh, need to remap back to the original because here they're out of order
//            final float subScore = w.scorer.score();
//            scoreSum += subScore;
//            if (subScore > scoreMax) {
//                scoreMax = subScore;
//            }
//        }
//        return scoreMax + (scoreSum - scoreMax);
//        allScores.set
        return (float)_rankModel.eval(allScores);
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
