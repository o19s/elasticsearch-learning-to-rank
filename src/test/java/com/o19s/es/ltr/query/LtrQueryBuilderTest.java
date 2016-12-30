package com.o19s.es.ltr.query;

import com.o19s.es.ltr.query.LtrQuery;
import com.o19s.es.ltr.query.LtrQueryBuilder;
import com.o19s.es.ltr.query.LtrQueryParserPlugin;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.index.query.QueryBuilder;
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

    static String simpleModel = "## LambdaMART\\n" +
            "## name:foo\\n" +
            "## No. of trees = 1\\n" +
            "## No. of leaves = 10\\n" +
            "## No. of threshold candidates = 256\\n" +
            "## Learning rate = 0.1\\n" +
            "## Stop early = 100\\n" +
            "\\n" +
            "<ensemble>\\n" +
            " <tree id=\\\"1\\\" weight=\\\"0.1\\\">\\n" +
            "  <split>\\n" +
            "   <feature> 1 </feature>\\n" +
            "   <threshold> 0.45867884 </threshold>\\n" +
            "   <split pos=\\\"left\\\">\\n" +
            "    <feature> 1 </feature>\\n" +
            "    <threshold> 0.0 </threshold>\\n" +
            "    <split pos=\\\"left\\\">\\n" +
            "     <output> -2.0 </output>\\n" +
            "    </split>\\n" +
            "    <split pos=\\\"right\\\">\\n" +
            "     <output> -1.3413081169128418 </output>\\n" +
            "    </split>\\n" +
            "   </split>\\n" +
            "   <split pos=\\\"right\\\">\\n" +
            "    <feature> 1 </feature>\\n" +
            "    <threshold> 0.6115718 </threshold>\\n" +
            "    <split pos=\\\"left\\\">\\n" +
            "     <output> 0.3089442849159241 </output>\\n" +
            "    </split>\\n" +
            "    <split pos=\\\"right\\\">\\n" +
            "     <output> 2.0 </output>\\n" +
            "    </split>\\n" +
            "   </split>\\n" +
            "  </split>\\n" +
            " </tree>\\n" +
            "</ensemble>";

    @Test
    public void testCachedQueryParsing() throws IOException {
        String scriptSpec = "{\"inline\": \"" + simpleModel + "\"}";

        String ltrQuery =       "{  " +
                                "   \"ltr\": {" +
                                "      \"model\": " + scriptSpec + ",        " +
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
        String scriptSpec = "{\"inline\": \"" + simpleModel + "\"}";

        String ltrQuery =       "{  " +
                "   \"ltr\": {" +
                "      \"model\": " + scriptSpec + "," +
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
        LtrQueryBuilder queryBuilder = null;
        try {
            queryBuilder = (LtrQueryBuilder)parseQuery(ltrQuery, ParseFieldMatcher.EMPTY);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return queryBuilder;
    }



    @Override
    protected void doAssertLuceneQuery(LtrQueryBuilder queryBuilder, Query query, SearchContext context) throws IOException {
        assertThat(query, instanceOf(LtrQuery.class));
    }
}
