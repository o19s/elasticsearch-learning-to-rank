/*
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
 *
 */
package com.o19s.es.explore;

import com.o19s.es.ltr.LtrQueryParserPlugin;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.instanceOf;

public class ExplorerQueryBuilderTests extends AbstractQueryTestCase<ExplorerQueryBuilder> {
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singletonList(LtrQueryParserPlugin.class);
    }

    @Override
    protected ExplorerQueryBuilder doCreateTestQueryBuilder() {
        ExplorerQueryBuilder builder = new ExplorerQueryBuilder();
        builder.query(new TermQueryBuilder("foo", "bar"));
        builder.statsType("sum_raw_ttf");
        return builder;
    }

    public void testParse() throws Exception {
        String query = " {" +
                        "  \"match_explorer\": {" +
                        "    \"query\": {" +
                        "      \"match\": {" +
                        "        \"title\": \"test\"" +
                        "      }" +
                        "    }," +
                        "   \"type\": \"stddev_raw_tf\"" +
                        "  }" +
                        "}";

        ExplorerQueryBuilder builder = (ExplorerQueryBuilder)parseQuery(query);

        assertNotNull(builder.query());
        assertEquals(builder.statsType(), "stddev_raw_tf");
    }

    public void testMissingQuery() throws Exception {
        String query =  " {" +
                        "  \"match_explorer\": {" +
                        "   \"type\": \"stddev_raw_tf\"" +
                        "  }" +
                        "}";

        expectThrows(ParsingException.class, () -> parseQuery(query));
    }

    public void testMissingType() throws Exception {
        String query =  " {" +
                        "  \"match_explorer\": {" +
                        "    \"query\": {" +
                        "      \"match\": {" +
                        "        \"title\": \"test\"" +
                        "      }" +
                        "    }" +
                        "  }" +
                        "}";

        expectThrows(ParsingException.class, () -> parseQuery(query));
    }

    @Override
    protected void doAssertLuceneQuery(ExplorerQueryBuilder queryBuilder, Query query, SearchContext context) throws IOException {
        assertThat(query, instanceOf(ExplorerQuery.class));
    }
}
