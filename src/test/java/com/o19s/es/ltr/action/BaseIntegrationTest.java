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

import com.o19s.es.ltr.LtrQueryParserPlugin;
import com.o19s.es.ltr.action.FeatureStoreAction.FeatureStoreRequestBuilder;
import com.o19s.es.ltr.action.FeatureStoreAction.FeatureStoreResponse;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexAction;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.junit.Before;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

public abstract class BaseIntegrationTest extends ESSingleNodeTestCase {
    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Arrays.asList(LtrQueryParserPlugin.class);
    }

    public void createStore(String name) throws Exception {
        assert IndexFeatureStore.isIndexStore(name);
        CreateIndexResponse resp = client().execute(CreateIndexAction.INSTANCE, IndexFeatureStore.buildIndexRequest(name)).get();
        assertTrue(resp.isAcknowledged());
    }

    @Before
    public void setup() throws Exception {
        createDefaultStore();
    }

    public void deleteDefaultStore() throws Exception {
        deleteStore(IndexFeatureStore.DEFAULT_STORE);
    }

    public void deleteStore(String name) throws Exception {
        DeleteIndexResponse resp = client().admin().indices().prepareDelete(name).get();
        assertTrue(resp.isAcknowledged());
    }

    public void createDefaultStore() throws Exception {
        createStore(IndexFeatureStore.DEFAULT_STORE);
    }

    public FeatureStoreResponse addElement(StorableElement element) throws ExecutionException, InterruptedException {
        return addElement(element, IndexFeatureStore.DEFAULT_STORE);
    }

    public <E extends StorableElement> E getElement(Class<E> clazz, String type, String name) throws IOException {
        return getElement(clazz, type, name, IndexFeatureStore.DEFAULT_STORE);
    }

    public <E extends StorableElement> E getElement(Class<E> clazz, String type, String name, String store) throws IOException {
        return new IndexFeatureStore(store, client(), parserFactory()).getAndParse(name, clazz, type);
    }

    protected LtrRankerParserFactory parserFactory() {
        return getInstanceFromNode(LtrRankerParserFactory.class);
    }

    public FeatureStoreResponse addElement(StorableElement element, String store) throws ExecutionException, InterruptedException {
        FeatureStoreRequestBuilder builder = FeatureStoreAction.INSTANCE.newRequestBuilder(client());
        builder.request().setStorableElement(element);
        builder.request().setAction(FeatureStoreAction.FeatureStoreRequest.Action.CREATE);
        builder.request().setStore(store);
        FeatureStoreResponse response = builder.execute().get();
        assertEquals(1, response.getResponse().getVersion());
        assertEquals(IndexFeatureStore.ES_TYPE, response.getResponse().getType());
        assertEquals(DocWriteResponse.Result.CREATED, response.getResponse().getResult());
        assertEquals(element.id(), response.getResponse().getId());
        assertEquals(store, response.getResponse().getIndex());
        return response;
    }
}
