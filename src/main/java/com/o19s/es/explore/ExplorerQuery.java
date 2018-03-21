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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.ClassicSimilarity;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ExplorerQuery extends Query {
    private final Query query;
    private final String type;

    public ExplorerQuery(Query query, String type) {
        this.query = query;
        this.type = type;
    }

    private boolean isCollectionScoped() {
        return type.endsWith("_count")
                || type.endsWith("_df")
                || type.endsWith("_idf")
                || type.endsWith(("_ttf"));
    }

    public Query getQuery() { return this.query; }
    public String getType() { return this.type; }

    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) &&
                equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(ExplorerQuery other) {
        return Objects.equals(query, other.query)
                && Objects.equals(type, other.type);
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        Query rewritten = query.rewrite(reader);

        if(rewritten != query) {
            return new ExplorerQuery(rewritten, type);
        }

        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, type);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
        if (!needsScores) {
            return searcher.createWeight(query, false, boost);
        }
        final Weight subWeight = searcher.createWeight(query, true, boost);
        Set<Term> terms = new HashSet<>();
        subWeight.extractTerms(terms);
        if(isCollectionScoped()) {
            ClassicSimilarity sim = new ClassicSimilarity();
            StatisticsHelper df_stats = new StatisticsHelper();
            StatisticsHelper idf_stats = new StatisticsHelper();
            StatisticsHelper ttf_stats = new StatisticsHelper();

            for(Term term : terms) {
                TermContext ctx = TermContext.build(searcher.getTopReaderContext(), term);
                TermStatistics tStats = searcher.termStatistics(term, ctx);
                df_stats.add(tStats.docFreq());
                idf_stats.add(sim.idf(tStats.docFreq(), searcher.getIndexReader().numDocs()));
                ttf_stats.add(tStats.totalTermFreq());
            }

            /*
                If no terms are parsed in the query we opt for returning 0
                instead of throwing an exception that could break various
                pipelines.
             */
            float constantScore;

            if(terms.size() > 0) {
                switch (type) {
                    case ("sum_classic_idf"):
                        constantScore = idf_stats.getSum();
                        break;
                    case ("mean_classic_idf"):
                        constantScore = idf_stats.getMean();
                        break;
                    case ("max_classic_idf"):
                        constantScore = idf_stats.getMax();
                        break;
                    case ("min_classic_idf"):
                        constantScore = idf_stats.getMin();
                        break;
                    case ("stddev_classic_idf"):
                        constantScore = idf_stats.getStdDev();
                        break;
                    case "sum_raw_df":
                        constantScore = df_stats.getSum();
                        break;
                    case "min_raw_df":
                        constantScore = df_stats.getMin();
                        break;
                    case "max_raw_df":
                        constantScore = df_stats.getMax();
                        break;
                    case "mean_raw_df":
                        constantScore = df_stats.getMean();
                        break;
                    case "stddev_raw_df":
                        constantScore = df_stats.getStdDev();
                        break;
                    case "sum_raw_ttf":
                        constantScore = ttf_stats.getSum();
                        break;
                    case "min_raw_ttf":
                        constantScore = ttf_stats.getMin();
                        break;
                    case "max_raw_ttf":
                        constantScore = ttf_stats.getMax();
                        break;
                    case "mean_raw_ttf":
                        constantScore = ttf_stats.getMean();
                        break;
                    case "stddev_raw_ttf":
                        constantScore = ttf_stats.getStdDev();
                        break;
                    case "unique_terms_count":
                        constantScore = terms.size();
                        break;

                    default:
                        throw new RuntimeException("Invalid stat type specified.");
                }
            } else {
                constantScore = 0.0f;
            }

            return new ConstantScoreWeight(ExplorerQuery.this, constantScore) {

                @Override
                public Explanation explain(LeafReaderContext context, int doc) throws IOException {
                    Scorer scorer = scorer(context);
                    int newDoc = scorer.iterator().advance(doc);
                    assert newDoc == doc; // this is a DocIdSetIterator.all
                    return Explanation.match(
                            scorer.score(),
                            "Stat Score: " + type);
                }

                @Override
                public Scorer scorer(LeafReaderContext context) throws IOException {
                    return new ConstantScoreScorer(this, constantScore, DocIdSetIterator.all(context.reader().maxDoc()));
                }

                @Override
                public boolean isCacheable(LeafReaderContext ctx) {
                    return true;
                }

            };
        } else if (type.endsWith("_raw_tf")) {
            // Rewrite this into a boolean query where we can inject our PostingsExplorerQuery
            BooleanQuery.Builder qb = new BooleanQuery.Builder();
            for (Term t : terms) {
                qb.add(new BooleanClause(new PostingsExplorerQuery(t, PostingsExplorerQuery.Type.TF), BooleanClause.Occur.SHOULD));
            }
            // FIXME: completely refactor this class and stop accepting a random query but a list of terms directly
            // rewriting at this point is wrong, additionally we certainly build the TermContext twice for every terms
            // problem is that we rely on extractTerms which happen too late in the process
            Query q = qb.build().rewrite(searcher.getIndexReader());
            return new ExplorerQuery.ExplorerWeight(this, searcher.createWeight(q, true, boost), type);
        }
        throw new IllegalArgumentException( "Unknown ExplorerQuery type [" + type + "]" );
    }

    static class ExplorerWeight extends Weight {
        protected final Weight weight;
        private final String type;

        ExplorerWeight(Query q, Weight subWeight, String type) throws IOException {
            super(q);
            weight = subWeight;
            this.type = type;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            weight.extractTerms(terms);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = scorer(context);

            if (scorer != null) {
                int newDoc = scorer.iterator().advance(doc);
                if (newDoc == doc) {
                    return Explanation.match(
                            scorer.score(),
                            "Stat Score: " + type);
                }
            }
            return Explanation.noMatch("no matching term");
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
            Scorer subscorer = weight.scorer(context);
            return new ExplorerScorer(weight, type, subscorer);
        }
    }

    public String toString(String field) {
        return query.toString();
    };
}
