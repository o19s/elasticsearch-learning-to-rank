package com.o19s.es.termstat;

import com.o19s.es.explore.StatisticsHelper.AggrType;

import com.o19s.es.ltr.LtrQueryParserPlugin;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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
        builder.aggr(AggrType.AVG.getType());
        builder.posAggr(AggrType.AVG.getType());
        builder.query(new TermQueryBuilder("text", "cow"));

        return builder;
    }

    public void testParse() throws Exception {
        String query = " {" +
                "  \"term_stat\": {" +
                "   \"expr\": \"tf\"," +
                "   \"aggr\": \"min\"," +
                "   \"pos_aggr\": \"max\"," +
                "   \"query\": { \"match\": {\"text\": \"cow\"}}" +
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
                "   \"query\": { \"match\": {\"text\": \"cow\"}}" +
                "  }" +
                "}";

        expectThrows(ParsingException.class, () -> parseQuery(query));
    }

    @Override
    public void testMustRewrite() throws IOException {
        QueryShardContext context = createShardContext();
        context.setAllowUnmappedFields(true);
        TermStatQueryBuilder queryBuilder = createTestQueryBuilder();
        queryBuilder.boost(AbstractQueryBuilder.DEFAULT_BOOST);
        QueryBuilder rewritten = queryBuilder.rewrite(context);

        assertThat(rewritten, instanceOf(TermStatQueryBuilder.class));
        Query q = rewritten.toQuery(context);
        assertThat(q, instanceOf(TermStatQuery.class));
    }

    @Override
    protected void doAssertLuceneQuery(TermStatQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        assertThat(query, instanceOf(TermStatQuery.class));
    }
}
