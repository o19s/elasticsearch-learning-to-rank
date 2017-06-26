/*
 * Copyright [2017] Dan Worley
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
package com.o19s.es.explore;

import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.fetch.FetchSubPhase;
import org.elasticsearch.search.internal.InternalSearchHitField;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class ExplorerFetchSubPhase implements FetchSubPhase {
    @Override
    public void hitExecute(SearchContext context, HitContext hitContext) {
        if (hitContext.hit().fieldsOrNull() == null) {
            hitContext.hit().fields(new HashMap<>(13));
        }

        // Check to see if we're enabled
        ExplorerExtBuilder extBuilder = (ExplorerExtBuilder)context.getSearchExt(ExplorerExtBuilder.NAME);

        // No stats if not enabled
        if(extBuilder == null || !extBuilder.isEnabled()) {
            return;
        }

        List<String> enabledStats = Arrays.asList(extBuilder.getStats().split("[,\\s]+"));

        try {
            Query query = context.query();
            Weight weight = query.createWeight(context.searcher(), false);
            Set<Term> terms = new HashSet<Term>();
            weight.extractTerms(terms);

            // No stats if no terms
            if(terms.size() == 0) return;

            Terms termVector = hitContext.reader().getTermVector(hitContext.docId(), extBuilder.getField());

            // No stats are available if no term vectors...
            if(termVector == null) return;

            TermsEnum itr = termVector.iterator();
            PostingsEnum postings = null;

            ClassicSimilarity sim = new ClassicSimilarity();
            StatisticsHelper tf_stats = new StatisticsHelper();
            StatisticsHelper idf_stats = new StatisticsHelper();

            for (Term term : terms) {
                boolean found = itr.seekExact(term.bytes());

                if(found) {
                    postings = itr.postings(postings, PostingsEnum.FREQS);
                    postings.nextDoc();
                    tf_stats.add(postings.freq());
                    idf_stats.add(sim.idf(hitContext.reader().docFreq(term), hitContext.reader().numDocs()));
                } else {
                    tf_stats.add(0);
                    idf_stats.add(0);
                }
            }

            addData(hitContext, enabledStats, "sum_raw_tf", tf_stats.getSum());
            addData(hitContext, enabledStats, "mean_raw_tf", tf_stats.getMean());
            addData(hitContext, enabledStats, "max_raw_tf", tf_stats.getMax());
            addData(hitContext, enabledStats, "min_raw_tf", tf_stats.getMin());
            addData(hitContext, enabledStats, "stddev_raw_tf", tf_stats.getStdDev());

            addData(hitContext, enabledStats, "sum_raw_idf", idf_stats.getSum());
            addData(hitContext, enabledStats, "mean_raw_idf", idf_stats.getMean());
            addData(hitContext, enabledStats, "max_raw_idf", idf_stats.getMax());
            addData(hitContext, enabledStats, "min_raw_idf", idf_stats.getMin());
            addData(hitContext, enabledStats, "stddev_raw_idf", idf_stats.getStdDev());
        } catch (IOException ex) {
            // No-op
        }

    }

    private void addData(HitContext context, List<String> enabledStats, String name, Object value) {
        if(enabledStats.contains(name) || enabledStats.contains("*")) {
            SearchHitField hitField = context.hit().fields().get(name);
            if (hitField == null) {
                final List<Object> values = Collections.singletonList(value);

                hitField = new InternalSearchHitField(name, values);
                context.hit().fields().put(name, hitField);
            }
        }
    }
}
