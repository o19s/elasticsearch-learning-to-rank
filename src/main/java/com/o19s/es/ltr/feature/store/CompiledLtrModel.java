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

package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.LtrModel;
import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_OBJECT_HEADER;

public class CompiledLtrModel implements LtrModel, Accountable {
    private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(StoredLtrModel.class);

    private final String name;
    private final FeatureSet set;
    private final LtrRanker ranker;

    public CompiledLtrModel(String name, FeatureSet set, LtrRanker ranker) {
        this.name = name;
        this.set = set;
        this.ranker = ranker;
    }

    /**
     * Name of the model
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Return the {@link LtrRanker} implementation used by this model
     */
    @Override
    public LtrRanker ranker() {
        return ranker;
    }

    /**
     * The set of features used by this model
     */
    @Override
    public FeatureSet featureSet() {
        return set;
    }

    /**
     * Return the memory usage of this object in bytes. Negative values are illegal.
     */
    @Override
    public long ramBytesUsed() {
        return BASE_RAM_USED + name.length() * Character.BYTES + NUM_BYTES_ARRAY_HEADER
                + (set instanceof Accountable ? ((Accountable)set).ramBytesUsed() : set.size() * NUM_BYTES_OBJECT_HEADER)
                + (ranker instanceof Accountable ?
                ((Accountable)ranker).ramBytesUsed() : set.size() * NUM_BYTES_OBJECT_HEADER);
    }
}
