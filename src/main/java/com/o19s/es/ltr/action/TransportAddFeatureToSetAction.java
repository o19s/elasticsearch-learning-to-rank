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

import com.o19s.es.ltr.action.AddFeaturesToSetAction.AddFeaturesToSetRequest;
import com.o19s.es.ltr.action.AddFeaturesToSetAction.AddFeaturesToSetResponse;
import com.o19s.es.ltr.action.FeatureStoreAction.FeatureStoreRequest;
import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
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

public class TransportAddFeatureToSetAction extends HandledTransportAction<AddFeaturesToSetRequest, AddFeaturesToSetResponse> {
    private final ClusterService clusterService;
    private final TransportSearchAction searchAction;
    private final TransportGetAction getAction;
    private final TransportFeatureStoreAction featureStoreAction;

    @Inject
    public TransportAddFeatureToSetAction(Settings settings, ThreadPool threadPool,
                                             TransportService transportService, ActionFilters actionFilters,
                                             IndexNameExpressionResolver indexNameExpressionResolver,
                                             ClusterService clusterService, TransportSearchAction searchAction,
                                             TransportGetAction getAction, TransportFeatureStoreAction featureStoreAction) {
        super(AddFeaturesToSetAction.NAME, transportService, actionFilters, AddFeaturesToSetRequest::new);
        this.clusterService = clusterService;
        this.searchAction = searchAction;
        this.getAction = getAction;
        this.featureStoreAction = featureStoreAction;
    }

    @Override
    protected void doExecute(Task task, AddFeaturesToSetRequest request, ActionListener<AddFeaturesToSetResponse> listener) {
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
        private final ActionListener<AddFeaturesToSetResponse> listener;
        private final String featureNamesQuery;
        private final List<StoredFeature> features;
        private final boolean merge;
        private final String featureSetName;
        private final String routing;
        private final AtomicReference<Exception> searchException = new AtomicReference<>();
        private final AtomicReference<Exception> getException = new AtomicReference<>();
        private final AtomicReference<StoredFeatureSet> setRef = new AtomicReference<>();
        private final AtomicReference<List<StoredFeature>> featuresRef = new AtomicReference<>();
        private final CountDown countdown;
        private final AtomicLong version = new AtomicLong(-1L);
        private final ClusterService clusterService;
        private final TransportSearchAction searchAction;
        private final TransportGetAction getAction;
        private final TransportFeatureStoreAction featureStoreAction;
        private final FeatureValidation validation;

        AsyncAction(Task task, AddFeaturesToSetRequest request, ActionListener<AddFeaturesToSetResponse> listener,
                           ClusterService clusterService, TransportSearchAction searchAction, TransportGetAction getAction,
                           TransportFeatureStoreAction featureStoreAction) {
            this.task = task;
            this.listener = listener;
            this.featureSetName = request.getFeatureSet();
            this.featureNamesQuery = request.getFeatureNameQuery();
            this.features = request.getFeatures();
            if (featureNamesQuery != null) {
                assert features == null || features.isEmpty();
                // 2 async actions if we fetch features from store, one otherwize.
                this.countdown = new CountDown(2);
            } else {
                assert features != null && !features.isEmpty();
                // 1 async actions if we already have features.
                this.countdown = new CountDown(1);
            }
            this.merge = request.isMerge();
            this.store = request.getStore();
            this.routing = request.getRouting();
            this.clusterService = clusterService;
            this.searchAction = searchAction;
            this.getAction = getAction;
            this.featureStoreAction = featureStoreAction;
            this.validation = request.getValidation();
        }

        private void start() {
            if (featureNamesQuery != null) {
                fetchFeaturesFromStore();
            } else {
                featuresRef.set(features);
            }
            GetRequest getRequest = new GetRequest(store)
                    .type(IndexFeatureStore.ES_TYPE)
                    .id(StorableElement.generateId(StoredFeatureSet.TYPE, featureSetName))
                    .routing(routing);

            getRequest.setParentTask(clusterService.localNode().getId(), task.getId());
            getAction.execute(getRequest, wrap(this::onGetResponse, this::onGetFailure));
        }

        private void fetchFeaturesFromStore() {
            SearchRequest srequest = new SearchRequest(store);
            srequest.setParentTask(clusterService.localNode().getId(), task.getId());
            QueryBuilder nameQuery;

            if (featureNamesQuery.endsWith("*")) {
                String parsed = featureNamesQuery.replaceAll("[*]+$", "");
                if (parsed.isEmpty()) {
                    nameQuery = QueryBuilders.matchAllQuery();
                } else {
                    nameQuery = QueryBuilders.matchQuery("name.prefix", parsed);
                }
            } else {
                nameQuery = QueryBuilders.matchQuery("name", featureNamesQuery);
            }
            BoolQueryBuilder bq = QueryBuilders.boolQuery();
            bq.must(nameQuery);
            bq.must(QueryBuilders.matchQuery("type", StoredFeature.TYPE));
            srequest.types(IndexFeatureStore.ES_TYPE);
            srequest.source().query(bq);
            srequest.source().fetchSource(true);
            srequest.source().size(StoredFeatureSet.MAX_FEATURES);
            searchAction.execute(srequest, wrap(this::onSearchResponse, this::onSearchFailure));
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
                if (sr.getHits().getTotalHits().value > StoredFeatureSet.MAX_FEATURES) {
                    throw new IllegalArgumentException("The feature query [" + featureNamesQuery + "] returns too many features");
                }
                if (sr.getHits().getTotalHits().value == 0) {
                    throw new IllegalArgumentException("The feature query [" + featureNamesQuery + "] returned no features");
                }
                final List<StoredFeature> features = new ArrayList<>(sr.getHits().getHits().length);
                for (SearchHit hit : sr.getHits().getHits()) {
                    features.add(IndexFeatureStore.parse(StoredFeature.class, StoredFeature.TYPE, hit.getSourceRef()));
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
            if (merge) {
                set = set.merge(featuresRef.get());
            } else {
                set = set.append(featuresRef.get());
            }
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
            frequest.setValidation(validation);
            featureStoreAction.execute(frequest, wrap(
                    (r) -> listener.onResponse(new AddFeaturesToSetResponse(r.getResponse())),
                    listener::onFailure));
        }
    }

    private static class AsyncFetchSet implements ActionListener<GetResponse> {
        private ActionListener<AddFeaturesToSetResponse> listener;

        @Override
        public void onResponse(GetResponse getFields) {

        }

        @Override
        public void onFailure(Exception e) {
            listener.onFailure(e);
        }
    }
}
