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

import org.apache.lucene.util.LuceneTestCase;

import static org.hamcrest.CoreMatchers.containsString;

public class LtrRankerParserFactoryTests extends LuceneTestCase {
    public void testGetParser() {
        LtrRankerParser parser = (set, model) -> null;
        LtrRankerParserFactory factory = new LtrRankerParserFactory.Builder()
                .register("model/test", () -> parser)
                .build();
        assertSame(parser, factory.getParser("model/test"));
        assertThat(expectThrows(IllegalArgumentException.class,
                () -> factory.getParser("model/foobar")).getMessage(),
                containsString("Unsupported LtrRanker format/type [model/foobar]"));
    }

    public void testDeclareMultiple() {
        LtrRankerParser parser = (set, model) -> null;
        LtrRankerParserFactory.Builder builder = new LtrRankerParserFactory.Builder()
                .register("model/test", () -> parser);
        expectThrows(RuntimeException.class,
                () -> builder.register("model/test", () -> parser));
    }

}