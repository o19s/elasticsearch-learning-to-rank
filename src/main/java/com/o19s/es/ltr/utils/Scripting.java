/*
 * Copyright [2017] Wikimedia Foundation
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
 */
package com.o19s.es.ltr.utils;

import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.script.ClassPermission;
import org.elasticsearch.script.ScriptException;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Scripting {
    private Scripting() {}

    public static Object compile(String scriptSource) {
        return compile(scriptSource, JavascriptCompiler.DEFAULT_FUNCTIONS);
    }

    public static Object compile(String scriptSource, Map<String,java.lang.reflect.Method> functions) {
        // classloader created here
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SpecialPermission());
        }
        return AccessController.doPrivileged(new PrivilegedAction<Expression>() {
            @Override
            public Expression run() {
                try {
                    // snapshot our context here, we check on behalf of the expression
                    AccessControlContext engineContext = AccessController.getContext();
                    ClassLoader loader = getClass().getClassLoader();
                    if (sm != null) {
                        loader = new ClassLoader(loader) {
                            @Override
                            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                                try {
                                    engineContext.checkPermission(new ClassPermission(name));
                                } catch (SecurityException e) {
                                    throw new ClassNotFoundException(name, e);
                                }
                                return super.loadClass(name, resolve);
                            }
                        };
                    }
                    // NOTE: validation is delayed to allow runtime vars, and we don't have access to per index stuff here
                    return JavascriptCompiler.compile(scriptSource, functions, loader);
                } catch (ParseException e) {
                    throw convertToScriptException("compile error", scriptSource, scriptSource, e);
                }
            }
        });
    }

    private static ScriptException convertToScriptException(String message, String source, String portion, Throwable cause) {
        List<String> stack = new ArrayList<>();
        stack.add(portion);
        StringBuilder pointer = new StringBuilder();
        if (cause instanceof ParseException) {
            int offset = ((ParseException) cause).getErrorOffset();
            for (int i = 0; i < offset; i++) {
                pointer.append(' ');
            }
        }
        pointer.append("^---- HERE");
        stack.add(pointer.toString());
        throw new ScriptException(message, cause, stack, source, "STATIC_COMPILER");
    }
}
