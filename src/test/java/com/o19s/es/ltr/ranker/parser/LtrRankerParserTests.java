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

package com.o19s.es.ltr.ranker.parser;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import com.o19s.es.ltr.ranker.linear.LinearRankerTests;
import com.o19s.es.ltr.ranker.ranklib.RanklibModelParser;
import com.o19s.es.ltr.ranker.ranklib.learning.FEATURE_TYPE;
import com.o19s.es.ltr.ranker.ranklib.learning.RankLibError;
import com.o19s.es.ltr.ranker.ranklib.learning.RankerFactory;
import com.o19s.es.ltr.utils.Suppliers;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.junit.Assert;
import org.junit.Before;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class LtrRankerParserTests extends LuceneTestCase {
    private final List<String> modelFiles = Arrays.asList("coord_ascent.txt",
            "lambdaMART.txt",
            "linRegression.txt",
            "mart.txt",
            "randomForest.txt");

    private RanklibModelParser parser;

    @Before
    public void setup() {
        Supplier<RankerFactory> ranklib = Suppliers.memoize(RankerFactory::new);
        parser = new RanklibModelParser(ranklib.get());
    }

    public void testParseModels() throws IOException {
        for (String model : modelFiles) {
            evalModel(model, true);
        }
    }

    public void testInvalidFeatures() throws IOException {
        for (String model : modelFiles) {
            evalModel(model, false);
        }
    }

    private void evalModel(String filename, boolean validFeatures) throws IOException {
        String model = readModel("/models/" + filename);
        List<StoredFeature> features = new ArrayList<>();
        List<String> names;

        if(validFeatures) {
            names = Arrays.asList("1", "2");
        } else {
            names = Arrays.asList("Bad", "Features");
        }

        for (String n : names) {
            features.add(LtrTestUtils.randomFeature(n));
        }

        StoredFeatureSet set = new StoredFeatureSet("set", features);

        if(validFeatures) {
            parser.parse(set, model, FEATURE_TYPE.NAMED);
        } else {
            expectThrows(RankLibError.class, () -> parser.parse(set, model, FEATURE_TYPE.NAMED));
        }
    }

    private String readModel(String model) throws IOException {
        try (InputStream is = this.getClass().getResourceAsStream(model)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Streams.copy(is,  bos);
            return bos.toString(IOUtils.UTF_8);
        }
    }
}