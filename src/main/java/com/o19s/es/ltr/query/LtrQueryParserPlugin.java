/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.o19s.es.ltr.query;

import ciir.umass.edu.learning.Ranker;
import ciir.umass.edu.learning.RankerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SearchPlugin;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.singletonList;

public class LtrQueryParserPlugin extends Plugin implements SearchPlugin {

    Map<String, Ranker> cachedRankers = null;

    LtrQueryParserPlugin(Settings ltrSettings) {
        cachedRankers = new HashMap<String, Ranker>();
        RankerFactory rankerFactory = new RankerFactory();
        Map<String, String> settingsAsMap = ltrSettings.getAsMap();
        for (Map.Entry<String, String> setting : settingsAsMap.entrySet()) {
            String settingName = setting.getKey();
            String settingValue = setting.getValue();
            if (settingName.startsWith("ltr.models")) {
                String modelName = settingName.substring(10);
                if (settingValue.length() > 0) {
                    Ranker ranker = rankerFactory.loadRankerFromString(settingValue);
                    cachedRankers.put(modelName, ranker);
                }
            }
        }
    }

    @Override
    public Settings additionalSettings() {
        // TODO -- HACK ALERT!
        // I wish this was done more RESTfuly, and in my ignorance perhaps it should be
        // My requirements is that the LTR models:
        // 1. Can be persisted across the cluster
        // 2. Can be CRUD'd easily
        // 3. Can persist relatively large RankLib models into Ranker objects in this plugin
        // Perhaps this should be done by a file? But then there's a lot of manual updating of files when a model changes
        // Perhaps this should be done by a REST plugin? But then there's a lot of distributed plumbing to wire together
        //      and the model needs to be persisted to disk
        // So I piggybacked on teh settings API for now, giving you some handy fun fruit names to name your models!
        // I am still nervous about this, because models can be quite large. I'm wondering if this will create any
        // perf problems transfering settings around.
        return Settings.builder()
                .put("ltr.models.apple", "") // fruit names because of Relevant Search and John Berryman
                .put("ltr.models.banana", "")
                .put("ltr.models.orange", "")
                .put("ltr.models.grapefruit", "")
                .put("ltr.models.durian", "")
                .put("ltr.models.watermelon", "")
                .build();

    }

    @Override
    public List<QuerySpec<?>> getQueries() {
        QueryParser<LtrQueryBuilder> qp = new QueryParser<LtrQueryBuilder>() {
            @Override
            public Optional<LtrQueryBuilder> fromXContent(QueryParseContext parseContext) throws IOException {
                Optional<LtrQueryBuilder> opt;
                return Optional.of(LtrQueryBuilder.fromXContent(parseContext, cachedRankers));

            }
        };
        return singletonList(new QuerySpec<>(LtrQueryBuilder.NAME, LtrQueryBuilder::new, qp));
    }

}
