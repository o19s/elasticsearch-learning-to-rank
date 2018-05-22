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

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.query.DerivedExpressionQuery;
import com.o19s.es.ltr.utils.Scripting;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.apache.lucene.util.RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;

public class PrecompiledExpressionFeature implements Feature, Accountable {
    public static final String TEMPLATE_LANGUAGE = "derived_expression";

    private static final long BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(PrecompiledExpressionFeature.class);

    private final String name;
    private final Expression expression;

    private PrecompiledExpressionFeature(String name, Expression expression) {
        this.name = name;
        this.expression = expression;
    }

    public static PrecompiledExpressionFeature compile(StoredFeature feature) {
        assert TEMPLATE_LANGUAGE.equals(feature.templateLanguage());
        Expression expr = (Expression) Scripting.compile(feature.template());
        return new PrecompiledExpressionFeature(feature.name(), expr);
    }

    @Override
    public long ramBytesUsed() {
        return BASE_RAM_USED +
                (Character.BYTES * name.length()) + NUM_BYTES_ARRAY_HEADER +
                (((Character.BYTES * expression.sourceText.length()) + NUM_BYTES_ARRAY_HEADER)*2);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Query doToQuery(LtrQueryContext context, FeatureSet set, Map<String, Object> params) {
        return new DerivedExpressionQuery(set, expression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PrecompiledExpressionFeature that = (PrecompiledExpressionFeature) o;

        return Objects.equals(name, that.name)
                && Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, expression);
    }

    @Override
    public void validate(FeatureSet set) {
        for(String var : expression.variables) {
            if(!set.hasFeature(var)) {
                throw new IllegalArgumentException("Derived feature [" + this.name + "] refers " +
                        "to unknown feature: [" + var + "]");
            }
        }
    }
}
