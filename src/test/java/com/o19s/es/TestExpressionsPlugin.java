package com.o19s.es;

import org.apache.lucene.expressions.Bindings;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.Rescorer;
import org.apache.lucene.search.SortField;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.DoubleValuesScript;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.test.PrivilegedOperations;

import java.text.ParseException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class TestExpressionsPlugin extends Plugin implements ScriptPlugin {
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new ExpressionScriptEngine();
    }

    public static class ExpressionScriptEngine implements ScriptEngine {
        private static Map<ScriptContext<?>, Function<Expression, Object>> contexts = Map.of(
            DoubleValuesScript.CONTEXT,
            (Expression expr) -> new ExpressionDoubleValuesScript(expr) {
                @Override
                public boolean isResultDeterministic() {
                    return true;
                }
            }
        );

        @Override
        public String getType() {
            return "expression";
        }

        @Override
        public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> params) {
            T result = PrivilegedOperations.supplierWithCreateClassLoader(
                    () -> compileInternal(scriptSource, context)
            );

            return result;
        }

        public <T> T compileInternal(String scriptSource, ScriptContext<T> context) {
            try {
                var expr = JavascriptCompiler.compile(scriptSource, JavascriptCompiler.DEFAULT_FUNCTIONS, getClass().getClassLoader());
                if (contexts.containsKey(context) == false) {
                    throw new IllegalArgumentException("expression engine does not know how to handle script context [" + context.name + "]");
                }
                return context.factoryClazz.cast(contexts.get(context).apply(expr));
            } catch (ParseException e) {
                throw new RuntimeException("compile error: " + scriptSource, e);
            }
        }

        @Override
        public Set<ScriptContext<?>> getSupportedContexts() {
            return contexts.keySet();
        }
    }

    public static class ExpressionDoubleValuesScript implements DoubleValuesScript.Factory {
        private final Expression exprScript;

        ExpressionDoubleValuesScript(Expression e) {
            this.exprScript = e;
        }

        @Override
        public DoubleValuesScript newInstance() {
            return new DoubleValuesScript() {
                @Override
                public double execute() {
                    return exprScript.evaluate(new DoubleValues[0]);
                }

                @Override
                public double evaluate(DoubleValues[] functionValues) {
                    return exprScript.evaluate(functionValues);
                }

                @Override
                public DoubleValuesSource getDoubleValuesSource(Function<String, DoubleValuesSource> sourceProvider) {
                    return exprScript.getDoubleValuesSource(new Bindings() {
                        @Override
                        public DoubleValuesSource getDoubleValuesSource(String name) {
                            return sourceProvider.apply(name);
                        }
                    });
                }

                @Override
                public SortField getSortField(Function<String, DoubleValuesSource> sourceProvider, boolean reverse) {
                    return exprScript.getSortField(new Bindings() {
                        @Override
                        public DoubleValuesSource getDoubleValuesSource(String name) {
                            return sourceProvider.apply(name);
                        }
                    }, reverse);
                }

                @Override
                public Rescorer getRescorer(Function<String, DoubleValuesSource> sourceProvider) {
                    return exprScript.getRescorer(new Bindings() {
                        @Override
                        public DoubleValuesSource getDoubleValuesSource(String name) {
                            return sourceProvider.apply(name);
                        }
                    });
                }

                @Override
                public String sourceText() {
                    return exprScript.sourceText;
                }

                @Override
                public String[] variables() {
                    return exprScript.variables;
                }
            };
        }
    }

}
