package com.o19s.es.termstat;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.instanceOf;

import com.o19s.es.explore.StatisticsHelper.AggrType;
import com.o19s.es.ltr.LtrQueryParserPlugin;
import java.io.IOException;
import java.util.Collection;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.elasticsearch.test.TestGeoShapeFieldMapperPlugin;

public class TermStatQueryBuilderTests extends AbstractQueryTestCase<TermStatQueryBuilder> {
  // TODO: Remove the TestGeoShapeFieldMapperPlugin once upstream has completed the migration.
  protected Collection<Class<? extends Plugin>> getPlugins() {
    return asList(LtrQueryParserPlugin.class, TestGeoShapeFieldMapperPlugin.class);
  }

  @Override
  protected TermStatQueryBuilder doCreateTestQueryBuilder() {
    TermStatQueryBuilder builder = new TermStatQueryBuilder();

    builder.analyzer("standard");
    builder.expr("tf");
    builder.aggr(AggrType.AVG.getType());
    builder.posAggr(AggrType.AVG.getType());
    builder.fields(new String[] {"text"});
    builder.terms(new String[] {"cow"});

    return builder;
  }

  public void testParse() throws Exception {
    String query =
        " {"
            + "  \"term_stat\": {"
            + "   \"expr\": \"tf\","
            + "   \"aggr\": \"min\","
            + "   \"pos_aggr\": \"max\","
            + "   \"fields\": [\"text\"],"
            + "   \"terms\":  [\"cow\"]"
            + "  }"
            + "}";

    TermStatQueryBuilder builder = (TermStatQueryBuilder) parseQuery(query);

    assertEquals(builder.expr(), "tf");
    assertEquals(builder.aggr(), "min");
    assertEquals(builder.posAggr(), "max");
  }

  public void testMissingExpr() throws Exception {
    String query =
        " {"
            + "  \"term_stat\": {"
            + "   \"aggr\": \"min\","
            + "   \"pos_aggr\": \"max\","
            + "   \"fields\": [\"text\"],"
            + "   \"terms\": [\"cow\"]"
            + "  }"
            + "}";

    expectThrows(ParsingException.class, () -> parseQuery(query));
  }

  @Override
  protected void doAssertLuceneQuery(
      TermStatQueryBuilder queryBuilder, Query query, SearchExecutionContext context)
      throws IOException {
    assertThat(query, instanceOf(TermStatQuery.class));
  }
}
