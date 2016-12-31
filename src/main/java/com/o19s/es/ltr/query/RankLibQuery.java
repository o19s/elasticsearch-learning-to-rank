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
