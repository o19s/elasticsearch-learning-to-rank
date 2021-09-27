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

package com.o19s.es.ltr.feature.store.index;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.MemStore;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.core.TimeValue;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.instanceOf;

public class CachedFeatureStoreTests extends LuceneTestCase {
    private final MemStore memStore = new MemStore();
    private final Caches caches = new Caches(Settings.EMPTY);

    public void testStoreName() {
        CachedFeatureStore store = new CachedFeatureStore(memStore, caches);
        assertSame(memStore.getStoreName(), store.getStoreName());
    }

    public void testCachedFeature() throws IOException {
        StoredFeature feat = LtrTestUtils.randomFeature();
        memStore.add(feat);
        CachedFeatureStore store = new CachedFeatureStore(memStore, caches);
        assertNull(store.getCachedFeature(feat.name()));
        store.load(feat.name());
        assertNotNull(store.getCachedFeature(feat.name()));
        assertEquals(feat.ramBytesUsed(), store.featuresWeight());
        assertEquals(feat.ramBytesUsed(), store.totalWeight());
        assertEquals(feat.ramBytesUsed(), caches.getPerStoreStats(memStore.getStoreName()).featureRam());
        assertEquals(1, caches.getPerStoreStats(memStore.getStoreName()).featureCount());
        assertEquals(feat.ramBytesUsed(), caches.getPerStoreStats(memStore.getStoreName()).totalRam());
        assertEquals(1, caches.getPerStoreStats(memStore.getStoreName()).totalCount());
        assertThat(expectThrows(IOException.class, () -> store.load("unk")).getCause(),
            instanceOf(IllegalArgumentException.class));
    }

    public void testCachedFeatureSet() throws IOException {
        StoredFeatureSet set = LtrTestUtils.randomFeatureSet();
        memStore.add(set);
        CachedFeatureStore store = new CachedFeatureStore(memStore, caches);
        assertNull(store.getCachedFeatureSet(set.name()));
        store.loadSet(set.name());
        assertNotNull(store.getCachedFeatureSet(set.name()));
        assertEquals(set.ramBytesUsed(), store.featureSetWeight());
        assertEquals(set.ramBytesUsed(), store.totalWeight());
        assertEquals(set.ramBytesUsed(), caches.getPerStoreStats(memStore.getStoreName()).featureSetRam());
        assertEquals(1, caches.getPerStoreStats(memStore.getStoreName()).featureSetCount());
        assertEquals(set.ramBytesUsed(), caches.getPerStoreStats(memStore.getStoreName()).totalRam());
        assertEquals(1, caches.getPerStoreStats(memStore.getStoreName()).totalCount());

        assertThat(expectThrows(IOException.class, () -> store.loadSet("unk")).getCause(),
                instanceOf(IllegalArgumentException.class));
    }

    public void testCachedModelSet() throws IOException {
        CompiledLtrModel model = LtrTestUtils.buildRandomModel();
        memStore.add(model);
        CachedFeatureStore store = new CachedFeatureStore(memStore, caches);
        assertNull(store.getCachedModel(model.name()));
        store.loadModel(model.name());
        assertNotNull(store.getCachedModel(model.name()));
        assertEquals(model.ramBytesUsed(), store.modelWeight());
        assertEquals(model.ramBytesUsed(), store.totalWeight());
        assertEquals(model.ramBytesUsed(), caches.getPerStoreStats(memStore.getStoreName()).modelRam());
        assertEquals(1, caches.getPerStoreStats(memStore.getStoreName()).modelCount());
        assertEquals(model.ramBytesUsed(), caches.getPerStoreStats(memStore.getStoreName()).modelRam());
        assertEquals(1, caches.getPerStoreStats(memStore.getStoreName()).totalCount());
        assertThat(expectThrows(IOException.class, () -> store.loadModel("unk")).getCause(),
                instanceOf(IllegalArgumentException.class));
    }

    public void testWontBlowUp() throws IOException {
        Caches caches = new Caches(TimeValue.timeValueHours(1), TimeValue.timeValueHours(1), new ByteSizeValue(100000));
        CachedFeatureStore store = new CachedFeatureStore(memStore, caches);
        long curWeight = store.modelWeight();
        long maxWeight = caches.getMaxWeight();
        long maxIter = 1000;
        while (true) {
            CompiledLtrModel model = LtrTestUtils.buildRandomModel();
            memStore.add(model);
            if (curWeight + model.ramBytesUsed() > maxWeight) {
                store.loadModel(model.name());
                assertTrue(store.modelWeight() < maxWeight);
                break;
            }
            assertTrue(maxIter-- > 0);
            memStore.clear();
            curWeight = store.modelWeight();
        }
    }

    @BadApple(bugUrl = "https://github.com/o19s/elasticsearch-learning-to-rank/issues/75")
    public void testExpirationOnWrite() throws IOException, InterruptedException {
        Caches caches = new Caches(TimeValue.timeValueMillis(100), TimeValue.timeValueHours(1), new ByteSizeValue(1000000));
        CachedFeatureStore store = new CachedFeatureStore(memStore, caches);
        CompiledLtrModel model = LtrTestUtils.buildRandomModel();
        memStore.add(model);
        store.loadModel(model.name());
        assertNotNull(store.getCachedModel(model.name()));
        assertFalse(caches.getCachedStoreNames().isEmpty());
        assertEquals(1, caches.getPerStoreStats(memStore.getStoreName()).totalCount());
        Thread.sleep(500);
        caches.modelCache().refresh();
        assertNull(store.getCachedModel(model.name()));
        assertTrue(caches.getCachedStoreNames().isEmpty());
        assertEquals(0, caches.getPerStoreStats(memStore.getStoreName()).modelRam());
        assertEquals(0, caches.getPerStoreStats(memStore.getStoreName()).totalCount());
    }

    @BadApple(bugUrl = "https://github.com/o19s/elasticsearch-learning-to-rank/issues/75")
    public void testExpirationOnGet() throws IOException, InterruptedException {
        Caches caches = new Caches(TimeValue.timeValueHours(1), TimeValue.timeValueMillis(100), new ByteSizeValue(1000000));
        CachedFeatureStore store = new CachedFeatureStore(memStore, caches);
        CompiledLtrModel model = LtrTestUtils.buildRandomModel();
        memStore.add(model);
        store.loadModel(model.name()); // fill cache
        store.loadModel(model.name()); // access cache
        assertNotNull(store.getCachedModel(model.name()));
        Thread.sleep(500);
        caches.modelCache().refresh();
        assertNull(store.getCachedModel(model.name()));
        assertNull(store.getCachedModel(model.name()));
        assertTrue(caches.getCachedStoreNames().isEmpty());
        assertEquals(0, caches.getPerStoreStats(memStore.getStoreName()).modelRam());
        assertEquals(0, caches.getPerStoreStats(memStore.getStoreName()).totalCount());
    }

    public void testFullEviction() throws IOException {
        int pass = TestUtil.nextInt(random(), 10, 100);
        CachedFeatureStore cachedFeatureStore = new CachedFeatureStore(memStore, caches);
        while (pass-- > 0) {
            StoredFeature feat = LtrTestUtils.randomFeature();
            memStore.add(feat);
            cachedFeatureStore.load(feat.name());

            StoredFeatureSet set = LtrTestUtils.randomFeatureSet();
            memStore.add(set);
            cachedFeatureStore.loadSet(set.name());

            CompiledLtrModel model = LtrTestUtils.buildRandomModel();
            memStore.add(model);
            cachedFeatureStore.loadModel(model.name());
        }
        caches.evict(memStore.getStoreName());
        assertEquals(0, cachedFeatureStore.totalWeight());
        assertTrue(caches.getCachedStoreNames().isEmpty());
        assertEquals(0, caches.getPerStoreStats(memStore.getStoreName()).modelRam());
        assertEquals(0, caches.getPerStoreStats(memStore.getStoreName()).totalCount());
    }

    public void testCacheStatsIsolation() throws IOException {
        MemStore one = new MemStore("one");
        MemStore two = new MemStore("two");
        CachedFeatureStore onefs = new CachedFeatureStore(one, caches);
        CachedFeatureStore twofs = new CachedFeatureStore(two, caches);
        int pass = TestUtil.nextInt(random(), 10, 20);
        while (pass-- > 0) {
            StoredFeature feat = LtrTestUtils.randomFeature();
            one.add(feat);
            two.add(feat);
            onefs.load(feat.name());
            twofs.load(feat.name());
        }
        assertEquals(2, caches.getCachedStoreNames().size());
        caches.evict(one.getStoreName());
        assertEquals(1, caches.getCachedStoreNames().size());
        caches.evict(two.getStoreName());
        assertTrue(caches.getCachedStoreNames().isEmpty());
    }
}