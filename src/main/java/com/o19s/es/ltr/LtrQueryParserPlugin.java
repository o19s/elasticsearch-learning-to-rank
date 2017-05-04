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

import com.o19s.es.ltr.query.LtrQueryBuilder;
import com.o19s.es.ltr.ranker.ranklib.RankLibScriptEngine;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.script.ScriptEngineService;

import java.util.List;

import static java.util.Collections.singletonList;

public class LtrQueryParserPlugin extends Plugin implements SearchPlugin, ScriptPlugin {
    @Override
    public List<QuerySpec<?>> getQueries() {
        return singletonList(new QuerySpec<>(LtrQueryBuilder.NAME, LtrQueryBuilder::new, LtrQueryBuilder::fromXContent));
    }

    @Override
    /**
     * Returns a {@link ScriptEngineService} instance or <code>null</code> if this plugin doesn't add a new script engine
     */
    public ScriptEngineService getScriptEngineService(Settings settings) {
        return new RankLibScriptEngine(settings);
    }

}
