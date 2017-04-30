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

package com.o19s.es.ltr.utils;

import com.o19s.es.ltr.feature.store.FeatureStore;
import com.o19s.es.ltr.feature.store.index.CachedFeatureStore;
import com.o19s.es.ltr.feature.store.index.Caches;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.script.AbstractExecutableScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.NativeScriptEngineService;
import org.elasticsearch.script.NativeScriptFactory;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptType;

import java.util.HashMap;
import java.util.Map;

/**
 * Hack to contruct the CachedFeatureStore based on some context availabe in
 * {@link org.elasticsearch.index.query.QueryBuilder#toQuery(QueryShardContext)}
 * and the Caches built when {@link com.o19s.es.ltr.LtrQueryParserPlugin} is initialized.
 *
 * TODO: investigate cleaner ways to do this.
 */
public class FeatureStoreProvider extends AbstractExecutableScript {
    private static final String NAME = "_ltr_internals:" + FeatureStoreProvider.class.getSimpleName();

    private final FeatureStore store;

    private FeatureStoreProvider(FeatureStore store) {
        this.store = store;
    }

    /**
     * Executes the script.
     */
    @Override
    public FeatureStore run() {
        return store;
    }

    public static class Factory implements NativeScriptFactory {
        private final FeatureStoreLoader loader;

        public Factory(FeatureStoreLoader loader) {
            this.loader = loader;
        }

        @Override
        public ExecutableScript newScript(Map<String, Object> params) {
            String storeName = (String) params.get("store");
            Client client = (Client) params.get("client");

            if (storeName == null || client == null) {
                throw new RuntimeException(NAME + " is an internal script and must not be used directly.");
            }

            return new FeatureStoreProvider(loader.load(storeName, client));
        }

        @Override
        public boolean needsScores() {
            return false;
        }

        @Override
        public String getName() {
            return NAME;
        }
    }

    public static FeatureStore findFeatureStore(String storeName, QueryShardContext context) {
        Map<String, Object> params = new HashMap<>();
        params.put("store", storeName);
        params.put("client", context.getClient());
        Script script = new Script(ScriptType.INLINE, NativeScriptEngineService.NAME, NAME, params);
        return (FeatureStore) context.getExecutableScript(script, ScriptContext.Standard.SEARCH).run();
    }

    @FunctionalInterface
    public interface FeatureStoreLoader {
        FeatureStore load(String storeName, Client client);
    }

    public static FeatureStoreLoader defaultFeatureStoreLoad(Caches caches, LtrRankerParserFactory factory) {
        return (storeName, client) -> new CachedFeatureStore(new IndexFeatureStore(storeName, client, factory), caches);
    }
}