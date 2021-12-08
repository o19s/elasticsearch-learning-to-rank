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
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureNormalizers;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.xcontent.NamedXContentRegistry;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.STORE_PREFIX;
import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.indexName;
import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.isIndexStore;
import static com.o19s.es.ltr.feature.store.index.IndexFeatureStore.storeName;
import static org.apache.lucene.util.TestUtil.randomRealisticUnicodeString;
import static org.apache.lucene.util.TestUtil.randomSimpleString;
import static org.hamcrest.CoreMatchers.equalTo;

public class IndexFeatureStoreTests extends LuceneTestCase {

    public void testParse() throws Exception {
        parseAssertions(LtrTestUtils.randomFeature());
        parseAssertions(LtrTestUtils.randomFeatureSet());
        parseAssertions(new StoredLtrModel(
                randomSimpleString(random(), 5, 10),
                LtrTestUtils.randomFeatureSet(),
                randomSimpleString(random(), 5, 10),
                randomRealisticUnicodeString(random(), 5, 1000),
                true,
                new StoredFeatureNormalizers()));
    }

    public void testIsIndexName() {
        assertTrue(isIndexStore(IndexFeatureStore.DEFAULT_STORE));
        assertFalse(isIndexStore("not_really"));
        assertFalse(isIndexStore(IndexFeatureStore.STORE_PREFIX));
        int nPass = random().nextInt(10) + 10;
        for (int i = 0; i < nPass; i++) {
            assertTrue(isIndexStore(indexName(TestUtil.randomSimpleString(random(), 1, 10))));
            assertFalse(isIndexStore(TestUtil.randomSimpleString(random(), 1, STORE_PREFIX.length())));
        }
    }

    public void testIndexName() {
        int nPass = random().nextInt(10) + 10;
        for (int i = 0; i < nPass; i++) {
            String name = indexName(TestUtil.randomSimpleString(random(), 1, 10));
            assertTrue(name.startsWith(STORE_PREFIX));
        }
    }

    public void testStoreName() {
        assertEquals("_default_", storeName(IndexFeatureStore.DEFAULT_STORE));
        assertEquals("test", storeName(STORE_PREFIX + "test"));
        expectThrows(IllegalArgumentException.class, () -> IndexFeatureStore.storeName("not really"));

        int nPass = random().nextInt(10) + 10;
        for (int i = 0; i < nPass; i++) {
            String storeName = TestUtil.randomSimpleString(random(), 1, 10);
            String indexName = indexName(storeName);
            assertEquals(storeName, storeName(indexName));
        }
    }

    public void testBadValues() throws IOException {
        Map<String, Object> map = new HashMap<>();
        XContentBuilder builder = XContentBuilder.builder(Requests.INDEX_CONTENT_TYPE.xContent());
        BytesReference bytes = BytesReference.bytes(builder.map(map));
        assertThat(expectThrows(IllegalArgumentException.class,
                () -> IndexFeatureStore.parse(StoredFeature.class, StoredFeature.TYPE, bytes))
                .getMessage(), equalTo("No StorableElement found."));

        builder = XContentBuilder.builder(Requests.INDEX_CONTENT_TYPE.xContent());
        map.put("featureset", LtrTestUtils.randomFeatureSet());
        BytesReference bytes2 = BytesReference.bytes(builder.map(map));
        assertThat(expectThrows(IllegalArgumentException.class,
                () -> IndexFeatureStore.parse(StoredFeature.class, StoredFeature.TYPE, bytes2))
                .getMessage(), equalTo("Expected an element of type [" + StoredFeature.TYPE + "] but" +
                " got [" + StoredFeatureSet.TYPE + "]."));
    }

    private void parseAssertions(StorableElement elt) throws IOException {
        XContentBuilder builder = IndexFeatureStore.toSource(elt);
        BytesReference first = BytesReference.bytes(builder);
        StorableElement reParsed = IndexFeatureStore.parse(elt.getClass(), elt.type(), first);
        assertEquals(elt, reParsed);
        BytesRef ref = first.toBytesRef();
        reParsed = IndexFeatureStore.parse(elt.getClass(), elt.type(), ref.bytes, ref.offset, ref.length);
        assertEquals(elt, reParsed);
        BytesReference second = BytesReference.bytes(IndexFeatureStore.toSource(reParsed));
        assertEquals(first, second);
        assertNameAndTypes(elt, first);
        assertNameAndTypes(elt, second);
    }

    private void assertNameAndTypes(StorableElement elt, BytesReference ref) throws IOException {
        XContentParser parser = XContentFactory.xContent(Requests.INDEX_CONTENT_TYPE).createParser(NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE, ref.streamInput());
        Map<String,Object> map = parser.map();
        assertEquals(elt.name(), map.get("name"));
        assertEquals(elt.type(), map.get("type"));
        assertTrue(map.containsKey(elt.type()));
    }
}