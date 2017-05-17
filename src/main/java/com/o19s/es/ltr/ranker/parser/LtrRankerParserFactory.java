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

package com.o19s.es.ltr.ranker.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * LtrModel parser registry
 */
public class LtrRankerParserFactory {
    private final Map<String, Supplier<LtrRankerParser>> parsers;

    private LtrRankerParserFactory(Map<String, Supplier<LtrRankerParser>> parsers) {
        this.parsers = parsers;
    }

    /**
     *
     * @param type type or content-type like string defining the model format
     * @return a model parser
     * @throws IllegalArgumentException if the type is not supported
     */
    public LtrRankerParser getParser(String type) {
        Supplier<LtrRankerParser> supplier = parsers.get(type);
        if (supplier == null) {
            throw new IllegalArgumentException("Unsupported LtrRanker format/type [" + type + "]");
        }
        return supplier.get();
    }

    public static class Builder {
        private final Map<String, Supplier<LtrRankerParser>> registry = new HashMap<>();

        public Builder register(String type, Supplier<LtrRankerParser> parser) {
            if (registry.put(type, parser) != null) {
                throw new RuntimeException("Cannot register LtrRankerParser: [" + type + "] already registered.");
            }
            return this;
        }

        public LtrRankerParserFactory build() {
            return new LtrRankerParserFactory(Collections.unmodifiableMap(registry));
        }
    }
}
