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

package com.o19s.es.ltr.rest;

import com.o19s.es.ltr.action.AddDerivedFeaturesToSetAction;
import com.o19s.es.ltr.action.AddDerivedFeaturesToSetAction.AddDerivedFeaturesToSetRequestBuilder;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestStatusToXContentListener;

import java.io.IOException;

public class RestAddDerivedFeatureToSet extends FeatureStoreBaseRestHandler {

    public RestAddDerivedFeatureToSet(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(RestRequest.Method.POST, "/_ltr/_featureset/{name}/_addderivedfeatures/{expr}", this);
        controller.registerHandler(RestRequest.Method.POST, "/_ltr/{store}/_featureset/{name}/_addderivedfeatures/{expr}", this);
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String store = indexName(request);
        String setName = request.param("name");
        String routing = request.param("routing");
        String exprName = request.param("expr");
        AddDerivedFeaturesToSetRequestBuilder builder = AddDerivedFeaturesToSetAction.INSTANCE.newRequestBuilder(client);
        builder.request().setStore(store);
        builder.request().setFeatureSet(setName);
        builder.request().setDerivedName(exprName);
        builder.request().setRouting(routing);
        return (channel) -> builder.execute(new RestStatusToXContentListener<>(channel, (r) -> r.getResponse().getLocation(routing)));
    }
}
