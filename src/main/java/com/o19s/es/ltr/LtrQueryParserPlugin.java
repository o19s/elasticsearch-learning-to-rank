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
import com.o19s.es.ltr.feature.store.index.Caches;
import com.o19s.es.ltr.query.LtrQueryBuilder;
import com.o19s.es.ltr.query.StoredLtrQueryBuilder;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import com.o19s.es.ltr.ranker.ranklib.RankLibScriptEngine;
import com.o19s.es.ltr.ranker.ranklib.RanklibModelParser;
import com.o19s.es.ltr.utils.FeatureStoreProvider;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.script.NativeScriptFactory;
import org.elasticsearch.script.ScriptEngineService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LtrQueryParserPlugin extends Plugin implements SearchPlugin, ScriptPlugin {
    private final LtrRankerParserFactory parserFactory;
    private final Caches caches;
    // Lazy loaded
    private RankerFactory ranklibFactory;

    public LtrQueryParserPlugin(Settings settings) {
        caches = new Caches(settings);
        parserFactory = new LtrRankerParserFactory.Builder()
                .register(RanklibModelParser.TYPE, () -> new RanklibModelParser(getRanklibFactory()))
                .build();
    }

    @Override
    public List<QuerySpec<?>> getQueries() {
        return Arrays.asList(
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

    protected FeatureStoreProvider.FeatureStoreLoader getFeatureStoreLoader() {
        return FeatureStoreProvider.defaultFeatureStoreLoad(caches, parserFactory);
    }

    private RankerFactory getRanklibFactory() {
        if (ranklibFactory == null) {
            ranklibFactory = new RankerFactory();
        }
        return  ranklibFactory;
    }
}
