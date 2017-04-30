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

package com.o19s.es.ltr;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.CompiledScript;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.ScriptEngineService;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Very simple Script plugin to mock mustaphe
 * This is not even close to the real mustaphe but should cover the simple
 * cases needed for testing the query builders.
 */
public class MockMustachePlugin extends Plugin implements ScriptPlugin {
    @Override
    public ScriptEngineService getScriptEngineService(Settings settings) {
        return new ScriptEngineService() {
            @Override
            public String getType() {
                return "mustache";
            }

            @Override
            public String getExtension() {
                return "mustache";
            }

            @Override
            public Object compile(String scriptName, String scriptSource, Map<String, String> params) {
                return CompiledTemplate.compile(scriptSource);
            }

            @Override
            public ExecutableScript executable(CompiledScript compiledScript, Map<String, Object> vars) {
                return new ExecutableScript() {
                    @Override
                    public void setNextVar(String name, Object value) {
                    }

                    @Override
                    public Object run() {
                        return ((CompiledTemplate)compiledScript.compiled()).apply(vars);
                    }
                };
            }

            @Override
            public SearchScript search(CompiledScript compiledScript, SearchLookup lookup, Map<String, Object> vars) {
                return null;
            }

            @Override
            public boolean isInlineScriptEnabled() {
                return true;
            }

            @Override
            public void close() throws IOException {
            }
        };
    }

    private static class CompiledTemplate {
        static final Pattern VAR_PATTERN = Pattern.compile("\\Q{{\\E([a-zA-Z0-9_.-]+)\\Q}}\\E");
        private final List<BiConsumer<StringBuilder, Map<String,Object>>> chunks;

        private CompiledTemplate(List<BiConsumer<StringBuilder, Map<String, Object>>> chunks) {
            this.chunks = chunks;
        }

        private static CompiledTemplate compile(String template) {
            List<BiConsumer<StringBuilder, Map<String,Object>>> chunks = new ArrayList<>();
            Matcher m = VAR_PATTERN.matcher(template);
            int so = 0;
            while (m.find()) {
                int offset = m.start();
                if (offset > so) {
                    chunks.add(new StringChunk(template.subSequence(so, offset)));
                }
                chunks.add(new VarChunk(m.group(1)));
                so = m.end();
            }
            if (so < template.length()) {
                chunks.add(new StringChunk(template.subSequence(so, template.length())));
            }
            return new CompiledTemplate(chunks);
        }

        String apply(Map<String,Object> params) {
            StringBuilder sb = new StringBuilder();
            for (BiConsumer<StringBuilder, Map<String,Object>> chunk : chunks) {
                chunk.accept(sb, params);
            }
            return sb.toString();
        }
    }

    public static class StringChunk implements BiConsumer<StringBuilder, Map<String,Object>> {
        private final CharSequence sub;

        public StringChunk(CharSequence sub) {
            this.sub = sub;
        }

        @Override
        public void accept(StringBuilder sb, Map<String, Object> params) {
            sb.append(sub);
        }
    }

    public static class VarChunk implements BiConsumer<StringBuilder, Map<String,Object>> {
        private final String var;

        public VarChunk(String var) {
            this.var = var;
        }

        @Override
        public void accept(StringBuilder sb, Map<String, Object> params) {
            Object o = params.get(var);
            if (o == null) {
                throw new IllegalArgumentException("Param [" + var + "] defined in the template but not found in the params.");
            }
            String value;
            if (o instanceof String) {
                value = ((String)o).replace("\\", "\\\\");
                value = value.replace("\"", "\\\"");
            } else if(o instanceof Number) {
                value = String.valueOf(o);
            } else {
                throw new IllegalArgumentException("Param [" + var +"] is not a simple type, only strings and numbers are allowed.");
            }
            sb.append(value);
        }
    }
}