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
import com.o19s.es.ltr.utils.Suppliers;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.common.CheckedSupplier;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.FetchContext;
import org.elasticsearch.search.fetch.FetchSubPhase;
import org.elasticsearch.search.fetch.FetchSubPhaseProcessor;
import org.elasticsearch.search.rescore.QueryRescorer;
import org.elasticsearch.search.rescore.RescoreContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LoggingFetchSubPhase implements FetchSubPhase {
    @Override
    public FetchSubPhaseProcessor getProcessor(FetchContext context) throws IOException {
        LoggingSearchExtBuilder ext = (LoggingSearchExtBuilder) context.getSearchExt(LoggingSearchExtBuilder.NAME);
        if (ext == null) {
            return null;
        }

        // NOTE: we do not support logging on nested hits but sadly at this point we cannot know
        // if we are going to run on top level hits or nested hits.
        // Delegate creation of the loggers until we know the hits checking for SearchHit#getNestedIdentity
        CheckedSupplier<Tuple<Weight, List<HitLogConsumer>>, IOException> weigthtAndLogSpecsSupplier = () -> {
            List<HitLogConsumer> loggers = new ArrayList<>();
            Map<String, Query> namedQueries = context.parsedQuery().namedFilters();
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
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
            Weight w = context.searcher().rewrite(builder.build()).createWeight(context.searcher(), ScoreMode.COMPLETE, 1.0F);
            return new Tuple<>(w, loggers);
        };


        return new LoggingFetchSubPhaseProcessor(Suppliers.memoizeCheckedSupplier(weigthtAndLogSpecsSupplier));
    }

    private Tuple<RankerQuery, HitLogConsumer> extractQuery(LoggingSearchExtBuilder.LogSpec
                                                                    logSpec, Map<String, Query> namedQueries) {
        Query q = namedQueries.get(logSpec.getNamedQuery());
        if (q == null) {
            throw new IllegalArgumentException("No query named [" + logSpec.getNamedQuery() + "] found");
        }
        return toLogger(logSpec, inspectQuery(q)
                .orElseThrow(() -> new IllegalArgumentException("Query named [" + logSpec.getNamedQuery() +
                        "] must be a [sltr] query [" +
                        ((q instanceof BoostQuery) ? ((BoostQuery) q).getQuery().getClass().getSimpleName(

                        ) : q.getClass().getSimpleName()) +
                        "] found")));
    }

    private Tuple<RankerQuery, HitLogConsumer> extractRescore(LoggingSearchExtBuilder.LogSpec logSpec,
                                                              List<RescoreContext> contexts) {
        if (logSpec.getRescoreIndex() >= contexts.size()) {
            throw new IllegalArgumentException("rescore index [" + logSpec.getRescoreIndex() + "] is out of bounds, only " +
                    "[" + contexts.size() + "] rescore context(s) are available");
        }
        RescoreContext context = contexts.get(logSpec.getRescoreIndex());
        if (!(context instanceof QueryRescorer.QueryRescoreContext)) {
            throw new IllegalArgumentException("Expected a [QueryRescoreContext] but found a " +
                    "[" + context.getClass().getSimpleName() + "] " +
                    "at index [" + logSpec.getRescoreIndex() + "]");
        }
        QueryRescorer.QueryRescoreContext qrescore = (QueryRescorer.QueryRescoreContext) context;
        return toLogger(logSpec, inspectQuery(qrescore.query())
                .orElseThrow(() -> new IllegalArgumentException("Expected a [sltr] query but found a " +
                        "[" + qrescore.query().getClass().getSimpleName() + "] " +
                        "at index [" + logSpec.getRescoreIndex() + "]")));
    }

    private Optional<RankerQuery> inspectQuery(Query q) {
        if (q instanceof RankerQuery) {
            return Optional.of((RankerQuery) q);
        } else if (q instanceof BoostQuery && ((BoostQuery) q).getQuery() instanceof RankerQuery) {
            return Optional.of((RankerQuery) ((BoostQuery) q).getQuery());
        }
        return Optional.empty();
    }

    private Tuple<RankerQuery, HitLogConsumer> toLogger(LoggingSearchExtBuilder.LogSpec logSpec, RankerQuery query) {
        HitLogConsumer consumer = new HitLogConsumer(logSpec.getLoggerName(), query.featureSet(), logSpec.isMissingAsZero());
        query = query.toLoggerQuery(consumer);
        return new Tuple<>(query, consumer);
    }
    static class LoggingFetchSubPhaseProcessor implements FetchSubPhaseProcessor {
        private final CheckedSupplier<Tuple<Weight, List<HitLogConsumer>>, IOException> loggersSupplier;
        private Scorer scorer;
        private LeafReaderContext currentContext;

        LoggingFetchSubPhaseProcessor(CheckedSupplier<Tuple<Weight, List<HitLogConsumer>>, IOException> loggersSupplier) {
            this.loggersSupplier = loggersSupplier;
        }


        @Override
        public void setNextReader(LeafReaderContext readerContext) throws IOException {
            currentContext = readerContext;
            scorer = null;
        }

        @Override
        public void process(HitContext hitContext) throws IOException {
            if (hitContext.hit().getNestedIdentity() != null) {
                // we do not support logging nested docs
                return;
            }
            Tuple<Weight, List<HitLogConsumer>> weightAndLoggers = loggersSupplier.get();
            if (scorer == null) {
                scorer = weightAndLoggers.v1().scorer(currentContext);
            }
            List<HitLogConsumer> loggers = weightAndLoggers.v2();
            if (scorer != null && scorer.iterator().advance(hitContext.docId()) == hitContext.docId()) {
                loggers.forEach((l) -> l.nextDoc(hitContext.hit()));
                // Scoring will trigger log collection
                scorer.score();
            }
        }
    }

    static class HitLogConsumer implements LogLtrRanker.LogConsumer {
        private static final String FIELD_NAME = "_ltrlog";
        private static final String EXTRA_LOGGING_NAME = "extra_logging";
        private final String name;
        private final FeatureSet set;
        private final boolean missingAsZero;

        // [
        //      {
        //          "name": "featureName",
        //          "value": 1.33
        //      },
        //      {
        //          "name": "otherFeatureName",
        //      }
        // ]
        private List<Map<String, Object>> currentLog;
        private SearchHit currentHit;
        private Map<String, Object> extraLogging;


        HitLogConsumer(String name, FeatureSet set, boolean missingAsZero) {
            this.name = name;
            this.set = set;
            this.missingAsZero = missingAsZero;
        }

        private void rebuild() {
            // Allocate one Map per feature, plus one placeholder for an extra logging Map
            // that will only be added if used.
            List<Map<String, Object>> ini = new ArrayList<>(set.size() + 1);

            for (int i = 0; i < set.size(); i++) {
                Map<String, Object> defaultKeyVal = new HashMap<>();
                defaultKeyVal.put("name", set.feature(i).name());
                if (missingAsZero) {
                    defaultKeyVal.put("value", 0.0F);
                }
                ini.add(i, defaultKeyVal);
            }
            currentLog = ini;
            extraLogging = null;
        }

        @Override
        public void accept(int featureOrdinal, float score) {
            assert currentLog != null;
            assert currentHit != null;
            currentLog.get(featureOrdinal).put("value", score);
        }

        /**
         * Return Map to store additional logging information returned with the feature values.
         * <p>
         * The Map is created on first access.
         */
        @Override
        public Map<String, Object> getExtraLoggingMap() {
            if (extraLogging == null) {
                extraLogging = new HashMap<>();
                Map<String, Object> logEntry = new HashMap<>();
                logEntry.put("name", EXTRA_LOGGING_NAME);
                logEntry.put("value", extraLogging);
                currentLog.add(logEntry);
            }
            return extraLogging;
        }

        void nextDoc(SearchHit hit) {
            DocumentField logs = hit.getFields().get(FIELD_NAME);
            if (logs == null) {
                logs = newLogField();
                hit.setDocumentField(FIELD_NAME, logs);
            }
            Map<String, List<Map<String, Object>>> entries = logs.getValue();
            rebuild();
            currentHit = hit;
            entries.put(name, currentLog);
        }

        DocumentField newLogField() {
            List<Object> logList = Collections.singletonList(new HashMap<String, List<Map<String, Object>>>());
            return new DocumentField(FIELD_NAME, logList);
        }
    }
}
