/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.feature;

import com.o19s.es.ltr.utils.Scripting;
import org.apache.lucene.expressions.Expression;
import org.elasticsearch.common.Nullable;
import java.util.Objects;

/**
 * A prebuilt derived feature
 */
public class PrebuiltDerivedFeature implements DerivedFeature {
    private final String name;
    private final Expression expression;

    public PrebuiltDerivedFeature(@Nullable String name, String expression) {
        this.name = name;
        this.expression = (Expression) Scripting.compile(expression);
    }

    @Override @Nullable
    public String name() {
        return name;
    }

    @Override
    public Expression expression() { return expression;}

    @Override
    public int hashCode() {
        return Objects.hash(name, expression);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PrebuiltDerivedFeature)) {
            return false;
        }
        PrebuiltDerivedFeature other = (PrebuiltDerivedFeature) o;
        return Objects.equals(name, other.name)
                && Objects.equals(expression, other.expression);
    }

    @Override
    public String toString() {
        return "[" + name + "]: " + expression;
    }
}
