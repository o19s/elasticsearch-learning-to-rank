package com.o19s.es.termstat;

import com.o19s.es.ltr.LtrQueryParserPlugin;
import com.o19s.es.termstat.TermStatQuery;
import com.o19s.es.termstat.TermStatQueryBuilder;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.AbstractQueryTestCase;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.instanceOf;

public class TermStatQueryBuilderTests extends AbstractQueryTestCase<TermStatQueryBuilder> {
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singletonList(LtrQueryParserPlugin.class);
    }

    @Override
    protected TermStatQueryBuilder doCreateTestQueryBuilder() {
        TermStatQueryBuilder builder = new TermStatQueryBuilder();

        Set<Term> terms = new HashSet<Term>();
        terms.add(new Term("text", "cow"));
        builder.expr("tf");
        builder.aggr("avg");
        builder.posAggr("avg");
        builder.terms(terms);

        return builder;
    }

    public void testParse() throws Exception {
        String query = " {" +
                "  \"term_stat\": {" +
                "   \"expr\": \"tf\"," +
                "   \"aggr\": \"min\"," +
                "   \"pos_aggr\": \"max\"," +
                "   \"terms\": {}" +
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
                "   \"terms\": {}" +
                "  }" +
                "}";

        expectThrows(ParsingException.class, () -> parseQuery(query));
    }

    @Override
    protected void doAssertLuceneQuery(TermStatQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        assertThat(query, instanceOf(TermStatQuery.class));
    }
}
