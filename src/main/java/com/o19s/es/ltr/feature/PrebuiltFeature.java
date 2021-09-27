/*
 * Copyright [2017] Wikimedia Foundation
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

package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.LtrQueryContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.elasticsearch.core.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * A prebuilt featured query, needed by query builders
 * that provides the query itself.
 */
public class PrebuiltFeature extends Query implements Feature {
    private final String name;
    private final Query query;

    public PrebuiltFeature(@Nullable String name, Query query) {
        this.name = name;
        this.query = Objects.requireNonNull(query);
    }

    @Override @Nullable
    public String name() {
        return name;
    }

    @Override
    public Query doToQuery(LtrQueryContext context, FeatureSet set, Map<String, Object> params) {
        return query;
    }

    public Query getPrebuiltQuery() {
        return query;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, query);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PrebuiltFeature)) {
            return false;
        }
        PrebuiltFeature other = (PrebuiltFeature) o;
        return Objects.equals(name, other.name)
                && Objects.equals(query, other.query);
    }

    @Override
    public String toString(String field) {
        return query.toString(field);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        return query.createWeight(searcher, scoreMode, boost);
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        return query.rewrite(reader);
    }
}
