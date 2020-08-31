package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper.AggrType;

import com.o19s.es.ltr.LtrQueryParserPlugin;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.index.query.QueryShardContext;
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

        builder.analyzer("standard");
        builder.expr("tf");
        builder.aggr(AggrType.AVG.getType());
        builder.posAggr(AggrType.AVG.getType());
        builder.fields(new String[]{"text"});
        builder.terms("cow");

        return builder;
    }

    public void testParse() throws Exception {
        String query = " {" +
                "  \"term_stat\": {" +
                "   \"expr\": \"tf\"," +
                "   \"aggr\": \"min\"," +
                "   \"pos_aggr\": \"max\"," +
                "   \"fields\": [\"text\"]," +
                "   \"terms\":  \"cow\"" +
                "  }" +
                "}";

        TermStatQueryBuilder builder = (TermStatQueryBuilder) parseQuery(query);

        assertEquals(builder.expr(), "tf");
        assertEquals(builder.aggr(), "min");
        assertEquals(builder.posAggr(), "max");

    }

    public void testMissingExpr() throws Exception {
        String query = " {" +
                "  \"term_stat\": {" +
                "   \"aggr\": \"min\"," +
                "   \"pos_aggr\": \"max\"," +
                "   \"fields\": [\"text\"]," +
                "   \"terms\": \"cow\"" +
                "  }" +
                "}";

        expectThrows(ParsingException.class, () -> parseQuery(query));
    }

    @Override
    protected void doAssertLuceneQuery(TermStatQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        assertThat(query, instanceOf(TermStatQuery.class));
    }
}
