package com.o19s.es.ltr.query;

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
 */
public class RankLibScriptEngine extends AbstractComponent implements ScriptEngineService {

    private RankerFactory rankerFactory;

    public static final String NAME = "ranklib";
    public static final String EXTENSION = "ranklib";

    public RankLibScriptEngine(Settings settings) {
        super(settings);
        rankerFactory = new CachingRankerFactory();
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
