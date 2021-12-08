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

package com.o19s.es.ltr;

import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.FeatureNormDefinition;
import com.o19s.es.ltr.feature.store.MemStore;
import com.o19s.es.ltr.feature.store.MinMaxFeatureNormDefinition;
import com.o19s.es.ltr.feature.store.StandardFeatureNormDefinition;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureNormalizers;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredFeatureSetParserTests;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.query.StoredLtrQueryBuilder;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTreeTests;
import com.o19s.es.ltr.ranker.linear.LinearRankerTests;
import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizingRanker;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import com.o19s.es.ltr.ranker.parser.LinearRankerParser;
import com.o19s.es.ltr.utils.FeatureStoreLoader;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.core.CheckedFunction;
import org.elasticsearch.common.Strings;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.WrapperQueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.apache.lucene.util.LuceneTestCase.random;

public class LtrTestUtils {

    public static StoredFeature randomFeature() throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeature();
    }

    public static StoredFeature randomFeature(String name) throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeature(name);
    }

    public static StoredFeatureSet randomFeatureSet() throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeatureSet();
    }

    public static StoredFeatureSet randomFeatureSet(int nbFeature) throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeatureSet(nbFeature);
    }

    public static StoredFeatureSet randomFeatureSet(String name) throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeatureSet(name);
    }

    public static CompiledLtrModel buildRandomModel() throws IOException {
        StoredFeatureSet set = StoredFeatureSetParserTests.buildRandomFeatureSet();
        Map<Integer, Normalizer> ftrNorms = randomFtrNorms(set).compileOrdToNorms(set);
        LtrRanker ranker = buildRandomRanker(set.size());
        if (ftrNorms.size() > 0) {
            ranker = new FeatureNormalizingRanker(ranker, ftrNorms);
        }
        return new CompiledLtrModel(TestUtil.randomSimpleString(random(), 5, 10), set,
                                                                ranker);
    }

    public static StoredLtrModel randomLinearModel(String name, StoredFeatureSet set) throws IOException {
        XContentBuilder builder = JsonXContent.contentBuilder();
        builder.startObject();
        for (int i = 0; i < set.size(); i++) {
            builder.field(set.feature(i).name(), random().nextFloat());
        }
        builder.endObject();
        return new StoredLtrModel(name, set, LinearRankerParser.TYPE, Strings.toString(builder), false, randomFtrNorms(set));
    }

    public static StoredFeatureNormalizers randomFtrNorms(FeatureSet set) {
        final List<FeatureNormDefinition> ftrNormDefns = new ArrayList<>();

        for (int i = 0; i < set.size(); i++) {

            if (random().nextBoolean()) {
                continue;
            }
            else {

                Feature ftr = set.feature(i);

                if (random().nextBoolean()) {
                    StandardFeatureNormDefinition stdFtrNormDefn = new StandardFeatureNormDefinition(ftr.name());
                    stdFtrNormDefn.setMean(random().nextFloat());
                    stdFtrNormDefn.setStdDeviation(random().nextFloat());
                    ftrNormDefns.add(stdFtrNormDefn);

                } else {
                    MinMaxFeatureNormDefinition minMaxFtrNormDefn = new MinMaxFeatureNormDefinition(ftr.name());
                    minMaxFtrNormDefn.setMinimum(random().nextFloat());
                    minMaxFtrNormDefn.setMaximum(1.01f + random().nextFloat());
                    ftrNormDefns.add(minMaxFtrNormDefn);
                }
            }
        }

        StoredFeatureNormalizers ftrNorms = new StoredFeatureNormalizers(ftrNormDefns);
        return ftrNorms;
    }

    public static LtrRanker buildRandomRanker(int fSize) {
        LtrRanker ranker;
        if (random().nextBoolean()) {
            ranker = LinearRankerTests.generateRandomRanker(fSize);
        } else {
            ranker = NaiveAdditiveDecisionTreeTests.generateRandomDecTree(fSize, TestUtil.nextInt(random(), 1, 50),
                    5, 50, null);
        }
        return ranker;
    }

    public static FeatureStoreLoader nullLoader() {
        return (storeName, client) -> {throw new IllegalStateException("Invalid state, this query cannot be " +
                "built without a valid store loader. Your are seeing this exception because you attempt to call " +
                "doToQuery on a " + StoredLtrQueryBuilder.class.getSimpleName() + " instance that was built with " +
                "an invalid FeatureStoreLoader. If you are trying to run integration tests with this query consider " +
                "wrapping it inside a " + WrapperQueryBuilder.class.getSimpleName() + ":\n" +
                "\tnew WrapperQueryBuilder(sltrBuilder.toString())\n" +
                "This will force elastic to initialize the feature loader properly");};
    }

    public static <T,R,E extends Exception> Function<T, R> wrapFuncion(CheckedFunction<T, R, E> f) {
        return (p) -> {
            try {
                return f.apply(p);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static <R,E extends Exception> IntFunction<R> wrapIntFuncion(CheckedFunction<Integer, R, E> f) {
        return (p) -> {
            try {
                return f.apply(p);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static FeatureStoreLoader wrapMemStore(MemStore store) {
        return (storeName, client) -> store;
    }


    public static String testFeatureSetString() {
        return "{\n" +
                "\"name\": \"movie_features\",\n" +
                "\"type\": \"featureset\",\n" +
                "\"featureset\": {\n" +
                "    \"name\": \"movie_features\",\n" +
                "    \"features\": [\n" +
                "        {\n" +
                "            \"name\": \"1\",\n" +
                "            \"params\": [\n" +
                "                \"keywords\"\n" +
                "            ],\n" +
                "            \"template_language\": \"mustache\",\n" +
                "            \"template\": {\n" +
                "                \"match\": {\n" +
                "                    \"title\": \"{{keywords}}\"\n" +
                "                }\n" +
                "            }\n" +
                "        },\n" +
                "        {\n" +
                "            \"name\": \"2\",\n" +
                "            \"params\": [\n" +
                "                \"keywords\"\n" +
                "            ],\n" +
                "            \"template_language\": \"mustache\",\n" +
                "            \"template\": {\n" +
                "                \"match\": {\n" +
                "                    \"overview\": \"{{keywords}}\"\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}\n}";
    }

    public static String testModelString() {
        return "{\n" +
                "\"name\": \"movie_model\",\n" +
                "\"type\": \"model\"" +
                "\n}";
    }
}
