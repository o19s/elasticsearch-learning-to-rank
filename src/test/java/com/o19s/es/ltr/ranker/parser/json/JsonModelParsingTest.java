/*
 * Copyright [2017] OpenSource Connections
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
package com.o19s.es.ltr.ranker.parser.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContentParser;

import java.io.IOException;

/**
 * Created by doug on 5/28/17.
 */
public class JsonModelParsingTest {
    private JsonFactory jsonFactory = new JsonFactory();

    protected XContentParser makeXContent(String jsonStr) throws IOException {
        JsonParser jsonParser = jsonFactory.createParser(jsonStr);
        return new JsonXContentParser(NamedXContentRegistry.EMPTY, jsonParser);
    }
}
