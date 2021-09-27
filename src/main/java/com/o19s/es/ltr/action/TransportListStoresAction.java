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

import com.o19s.es.ltr.action.ListStoresAction.ListStoresActionRequest;
import com.o19s.es.ltr.action.ListStoresAction.ListStoresActionResponse;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.search.MultiSearchRequestBuilder;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.TransportMasterNodeReadAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.core.Tuple;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.STORE_VERSION_PROP;
import static java.util.stream.Collectors.toMap;
import static org.elasticsearch.action.ActionListener.wrap;
import static org.elasticsearch.core.Tuple.tuple;

public class TransportListStoresAction extends TransportMasterNodeReadAction<ListStoresActionRequest, ListStoresActionResponse> {
    private final Client client;

    @Inject
    public TransportListStoresAction(TransportService transportService,
                                     ClusterService clusterService, ThreadPool threadPool, ActionFilters actionFilters,
                                     IndexNameExpressionResolver indexNameExpressionResolver, Client client) {
        super(ListStoresAction.NAME, transportService, clusterService, threadPool,
            actionFilters, ListStoresActionRequest::new, indexNameExpressionResolver, ListStoresActionResponse::new, ThreadPool.Names.SAME);
        this.client = client;
    }

    @Override
    protected void masterOperation(ListStoresActionRequest request, ClusterState state,
                                   ActionListener<ListStoresActionResponse> listener) throws Exception {
        String[] names = indexNameExpressionResolver.concreteIndexNames(state,
                new ClusterStateRequest().indices(IndexFeatureStore.DEFAULT_STORE, IndexFeatureStore.STORE_PREFIX + "*"));
        final MultiSearchRequestBuilder req = client.prepareMultiSearch();
        final List<Tuple<String, Integer>> versions = new ArrayList<>();
        Stream.of(names)
                .filter(IndexFeatureStore::isIndexStore)
                .map((s) -> clusterService.state().metadata().getIndices().get(s))
                .filter(Objects::nonNull)
                .filter((im) -> STORE_VERSION_PROP.exists(im.getSettings()))
                .forEach((m) -> {
                    req.add(countSearchRequest(m));
                    versions.add(tuple(m.getIndex().getName(),STORE_VERSION_PROP.get(m.getSettings())));
                });
        if (versions.isEmpty()) {
            listener.onResponse(new ListStoresActionResponse(Collections.emptyList()));
        } else {
            req.execute(wrap((r) -> listener.onResponse(toResponse(r, versions)), listener::onFailure));
        }
    }


    private SearchRequestBuilder countSearchRequest(IndexMetadata meta) {
        return client.prepareSearch(meta.getIndex().getName())
                .setQuery(QueryBuilders.matchAllQuery())
                .setSize(0)
                .addAggregation(AggregationBuilders.terms("type").field("type").size(100));
    }

    private ListStoresActionResponse toResponse(MultiSearchResponse response, List<Tuple<String, Integer>> versions) {
        assert versions.size() == response.getResponses().length;
        Iterator<Tuple<String, Integer>> vs = versions.iterator();
        Iterator<MultiSearchResponse.Item> rs = response.iterator();
        List<ListStoresAction.IndexStoreInfo> infos = new ArrayList<>(versions.size());
        while (vs.hasNext() && rs.hasNext()) {
            MultiSearchResponse.Item it = rs.next();
            Tuple<String, Integer> idxAndVersion = vs.next();
            Map<String, Integer> counts = Collections.emptyMap();
            if (!it.isFailure()) {
                Terms aggs = it.getResponse()
                        .getAggregations()
                        .get("type");
                counts = aggs
                        .getBuckets()
                        .stream()
                        .collect(toMap(MultiBucketsAggregation.Bucket::getKeyAsString,
                                (b) -> (int) b.getDocCount()));
            }
            infos.add(new ListStoresAction.IndexStoreInfo(idxAndVersion.v1(), idxAndVersion.v2(), counts));
        }
        return new ListStoresActionResponse(infos);
    }

    private Tuple<String, Integer> toVersion(String s) {
        if (!IndexFeatureStore.isIndexStore(s)) {
            return null;
        }
        IndexMetadata index = clusterService.state().metadata().getIndices().get(s);

        if (index != null && STORE_VERSION_PROP.exists(index.getSettings())) {
            return new Tuple<>(index.getIndex().getName(), STORE_VERSION_PROP.get(index.getSettings()));
        }
        return null;
    }

    @Override
    protected ClusterBlockException checkBlock(ListStoresActionRequest request, ClusterState state) {
        return null;
    }
}
