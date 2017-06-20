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

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.action.AddFeaturesToSetAction.AddFeaturesToSetRequestBuilder;
import com.o19s.es.ltr.action.AddFeaturesToSetAction.AddFeaturesToSetResponse;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.elasticsearch.action.DocWriteResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.elasticsearch.ExceptionsHelper.unwrap;
import static org.hamcrest.CoreMatchers.containsString;

public class AddFeaturesToSetActionIT extends BaseIntegrationTest {
    public void testAddToSet() throws Exception {
        int nFeature = random().nextInt(99) + 1;
        List<StoredFeature> features = new ArrayList<>(nFeature);
        for (int i = 0; i < nFeature; i++) {
            StoredFeature feat = LtrTestUtils.randomFeature("feature" + i);
            features.add(feat);
            addElement(feat);
        }

        addElement(LtrTestUtils.randomFeature("another"));

        AddFeaturesToSetRequestBuilder builder = AddFeaturesToSetAction.INSTANCE.newRequestBuilder(client());
        builder.request().setFeatureSet("new_feature_set");
        builder.request().setFeatureNameQuery("feature*");
        builder.request().setStore(IndexFeatureStore.DEFAULT_STORE);
        AddFeaturesToSetResponse resp = builder.execute().get();

        assertEquals(DocWriteResponse.Result.CREATED, resp.getResponse().getResult());
        assertEquals(1, resp.getResponse().getVersion());
        StoredFeatureSet set = getElement(StoredFeatureSet.class, StoredFeatureSet.TYPE, "new_feature_set");
        assertNotNull(set);
        assertEquals("new_feature_set", set.name());
        assertEquals(features.size(), set.size());
        assertTrue(features.stream().map(StoredFeature::name).allMatch(set::hasFeature));

        builder = AddFeaturesToSetAction.INSTANCE.newRequestBuilder(client());
        builder.request().setFeatureSet("new_feature_set");
        builder.request().setFeatureNameQuery("another");
        builder.request().setStore(IndexFeatureStore.DEFAULT_STORE);
        resp = builder.execute().get();
        assertEquals(2, resp.getResponse().getVersion());
        set = getElement(StoredFeatureSet.class, StoredFeatureSet.TYPE, "new_feature_set");
        assertTrue(set.hasFeature("another"));
    }

    public void testFailuresWhenEmpty() throws Exception {
        AddFeaturesToSetRequestBuilder builder = AddFeaturesToSetAction.INSTANCE.newRequestBuilder(client());
        builder.request().setFeatureSet("new_broken_set");
        builder.request().setFeatureNameQuery("doesnotexist*");
        builder.request().setStore(IndexFeatureStore.DEFAULT_STORE);
        Throwable iae = unwrap(expectThrows(ExecutionException.class, () -> builder.execute().get()),
                IllegalArgumentException.class);
        assertNotNull(iae);
        assertThat(iae.getMessage(), containsString("returned no features"));
    }

    public void testFailuresOnDuplicates() throws Exception {
        addElement(LtrTestUtils.randomFeature("duplicated"));

        AddFeaturesToSetRequestBuilder builder = AddFeaturesToSetAction.INSTANCE.newRequestBuilder(client());
        builder.request().setFeatureSet("duplicated_set");
        builder.request().setFeatureNameQuery("duplicated*");
        builder.request().setStore(IndexFeatureStore.DEFAULT_STORE);
        AddFeaturesToSetResponse resp = builder.execute().get();

        assertEquals(DocWriteResponse.Result.CREATED, resp.getResponse().getResult());
        assertEquals(1, resp.getResponse().getVersion());


        AddFeaturesToSetRequestBuilder builder2 = AddFeaturesToSetAction.INSTANCE.newRequestBuilder(client());
        builder2.request().setFeatureSet("duplicated_set");
        builder2.request().setFeatureNameQuery("duplicated");
        builder2.request().setStore(IndexFeatureStore.DEFAULT_STORE);

        Throwable iae = unwrap(expectThrows(ExecutionException.class, () -> builder2.execute().get()),
                IllegalArgumentException.class);
        assertNotNull(iae);
        assertThat(iae.getMessage(), containsString("defined twice in this set"));
    }
}
