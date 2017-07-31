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

package com.o19s.es.ltr.logging;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.query.RankerQuery;
import com.o19s.es.ltr.ranker.LogLtrRanker;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.fetch.FetchPhaseExecutionException;
import org.elasticsearch.search.fetch.FetchSubPhase;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.search.rescore.QueryRescorer;
import org.elasticsearch.search.rescore.RescoreSearchContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoggingFetchSubPhase implements FetchSubPhase {
    @Override
    public void hitsExecute(SearchContext context, SearchHit[] hits) {
        LoggingSearchExtBuilder ext = (LoggingSearchExtBuilder) context.getSearchExt(LoggingSearchExtBuilder.NAME);
        if (ext == null) {
            return;
        }

        // Use a boolean query with all the models to log
        // This way reuse existing code to advance through multiple scorers/iterators
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        List<HitLogConsumer> loggers = new ArrayList<>();
        Map<String, Query> namedQueries = context.parsedQuery().namedFilters();
        ext.logSpecsStream().filter((l) -> l.getNamedQuery() != null).forEach((l) -> {
            Tuple<RankerQuery, HitLogConsumer> query = extractQuery(l, namedQueries);
            builder.add(new BooleanClause(query.v1(), BooleanClause.Occur.MUST));
            loggers.add(query.v2());
        });

        ext.logSpecsStream().filter((l) -> l.getRescoreIndex() != null).forEach((l) -> {
            Tuple<RankerQuery, HitLogConsumer> query = extractRescore(l, context.rescore());
            builder.add(new BooleanClause(query.v1(), BooleanClause.Occur.MUST));
            loggers.add(query.v2());
        });


        try {
            doLog(builder.build(), loggers, context.searcher(), hits);
        } catch (IOException e) {
            throw new FetchPhaseExecutionException(context, e.getMessage(), e);
        }
    }

    void doLog(BooleanQuery query, List<HitLogConsumer> loggers, IndexSearcher searcher, SearchHit[] hits) throws IOException {
        // Reorder hits by id so we can scan all the docs belonging to the same
        // segment by reusing the same scorer.
        SearchHit[] reordered = new SearchHit[hits.length];
        System.arraycopy(hits, 0, reordered, 0, hits.length);
        Arrays.sort(reordered, Comparator.comparingInt(SearchHit::docId));

        int hitUpto = 0;
        int readerUpto = -1;
        int endDoc = 0;
        int docBase = 0;
        Scorer scorer = null;
        Weight weight = searcher.createNormalizedWeight(query, true);
        // Loop logic borrowed from lucene QueryRescorer
        while (hitUpto < reordered.length) {
            SearchHit hit = reordered[hitUpto];
            int docID = hit.docId();
            loggers.forEach((l) -> l.nextDoc(hit));
            LeafReaderContext readerContext = null;
            while (docID >= endDoc) {
                readerUpto++;
                readerContext = searcher.getTopReaderContext().leaves().get(readerUpto);
                endDoc = readerContext.docBase + readerContext.reader().maxDoc();
            }

            if (readerContext != null) {
                // We advanced to another segment:
                docBase = readerContext.docBase;
                scorer = weight.scorer(readerContext);
            }

            if(scorer != null) {
                int targetDoc = docID - docBase;
                int actualDoc = scorer.docID();
                if (actualDoc < targetDoc) {
                    actualDoc = scorer.iterator().advance(targetDoc);
                }
                if (actualDoc == targetDoc) {
                    // Scoring will trigger log collection
                    scorer.score();
                }
            }

            hitUpto++;
        }
    }

    private Tuple<RankerQuery, HitLogConsumer> extractQuery(LoggingSearchExtBuilder.LogSpec logSpec, Map<String, Query> namedQueries) {
        Query q = namedQueries.get(logSpec.getNamedQuery());
        if (q == null) {
            throw new IllegalArgumentException("No query named [" + logSpec.getNamedQuery() + "] found");
        }
        if (!(q instanceof RankerQuery)) {
            throw new IllegalArgumentException("Query named [" + logSpec.getNamedQuery() +
                    "] must be a [sltr] query [" + q.getClass().getSimpleName() + "] found");
        }
        RankerQuery query = (RankerQuery) q;
        return toLogger(logSpec, query);
    }

    private Tuple<RankerQuery, HitLogConsumer> extractRescore(LoggingSearchExtBuilder.LogSpec logSpec,
                                                              List<RescoreSearchContext> contexts) {
        if (logSpec.getRescoreIndex() >= contexts.size()) {
            throw new IllegalArgumentException("rescore index [" + logSpec.getRescoreIndex()+"] is out of bounds, only " +
                    "[" + contexts.size() + "] rescore context(s) are available");
        }
        RescoreSearchContext context = contexts.get(logSpec.getRescoreIndex());
        if (!(context instanceof QueryRescorer.QueryRescoreContext)) {
            throw new IllegalArgumentException("Expected a [QueryRescoreContext] but found a " +
                    "[" + context.getClass().getSimpleName() + "] " +
                    "at index [" + logSpec.getRescoreIndex() + "]");
        }
        QueryRescorer.QueryRescoreContext qrescore = (QueryRescorer.QueryRescoreContext) context;
        if (!(qrescore.query() instanceof RankerQuery)) {
            throw new IllegalArgumentException("Expected a [sltr] query but found a " +
                    "[" + qrescore.query().getClass().getSimpleName() + "] " +
                    "at index [" + logSpec.getRescoreIndex() + "]");
        }

        RankerQuery query = (RankerQuery) qrescore.query();
        return toLogger(logSpec, query);
    }

    Tuple<RankerQuery, HitLogConsumer> toLogger(LoggingSearchExtBuilder.LogSpec logSpec, RankerQuery query) {
        HitLogConsumer consumer = new HitLogConsumer(logSpec.getLoggerName(), query.featureSet(), logSpec.isMissingAsZero());
        // Use a null ranker, we don't care about the final score here so don't spend time on it.
        query = query.toLoggerQuery(consumer, true);

        return new Tuple<>(query, consumer);
    }

    static class HitLogConsumer implements LogLtrRanker.LogConsumer {
        private static final String FIELD_NAME = "_ltrlog";
        private final String name;
        private final FeatureSet set;
        private final Map<String, Float> initialLog;
        private Map<String, Float> currentLog;

        HitLogConsumer(String name, FeatureSet set, boolean missingAsZero) {
            this.name = name;
            this.set = set;
            Map<String, Float> ini = new HashMap<>();
            if (missingAsZero) {
                for (int i = 0; i < set.size(); i++) {
                    ini.put(set.feature(i).name(), 0F);
                }
            }
            initialLog = Collections.unmodifiableMap(ini);
        }

        @Override
        public void accept(int featureOrdinal, float score) {
            currentLog.put(set.feature(featureOrdinal).name(), score);
        }

        void nextDoc(SearchHit hit) {
            if (hit.fieldsOrNull() == null) {
                hit.fields(new HashMap<>());
            }
            SearchHitField logs = hit.getFields()
                    .computeIfAbsent(FIELD_NAME,(k) -> newLogField());
            Map<String, Map<String, Float>> entries = logs.getValue();
            currentLog = new HashMap<>(initialLog);
            entries.put(name, currentLog);
        }

        SearchHitField newLogField() {
            List<Object> logList = Collections.singletonList(new HashMap<String, Map<String, Float>>());
            return new SearchHitField(FIELD_NAME, logList);
        }
    }
}
