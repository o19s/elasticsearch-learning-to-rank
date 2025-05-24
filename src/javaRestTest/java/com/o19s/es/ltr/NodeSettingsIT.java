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

import com.o19s.es.ltr.action.BaseIntegrationTest;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.MemStore;
import com.o19s.es.ltr.feature.store.index.CachedFeatureStore;
import com.o19s.es.ltr.feature.store.index.Caches;
import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.util.Accountable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;

import java.io.IOException;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

public class NodeSettingsIT extends BaseIntegrationTest {
    private final MemStore memStore = new MemStore();
    private final int memSize = 1024;
    private final int expireAfterRead = 100;
    private final int expireAfterWrite = expireAfterRead * 4;

    @Override
    protected Settings nodeSettings() {
        Settings settings = super.nodeSettings();
        return Settings.builder().put(settings)
                .put(Caches.LTR_CACHE_MEM_SETTING.getKey(), memSize + "kb")
                .put(Caches.LTR_CACHE_EXPIRE_AFTER_READ.getKey(), expireAfterRead + "ms")
                .put(Caches.LTR_CACHE_EXPIRE_AFTER_WRITE.getKey(), expireAfterWrite + "ms")
                .build();
    }

    public void testCacheSettings() throws IOException, InterruptedException {
        Caches caches = getInstanceFromNode(Caches.class);
        CachedFeatureStore cached = new CachedFeatureStore(memStore, caches);
        long totalAdded = 0;
        int i = 0;
        long maxMemSize = ByteSizeValue.of(memSize, ByteSizeUnit.KB).getBytes();
        do {
            CompiledLtrModel compiled = new DummyModel("test" + i++, 250);
            long lastAddedSize = compiled.ramBytesUsed();
            totalAdded += lastAddedSize;
            memStore.add(compiled);
            cached.loadModel(compiled.name());
            caches.modelCache().refresh();
            assertThat(caches.modelCache().weight(), allOf(lessThan(maxMemSize), greaterThanOrEqualTo(lastAddedSize)));
        } while (totalAdded < maxMemSize);
        assertThat(totalAdded, greaterThan(maxMemSize));
        assertThat(caches.modelCache().weight(), greaterThan(0L));
        Thread.sleep(expireAfterWrite * 2);
        caches.modelCache().refresh();
        assertEquals(0, caches.modelCache().weight());
        cached.loadModel("test0");
        // Second load for accessTime
        cached.loadModel("test0");
        assertThat(caches.modelCache().weight(), greaterThan(0L));
        Thread.sleep(expireAfterRead * 2);
        caches.modelCache().refresh();
        assertEquals(0, caches.modelCache().weight());
    }

    public static class DummyModel extends CompiledLtrModel {
        public DummyModel(String name, long size) throws IOException {
            super(name, LtrTestUtils.randomFeatureSet(1), new DummyRanker(size));
        }
    }

    public static class DummyRanker implements LtrRanker, Accountable {
        private final long ramUsed;

        public DummyRanker(long ramUsed) {
            this.ramUsed = ramUsed;
        }

        @Override
        public String name() {
            return "dummy";
        }

        @Override
        public FeatureVector newFeatureVector(FeatureVector reuse) {
            return null;
        }

        @Override
        public float score(FeatureVector point) {
            return 0;
        }

        @Override
        public long ramBytesUsed() {
            return ramUsed;
        }
    }
}
