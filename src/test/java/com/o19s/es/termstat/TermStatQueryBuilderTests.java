package com.o19s.es.termstat;

import com.o19s.es.ltr.LtrQueryParserPlugin;
import com.o19s.es.termstat.TermStatQuery;
import com.o19s.es.termstat.TermStatQueryBuilder;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.AbstractQueryTestCase;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.instanceOf;

public class TermStatQueryBuilderTests extends AbstractQueryTestCase<TermStatQueryBuilder> {
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singletonList(LtrQueryParserPlugin.class);
    }

    @Override
    protected TermStatQueryBuilder doCreateTestQueryBuilder() {
        TermStatQueryBuilder builder = new TermStatQueryBuilder();
        builder.query(new TermQueryBuilder("foo", "bar"));
        builder.expr("1.0");
        return builder;
    }

    public void testParse() throws Exception {
        String query = " {" +
                "  \"term_stat\": {" +
                "    \"query\": {" +
                "      \"match\": {" +
                "        \"title\": \"test\"" +
                "      }" +
                "    }," +
                "   \"expr\": \"1.0\"" +
                "  }" +
                "}";

        TermStatQueryBuilder builder = (TermStatQueryBuilder) parseQuery(query);

        assertNotNull(builder.query());
        assertEquals(builder.expr(), "1.0");
    }

    public void testMissingQuery() throws Exception {
        String query = " {" +
                "  \"term_stat\": {" +
                "   \"expr\": \"1.0\"" +
                "  }" +
                "}";

        expectThrows(ParsingException.class, () -> parseQuery(query));
    }

    public void testMissingExpr() throws Exception {
        String query = " {" +
                "  \"term_stat\": {" +
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
    protected void doAssertLuceneQuery(TermStatQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        assertThat(query, instanceOf(TermStatQuery.class));
    }
}
