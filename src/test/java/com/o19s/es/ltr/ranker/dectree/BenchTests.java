/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.ranker.dectree;

import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.DenseLtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTreeTests.SimpleCountRandomTreeGeneratorStatsCollector;
import com.o19s.es.ltr.ranker.linear.LinearRankerTests;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.logging.ESLoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class BenchTests extends LuceneTestCase {
    static final Logger LOGGER = ESLoggerFactory.getLogger(BenchTests.class);
    static final int NFEAT = 1000;
    static final int MAX_TREES = 1000;
    static final int TREE_STEPS = 100;
    static final int[] DEPTHS = new int[]{4,6,8,10};
    static final int DOCS = 4000;
    static final int NRUN = 100;
    private SortedMap<String, List<Double>> metrics = new TreeMap<>();
    private float[][] scores = new float[100][NFEAT];

    public void renameMeToRunTheBench() throws IOException {
        for (float[] score : scores) {
            LinearRankerTests.fillRandomWeights(score);
        }
        int nbTests = DEPTHS.length * (MAX_TREES/TREE_STEPS);
        int test = 0;
        for (int depth : DEPTHS) {
            for (int trees = TREE_STEPS; trees <= MAX_TREES; trees += TREE_STEPS) {
                LOGGER.info("Running test {}/{} depth:{}, nbtrees: {}", ++test, nbTests, depth, trees);
                benchLine(trees, depth);
            }
        }
        dumpStats();
    }

    private void dumpStats() throws IOException {
        int l = metrics.values().iterator().next().size();
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(PathUtils.get("/tmp/tree_bench.csv")), StandardCharsets.UTF_8)) {
            String header = metrics.keySet().stream().collect(Collectors.joining(";"));
            writer.write(header);
            writer.write("\n");
            for (int i = 0; i < l; i++) {
                StringBuilder line = new StringBuilder();
                for (String k : metrics.keySet()) {
                    if (line.length() > 0) {
                        line.append(";");
                    }
                    line.append(metrics.get(k).get(i));
                }
                writer.write(line.toString());
                writer.write("\n");
            }
        }
    }

    public void benchLine(int nTree, int depth) {
        SimpleCountRandomTreeGeneratorStatsCollector counts = new SimpleCountRandomTreeGeneratorStatsCollector();
        NaiveAdditiveDecisionTree naive = NaiveAdditiveDecisionTreeTests.generateRandomDecTree(NFEAT, NFEAT,
                nTree, nTree,
                depth, depth, counts);
        addMetric("00_depth", depth);
        addMetric("01_n_tree", nTree);
        addMetric("02_leaves", counts.leaves.doubleValue());
        addMetric("03_splits", counts.splits.doubleValue());
        addMetric("04_nodes", counts.nodes.doubleValue());
        addMetric("05_docs", DOCS);

        benchRanker(naive.toBinBinTree());
        benchRanker(naive.toPrimTree());
        benchRanker(naive);
    }

    public void benchRanker(DenseLtrRanker ranker) {
        String name = ranker.name().replaceAll("^([^_]+)_.*$", "$1");
        // Warm up
        recordTime(ranker, scores, DOCS);
        recordTime(ranker, scores, DOCS);
        long totalTime = 0;
        for (int i = 0; i < NRUN; i++) {
            long time = recordTime(ranker, scores, DOCS);
            totalTime = Math.addExact(totalTime, time);
        }
        LOGGER.info("\t{} \t {}", name, totalTime);
        addMetric(name + "_tot_run_time", totalTime);
        addMetric(name + "_avg_run_time", totalTime/NRUN);
        addMetric(name + "_mem_size", ((Accountable)ranker).ramBytesUsed());
    }

    long recordTime(DenseLtrRanker ranker, float[][] scores, int nPass) {
        long st = System.currentTimeMillis();
        DenseFeatureVector vector = null;
        while(nPass-->0) {
            vector = ranker.newFeatureVector(vector);
            System.arraycopy(scores[nPass%scores.length], 0, vector.scores, 0, vector.scores.length);
            ranker.score(vector);
        }
        return System.currentTimeMillis() - st;
    }

    void addMetric(String metric, double value) {
        metrics.compute(metric, (s, vs) -> {
            if (vs == null) {
                vs = new ArrayList<>();
            }
            vs.add(value);
            return vs;
        });
    }
}
