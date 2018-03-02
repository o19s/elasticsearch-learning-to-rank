/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.rescore;

import com.o19s.es.ltr.query.FeatureMatrixCollector;
import com.o19s.es.ltr.query.Normalizer;
import com.o19s.es.ltr.query.RankerQuery;
import com.o19s.es.ltr.ranker.BulkLtrRanker;
import com.o19s.es.ltr.ranker.FeatureMatrix;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryRescorer;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.search.rescore.RescoreContext;
import org.elasticsearch.search.rescore.Rescorer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

public class LtrRescorer implements Rescorer {
    public static final String NAME = "ltr";
    public static final Comparator<ScoreDoc> SCORE_DOC_COMPARATOR = (o1, o2) -> {
        int cmp = Float.compare(o2.score, o1.score);
        return cmp == 0 ? Integer.compare(o1.doc, o2.doc) : cmp;
    };

    @Override
    public TopDocs rescore(TopDocs topDocs, IndexSearcher searcher, RescoreContext rescoreContext) throws IOException {
        LtrRescoreContext ctx = (LtrRescoreContext) rescoreContext;

        if (ctx.query instanceof RankerQuery &&  ((RankerQuery) ctx.query).getRanker() instanceof BulkLtrRanker && ctx.batchSize != 1) {
            return bulkRescore(topDocs, searcher, ctx);
        } else {
            return classicRescore(topDocs, searcher, ctx);
        }
    }

    TopDocs bulkRescore(TopDocs topDocs, IndexSearcher searcher, LtrRescoreContext ctx) throws IOException {
        ScoreDoc[] docs = topDocs.scoreDocs;
        if (docs.length == 0) {
            return topDocs;
        }
        FeatureMatrix matrix = null;

        Query query = searcher.rewrite(ctx.query);
        assert query instanceof RankerQuery;
        RankerQuery rquery = (RankerQuery) query;

        RankerQuery.ElasticProfilerWeightWrapper wrapper = RankerQuery.ElasticProfilerWeightWrapper.wrap(rquery);
        searcher.createWeight(wrapper, true, 1F);
        RankerQuery.RankerWeight weight = wrapper.getWeight();
        assert weight != null;
        BulkLtrRanker ranker = (BulkLtrRanker) rquery.getRanker();

        int wSize = Math.min(ctx.getWindowSize(), docs.length);
        FeatureMatrixCollector collector = new FeatureMatrixCollector(weight, searcher.getIndexReader(), docs, wSize);
        // We want to scan the index in doc order, reorder only the docs
        Arrays.sort(docs, 0, wSize, Comparator.comparingInt((s) -> s.doc));
        int batchSize = ctx.batchSize > 0 ? ctx.batchSize : ranker.getPreferedBatchSize(wSize);
        batchSize = Math.min(batchSize, wSize);

        for (int i = 0; i < wSize;) {
            final int base = i;
            matrix = ranker.newMatrix(matrix, batchSize);
            int docsToScore = collector.collect(matrix);
            ranker.bulkScore(matrix, 0, docsToScore, (matrixIndex, s) -> {
                int idx = matrixIndex+base;
                ScoreDoc original = docs[idx];
                original.score = this.combine(original.score, s, ctx);
            });
            i+=docsToScore;
        }

        // Normalize remaining docs
        for (int i = wSize; i < docs.length; i++) {
            ScoreDoc original = docs[i];
            original.score = (float) ctx.normalizedQueryScore(original.score);
        }

        Arrays.sort(docs, SCORE_DOC_COMPARATOR);
        topDocs.setMaxScore(docs[0].score);
        return topDocs;
    }

    TopDocs classicRescore(TopDocs topDocs, IndexSearcher searcher, LtrRescoreContext ctx) throws IOException {
        if (topDocs.scoreDocs.length == 0) {
            return topDocs;
        }

        QueryRescorer rescorer = new QueryRescorer(ctx.query) {
            @Override
            protected float combine(float firstPassScore, boolean secondPassMatches, float secondPassScore) {
                if (secondPassMatches) {
                    return LtrRescorer.this.combine(firstPassScore, secondPassScore, ctx);
                }
                // should not happen, the ranker query always match
                return (float) ctx.normalizedQueryScore(firstPassScore);
            }
        };
        TopDocs topDocsRescored = topDocs;
        if (ctx.getWindowSize() < topDocs.scoreDocs.length) {
            ScoreDoc[] rescored = Arrays.copyOfRange(topDocs.scoreDocs, 0, ctx.getWindowSize());
            topDocsRescored = new TopDocs(topDocs.totalHits, rescored, rescored[0].score);
        }

        topDocsRescored = rescorer.rescore(searcher, topDocsRescored, topDocsRescored.scoreDocs.length);
        System.arraycopy(topDocsRescored.scoreDocs, 0, topDocs.scoreDocs, 0, topDocsRescored.scoreDocs.length);
        for (int i = topDocsRescored.scoreDocs.length; i < topDocs.scoreDocs.length; i++) {
            ScoreDoc doc = topDocs.scoreDocs[i];
            doc.score = (float) (ctx.queryNormalizer.normalize(doc.score)*ctx.queryWeight);
        }
        Arrays.sort(topDocs.scoreDocs, SCORE_DOC_COMPARATOR);
        topDocs.setMaxScore(topDocs.scoreDocs[0].score);
        return topDocs;
    }

    float combine(float queryScore, float rescoreQueryScore, LtrRescoreContext ctx) {
        double weightedRescoreScore = ctx.rescoreQueryNormalizer.normalize(rescoreQueryScore)*ctx.rescoreQueryWeight;
        if (ctx.scoreMode == LtrRescoreMode.Replace) {
            return (float) weightedRescoreScore;
        }
        double weightedQueryScore = ctx.queryNormalizer.normalize(queryScore)*ctx.queryWeight;
        return (float) ctx.scoreMode.combine(weightedQueryScore, weightedRescoreScore);
    }

    private Explanation explainCombine(Explanation queryExplanation, Explanation rescoreQueryExplanation, LtrRescoreContext ctx) {
        Explanation queryExplain = ctx.queryNormalizer.explain(queryExplanation.getValue(), queryExplanation);
        queryExplain = Explanation.match((float) (queryExplain.getValue() * ctx.queryWeight), "product of:",
                queryExplain, Explanation.match((float) ctx.rescoreQueryWeight, "primaryWeight"));
        Explanation explain = queryExplain;

        if (rescoreQueryExplanation.isMatch()) {
            Explanation rescoreExplain = ctx.rescoreQueryNormalizer.explain(rescoreQueryExplanation.getValue(), rescoreQueryExplanation);
            rescoreExplain = Explanation.match((float) (rescoreExplain.getValue() * ctx.rescoreQueryWeight), "product of:",
                    rescoreExplain, Explanation.match((float) ctx.rescoreQueryWeight, "secondaryWeight"));

            float finalScore = combine(queryExplanation.getValue(), rescoreQueryExplanation.getValue(), ctx);
            explain = Explanation.match(finalScore, ctx.scoreMode.toString(), queryExplain, rescoreExplain);
        }
        return explain;
    }

    @Override
    public Explanation explain(int topLevelDocId, IndexSearcher searcher, RescoreContext rescoreContext,
                               Explanation sourceExplanation) throws IOException {
        Explanation rescoreQueryExplanation = searcher.explain(((LtrRescoreContext) rescoreContext).getQuery(), topLevelDocId);
        return explainCombine(sourceExplanation, rescoreQueryExplanation, (LtrRescoreContext) rescoreContext);
    }

    @Override
    public void extractTerms(IndexSearcher searcher, RescoreContext rescoreContext, Set<Term> termsSet) throws IOException {
        searcher.createWeight(searcher.rewrite(((LtrRescoreContext) rescoreContext).query), true, 1F).extractTerms(termsSet);
    }

    public static class LtrRescoreContext extends RescoreContext {
        private Query query;
        private int batchSize = -1;

        private Normalizer queryNormalizer = Normalizer.NOOP;
        private Normalizer rescoreQueryNormalizer = Normalizer.NOOP;

        private double queryWeight = 1.0f;
        private double rescoreQueryWeight = 1.0f;

        private LtrRescoreMode scoreMode = LtrRescoreMode.Replace;

        public LtrRescoreContext(int windowSize, LtrRescorer rescorer) {
            super(windowSize, rescorer);
        }

        public double normalizedQueryScore(float score) {
            return queryNormalizer.normalize(score)* queryWeight;
        }

        public double normalizedRescoreQueryScore(float score) {
            return rescoreQueryNormalizer.normalize(score)*rescoreQueryWeight;
        }

        public Query getQuery() {
            return query;
        }

        public LtrRescoreContext setQuery(Query query) {
            this.query = query;
            return this;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public LtrRescoreContext setBatchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Normalizer getQueryNormalizer() {
            return queryNormalizer;
        }

        public LtrRescoreContext setQueryNormalizer(Normalizer queryNormalizer) {
            this.queryNormalizer = queryNormalizer;
            return this;
        }

        public Normalizer getRescoreQueryNormalizer() {
            return rescoreQueryNormalizer;
        }

        public LtrRescoreContext setRescoreQueryNormalizer(Normalizer rescoreQueryNormalizer) {
            this.rescoreQueryNormalizer = rescoreQueryNormalizer;
            return this;
        }

        public double getQueryWeight() {
            return queryWeight;
        }

        public LtrRescoreContext setQueryWeight(double queryWeight) {
            this.queryWeight = queryWeight;
            return this;
        }

        public double getRescoreQueryWeight() {
            return rescoreQueryWeight;
        }

        public LtrRescoreContext setRescoreQueryWeight(double rescoreQueryWeight) {
            this.rescoreQueryWeight = rescoreQueryWeight;
            return this;
        }

        public LtrRescoreMode getScoreMode() {
            return scoreMode;
        }

        public LtrRescoreContext setScoreMode(LtrRescoreMode scoreMode) {
            this.scoreMode = scoreMode;
            return this;
        }
    }

    public enum LtrRescoreMode implements Writeable {
        Replace {
            @Override
            public double combine(double primary, double secondary) {
                return secondary;
            }

            @Override
            public String toString() {
                return "replace";
            }
        },
        Avg {
            @Override
            public double combine(double primary, double secondary) {
                return (primary + secondary) / 2;
            }

            @Override
            public String toString() {
                return "avg";
            }
        },
        Max {
            @Override
            public double combine(double primary, double secondary) {
                return Math.max(primary, secondary);
            }

            @Override
            public String toString() {
                return "max";
            }
        },
        Min {
            @Override
            public double combine(double primary, double secondary) {
                return Math.min(primary, secondary);
            }

            @Override
            public String toString() {
                return "min";
            }
        },
        Total {
            @Override
            public double combine(double primary, double secondary) {
                return primary + secondary;
            }

            @Override
            public String toString() {
                return "total";
            }
        },
        Multiply {
            @Override
            public double combine(double primary, double secondary) {
                return primary * secondary;
            }

            @Override
            public String toString() {
                return "multiply";
            }
        };

        public abstract double combine(double primary, double secondary);

        public static LtrRescoreMode readFromStream(StreamInput in) throws IOException {
            return in.readEnum(LtrRescoreMode.class);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeEnum(this);
        }

        public static LtrRescoreMode fromString(String scoreMode) {
            String lscoreMode = scoreMode.toLowerCase(Locale.ROOT);
            for (LtrRescoreMode mode : values()) {
                if (lscoreMode.equals(mode.toString())) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("illegal score_mode [" + scoreMode + "]");
        }
    }
}
