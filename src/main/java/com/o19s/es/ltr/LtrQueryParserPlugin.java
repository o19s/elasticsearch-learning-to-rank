/*
 * Copyright [2016] Doug Turnbull
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
package com.o19s.es.ltr;

import ciir.umass.edu.learning.RankerFactory;
import com.o19s.es.ltr.action.AddFeaturesToSetAction;
import com.o19s.es.ltr.action.CachesStatsAction;
import com.o19s.es.ltr.action.ClearCachesAction;
import com.o19s.es.ltr.action.CreateModelFromSetAction;
import com.o19s.es.ltr.action.FeatureStoreAction;
import com.o19s.es.ltr.action.TransportAddFeatureToSetAction;
import com.o19s.es.ltr.action.TransportCacheStatsAction;
import com.o19s.es.ltr.action.TransportClearCachesAction;
import com.o19s.es.ltr.action.TransportCreateModelFromSetAction;
import com.o19s.es.ltr.action.TransportFeatureStoreAction;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.Caches;
import com.o19s.es.ltr.query.LtrQueryBuilder;
import com.o19s.es.ltr.query.StoredLtrQueryBuilder;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import com.o19s.es.ltr.ranker.ranklib.RankLibScriptEngine;
import com.o19s.es.ltr.ranker.ranklib.RanklibModelParser;
import com.o19s.es.ltr.rest.RestAddFeatureToSet;
import com.o19s.es.ltr.rest.RestCreateModelFromSet;
import com.o19s.es.ltr.rest.RestFeatureStoreCaches;
import com.o19s.es.ltr.rest.RestSimpleFeatureStore;
import com.o19s.es.ltr.utils.FeatureStoreProvider;
import com.o19s.es.ltr.utils.Suppliers;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry.Entry;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.script.NativeScriptFactory;
import org.elasticsearch.script.ScriptEngineService;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public class LtrQueryParserPlugin extends Plugin implements SearchPlugin, ScriptPlugin, ActionPlugin {
    private final LtrRankerParserFactory parserFactory;
    private final Caches caches;

    public LtrQueryParserPlugin(Settings settings) {
        caches = new Caches(settings);
        parserFactory = buildParserFactory();
    }

    public static LtrRankerParserFactory buildParserFactory() {
        // Use memoize to Lazy load the RankerFactory as it's a heavy object to construct
        Supplier<RankerFactory> ranklib = Suppliers.memoize(RankerFactory::new);
        return new LtrRankerParserFactory.Builder()
                .register(RanklibModelParser.TYPE, () -> new RanklibModelParser(ranklib.get()))
                .build();
    }

    @Override
    public List<QuerySpec<?>> getQueries() {
        return asList(
                new QuerySpec<>(LtrQueryBuilder.NAME, LtrQueryBuilder::new, LtrQueryBuilder::fromXContent),
                new QuerySpec<>(StoredLtrQueryBuilder.NAME, StoredLtrQueryBuilder::new, StoredLtrQueryBuilder::fromXContent));
    }

    @Override
    public ScriptEngineService getScriptEngineService(Settings settings) {
        return new RankLibScriptEngine(settings, parserFactory);
    }

    /**
     * Returns a list of {@link NativeScriptFactory} instances.
     */
    @Override
    public List<NativeScriptFactory> getNativeScripts() {
        return Collections.singletonList(new FeatureStoreProvider.Factory(getFeatureStoreLoader()));
    }

    @Override
    public List<RestHandler> getRestHandlers(Settings settings, RestController restController,
                                             ClusterSettings clusterSettings, IndexScopedSettings indexScopedSettings,
                                             SettingsFilter settingsFilter, IndexNameExpressionResolver indexNameExpressionResolver,
                                             Supplier<DiscoveryNodes> nodesInCluster) {
        List<RestHandler> list = new ArrayList<>();
        RestSimpleFeatureStore.register(list, settings, restController);
        list.add(new RestFeatureStoreCaches(settings, restController));
        list.add(new RestCreateModelFromSet(settings, restController));
        list.add(new RestAddFeatureToSet(settings, restController));
        return unmodifiableList(list);
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return unmodifiableList(asList(
                new ActionHandler<>(FeatureStoreAction.INSTANCE, TransportFeatureStoreAction.class),
                new ActionHandler<>(CachesStatsAction.INSTANCE, TransportCacheStatsAction.class),
                new ActionHandler<>(ClearCachesAction.INSTANCE, TransportClearCachesAction.class),
                new ActionHandler<>(AddFeaturesToSetAction.INSTANCE, TransportAddFeatureToSetAction.class),
                new ActionHandler<>(CreateModelFromSetAction.INSTANCE, TransportCreateModelFromSetAction.class)));
    }

    @Override
    public Collection<Object> createComponents(Client client, ClusterService clusterService, ThreadPool threadPool,
                                               ResourceWatcherService resourceWatcherService, ScriptService scriptService,
                                               NamedXContentRegistry xContentRegistry) {
        return asList(caches, parserFactory);
    }

    @Override
    public List<Entry> getNamedWriteables() {
        return unmodifiableList(asList(
                new Entry(StorableElement.class, StoredFeature.TYPE, StoredFeature::new),
                new Entry(StorableElement.class, StoredFeatureSet.TYPE, StoredFeatureSet::new),
                new Entry(StorableElement.class, StoredLtrModel.TYPE, StoredLtrModel::new)
        ));
    }

    @Override
    public List<NamedXContentRegistry.Entry> getNamedXContent() {
        return unmodifiableList(asList(
                new NamedXContentRegistry.Entry(StorableElement.class, new ParseField(StoredFeature.TYPE), StoredFeature::parse),
                new NamedXContentRegistry.Entry(StorableElement.class, new ParseField(StoredFeatureSet.TYPE), StoredFeatureSet::parse),
                new NamedXContentRegistry.Entry(StorableElement.class, new ParseField(StoredLtrModel.TYPE), StoredLtrModel::parse)
        ));
    }

    protected FeatureStoreProvider.FeatureStoreLoader getFeatureStoreLoader() {
        return FeatureStoreProvider.defaultFeatureStoreLoad(caches, parserFactory);
    }

}
