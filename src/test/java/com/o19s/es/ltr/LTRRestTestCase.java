/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.o19s.es.ltr;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.test.rest.ESRestTestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class LTRRestTestCase extends ESRestTestCase {

    /**
     * Utility to update settings
     */
    public void updateClusterSettings(String settingKey, Object value) throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("persistent")
                .field(settingKey, value)
                .endObject()
                .endObject();
        Request request = new Request("PUT", "_cluster/settings");
        request.setJsonEntity(Strings.toString(builder));
        Response response = client().performRequest(request);
        assertEquals(RestStatus.OK,  RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    /**
     * Create LTR store index
     * @param name suffix of index name
     */
    public void createLTRStore(String name) throws IOException {
        String path = "_ltr";;

        if (name != null && !name.isEmpty()) {
            path = path + "/" + name;
        }

        Request request = new Request(
                "PUT",
                "/" + path
        );

        Response response = client().performRequest(request);
        assertEquals(request.getEndpoint() + ": failed", RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    /**
     * Create default LTR store index
     */
    public void createDefaultLTRStore() throws IOException {
        createLTRStore("");
    }

    /**
     * Delete LTR store index
     * @param name suffix of index name
     */
    public void deleteLTRStore(String name) throws IOException {
        String path = "_ltr";;

        if (name != null && !name.isEmpty()) {
            path = path + "/" + name;
        }

        Request request = new Request(
                "DELETE",
                "/" + path
        );

        Response response = client().performRequest(request);
        assertEquals(request.getEndpoint() + ": failed", RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    /**
     * Delete default LTR store index
     */
    public void deleteDefaultLTRStore() throws IOException {
        deleteLTRStore("");
    }

    /**
     * Create LTR featureset
     * @param name feature set
     */
    public void createFeatureSet(String name) throws IOException {
        Request request = new Request(
                "POST",
                "/_ltr/_featureset/" + name
        );

        XContentBuilder xb = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("featureset")
                .field("name", name)
                .startArray("features");

        for (int i=1; i<3; ++i) {
            xb.startObject()
                    .field("name", String.valueOf(i))
                    .array("params", "keywords")
                    .field("template_language", "mustache")
                    .startObject("template")
                    .startObject("match")
                    .field("field"+i, "{{keywords}}")
                    .endObject()
                    .endObject()
                    .endObject();
        }
        xb.endArray().endObject().endObject();

        request.setJsonEntity(Strings.toString(xb));
        Response response = client().performRequest(request);
        assertEquals(request.getEndpoint() + ": failed", RestStatus.CREATED, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    /**
     * Create LTR default featureset
     */
    public void createDefaultFeatureSet() throws IOException {
        createFeatureSet("default_features");
    }

    /**
     * Delete LTR featureset
     * @param name feature set
     */
    public void deleteFeatureSet(String name) throws IOException {
        Request request = new Request(
                "DELETE",
                "/_ltr/_featureset/" + name
        );

        Response response = client().performRequest(request);
        assertEquals(request.getEndpoint() + ": failed", RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    /**
     * Delete LTR default featureset
     */
    public void deleteDefaultFeatureSet() throws IOException {
        deleteFeatureSet("default_features");
    }

    /**
     * Get LTR featureset
     * @param name feature set
     */
    public void getFeatureSet(String name) throws IOException {
        Request request = new Request(
                "GET",
                "/_ltr/_featureset/" + name
        );

        Response response = client().performRequest(request);
        assertEquals(request.getEndpoint() + ": failed", RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    /**
     * Get LTR default featureset
     */
    public void getDefaultFeatureSet() throws IOException {
        getFeatureSet("default_features");
    }

    /**
     * Create LTR default model
     * @param name model name
     */
    public void createModel(String name) throws IOException {

        String defaultJsonModel = readSourceModel("/models/default-xgb-model.json");

        Request request = new Request(
                "POST",
                "/_ltr/_featureset/default_features/_createmodel"
        );

        XContentBuilder xb = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("model")
                .field("name", name)
                .startObject("model")
                .field("type", "model/xgboost+json")
                .field("definition", defaultJsonModel)
                .endObject()
                .endObject()
                .endObject();

        request.setJsonEntity(Strings.toString(xb));
        Response response = client().performRequest(request);
        assertEquals(request.getEndpoint() + ": failed", RestStatus.CREATED, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    /**
     * Create LTR default model
     */
    public void createDefaultModel() throws IOException {
        createModel("default_xgb_model");
    }

    /**
     * Delete LTR model
     * @param name feature set
     */
    public void deleteModel(String name) throws IOException {
        Request request = new Request(
                "DELETE",
                "/_ltr/_model/" + name
        );

        Response response = client().performRequest(request);
        assertEquals(request.getEndpoint() + ": failed", RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    /**
     * Delete LTR default model
     */
    public void deleteDefaultModel() throws IOException {
        deleteModel("default_xgb_model");
    }

    /**
     * Get LTR model
     * @param name feature set
     */
    public void getModel(String name) throws IOException {
        Request request = new Request(
                "GET",
                "/_ltr/_model/" + name
        );

        Response response = client().performRequest(request);
        assertEquals(request.getEndpoint() + ": failed", RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    }

    /**
     * Get LTR default model
     */
    public void getDefaultModel() throws IOException {
        getModel("default_xgb_model");
    }

    private String readSource(String path) throws IOException {
        try (InputStream is = this.getClass().getResourceAsStream(path)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Streams.copy(is,  bos);
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }

    public String readSourceModel(String sourcePath) throws IOException {
        return readSource(sourcePath);
    }
}
