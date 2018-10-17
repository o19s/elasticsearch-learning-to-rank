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

package com.o19s.es.ltr.feature.store;

import org.apache.lucene.util.LuceneTestCase;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.o19s.es.ltr.LtrTestUtils.randomFeature;
import static com.o19s.es.ltr.LtrTestUtils.wrapIntFuncion;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;

public class StoredFeatureSetTests extends LuceneTestCase {
    public void testCreate() throws IOException {
        StoredFeature f1 = randomFeature("feat1");
        StoredFeatureSet set = new StoredFeatureSet("name", singletonList(f1));
        assertEquals(1, set.size());
        assertTrue(set.hasFeature("feat1"));
        assertEquals(0, set.featureOrdinal("feat1"));
        assertEquals("feat1", set.feature(0).name());
        assertSame(set, set.optimize());
        StoredFeature f2 = randomFeature("feat2");
        set = new StoredFeatureSet("name", asList(f1, f2));

        assertTrue(set.hasFeature("feat1"));
        assertTrue(set.hasFeature("feat2"));
        assertEquals(1, set.featureOrdinal("feat2"));
        assertEquals("feat2", set.feature(1).name());
        assertSame(set, set.optimize());
        set.validate();
    }

    public void testAppend() throws IOException {
        StoredFeatureSet set = new StoredFeatureSet("name", emptyList());
        assertEquals(0, set.size());
        StoredFeature v1 = randomFeature("feat1");
        StoredFeatureSet set2 = set.append(singletonList(v1));
        assertSame(v1, set2.feature(0));
        assertEquals(0, set.size());
        assertEquals(1, set2.size());
        StoredFeature v2 = randomFeature("feat1");
        expectThrows(IllegalArgumentException.class, () -> set2.append(singletonList(randomFeature("feat1"))));
        assertSame(v1, set2.feature(0));
        set.validate();
        set2.validate();
    }

    public void testAppendMaxSize() throws IOException {
        StoredFeatureSet set_v1 = new StoredFeatureSet("name", emptyList());
        assertEquals(0, set_v1.size());
        List<StoredFeature> features = IntStream.range(0, StoredFeatureSet.MAX_FEATURES)
                .mapToObj(wrapIntFuncion((i) -> randomFeature("feat" + i)))
                .collect(Collectors.toList());
        StoredFeatureSet set_v2 = set_v1.append(features);

        IllegalArgumentException iae = expectThrows(IllegalArgumentException.class,
                () -> set_v2.append(singletonList(randomFeature("new_feat"))));
        assertThat(iae.getMessage(), equalTo("The resulting feature set would be too large"));
    }

    public void testMergeMaxSize() throws IOException {
        StoredFeatureSet set_v1 = new StoredFeatureSet("name", emptyList());
        assertEquals(0, set_v1.size());
        //noinspection ConstantConditions
        assert StoredFeatureSet.MAX_FEATURES > 10;
        List<StoredFeature> features = IntStream.range(0, StoredFeatureSet.MAX_FEATURES - 2)
                .mapToObj(wrapIntFuncion((i) -> randomFeature("feat" + i)))
                .collect(Collectors.toList());
        StoredFeatureSet set_v2 = set_v1.append(features)
                .merge(asList(randomFeature("feat0"),
                        randomFeature("feat9"),
                        randomFeature("new1"),
                        randomFeature("new2")));
        assertEquals(StoredFeatureSet.MAX_FEATURES, set_v2.size());

        IllegalArgumentException iae = expectThrows(IllegalArgumentException.class,
                () -> set_v2.merge(asList(randomFeature("new4"), randomFeature("feat9"))));
        assertThat(iae.getMessage(), equalTo("The resulting feature set would be too large"));
    }

    public void testMerge() throws IOException {
        StoredFeatureSet set_v1 = new StoredFeatureSet("name", emptyList());
        assertEquals(0, set_v1.size());
        StoredFeature feat1_v1 = randomFeature("feat1");
        StoredFeature feat2_v1 = randomFeature("feat2");
        StoredFeatureSet set_v2 = set_v1.merge(asList(feat1_v1, feat2_v1));
        assertSame(feat1_v1, set_v2.feature(0));
        assertSame(feat2_v1, set_v2.feature(1));
        assertEquals(0, set_v1.size());
        assertEquals(2, set_v2.size());
        StoredFeature feat1_v2 = randomFeature("feat1");
        StoredFeature feat3_v1 = randomFeature("feat3");
        StoredFeatureSet set_v3 = set_v2.merge(asList(feat3_v1, feat1_v2));
        assertSame(feat1_v2, set_v3.feature(0));
        assertSame(feat2_v1, set_v3.feature(1));
        assertSame(feat3_v1, set_v3.feature(2));
    }
}