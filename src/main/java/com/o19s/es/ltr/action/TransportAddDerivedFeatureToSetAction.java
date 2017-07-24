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

package com.o19s.es.ltr.action;

import com.o19s.es.ltr.action.AddDerivedFeaturesToSetAction.AddDerivedFeaturesToSetRequest;
import com.o19s.es.ltr.action.AddDerivedFeaturesToSetAction.AddDerivedFeaturesToSetResponse;
import com.o19s.es.ltr.action.FeatureStoreAction.FeatureStoreRequest;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredDerivedFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.TransportGetAction;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.TransportSearchAction;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.CountDown;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.elasticsearch.action.ActionListener.wrap;

public class TransportAddDerivedFeatureToSetAction extends HandledTransportAction<AddDerivedFeaturesToSetRequest,
        AddDerivedFeaturesToSetResponse> {
    private final ClusterService clusterService;
    private final TransportSearchAction searchAction;
    private final TransportGetAction getAction;
    private final TransportFeatureStoreAction featureStoreAction;

    @Inject
    public TransportAddDerivedFeatureToSetAction(Settings settings, ThreadPool threadPool,
                                                 TransportService transportService, ActionFilters actionFilters,
                                                 IndexNameExpressionResolver indexNameExpressionResolver,
                                                 ClusterService clusterService, TransportSearchAction searchAction,
                                                 TransportGetAction getAction, TransportFeatureStoreAction featureStoreAction) {
        super(settings, AddDerivedFeaturesToSetAction.NAME, threadPool, transportService, actionFilters, indexNameExpressionResolver,
                AddDerivedFeaturesToSetRequest::new);
        this.clusterService = clusterService;
        this.searchAction = searchAction;
        this.getAction = getAction;
        this.featureStoreAction = featureStoreAction;
    }

    @Override
    protected final void doExecute(AddDerivedFeaturesToSetRequest request, ActionListener<AddDerivedFeaturesToSetResponse> listener) {
        throw new UnsupportedOperationException("attempt to execute a TransportAddDerivedFeatureToSetAction without a task");
    }

    @Override
    protected void doExecute(Task task, AddDerivedFeaturesToSetRequest request, ActionListener<AddDerivedFeaturesToSetResponse> listener) {
        if (!clusterService.state().routingTable().hasIndex(request.getStore())) {
            throw new IllegalArgumentException("Store [" + request.getStore() + "] does not exist, please create it first.");
        }
        new AsyncAction(task, request, listener, clusterService, searchAction, getAction, featureStoreAction).start();
    }

    /**
     * Async action that does the following:
     * - send an async GetRequest to fetch the existing StoreFeatureSet if it exists
     * - send an async Searchrequest to fetch the features requested
     * - synchronize on CountDown, the last action to return will trigger the next step
     * - merge the StoredFeature and the new list of features
     * - send an async FeatureStoreAction to save the modified (or new) StoredFeatureSet
     */
    private static class AsyncAction {
        private final Task task;
        private final String store;
        private final ActionListener<AddDerivedFeaturesToSetResponse> listener;
        private final String dfName;
        private final String featureSetName;
        private final String routing;
        private final AtomicReference<Exception> searchException = new AtomicReference<>();
        private final AtomicReference<Exception> getException = new AtomicReference<>();
        private final AtomicReference<StoredFeatureSet> setRef = new AtomicReference<>();
        private final AtomicReference<List<StoredDerivedFeature>> featuresRef = new AtomicReference<>();
        private final CountDown countdown = new CountDown(2);
        private final AtomicLong version = new AtomicLong(-1L);
        private final ClusterService clusterService;
        private final TransportSearchAction searchAction;
        private final TransportGetAction getAction;
        private final TransportFeatureStoreAction featureStoreAction;

        AsyncAction(Task task, AddDerivedFeaturesToSetRequest request, ActionListener<AddDerivedFeaturesToSetResponse> listener,
                           ClusterService clusterService, TransportSearchAction searchAction, TransportGetAction getAction,
                           TransportFeatureStoreAction featureStoreAction) {
            this.task = task;
            this.listener = listener;
            this.featureSetName = request.getFeatureSet();
            this.dfName = request.getDerivedName();
            this.store = request.getStore();
            this.routing = request.getRouting();
            this.clusterService = clusterService;
            this.searchAction = searchAction;
            this.getAction = getAction;
            this.featureStoreAction = featureStoreAction;
        }

        private void start() {
            SearchRequest srequest = new SearchRequest(store);
            srequest.setParentTask(clusterService.localNode().getId(), task.getId());
            QueryBuilder nameQuery;
            if (dfName.endsWith("*")) {
                String parsed = dfName.replaceAll("[*]+$", "");
                if (parsed.isEmpty()) {
                    nameQuery = QueryBuilders.matchAllQuery();
                } else {
                    nameQuery = QueryBuilders.matchQuery("name.prefix", parsed);
                }
            } else {
                nameQuery = QueryBuilders.matchQuery("name", dfName);
            }
            BoolQueryBuilder bq = QueryBuilders.boolQuery();
            bq.must(nameQuery);
            bq.must(QueryBuilders.matchQuery("type", StoredDerivedFeature.TYPE));
            srequest.types(IndexFeatureStore.ES_TYPE);
            srequest.source().query(bq);
            srequest.source().fetchSource(true);
            srequest.source().size(StoredFeatureSet.MAX_FEATURES);
            ActionFuture<SearchResponse> resp = searchAction.execute(srequest);
            searchAction.execute(srequest, wrap(this::onSearchResponse, this::onSearchFailure));

            GetRequest getRequest = new GetRequest(store)
                    .type(IndexFeatureStore.ES_TYPE)
                    .id(StorableElement.generateId(StoredFeatureSet.TYPE, featureSetName))
                    .routing(routing);

            getRequest.setParentTask(clusterService.localNode().getId(), task.getId());
            getAction.execute(getRequest, wrap(this::onGetResponse, this::onGetFailure));
        }

        private void onGetFailure(Exception e) {
            getException.set(e);
            maybeFinish();
        }

        private void onSearchFailure(Exception e) {
            searchException.set(e);
            maybeFinish();
        }

        private void onGetResponse(GetResponse getResponse) {
            try {
                StoredFeatureSet featureSet;
                if (getResponse.isExists()) {
                    version.set(getResponse.getVersion());
                    featureSet = IndexFeatureStore.parse(StoredFeatureSet.class, StoredFeatureSet.TYPE, getResponse.getSourceAsBytesRef());
                } else {
                    version.set(-1L);
                    featureSet = new StoredFeatureSet(featureSetName, Collections.emptyList());
                }
                setRef.set(featureSet);
            } catch (Exception e) {
                getException.set(e);
            } finally {
                maybeFinish();
            }
        }

        private void onSearchResponse(SearchResponse sr) {
            try {
                if (sr.getHits().getTotalHits() > StoredFeatureSet.MAX_FEATURES) {
                    throw new IllegalArgumentException("The feature query [" + dfName + "] returns too many features");
                }
                if (sr.getHits().getTotalHits() == 0) {
                    throw new IllegalArgumentException("The feature query [" + dfName + "] returned no features");
                }
                final List<StoredDerivedFeature> features = new ArrayList<>(sr.getHits().getHits().length);
                for (SearchHit hit : sr.getHits().getHits()) {
                    features.add(IndexFeatureStore.parse(StoredDerivedFeature.class, StoredDerivedFeature.TYPE, hit.getSourceRef()));
                }
                featuresRef.set(features);
            } catch (Exception e) {
                searchException.set(e);
            } finally {
                maybeFinish();
            }
        }

        private void maybeFinish() {
            if (!countdown.countDown()) {
                return;
            }
            try {
                checkErrors();
                finishRequest();
            } catch (Exception e) {
                listener.onFailure(e);
            }
        }

        private void finishRequest() throws Exception {
            assert setRef.get() != null && featuresRef.get() != null;
            StoredFeatureSet set = setRef.get();
            set = set.appendDerived(featuresRef.get());
            updateSet(set);
        }

        private void checkErrors() throws Exception {
            if (searchException.get() == null && getException.get() == null) {
                return;
            }

            Exception sExc = searchException.get();
            Exception gExc = getException.get();
            final Exception exc;
            if (sExc != null && gExc != null) {
                sExc.addSuppressed(gExc);
                exc = sExc;
            } else if (sExc != null) {
                exc = sExc;
            } else {
                assert gExc != null;
                exc = gExc;
            }
            throw exc;
        }

        private void updateSet(StoredFeatureSet set) {
            long version = this.version.get();
            final FeatureStoreRequest frequest;
            if (version > 0) {
                 frequest = new FeatureStoreRequest(store, set, version);
            } else {
                frequest = new FeatureStoreRequest(store, set, FeatureStoreRequest.Action.CREATE);
            }
            frequest.setRouting(routing);
            frequest.setParentTask(clusterService.localNode().getId(), task.getId());

            featureStoreAction.execute(frequest, wrap(
                    (r) -> listener.onResponse(new AddDerivedFeaturesToSetResponse(r.getResponse())),
                    listener::onFailure));
        }
    }

    private static class AsyncFetchSet implements ActionListener<GetResponse> {
        private ActionListener<AddDerivedFeaturesToSetResponse> listener;

        @Override
        public void onResponse(GetResponse getFields) {

        }

        @Override
        public void onFailure(Exception e) {
            listener.onFailure(e);
        }
    }
}
