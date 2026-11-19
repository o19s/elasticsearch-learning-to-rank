package com.o19s.es;

import com.o19s.es.ltr.utils.Scripting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.script.DoubleValuesScript;
import org.elasticsearch.script.MockScriptService;

import java.util.Map;

/**
 * Initializes {@link Scripting} for unit tests. In production the script service is injected
 * by {@code LtrQueryParserPlugin#createComponents}, which unit tests never call.
 */
public final class TestScripting {
    private TestScripting() {}

    public static void init() {
        Scripting.initScriptService(new MockScriptService(
                Settings.EMPTY,
                Map.of("expression", new TestExpressionsPlugin.ExpressionScriptEngine()),
                Map.of(DoubleValuesScript.CONTEXT.name, DoubleValuesScript.CONTEXT)));
    }
}
