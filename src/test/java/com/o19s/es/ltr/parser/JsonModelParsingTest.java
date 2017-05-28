package com.o19s.es.ltr.parser;

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
