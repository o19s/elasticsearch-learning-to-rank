package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper;
import com.o19s.es.explore.StatisticsHelper.AggrType;
import org.apache.lucene.expressions.Bindings;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;

import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TermStatScorer extends Scorer {
    private final DocIdSetIterator iter;
    private final Expression compiledExpression;

    private AggrType aggr;
    private AggrType posAggr;

    private final LeafReaderContext context;
    private final IndexSearcher searcher;
    private final Set<Term> terms;
    private final ScoreMode scoreMode;
    private final Map<Term, TermStates> termContexts;

    public TermStatScorer(TermStatQuery.TermStatWeight weight,
                          IndexSearcher searcher,
                          LeafReaderContext context,
                          Expression compiledExpression,
                          Set<Term> terms,
                          ScoreMode scoreMode,
                          AggrType aggr,
                          AggrType posAggr,
                          Map<Term, TermStates> termContexts) {
        super(weight);
        this.context = context;
        this.compiledExpression = compiledExpression;
        this.searcher = searcher;
        this.terms = terms;
        this.scoreMode = scoreMode;
        this.aggr = aggr;
        this.posAggr = posAggr;
        this.termContexts = termContexts;

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
        TermStatSupplier tsq = new TermStatSupplier();

        // Refresh the term stats
        tsq.setPosAggr(posAggr);
        tsq.bump(searcher, context, docID(), terms, scoreMode, termContexts);

        // Prepare computed statistics
        StatisticsHelper computed = new StatisticsHelper();
        HashMap<String, Float> termStatDict = new HashMap<>();
        Bindings bindings = new Bindings(){
            @Override
            public DoubleValuesSource getDoubleValuesSource(String name) {
                return DoubleValuesSource.constant(termStatDict.get(name));
            }
        };

        // If no values found return 0
        if (tsq.size() == 0) {
            return 0.0f;
        }

        for(int i = 0; i < tsq.size(); i++) {
            // Update the term stat dictionary for the current term
            termStatDict.put("df", tsq.get("df").get(i));
            termStatDict.put("idf", tsq.get("idf").get(i));
            termStatDict.put("tf", tsq.get("tf").get(i));
            termStatDict.put("tp", tsq.get("tp").get(i));
            termStatDict.put("ttf", tsq.get("ttf").get(i));
            termStatDict.put("matches", (float) tsq.getMatchedTermCount());
            termStatDict.put("unique", (float) terms.size());

            // Run the expression and store the result in computed
            DoubleValuesSource dvSrc = compiledExpression.getDoubleValuesSource(bindings);
            DoubleValues values = dvSrc.getValues(context, null);

            values.advanceExact(docID());
            computed.add((float) values.doubleValue());
        }

        return computed.getAggr(aggr);
    }

    @Override
    public int docID() {
        return iter.docID();
    }
}
