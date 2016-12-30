package com.o19s.es.ltr.query;

import com.o19s.es.ltr.query.LtrQuery;
import com.o19s.es.ltr.query.LtrQueryBuilder;
import com.o19s.es.ltr.query.LtrQueryParserPlugin;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.instanceOf;


import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * Created by doug on 12/27/16.
 */
public class LtrQueryBuilderTest extends AbstractQueryTestCase<LtrQueryBuilder> {

    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singletonList(LtrQueryParserPlugin.class);
    }

    static String rankLibModel = "foo";

    @Test
    public void testQueryParsing() throws IOException {
        String ltrQuery =       "{  " +
                                "   \"ltr\": {" +
                                "      \"model\": \"apple\",        " +
                                "      \"features\": [        " +
                                "         {\"match\": {         " +
                                "            \"foo\": \"bar\"     " +
                                "         }},                   " +
                                "         {\"match\": {         " +
                                "            \"baz\": \"sham\"     " +
                                "         }}                   " +
                                "      ]                      " +
                                "   } " +
                                "}";
        LtrQueryBuilder queryBuilder = (LtrQueryBuilder)parseQuery(ltrQuery, ParseFieldMatcher.EMPTY);
    }


    @Override
    protected LtrQueryBuilder doCreateTestQueryBuilder() {
        return new LtrQueryBuilder();
    }

    @Override
    protected void doAssertLuceneQuery(LtrQueryBuilder queryBuilder, Query query, SearchContext context) throws IOException {
        assertThat(query, instanceOf(LtrQuery.class));
    }
}
