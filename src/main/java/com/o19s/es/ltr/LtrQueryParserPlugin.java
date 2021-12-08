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
import com.o19s.es.explore.ExplorerQueryBuilder;
import com.o19s.es.ltr.action.AddFeaturesToSetAction;
import com.o19s.es.ltr.action.CachesStatsAction;
import com.o19s.es.ltr.action.ClearCachesAction;
import com.o19s.es.ltr.action.CreateModelFromSetAction;
import com.o19s.es.ltr.action.FeatureStoreAction;
import com.o19s.es.ltr.action.LTRStatsAction;
import com.o19s.es.ltr.action.ListStoresAction;
import com.o19s.es.ltr.action.TransportAddFeatureToSetAction;
import com.o19s.es.ltr.action.TransportCacheStatsAction;
import com.o19s.es.ltr.action.TransportClearCachesAction;
import com.o19s.es.ltr.action.TransportCreateModelFromSetAction;
import com.o19s.es.ltr.action.TransportFeatureStoreAction;
import com.o19s.es.ltr.action.TransportLTRStatsAction;
import com.o19s.es.ltr.action.TransportListStoresAction;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.CachedFeatureStore;
import com.o19s.es.ltr.feature.store.index.Caches;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.logging.LoggingFetchSubPhase;
import com.o19s.es.ltr.logging.LoggingSearchExtBuilder;
import com.o19s.es.ltr.query.LtrQueryBuilder;
import com.o19s.es.ltr.query.StoredLtrQueryBuilder;
import com.o19s.es.ltr.query.ValidatingLtrQueryBuilder;
import com.o19s.es.ltr.ranker.parser.LinearRankerParser;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import com.o19s.es.ltr.ranker.parser.XGBoostJsonParser;
import com.o19s.es.ltr.ranker.ranklib.RankLibScriptEngine;
import com.o19s.es.ltr.ranker.ranklib.RanklibModelParser;
import com.o19s.es.ltr.rest.RestCreateModelFromSet;
import com.o19s.es.ltr.rest.RestFeatureManager;
import com.o19s.es.ltr.rest.RestSearchStoreElements;
import com.o19s.es.ltr.rest.RestStoreManager;
import com.o19s.es.ltr.rest.RestAddFeatureToSet;
import com.o19s.es.ltr.rest.RestFeatureStoreCaches;
import com.o19s.es.ltr.rest.RestLTRStats;
import com.o19s.es.ltr.stats.LTRStat;
import com.o19s.es.ltr.stats.LTRStats;
import com.o19s.es.ltr.stats.StatName;
import com.o19s.es.ltr.stats.suppliers.CacheStatsOnNodeSupplier;
import com.o19s.es.ltr.stats.suppliers.PluginHealthStatusSupplier;
import com.o19s.es.ltr.stats.suppliers.StoreStatsSupplier;
import com.o19s.es.ltr.utils.FeatureStoreLoader;
import com.o19s.es.ltr.utils.Suppliers;
import com.o19s.es.termstat.TermStatQueryBuilder;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.miscellaneous.LengthFilter;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenFilter;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.core.CheckedFunction;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry.Entry;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.PreConfiguredTokenFilter;
import org.elasticsearch.index.analysis.PreConfiguredTokenizer;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.repositories.RepositoriesService;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.fetch.FetchSubPhase;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

public class LtrQueryParserPlugin extends Plugin implements SearchPlugin, ScriptPlugin, ActionPlugin, AnalysisPlugin {
    private final LtrRankerParserFactory parserFactory;
    private final Caches caches;

    public LtrQueryParserPlugin(Settings settings) {
        caches = new Caches(settings);
        // Use memoize to Lazy load the RankerFactory as it's a heavy object to construct
        Supplier<RankerFactory> ranklib = Suppliers.memoize(RankerFactory::new);
        parserFactory = new LtrRankerParserFactory.Builder()
                .register(RanklibModelParser.TYPE, () -> new RanklibModelParser(ranklib.get()))
                .register(LinearRankerParser.TYPE, LinearRankerParser::new)
                .register(XGBoostJsonParser.TYPE, XGBoostJsonParser::new)
                .build();
    }

    @Override
    public List<QuerySpec<?>> getQueries() {

        return asList(
                new QuerySpec<>(ExplorerQueryBuilder.NAME, ExplorerQueryBuilder::new, ExplorerQueryBuilder::fromXContent),
                new QuerySpec<>(LtrQueryBuilder.NAME, LtrQueryBuilder::new, LtrQueryBuilder::fromXContent),
                new QuerySpec<>(StoredLtrQueryBuilder.NAME,
                        (input) -> new StoredLtrQueryBuilder(getFeatureStoreLoader(), input),
                        (ctx) -> StoredLtrQueryBuilder.fromXContent(getFeatureStoreLoader(), ctx)),
                new QuerySpec<>(TermStatQueryBuilder.NAME, TermStatQueryBuilder::new, TermStatQueryBuilder::fromXContent),
                new QuerySpec<>(ValidatingLtrQueryBuilder.NAME,
                        (input) -> new ValidatingLtrQueryBuilder(input, parserFactory),
                        (ctx) -> ValidatingLtrQueryBuilder.fromXContent(ctx, parserFactory)));
    }

    @Override
    public List<FetchSubPhase> getFetchSubPhases(FetchPhaseConstructionContext context) {
        return singletonList(new LoggingFetchSubPhase());
    }

    @Override
    public List<SearchExtSpec<?>> getSearchExts() {
        return singletonList(
                new SearchExtSpec<>(LoggingSearchExtBuilder.NAME, LoggingSearchExtBuilder::new, LoggingSearchExtBuilder::parse));
    }

    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new RankLibScriptEngine(parserFactory);
    }

    @Override
    public List<RestHandler> getRestHandlers(Settings settings, RestController restController,
                                             ClusterSettings clusterSettings, IndexScopedSettings indexScopedSettings,
                                             SettingsFilter settingsFilter, IndexNameExpressionResolver indexNameExpressionResolver,
                                             Supplier<DiscoveryNodes> nodesInCluster) {
        List<RestHandler> list = new ArrayList<>();

        for (String type : ValidatingLtrQueryBuilder.SUPPORTED_TYPES) {
            list.add(new RestFeatureManager(type));
            list.add(new RestSearchStoreElements(type));
        }
        list.add(new RestStoreManager());

        list.add(new RestFeatureStoreCaches());
        list.add(new RestCreateModelFromSet());
        list.add(new RestAddFeatureToSet());
        list.add(new RestLTRStats());
        return unmodifiableList(list);
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return unmodifiableList(asList(
                new ActionHandler<>(FeatureStoreAction.INSTANCE, TransportFeatureStoreAction.class),
                new ActionHandler<>(CachesStatsAction.INSTANCE, TransportCacheStatsAction.class),
                new ActionHandler<>(ClearCachesAction.INSTANCE, TransportClearCachesAction.class),
                new ActionHandler<>(AddFeaturesToSetAction.INSTANCE, TransportAddFeatureToSetAction.class),
                new ActionHandler<>(CreateModelFromSetAction.INSTANCE, TransportCreateModelFromSetAction.class),
                new ActionHandler<>(ListStoresAction.INSTANCE, TransportListStoresAction.class),
                new ActionHandler<>(LTRStatsAction.INSTANCE, TransportLTRStatsAction.class)));
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
    public List<ScriptContext<?>> getContexts() {
        ScriptContext<?> contexts = RankLibScriptEngine.CONTEXT;
        return Collections.singletonList(contexts);
    }

    @Override
    public List<NamedXContentRegistry.Entry> getNamedXContent() {
        return unmodifiableList(asList(
                new NamedXContentRegistry.Entry(StorableElement.class,
                        new ParseField(StoredFeature.TYPE),
                        (CheckedFunction<XContentParser, StorableElement, IOException>) StoredFeature::parse),
                new NamedXContentRegistry.Entry(StorableElement.class,
                        new ParseField(StoredFeatureSet.TYPE),
                        (CheckedFunction<XContentParser, StorableElement, IOException>) StoredFeatureSet::parse),
                new NamedXContentRegistry.Entry(StorableElement.class,
                        new ParseField(StoredLtrModel.TYPE),
                        (CheckedFunction<XContentParser, StorableElement, IOException>) StoredLtrModel::parse)
        ));
    }

    @Override
    public List<Setting<?>> getSettings() {
        return unmodifiableList(asList(
                IndexFeatureStore.STORE_VERSION_PROP,
                Caches.LTR_CACHE_MEM_SETTING,
                Caches.LTR_CACHE_EXPIRE_AFTER_READ,
                Caches.LTR_CACHE_EXPIRE_AFTER_WRITE));
    }

    @Override
    public Collection<Object> createComponents(Client client,
                                               ClusterService clusterService,
                                               ThreadPool threadPool,
                                               ResourceWatcherService resourceWatcherService,
                                               ScriptService scriptService,
                                               NamedXContentRegistry xContentRegistry,
                                               Environment environment,
                                               NodeEnvironment nodeEnvironment,
                                               NamedWriteableRegistry namedWriteableRegistry,
                                               IndexNameExpressionResolver indexNameExpressionResolver,
                                               Supplier<RepositoriesService> repositoriesServiceSupplier) {
        clusterService.addListener(event -> {
            for (Index i : event.indicesDeleted()) {
                if (IndexFeatureStore.isIndexStore(i.getName())) {
                    caches.evict(i.getName());
                }
            }
        });
        return asList(caches, parserFactory, getStats(client, clusterService, indexNameExpressionResolver));
    }

    private LTRStats getStats(Client client, ClusterService clusterService, IndexNameExpressionResolver indexNameExpressionResolver) {
        Map<String, LTRStat> stats = new HashMap<>();
        stats.put(StatName.CACHE.getName(),
                new LTRStat(false, new CacheStatsOnNodeSupplier(caches)));
        stats.put(StatName.STORES.getName(),
                new LTRStat(true, new StoreStatsSupplier(client, clusterService, indexNameExpressionResolver)));
        stats.put(StatName.PLUGIN_STATUS.getName(),
                new LTRStat(true, new PluginHealthStatusSupplier(clusterService, indexNameExpressionResolver)));
        return new LTRStats(unmodifiableMap(stats));
    }

    protected FeatureStoreLoader getFeatureStoreLoader() {
        return (storeName, clientSupplier) ->
            new CachedFeatureStore(new IndexFeatureStore(storeName, clientSupplier, parserFactory), caches);
    }

    // A simplified version of some token filters needed by the feature stores.
    // This is because some common filter have been moved to analysis-common module
    // which is not included in the integration test cluster.
    // Add a simple version of these token filter to make the plugin self contained.
    private static final int STORABLE_ELEMENT_MAX_NAME_SIZE = 512;

    @Override
    public List<PreConfiguredTokenFilter> getPreConfiguredTokenFilters() {
        return Arrays.asList(
                PreConfiguredTokenFilter.singleton("ltr_edge_ngram", true,
                        (ts) -> new EdgeNGramTokenFilter(ts, 1, STORABLE_ELEMENT_MAX_NAME_SIZE, false)),
                PreConfiguredTokenFilter.singleton("ltr_length", true,
                        (ts) -> new LengthFilter(ts, 0, STORABLE_ELEMENT_MAX_NAME_SIZE)));
    }

    public List<PreConfiguredTokenizer> getPreConfiguredTokenizers() {
        return Collections.singletonList(PreConfiguredTokenizer.singleton("ltr_keyword",
                () -> new KeywordTokenizer(KeywordTokenizer.DEFAULT_BUFFER_SIZE)));
    }
}
