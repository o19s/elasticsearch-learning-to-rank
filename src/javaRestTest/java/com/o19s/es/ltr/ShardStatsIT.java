package com.o19s.es.ltr;

import com.o19s.es.explore.ExplorerQueryBuilder;
import com.o19s.es.termstat.TermStatQueryBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.test.ESIntegTestCase;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.equalTo;

/*
    These tests mostly verify that shard vs collection stat counting is working as expected.
 */
public class ShardStatsIT extends ESIntegTestCase {
    @Override
    protected int numberOfShards() {
        return 2;
    }

    protected void createIdx() {
        prepareCreate("idx")
                .addMapping("type", "s", "type=text");

        for (int i = 0; i < 4; i++) {
            indexDoc(i);
        }
        refreshIndex();
    }

    protected void indexDoc(int id) {
        client().prepareIndex("idx", "type", Integer.toString(id))
                .setRouting( ((id % 2) == 0 ) ? "a" : "b" )
                .setSource("s", "zzz").get();
    }

    protected void refreshIndex() {
        client().admin().indices().prepareRefresh("idx").get();
    }

    public void testDfsExplorer() throws Exception {
        createIdx();

        QueryBuilder q = new TermQueryBuilder("s", "zzz");

        ExplorerQueryBuilder eq = new ExplorerQueryBuilder()
                .query(q)
                .statsType("min_raw_df");

        final SearchResponse r = client().prepareSearch("idx")
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setQuery(eq).get();

        assertSearchResponse(r);

        SearchHits hits = r.getHits();
        assertThat(hits.getAt(0).getScore(), equalTo(4.0f));
    }

    public void testNonDfsExplorer() throws Exception {
        createIdx();

        QueryBuilder q = new TermQueryBuilder("s", "zzz");

        ExplorerQueryBuilder eq = new ExplorerQueryBuilder()
                .query(q)
                .statsType("min_raw_df");

        final SearchResponse r = client().prepareSearch("idx")
                .setSearchType(SearchType.QUERY_THEN_FETCH)
                .setQuery(eq).get();

        assertSearchResponse(r);

        SearchHits hits = r.getHits();
        assertThat(hits.getAt(0).getScore(), equalTo(2.0f));
    }

    public void testDfsTSQ() throws Exception {
        createIdx();

        TermStatQueryBuilder tsq = new TermStatQueryBuilder()
                .expr("df")
                .aggr("min")
                .posAggr("min")
                .terms(new String[]{"zzz"})
                .fields(new String[]{"s"});

        final SearchResponse r = client().prepareSearch("idx")
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setQuery(tsq)
                .get();

        assertSearchResponse(r);

        SearchHits hits = r.getHits();
        assertThat(hits.getAt(0).getScore(), equalTo(4.0f));
    }

    public void testNonDfsTSQ() throws Exception {
        createIdx();

        TermStatQueryBuilder tsq = new TermStatQueryBuilder()
                .expr("df")
                .aggr("min")
                .posAggr("min")
                .terms(new String[]{"zzz"})
                .fields(new String[]{"s"});

        final SearchResponse r = client().prepareSearch("idx")
                .setSearchType(SearchType.QUERY_THEN_FETCH)
                .setQuery(tsq)
                .get();

        assertSearchResponse(r);

        SearchHits hits = r.getHits();
        assertThat(hits.getAt(0).getScore(), equalTo(2.0f));
    }
}
