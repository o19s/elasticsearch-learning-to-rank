/*
 * Copyright [2017] OpenSource Connections
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
 */
package com.o19s.es.ltr.ranker.parser.json;

import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.index.query.QueryShardContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feature Set that can't fail,
 * Creates a new feature everytime one is asked for, assigning it a unique ordinal
 * Returns MatchAll query for the feature
 */
public class MockFeatureSet implements FeatureSet {
    @Override
    public String name() {
        return "mock";
    }

    List<String> _features = new ArrayList<String>();

    @Override
    public List<Query> toQueries(QueryShardContext context, Map<String, Object> params) {
        List<Query> queries = new ArrayList<Query>();
        for (int i = 0; i < _features.size(); i++) {
            queries.add(feature(i).doToQuery(context, params));
        }
        return queries;
    }

    @Override
    public int featureOrdinal(String featureName) {
        if (_features.indexOf(featureName) == -1) {
            _features.add(featureName);
        }
        return _features.indexOf(featureName);
    }

    @Override
    public Feature feature(int ord) {
        return new Feature() {
            @Override
            public String name() {
                return  _features.get(ord);
            }

            @Override
            public Query doToQuery(QueryShardContext context, Map<String, Object> params) {
                return new MatchAllDocsQuery();
            }
        };
    }

    @Override
    public Feature feature(String featureName) {
        return feature(_features.indexOf(featureName));
    }

    @Override
    public boolean hasFeature(String featureName) {
        return (_features.indexOf(featureName) != -1);
    }

    @Override
    public int size() {
        return _features.size();
    }
}
