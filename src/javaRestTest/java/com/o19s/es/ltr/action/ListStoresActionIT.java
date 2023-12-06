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

package com.o19s.es.ltr.action;

import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.indexName;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.action.ListStoresAction.IndexStoreInfo;
import com.o19s.es.ltr.action.ListStoresAction.ListStoresActionResponse;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListStoresActionIT extends BaseIntegrationTest {
  public void testListStore() throws Exception {
    createStore(indexName("test2"));
    createStore(indexName("test3"));
    Map<String, IndexStoreInfo> infos = new HashMap<>();
    String[] stores =
        new String[] {IndexFeatureStore.DEFAULT_STORE, indexName("test2"), indexName("test3")};
    for (String store : stores) {
      infos.put(
          IndexFeatureStore.storeName(store),
          new IndexStoreInfo(store, IndexFeatureStore.VERSION, addElements(store)));
    }
    ListStoresActionResponse resp =
        new ListStoresAction.ListStoresActionBuilder(client()).execute().get();
    assertEquals(infos.size(), resp.getStores().size());
    assertEquals(infos.keySet(), resp.getStores().keySet());
    for (String k : infos.keySet()) {
      IndexStoreInfo expected = infos.get(k);
      IndexStoreInfo actual = resp.getStores().get(k);
      assertEquals(expected.getIndexName(), actual.getIndexName());
      assertEquals(expected.getStoreName(), actual.getStoreName());
      assertEquals(expected.getVersion(), actual.getVersion());
      assertEquals(expected.getCounts(), actual.getCounts());
    }
  }

  private Map<String, Integer> addElements(String store) throws Exception {
    Map<String, Integer> counts = new HashMap<>();
    int nFeats = randomInt(20) + 1;
    int nSets = randomInt(20) + 1;
    int nModels = randomInt(20) + 1;
    counts.put(StoredFeature.TYPE, nFeats);
    counts.put(StoredFeatureSet.TYPE, nSets);
    counts.put(StoredLtrModel.TYPE, nModels);
    addElements(store, nFeats, nSets, nModels);
    return counts;
  }

  private void addElements(String store, int nFeatures, int nSets, int nModels) throws Exception {
    for (int i = 0; i < nFeatures; i++) {
      StoredFeature feat = LtrTestUtils.randomFeature("feature" + i);
      addElement(feat, store);
    }

    List<StoredFeatureSet> sets = new ArrayList<>(nSets);
    for (int i = 0; i < nSets; i++) {
      StoredFeatureSet set = LtrTestUtils.randomFeatureSet("set" + i);
      addElement(set, store);
      sets.add(set);
    }

    for (int i = 0; i < nModels; i++) {
      addElement(
          LtrTestUtils.randomLinearModel("model" + i, sets.get(random().nextInt(sets.size()))),
          store);
    }
  }
}
