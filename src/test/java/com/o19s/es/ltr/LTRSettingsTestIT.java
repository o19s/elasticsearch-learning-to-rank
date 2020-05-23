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

import org.elasticsearch.client.ResponseException;

import static org.hamcrest.Matchers.containsString;

public class LTRSettingsTestIT extends LTRRestTestCase {

    public void testCreateStoreDisabled() throws Exception {
        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, false);

        Exception ex = expectThrows(ResponseException.class, this::createDefaultLTRStore);
        assertThat(ex.getMessage(), containsString("LTR plugin is disabled"));

        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, true);
        createDefaultLTRStore();
    }

    public void testDeleteStoreDisabled() throws Exception {
        createDefaultLTRStore();
        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, false);

        Exception ex = expectThrows(ResponseException.class, this::deleteDefaultLTRStore);
        assertThat(ex.getMessage(), containsString("LTR plugin is disabled"));

        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, true);
        deleteDefaultLTRStore();
    }

    public void testCreateFeatureSetDisabled() throws Exception {
        createDefaultLTRStore();
        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, false);

        Exception ex = expectThrows(ResponseException.class, this::createDefaultFeatureSet);
        assertThat(ex.getMessage(), containsString("LTR plugin is disabled"));

        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, true);
        createDefaultFeatureSet();
    }

    public void testDeleteFeatureSetDisabled() throws Exception {
        createDefaultLTRStore();
        createDefaultFeatureSet();
        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, false);

        Exception ex = expectThrows(ResponseException.class, this::deleteDefaultFeatureSet);
        assertThat(ex.getMessage(), containsString("LTR plugin is disabled"));

        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, true);
        deleteDefaultFeatureSet();
    }

    public void testGetFeatureSetDisabled() throws Exception {
        createDefaultLTRStore();
        createDefaultFeatureSet();
        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, false);

        Exception ex = expectThrows(ResponseException.class, this::getDefaultFeatureSet);
        assertThat(ex.getMessage(), containsString("LTR plugin is disabled"));

        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, true);
        getDefaultFeatureSet();
    }

    public void testCreateModelDisabled() throws Exception {
        createDefaultLTRStore();
        createDefaultFeatureSet();
        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, false);

        Exception ex = expectThrows(ResponseException.class, this::createDefaultModel);
        assertThat(ex.getMessage(), containsString("LTR plugin is disabled"));

        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, true);
        createDefaultModel();
    }

    public void testDeleteModelDisabled() throws Exception {
        createDefaultLTRStore();
        createDefaultFeatureSet();
        createDefaultModel();
        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, false);

        Exception ex = expectThrows(ResponseException.class, this::deleteDefaultModel);
        assertThat(ex.getMessage(), containsString("LTR plugin is disabled"));

        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, true);
        deleteDefaultModel();
    }

    public void testGetModelDisabled() throws Exception {
        createDefaultLTRStore();
        createDefaultFeatureSet();
        createDefaultModel();
        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, false);

        Exception ex = expectThrows(ResponseException.class, this::getDefaultModel);
        assertThat(ex.getMessage(), containsString("LTR plugin is disabled"));

        updateClusterSettings(LTRSettings.LTR_PLUGIN_ENABLED, true);
        getDefaultModel();
    }
}
