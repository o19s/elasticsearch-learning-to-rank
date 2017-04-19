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
package com.o19s.es.ltr.ranker.ranklib;

import ciir.umass.edu.learning.Ranker;
import ciir.umass.edu.learning.RankerFactory;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.component.AbstractComponent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.script.CompiledScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.ScriptEngineService;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.util.Map;

/**
 * Created by doug on 12/30/16.
 * The ranklib models are treated like scripts in that they
 * can be run inline, cached, or stored in files
 * However, we don't use the script query because we need to execute
 * many underlying queries.
 *
 * So this code acts as a hook for deserializing Ranklib models from ranklib XML
 * and as a convenient means for caching those deserialized model
 */
public class RankLibScriptEngine extends AbstractComponent implements ScriptEngineService {

    private RankerFactory rankerFactory;

    public static final String NAME = "ranklib";
    public static final String EXTENSION = "ranklib";

    public RankLibScriptEngine(Settings settings) {
        super(settings);
        rankerFactory = new RankerFactory();
    }


    @Override
    public String getType() {
        return NAME;
    }

    @Override
    public String getExtension() {
        return EXTENSION;
    }


    @Override
    public boolean isInlineScriptEnabled() {
        return true;
    }

    @Override
    public Object compile(String scriptName, String scriptSource, Map<String, String> params) {
        return (Object) rankerFactory.loadRankerFromString(scriptSource);
    }

    @Override
    public ExecutableScript executable(CompiledScript compiledScript, @Nullable Map<String, Object> vars) {
        return new RankLibExecutableScript((Ranker)compiledScript.compiled());
    }

    @Override
    public SearchScript search(CompiledScript compiledScript, SearchLookup lookup, @Nullable Map<String, Object> vars) {
        return null;
    }

    @Override
    public void close() throws IOException {

    }

    public class RankLibExecutableScript implements ExecutableScript {

        Ranker _ranker;

        public RankLibExecutableScript(Ranker ranker) {
            _ranker = ranker;
        }

        @Override
        public void setNextVar(String name, Object value) {
            _ranker = (Ranker)(value);

        }

        @Override
        public Object run() {
            return _ranker;
        }
    }
}
