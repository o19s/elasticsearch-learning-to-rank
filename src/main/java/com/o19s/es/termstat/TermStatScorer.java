package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.ClassicSimilarity;

import java.io.IOException;
import java.util.Set;

public class TermStatScorer extends Scorer {
    private final DocIdSetIterator iter;

    private final LeafReaderContext context;
    private final IndexSearcher searcher;
    private final Set<Term> terms;
    private final ScoreMode scoreMode;

    public TermStatScorer(Weight weight, IndexSearcher searcher, LeafReaderContext context, Set<Term> terms, ScoreMode scoreMode) {
        super(weight);
        this.context = context;
        this.searcher = searcher;
        this.terms = terms;
        this.scoreMode = scoreMode;

        // TODO: This should be limited to doc ID's from a filter query or passed in values
        this.iter = DocIdSetIterator.all(context.reader().maxDoc());
    }
    @Override
    public DocIdSetIterator iterator() {
        return iter;
    }

    @Override
    public float getMaxScore(int upTo) throws IOException {
        return Float.POSITIVE_INFINITY;
    }

    @Override
    public float score() throws IOException {
        /*
            TODO: DLW Braindump 5/12/2020

            At this point we have a set of terms.  We need to build a TermStates for each term
            and aggregate the data. It probably makes sense to make a helper class that holds
            all of the data and supports a "compute" method for the final score.

            Compute would fill a new array with the output of a lucene expression.
            The final step would be to take the min/max/avg of the compute list.
        */
        ClassicSimilarity sim = new ClassicSimilarity();
        StatisticsHelper df_stats = new StatisticsHelper();
        StatisticsHelper idf_stats = new StatisticsHelper();
        StatisticsHelper ttf_stats = new StatisticsHelper();
        StatisticsHelper tf_stats = new StatisticsHelper();
        StatisticsHelper tp_stats = new StatisticsHelper();

        PostingsEnum postingsEnum = null;
        for (Term term : this.terms) {
            if (docID() == DocIdSetIterator.NO_MORE_DOCS) {
                break;
            }

            TermStates termStates = TermStates.build(searcher.getTopReaderContext(), term, scoreMode.needsScores());

            assert termStates != null && termStates
                    .wasBuiltFor(ReaderUtil.getTopLevelContext(context));

            TermState state = termStates.get(context);

            if (state == null) {
                continue;
            }

            // Collection Statistics
            df_stats.add(termStates.docFreq());
            idf_stats.add(sim.idf(termStates.docFreq(), searcher.getIndexReader().numDocs()));
            ttf_stats.add(termStates.totalTermFreq());

            // Doc specifics
            TermsEnum termsEnum = context.reader().terms(term.field()).iterator();
            termsEnum.seekExact(term.bytes(), state);
            postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.ALL);
            postingsEnum.advance(docID());
            tf_stats.add(postingsEnum.freq());

            if(postingsEnum.freq() > 0) {
                StatisticsHelper positions = new StatisticsHelper();
                for (int i = 0; i < postingsEnum.freq(); i++) {
                    positions.add((float) postingsEnum.nextPosition() + 1);
                }
                tp_stats.add(positions.getMean());
            } else {
                // TODO: Revisit this behavior
                tp_stats.add(0.0f);
            }
        }

        // TODO: The lucene expression "compute" needs to happen here.
        final float constantScore = tp_stats.getMean();

        return constantScore;
    }

    @Override
    public int docID() {
        return iter.docID();
    }
}
