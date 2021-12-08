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

package com.o19s.es.ltr.logging;

import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.o19s.es.ltr.logging.LoggingSearchExtBuilder.parse;
import static org.hamcrest.CoreMatchers.containsString;

public class LoggingSearchExtBuilderTests extends ESTestCase {
    public LoggingSearchExtBuilder buildTestExt() {
        LoggingSearchExtBuilder builder = new LoggingSearchExtBuilder();
        builder.addQueryLogging("name1", "query1", true);
        builder.addQueryLogging(null, "query2", false);
        builder.addRescoreLogging("rescore0", 0, true);
        builder.addRescoreLogging(null, 1, false);
        return builder;
    }

    public String getTestExtAsString() {
        return "{\"log_specs\":[" +
                "{\"name\":\"name1\",\"named_query\":\"query1\",\"missing_as_zero\":true}," +
                "{\"named_query\":\"query2\"}," +
                "{\"name\":\"rescore0\",\"rescore_index\":0,\"missing_as_zero\":true}," +
                "{\"rescore_index\":1}]}";
    }

    public void testEquals() {
        LoggingSearchExtBuilder ext1 = buildTestExt();
        LoggingSearchExtBuilder ext2 = buildTestExt();
        assertTestExt(ext1);
        assertTestExt(ext2);
        assertEquals(ext1.hashCode(), ext2.hashCode());
        assertEquals(ext1, ext2);
    }

    public void testParse() throws IOException {
        XContentParser parser = createParser(JsonXContent.jsonXContent, getTestExtAsString());
        LoggingSearchExtBuilder ext = parse(parser);
        assertTestExt(ext);
    }

    public void testToXCtontent() throws IOException {
        LoggingSearchExtBuilder ext1 = buildTestExt();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        ext1.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
        builder.close();
        assertEquals("{\"ltr_log\":" + getTestExtAsString() + "}", Strings.toString(builder));
    }

    public void testSer() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        buildTestExt().writeTo(out);
        out.close();
        LoggingSearchExtBuilder ext = new LoggingSearchExtBuilder(out.bytes().streamInput());
        assertTestExt(ext);
    }

    public void testFailOnNoLogSpecs() throws IOException {
        String data = "{}";
        ParsingException exc = expectThrows(ParsingException.class,
                () ->parse(createParser(JsonXContent.jsonXContent, data)));
        assertThat(exc.getMessage(),
            containsString("should define at least one [log_specs]"));
    }

    public void testFailOnEmptyLogSpecs() throws IOException {
        String data = "{\"log_specs\":[]}";
        ParsingException exc = expectThrows(ParsingException.class,
                () ->parse(createParser(JsonXContent.jsonXContent, data)));
        assertThat(exc.getMessage(),
                containsString("should define at least one [log_specs]"));
    }

    public void testFailOnBadLogSpec() throws IOException {
        String data = "{\"log_specs\":[" +
                "{\"name\":\"name1\",\"missing_as_zero\":true}," +
                "]}";
        ParsingException exc = expectThrows(ParsingException.class,
                () ->parse(createParser(JsonXContent.jsonXContent, data)));
        assertThat(exc.getCause().getCause().getMessage(),
                containsString("Either [named_query] or [rescore_index] must be set"));
    }

    public void testFailOnNegativeRescoreIndex() throws IOException {
        String data = "{\"log_specs\":[" +
                "{\"name\":\"name1\",\"rescore_index\":-1, \"missing_as_zero\":true}," +
                "]}";
        ParsingException exc = expectThrows(ParsingException.class,
                () ->parse(createParser(JsonXContent.jsonXContent, data)));
        assertThat(exc.getCause().getCause().getMessage(),
                containsString("non-negative"));
    }

    public void assertTestExt(LoggingSearchExtBuilder actual) {
        List<LoggingSearchExtBuilder.LogSpec> logSpecs = actual.logSpecsStream().collect(Collectors.toList());
        assertEquals(4, logSpecs.size());
        LoggingSearchExtBuilder.LogSpec l = logSpecs.get(0);
        assertEquals("name1", l.getLoggerName());
        assertEquals("query1", l.getNamedQuery());
        assertNull(l.getRescoreIndex());
        assertTrue(l.isMissingAsZero());

        l = logSpecs.get(1);
        assertEquals("query2", l.getLoggerName());
        assertEquals("query2", l.getNamedQuery());
        assertNull(l.getRescoreIndex());
        assertFalse(l.isMissingAsZero());

        l = logSpecs.get(2);
        assertEquals("rescore0", l.getLoggerName());
        assertNull(l.getNamedQuery());
        assertEquals((Integer) 0, l.getRescoreIndex());
        assertTrue(l.isMissingAsZero());

        l = logSpecs.get(3);
        assertEquals("rescore[1]", l.getLoggerName());
        assertNull(l.getNamedQuery());
        assertEquals((Integer) 1, l.getRescoreIndex());
        assertFalse(l.isMissingAsZero());
    }
}
