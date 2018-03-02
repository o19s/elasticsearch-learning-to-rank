package com.o19s.es.ltr.query;

import com.o19s.es.ltr.ranker.FeatureMatrix;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.utils.Suppliers;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Scorer;
import org.elasticsearch.common.CheckedFunction;

import java.io.IOException;
import java.util.List;

public class FeatureMatrixCollector {
    private final ScoreDoc[] docs;
    private final IndexReader reader;
    private final int windowSize;
    private List<Scorer> scorers;
    // Number of docs in the initial ScoreDoc[] that belongs to the current segment
    private int docsInSegment;
    // Number of docs in the initial ScoreDoc[] that we already scored in this segment
    private int docsReadInSegment;
    private int current;
    private LeafReaderContext currentLeaf;
    private int readerMaxDoc;
    private CheckedFunction<LeafReaderContext, List<Scorer>, IOException> weight;
    private Suppliers.MutableSupplier<LtrRanker.FeatureVector> mutableSupplier;

    public FeatureMatrixCollector(RankerQuery.RankerWeight weight,
                                  IndexReader reader, ScoreDoc[] allDocs, int windowSize) {
        this.weight = weight;
        this.docs = allDocs;
        assert assertOrdered();
        this.reader = reader;
        assert windowSize <= docs.length;
        this.windowSize = windowSize;
        this.mutableSupplier = weight.getFeatureVectorSupplier();
    }

    private boolean assertOrdered() {
        int last = -1;
        for (int i = 0; i < windowSize; i++) {
            if (last > this.docs[i].doc) {
                return false;
            }
            last = this.docs[i].doc;
        }
        return true;
    }

    private void maybeNextSegment() throws IOException {
        assert current < windowSize && current < docs.length;
        int doc = docs[current].doc;
        if (doc < readerMaxDoc) return;
        currentLeaf = reader.leaves().get(ReaderUtil.subIndex(doc, reader.leaves()));
        readerMaxDoc = currentLeaf.docBase + currentLeaf.reader().maxDoc();
        scorers = weight.apply(currentLeaf);
        int i = current;
        for (; i < windowSize && docs[i].doc < readerMaxDoc; i++);
        docsInSegment = i - current;
        docsReadInSegment = 0;
    }

    public int collect(FeatureMatrix matrix) throws IOException {
        int matrixBase = 0;
        while (current < windowSize) {
            maybeNextSegment();
            int remainingDocsInSegment = docsInSegment - docsReadInSegment;
            int matrixCap = matrix.docSize() - matrixBase;
            int docsToRead = Math.min(matrixCap, remainingDocsInSegment);
            int maxDocToScore = current + docsToRead;
            for (int f = 0; f < scorers.size(); f++) {
                collectCurrentSegment(f, matrix, matrixBase, maxDocToScore);
            }
            current += docsToRead;
            docsReadInSegment += docsToRead;
            matrixBase += docsToRead;
            if (matrixCap == docsToRead) {
                // Matrix full
                break;
            }
        }
        return matrixBase;
    }

    private void collectCurrentSegment(int fIdx, FeatureMatrix matrix, int matrixBase, int maxDoc) throws IOException {
        Scorer scorer = scorers.get(fIdx);
        if (scorer == null) {
            return;
        }
        DocIdSetIterator iterator = scorer.iterator();
        // Keep two indices that increment in parallel:
        // i: the source in docs
        // matrixDoc: the target in our feature matrix
        for (int matrixDoc = matrixBase, i = current; i < maxDoc; i++, matrixDoc++) {
            // TODO: It might interesing to detect and split features that are dependent on others
            // so that we maintain the the featureVector only for them.
            mutableSupplier.set(matrix.vector(matrixDoc));
            int doc = docs[i].doc - currentLeaf.docBase;
            int scorerDocId = iterator.docID();
            if (doc > scorerDocId) {
                scorerDocId = iterator.advance(doc);
            }
            if (doc == scorerDocId) {
                matrix.setFeatureScoreForDoc(matrixDoc, fIdx, scorer.score());
            } else if (scorerDocId == DocIdSetIterator.NO_MORE_DOCS) {
                return;
            }
        }
    }
}
