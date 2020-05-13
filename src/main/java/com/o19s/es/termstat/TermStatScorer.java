package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper;
import org.apache.lucene.expressions.Bindings;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.similarities.ClassicSimilarity;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

public class TermStatScorer extends Scorer {
    private final DocIdSetIterator iter;
    private final Expression compiledExpression;

    private final LeafReaderContext context;
    private final IndexSearcher searcher;
    private final Set<Term> terms;
    private final ScoreMode scoreMode;

    public TermStatScorer(TermStatQuery.TermStatWeight weight, IndexSearcher searcher, LeafReaderContext context, Expression compiledExpression, Set<Term> terms, ScoreMode scoreMode) {
        super(weight);
        this.context = context;
        this.compiledExpression = compiledExpression;
        this.searcher = searcher;
        this.terms = terms;
        this.scoreMode = scoreMode;

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
                // TODO: Add modifier support for positions
                tp_stats.add(positions.getMean());
            } else {
                tp_stats.add(0.0f);
            }
        }

        // Prepare computed statistics
        StatisticsHelper computed = new StatisticsHelper();
        HashMap<String, Float> termStatDict = new HashMap<>();
        Bindings bindings = new Bindings(){
            @Override
            public DoubleValuesSource getDoubleValuesSource(String name) {
                return DoubleValuesSource.constant(termStatDict.get(name));
            }
        };

        for(int i = 0; i < tf_stats.getSize(); i++) {
            // Update the term stat dictionary for the current term
            termStatDict.put("df", df_stats.getData().get(i));
            termStatDict.put("idf", idf_stats.getData().get(i));
            termStatDict.put("tf", tf_stats.getData().get(i));
            termStatDict.put("tp", tp_stats.getData().get(i));
            termStatDict.put("ttf", ttf_stats.getData().get(i));

            DoubleValuesSource dvSrc = compiledExpression.getDoubleValuesSource(bindings);
            DoubleValues values = dvSrc.getValues(context, null);

            values.advanceExact(docID());
            computed.add((float) values.doubleValue());
        }

        // TODO: Stat type needs to be a parameter
        return computed.getMean();
    }

    @Override
    public int docID() {
        return iter.docID();
    }
}
