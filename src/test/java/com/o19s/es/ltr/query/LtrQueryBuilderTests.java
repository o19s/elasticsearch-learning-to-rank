/*
 * Copyright [2016] Doug Turnbull
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
package com.o19s.es.ltr.query;

import com.o19s.es.ltr.LtrQueryParserPlugin;
import org.apache.lucene.search.Query;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.internal.SearchContext;
import org.elasticsearch.test.AbstractQueryTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;

/**
 * Created by doug on 12/27/16.
 */
public class LtrQueryBuilderTests extends AbstractQueryTestCase<LtrQueryBuilder> {

    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singletonList(LtrQueryParserPlugin.class);
    }

    private static final String simpleModel = "## LambdaMART\\n" +
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

    public void testCachedQueryParsing() throws IOException {
        String scriptSpec = "{\"source\": \"" + simpleModel + "\"}";

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
        LtrQueryBuilder queryBuilder = (LtrQueryBuilder)parseQuery(ltrQuery);
    }

    public void testNamedFeatures() throws IOException {
        String scriptSpec = "{\"source\": \"" + simpleModel + "\"}";

        String ltrQuery =       "{  " +
                "   \"ltr\": {" +
                "      \"model\": " + scriptSpec + ",        " +
                "      \"features\": [        " +
                "         {\"match\": {         " +
                "            \"foo\": {     " +
                "              \"query\": \"bar\", " +
                "              \"_name\": \"bar_query\" " +
                "         }}},                   " +
                "         {\"match\": {         " +
                "            \"baz\": {" +
                "            \"query\": \"sham\"," +
                "            \"_name\": \"sham_query\" " +
                "         }}}                   " +
                "      ]                      " +
                "   } " +
                "}";
        LtrQueryBuilder queryBuilder = (LtrQueryBuilder)parseQuery(ltrQuery);
        QueryShardContext context = createShardContext();
        RankerQuery query = (RankerQuery)queryBuilder.toQuery(context);
        assertEquals(query.getFeature(0).name(), "bar_query");
        assertEquals(query.getFeature(1).name(), "sham_query");

    }

    public void testUnnamedFeatures() throws IOException {
        String scriptSpec = "{\"source\": \"" + simpleModel + "\"}";

        String ltrQuery =       "{  " +
                "   \"ltr\": {" +
                "      \"model\": " + scriptSpec + ",        " +
                "      \"features\": [        " +
                "         {\"match\": {         " +
                "            \"foo\": {     " +
                "              \"query\": \"bar\" " +
                "         }}},                   " +
                "         {\"match\": {         " +
                "            \"baz\": {" +
                "            \"query\": \"sham\"," +
                "            \"_name\": \"\" " +
                "         }}}                   " +
                "      ]                      " +
                "   } " +
                "}";
        LtrQueryBuilder queryBuilder = (LtrQueryBuilder)parseQuery(ltrQuery);
        QueryShardContext context = createShardContext();
        RankerQuery query = (RankerQuery)queryBuilder.toQuery(context);
        assertNull(query.getFeature(0).name());
        assertEquals(query.getFeature(1).name(), "");

    }

    @Override
    protected boolean builderGeneratesCacheableQueries() {
        return false;
    }

    @Override
    public void testCacheability() throws IOException {
        LtrQueryBuilder queryBuilder = createTestQueryBuilder();
        QueryShardContext context = createShardContext();
        assert context.isCacheable();
        QueryBuilder rewritten = rewriteQuery(queryBuilder, new QueryShardContext(context));
        assertNotNull(rewritten.toQuery(context));
        assertFalse("query should not be cacheable: " + queryBuilder.toString(), context.isCacheable());
    }

    @Override
    protected LtrQueryBuilder doCreateTestQueryBuilder() {
        LtrQueryBuilder builder = new LtrQueryBuilder();
        builder.features(Arrays.asList(
                new MatchQueryBuilder("foo", "bar"),
                new MatchQueryBuilder("baz", "sham")
        ));
        builder.rankerScript(new Script(ScriptType.INLINE, "ranklib",
                // Remove escape sequences
                simpleModel.replace("\\\"", "\"")
                        .replace("\\n", "\n"),
                Collections.emptyMap()));
        return builder;
    }

    /**
     * This test ensures that queries that need to be rewritten have dedicated tests.
     * These queries must override this method accordingly.
     */
    @Override
    public void testMustRewrite() throws IOException {
        Script script = new Script(ScriptType.INLINE, "ranklib", simpleModel, Collections.emptyMap());
        List<QueryBuilder> features = new ArrayList<>();
        boolean mustRewrite = false;
        int idx = 0;
        if (randomBoolean()) {
            idx++;
            features.add(new TermQueryBuilder("test", "test"));
        }
        if (randomBoolean()) {
            mustRewrite = true;
            features.add(new WrapperQueryBuilder(new TermsQueryBuilder("foo", "terms query feature").toString()));
        }
        if (randomBoolean()) {
            features.add(new TermQueryBuilder("test", "test"));
        }

        LtrQueryBuilder builder = new LtrQueryBuilder(script, features);
        QueryBuilder rewritten = builder.rewrite(createShardContext());
        if (!mustRewrite && features.isEmpty()) {
            // if it's empty we rewrite to match all
            assertEquals(rewritten, new MatchAllQueryBuilder());
        } else {
            LtrQueryBuilder rewrite = (LtrQueryBuilder) rewritten;
            if (mustRewrite) {
                assertNotSame(rewrite, builder);
                if (!builder.features().isEmpty()) {
                    assertEquals(builder.features().size(), rewrite.features().size());
                    assertSame(builder.rankerScript(), rewrite.rankerScript());
                    assertEquals(new TermsQueryBuilder("foo", "terms query feature"), rewrite.features().get(idx));
                }
            } else {
                assertSame(rewrite, builder);
            }
        }
    }

    @Override
    protected void doAssertLuceneQuery(LtrQueryBuilder queryBuilder, Query query, SearchContext context) throws IOException {
        assertThat(query, instanceOf(RankerQuery.class));
    }

}
