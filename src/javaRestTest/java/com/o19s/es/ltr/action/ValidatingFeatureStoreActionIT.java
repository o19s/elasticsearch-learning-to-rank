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

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.instanceOf;

import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.ranker.parser.LinearRankerParser;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.elasticsearch.index.query.QueryBuilders;
import org.hamcrest.CoreMatchers;

public class ValidatingFeatureStoreActionIT extends BaseIntegrationTest {
  public void testValidateFeature() throws ExecutionException, InterruptedException {
    prepareTestIndex();
    String brokenQuery = "{\"query\": {\"match\":{\"test\": \"{{query_string}}\"}}}";
    StoredFeature feature =
        new StoredFeature("test", singletonList("query_string"), "mustache", brokenQuery);
    Map<String, Object> params = new HashMap<>();
    params.put("query_string", "a query");
    Throwable e =
        expectThrows(
                ExecutionException.class,
                () -> addElement(feature, new FeatureValidation("test_index", params)))
            .getCause();
    assertThat(e, instanceOf(IllegalArgumentException.class));
    assertThat(
        e.getMessage(), CoreMatchers.containsString("Cannot store element, validation failed."));
  }

  public void testValidateFeatureSet() throws ExecutionException, InterruptedException {
    prepareTestIndex();
    String matchQuery = QueryBuilders.matchQuery("test", "{{query_string}}").toString();

    StoredFeature feature =
        new StoredFeature("test", singletonList("query_string"), "mustache", matchQuery);
    String brokenQuery = "{\"query\": {\"match\":{\"test\": \"{{query_string}}\"}}}";
    StoredFeature brokenFeature =
        new StoredFeature("broken", singletonList("query_string"), "mustache", brokenQuery);
    Map<String, Object> params = new HashMap<>();
    params.put("query_string", "a query");
    StoredFeatureSet brokenFeatureSet =
        new StoredFeatureSet("my_feature_set", Arrays.asList(feature, brokenFeature));
    Throwable e =
        expectThrows(
                ExecutionException.class,
                () -> addElement(brokenFeatureSet, new FeatureValidation("test_index", params)))
            .getCause();
    assertThat(e, instanceOf(IllegalArgumentException.class));
    assertThat(
        e.getMessage(), CoreMatchers.containsString("Cannot store element, validation failed."));
  }

  public void testValidateModel() throws ExecutionException, InterruptedException {
    prepareTestIndex();
    String matchQuery = QueryBuilders.matchQuery("test", "{{query_string}}").toString();

    StoredFeature feature =
        new StoredFeature("test", singletonList("query_string"), "mustache", matchQuery);
    String brokenQuery = "{\"query\": {\"match\":{\"test\": \"{{query_string}}\"}}}";
    StoredFeature brokenFeature =
        new StoredFeature("broken", singletonList("query_string"), "mustache", brokenQuery);
    Map<String, Object> params = new HashMap<>();
    params.put("query_string", "a query");
    String model = "{\"test\": 2.1, \"broken\": 4.3}";
    StoredLtrModel brokenModel =
        new StoredLtrModel(
            "broken_model",
            new StoredFeatureSet("my_feature_set", Arrays.asList(feature, brokenFeature)),
            new StoredLtrModel.LtrModelDefinition(LinearRankerParser.TYPE, model, true));
    Throwable e =
        expectThrows(
                ExecutionException.class,
                () -> addElement(brokenModel, new FeatureValidation("test_index", params)))
            .getCause();
    assertThat(e, instanceOf(IllegalArgumentException.class));
    assertThat(
        e.getMessage(), CoreMatchers.containsString("Cannot store element, validation failed."));
  }

  public void testValidationOnAddFeatureToSet() {
    prepareTestIndex();
    String matchQuery = QueryBuilders.matchQuery("test", "{{query_string}}").toString();

    StoredFeature feature =
        new StoredFeature("test", singletonList("query_string"), "mustache", matchQuery);
    String brokenQuery = "{\"query\": {\"match\":{\"test\": \"{{query_string}}\"}}}";
    StoredFeature brokenFeature =
        new StoredFeature("broken", singletonList("query_string"), "mustache", brokenQuery);
    Map<String, Object> params = new HashMap<>();
    params.put("query_string", "a query");
    AddFeaturesToSetAction.AddFeaturesToSetRequestBuilder request =
        new AddFeaturesToSetAction.AddFeaturesToSetRequestBuilder(client());
    request.request().setStore(IndexFeatureStore.DEFAULT_STORE);
    request.request().setValidation(new FeatureValidation("test_index", params));
    request.request().setFeatures(Arrays.asList(feature, brokenFeature));
    request.request().setFeatureSet("my_feature_set");
    IllegalArgumentException e = expectThrows(IllegalArgumentException.class, request::get);
    assertThat(
        e.getMessage(), CoreMatchers.containsString("Cannot store element, validation failed."));
  }

  public void testValidationOnCreateModelFromSet() throws ExecutionException, InterruptedException {
    prepareTestIndex();
    String matchQuery = QueryBuilders.matchQuery("test", "{{query_string}}").toString();

    StoredFeature feature =
        new StoredFeature("test", singletonList("query_string"), "mustache", matchQuery);
    String brokenQuery = "{\"query\": {\"match\":{\"test\": \"{{query_string}}\"}}}";
    StoredFeature brokenFeature =
        new StoredFeature("broken", singletonList("query_string"), "mustache", brokenQuery);
    Map<String, Object> params = new HashMap<>();
    params.put("query_string", "a query");
    StoredFeatureSet brokenFeatureSet =
        new StoredFeatureSet("my_feature_set", Arrays.asList(feature, brokenFeature));
    // Store a broken feature set
    addElement(brokenFeatureSet);
    CreateModelFromSetAction.CreateModelFromSetRequestBuilder request =
        new CreateModelFromSetAction.CreateModelFromSetRequestBuilder(client());
    request.request().setValidation(new FeatureValidation("test_index", params));
    StoredLtrModel.LtrModelDefinition definition =
        new StoredLtrModel.LtrModelDefinition(
            "model/linear", "{\"test\": 2.1, \"broken\": 4.3}", true);
    request.withoutVersion(
        IndexFeatureStore.DEFAULT_STORE, "my_feature_set", "broken_model", definition);
    request.request().setValidation(new FeatureValidation("test_index", params));
    IllegalArgumentException e = expectThrows(IllegalArgumentException.class, request::get);
    assertThat(
        e.getMessage(), CoreMatchers.containsString("Cannot store element, validation failed."));
  }

  private void prepareTestIndex() {
    client().admin().indices().prepareCreate("test_index").get();
  }
}
