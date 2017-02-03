package com.o19s.es.ltr.query;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import java.io.IOException;

/**
 * Created by doug on 2/3/17.
 */
public class NoopScorer extends Scorer {
    private DocIdSetIterator _noopIter;
    /**
     * Constructs a Scorer
     *
     * @param weight The scorers <code>Weight</code>.
     */
    protected NoopScorer(Weight weight, int maxDocs) {
        super(weight);
        _noopIter = DocIdSetIterator.all(maxDocs);

    }

    @Override
    public int docID() {
        return _noopIter.docID();
    }

    @Override
    public float score() throws IOException {
        return 0;
    }

    @Override
    public int freq() throws IOException {
        return 0;
    }

    @Override
    public DocIdSetIterator iterator() {
        return _noopIter;
    }
}
