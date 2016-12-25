package com.o19s.es.ltr;

import ciir.umass.edu.learning.Ranker;
import org.apache.lucene.queries.CustomScoreQuery;
import org.apache.lucene.search.Query;

/**
 * Created by doug on 12/23/16.
 */
public class RankLibQuery extends CustomScoreQuery {

    private Ranker rankLibRanker =  null;

    public RankLibQuery(Query subQuery) {
        super(subQuery);
    }
}
