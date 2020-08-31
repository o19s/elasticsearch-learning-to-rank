package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper;
import com.o19s.es.explore.StatisticsHelper.AggrType;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.TermState;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.similarities.ClassicSimilarity;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


public class TermStatSupplier extends AbstractMap<String, ArrayList<Float>>  {
    private final List<String> ACCEPTED_KEYS = Arrays.asList(new String[]{"df", "idf", "tf", "ttf", "tp"});
    private AggrType posAggrType = AggrType.AVG;

    private ClassicSimilarity sim;
    private StatisticsHelper df_stats, idf_stats, tf_stats, ttf_stats, tp_stats;

    public TermStatSupplier() {
        this.sim = new ClassicSimilarity();
        this.df_stats = new StatisticsHelper();
        this.idf_stats = new StatisticsHelper();
        this.tf_stats = new StatisticsHelper();
        this.ttf_stats = new StatisticsHelper();
        this.tp_stats = new StatisticsHelper();
    }

    public void bump (IndexSearcher searcher, LeafReaderContext context,
                      int docID, Set<Term> terms,
                      ScoreMode scoreMode) throws IOException {
        df_stats.getData().clear();
        idf_stats.getData().clear();
        tf_stats.getData().clear();
        ttf_stats.getData().clear();
        tp_stats.getData().clear();

        PostingsEnum postingsEnum = null;
        for (Term term : terms) {
            if (docID == DocIdSetIterator.NO_MORE_DOCS) {
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

            // Verify document is in postings
            if (postingsEnum.advance(docID) == docID){
                tf_stats.add(postingsEnum.freq());

                if(postingsEnum.freq() > 0) {
                    StatisticsHelper positions = new StatisticsHelper();
                    for (int i = 0; i < postingsEnum.freq(); i++) {
                        positions.add((float) postingsEnum.nextPosition() + 1);
                    }
                    // TODO: Can we return an array of arrays for the ScriptFeature injection usage?
                    tp_stats.add(positions.getAggr(posAggrType));
                } else {
                    tp_stats.add(0.0f);
                }
            // If document isn't in postings default to 0 for tf/tp
            } else {
                tf_stats.add(0.0f);
                tp_stats.add(0.0f);
            }
        }
    }

    /**
     * Returns {@code true} if this map contains a mapping for the specified
     * stat type;
     *
     * @param statType Stat type to retrieve from the supplier
     * @return {@code true} if this map contains a mapping for the specified
     * stat type
     * @throws ClassCastException if the key is of an inappropriate type for
     *                            this map
     */
    @Override
    public boolean containsKey(Object statType) {
        return ACCEPTED_KEYS.contains(statType);
    }

    /**
     * Returns the score to which the specified featureName is mapped,
     * or {@code null} if this map contains no mapping for the featureName.
     *
     * @param statType Stat type to retrieve from the supplier
     * @return the score to which the specified stat type is mapped, or
     * {@code null} if this map contains no mapping for the key
     */
    @Override
    public ArrayList<Float> get(Object statType) {
        String key = (String) statType;

        switch(key) {
            case "df":
                return df_stats.getData();

            case "idf":
                return idf_stats.getData();

            case "tf":
                return tf_stats.getData();

            case "ttf":
                return ttf_stats.getData();

            case "tp":
                return tp_stats.getData();

            default:
                throw new IllegalArgumentException("Unsupported key requested: " + key);
        }
    }

    /**
     * Strictly speaking the only methods of this {@code Map} needed are
     * containsKey and get. The remaining methods help fix issues like
     * - deserialization of FEATURE_VECTOR parameter as a Map object
     * - keeps editors like intellij happy when debugging e.g. providing introspection into Map object.
     */

    @Override
    public Set<Entry<String, ArrayList<Float>>> entrySet() {
        return new AbstractSet<Entry<String, ArrayList<Float>>>() {
            @Override
            public Iterator<Entry<String, ArrayList<Float>>> iterator() {
                return new Iterator<Entry<String, ArrayList<Float>>>() {
                    private int index;

                    @Override
                    public boolean hasNext() {
                        return index < ACCEPTED_KEYS.size();
                    }

                    @Override
                    public Entry<String, ArrayList<Float>> next() {
                        switch (index++) {
                            case 0:
                                return new SimpleImmutableEntry<>("df", df_stats.getData());
                            case 1:
                                return new SimpleImmutableEntry<>("idf", df_stats.getData());
                            case 2:
                                return new SimpleImmutableEntry<>("tf", df_stats.getData());
                            case 3:
                                return new SimpleImmutableEntry<>("ttf", df_stats.getData());
                            case 4:
                                return new SimpleImmutableEntry<>("tp", df_stats.getData());

                            default:
                                return null;
                        }
                    }
                };
            }

            @Override
            public int size() {
                // All stats objects will be the same size
                return idf_stats.getData().size();
            }
        };
    }

    public void setPosAggr(AggrType type) {
        this.posAggrType = type;
    }
}

