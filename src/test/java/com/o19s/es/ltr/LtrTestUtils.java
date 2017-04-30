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

import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredFeatureSetParserTests;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTreeTests;
import com.o19s.es.ltr.ranker.linear.LinearRankerTests;
import org.apache.lucene.util.TestUtil;

import java.io.IOException;

import static org.apache.lucene.util.LuceneTestCase.random;

public class LtrTestUtils {

    public static StoredFeature randomFeature() throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeature();
    }

    public static StoredFeatureSet randomFeatureSet() throws IOException {
        return StoredFeatureSetParserTests.buildRandomFeatureSet();
    }

    public static StoredLtrModel buildRandomModel() throws IOException {
        StoredFeatureSet set = StoredFeatureSetParserTests.buildRandomFeatureSet();
        LtrRanker ranker;
        ranker = buildRandomRanker(set.size());
        return new StoredLtrModel(TestUtil.randomSimpleString(random(), 5, 10), set, ranker);
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
}
