package com.o19s.es.ltr;

import org.apache.lucene.util.LuceneTestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

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

public class LtrQueryContextTests extends LuceneTestCase {

    public void testIsFeatureActiveForNull() {
        LtrQueryContext ltrContext = new LtrQueryContext(null, null);
        assertTrue(ltrContext.isFeatureActive("feature"));
    }

    public void testIsFeatureActiveForEmptySet() {
        LtrQueryContext ltrContext = new LtrQueryContext(null, Collections.emptySet());
        assertTrue(ltrContext.isFeatureActive("feature"));
    }

    public void testIsFeatureActiveTrue() {
        LtrQueryContext ltrContext = new LtrQueryContext(null, Collections.singleton("feature"));
        assertTrue(ltrContext.isFeatureActive("feature"));
    }

    public void testIsFeatureActiveFalse() {
        LtrQueryContext ltrContext = new LtrQueryContext(null, Collections.singleton("feature1"));
        assertFalse(ltrContext.isFeatureActive("feature2"));
    }

    public void testGetActiveFeaturesForNull() {
        LtrQueryContext ltrContext = new LtrQueryContext(null, null);
        assertEquals(Collections.emptySet(), ltrContext.getActiveFeatures());
    }

    public void testGetActiveFeatures() {
        HashSet<String> features = new HashSet<>(Arrays.asList("feature1", "feature2"));
        LtrQueryContext ltrContext = new LtrQueryContext(null, features);
        assertEquals(features, ltrContext.getActiveFeatures());
    }

}

