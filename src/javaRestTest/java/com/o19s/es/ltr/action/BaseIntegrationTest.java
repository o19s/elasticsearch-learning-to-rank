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
import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import com.o19s.es.ltr.ranker.ranklib.RankLibScriptEngine;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexAction;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.DocReader;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.elasticsearch.test.TestGeoShapeFieldMapperPlugin;
import org.junit.Before;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static com.o19s.es.ltr.feature.store.ScriptFeature.EXTRA_LOGGING;
import static com.o19s.es.ltr.feature.store.ScriptFeature.FEATURE_VECTOR;

public abstract class BaseIntegrationTest extends ESSingleNodeTestCase {

    public static final ScriptContext<ScoreScript.Factory> AGGS_CONTEXT = new ScriptContext<>("aggs", ScoreScript.Factory.class);

    @Override
    // TODO: Remove the TestGeoShapeFieldMapperPlugin once upstream has completed the migration.
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Arrays.asList(LtrQueryParserPlugin.class, NativeScriptPlugin.class, InjectionScriptPlugin.class,
                TestGeoShapeFieldMapperPlugin.class);
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
        AcknowledgedResponse resp = client().admin().indices().prepareDelete(name).get();
        assertTrue(resp.isAcknowledged());
    }

    public void createDefaultStore() throws Exception {
        createStore(IndexFeatureStore.DEFAULT_STORE);
    }

    public FeatureStoreResponse addElement(StorableElement element,
                                           FeatureValidation validation) throws ExecutionException, InterruptedException {
        return addElement(element, validation, IndexFeatureStore.DEFAULT_STORE);
    }

    public FeatureStoreResponse addElement(StorableElement element, String store) throws ExecutionException, InterruptedException {
        return addElement(element, null, store);
    }

    public FeatureStoreResponse addElement(StorableElement element) throws ExecutionException, InterruptedException {
        return addElement(element, null, IndexFeatureStore.DEFAULT_STORE);
    }

    public <E extends StorableElement> E getElement(Class<E> clazz, String type, String name) throws IOException {
        return getElement(clazz, type, name, IndexFeatureStore.DEFAULT_STORE);
    }

    public <E extends StorableElement> E getElement(Class<E> clazz, String type, String name, String store) throws IOException {
        return new IndexFeatureStore(store, this::client, parserFactory()).getAndParse(name, clazz, type);
    }

    protected LtrRankerParserFactory parserFactory() {
        return getInstanceFromNode(LtrRankerParserFactory.class);
    }

    public FeatureStoreResponse addElement(StorableElement element,
                                           @Nullable FeatureValidation validation,
                                           String store) throws ExecutionException, InterruptedException {
        FeatureStoreRequestBuilder builder =
            new FeatureStoreRequestBuilder(client(), FeatureStoreAction.INSTANCE);
        builder.request().setStorableElement(element);
        builder.request().setAction(FeatureStoreAction.FeatureStoreRequest.Action.CREATE);
        builder.request().setStore(store);
        builder.request().setValidation(validation);
        FeatureStoreResponse response = builder.execute().get();
        assertEquals(1, response.getResponse().getVersion());
        assertEquals(IndexFeatureStore.ES_TYPE, response.getResponse().getType());
        assertEquals(DocWriteResponse.Result.CREATED, response.getResponse().getResult());
        assertEquals(element.id(), response.getResponse().getId());
        assertEquals(store, response.getResponse().getIndex());
        return response;
    }

    /*
        This is a mock scripting plugin to test out the injection behaviors of the ScriptFeature
     */
    public static class InjectionScriptPlugin extends Plugin implements ScriptPlugin {
        public static final String FEATURE_EXTRACTOR = "feature_extractor";

        @Override
        public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
            return new ScriptEngine() {
                /**
                 * The language name used in the script APIs to refer to this scripting backend.
                 */
                @Override
                public String getType() {
                    return "inject";
                }

                /**
                 * Compiles a script.
                 *
                 * @param scriptName   the name of the script. {@code null} if it is anonymous (inline).
                 *                     For a stored script, its the identifier.
                 * @param scriptSource    actual source of the script
                 * @param context the context this script will be used for
                 * @param params  compile-time parameters (such as flags to the compiler)
                 * @return A compiled script of the FactoryType from {@link ScriptContext}
                 */
                @SuppressWarnings("unchecked")
                @Override
                public <FactoryType> FactoryType compile(String scriptName, String scriptSource,
                                                         ScriptContext<FactoryType> context, Map<String, String> params) {
                    if (!context.equals(ScoreScript.CONTEXT) && (!context.equals(AGGS_CONTEXT))) {
                        throw new IllegalArgumentException(getType() + " scripts cannot be used for context [" + context.name
                                + "]");
                    }
                    // we use the script "source" as the script identifier
                    ScoreScript.Factory factory = (p, lookup) ->
                            new ScoreScript.LeafFactory() {
                                @Override
                                public ScoreScript newInstance(DocReader reader) throws IOException {
                                    return new ScoreScript(p, lookup, reader) {
                                        @Override
                                        public double execute(ExplanationHolder explainationHolder) {
                                            // For testing purposes just look for the "terms" key and see if stats were injected
                                            if(p.containsKey("termStats")) {
                                                AbstractMap<String, ArrayList<Float>> termStats = (AbstractMap<String,
                                                        ArrayList<Float>>) p.get("termStats");
                                                ArrayList<Float> dfStats = termStats.get("df");
                                                return dfStats.size() > 0 ? dfStats.get(0) : 0.0;
                                            } else {
                                                return 0.0;
                                            }
                                        }
                                    };
                                }

                                @Override
                                public boolean needs_score() {
                                    return false;
                                }
                            };

                    return context.factoryClazz.cast(factory);
                }

                @Override
                public Set<ScriptContext<?>> getSupportedContexts() {
                    return Collections.singleton(RankLibScriptEngine.CONTEXT);
                }
            };
        }
    }


    public static class NativeScriptPlugin extends Plugin implements ScriptPlugin {
        public static final String FEATURE_EXTRACTOR = "feature_extractor";

        @Override
        public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
            return new ScriptEngine() {
                /**
                 * The language name used in the script APIs to refer to this scripting backend.
                 */
                @Override
                public String getType() {
                    return "native";
                }

                /**
                 * Compiles a script.
                 *
                 * @param scriptName   the name of the script. {@code null} if it is anonymous (inline).
                 *                     For a stored script, its the identifier.
                 * @param scriptSource    actual source of the script
                 * @param context the context this script will be used for
                 * @param params  compile-time parameters (such as flags to the compiler)
                 * @return A compiled script of the FactoryType from {@link ScriptContext}
                 */
                @SuppressWarnings("unchecked")
                @Override
                public <FactoryType> FactoryType compile(String scriptName, String scriptSource,
                                                         ScriptContext<FactoryType> context, Map<String, String> params) {
                    if (!context.equals(ScoreScript.CONTEXT) && (!context.equals(AGGS_CONTEXT))) {
                        throw new IllegalArgumentException(getType() + " scripts cannot be used for context [" + context.name
                                + "]");
                    }
                    // we use the script "source" as the script identifier
                    if (FEATURE_EXTRACTOR.equals(scriptSource)) {
                        ScoreScript.Factory factory = (p, lookup) ->
                                new ScoreScript.LeafFactory() {
                                    final Map<String, Float> featureSupplier;
                                    final String dependentFeature;
                                    double extraMultiplier = 0.0d;

                                    public static final String DEPENDENT_FEATURE = "dependent_feature";
                                    public static final String EXTRA_SCRIPT_PARAM = "extra_multiplier";

                                    {
                                        if (!p.containsKey(FEATURE_VECTOR)) {
                                            throw new IllegalArgumentException("Missing parameter [" + FEATURE_VECTOR + "]");
                                        }
                                        if (!p.containsKey(EXTRA_LOGGING)) {
                                            throw new IllegalArgumentException("Missing parameter [" + EXTRA_LOGGING + "]");
                                        }
                                        if (!p.containsKey(DEPENDENT_FEATURE)) {
                                            throw new IllegalArgumentException("Missing parameter [depdendent_feature ]");
                                        }
                                        if (p.containsKey(EXTRA_SCRIPT_PARAM)) {
                                            extraMultiplier = Double.valueOf(p.get(EXTRA_SCRIPT_PARAM).toString());
                                        }
                                        featureSupplier = (Map<String, Float>) p.get(FEATURE_VECTOR);
                                        dependentFeature = p.get(DEPENDENT_FEATURE).toString();
                                    }

                                    @Override
                                    public ScoreScript newInstance(DocReader reader) throws IOException {
                                        return new ScoreScript(p, lookup, reader) {
                                            @Override
                                            public double execute(ExplanationHolder explainationHolder ) {
                                                return extraMultiplier == 0.0d ?
                                                        featureSupplier.get(dependentFeature) * 10 :
                                                        featureSupplier.get(dependentFeature) * extraMultiplier;
                                            }
                                        };
                                    }

                                    @Override
                                    public boolean needs_score() {
                                        return false;
                                    }
                                };

                        return context.factoryClazz.cast(factory);
                    }
                    else if (scriptSource.equals(FEATURE_EXTRACTOR + "_extra_logging")) {
                        ScoreScript.Factory factory = (p, lookup) ->
                                new ScoreScript.LeafFactory() {
                                    {
                                        if (!p.containsKey(FEATURE_VECTOR)) {
                                            throw new IllegalArgumentException("Missing parameter [" + FEATURE_VECTOR + "]");
                                        }
                                        if (!p.containsKey(EXTRA_LOGGING)) {
                                            throw new IllegalArgumentException("Missing parameter [" + EXTRA_LOGGING + "]");
                                        }
                                    }

                                    @Override
                                    public ScoreScript newInstance(DocReader reader) throws IOException {
                                        return new ScoreScript(p, lookup, reader) {

                                            @Override
                                            public double execute(ExplanationHolder explanation) {
                                                Map<String,Object> extraLoggingMap = ((Supplier<Map<String,Object>>) getParams()
                                                        .get(EXTRA_LOGGING)).get();
                                                if (extraLoggingMap != null) {
                                                    extraLoggingMap.put("extra_float", 10.0f);
                                                    extraLoggingMap.put("extra_string", "additional_info");
                                                }
                                                return 1.0d;
                                            }
                                        };
                                    }

                                    @Override
                                    public boolean needs_score() {
                                        return false;
                                    }
                                };

                        return context.factoryClazz.cast(factory);
                    }
                    throw new IllegalArgumentException("Unknown script name " + scriptSource);
                }

                @Override
                public Set<ScriptContext<?>> getSupportedContexts() {
                    return Collections.singleton(RankLibScriptEngine.CONTEXT);
                }
            };
        }
    }
}
