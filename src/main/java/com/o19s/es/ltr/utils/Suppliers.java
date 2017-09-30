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

package com.o19s.es.ltr.utils;

import java.util.Objects;
import java.util.function.Supplier;

public final class Suppliers {
    /**
     * Utility class
     */
    private Suppliers() {}

    /**
     * Build a supplier that stores and return the same instance.
     */
    public static <E> Supplier<E> memoize(Supplier<E> supplier) {
        return new MemoizeSupplier<>(supplier);
    }

    private static class MemoizeSupplier<E> implements Supplier<E> {
        private volatile boolean initialized = false;
        private final Supplier<E> supplier;
        private E value;

        MemoizeSupplier(Supplier<E> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        public E get() {
            if (!initialized) {
                synchronized (this) {
                    if (!initialized) {
                        E t = supplier.get();
                        value = t;
                        initialized = true;
                        return t;
                    }
                }
            }
            return value;
        }
    }

    /**
     * A mutable supplier
     */
    public static class MutableSupplier<T> implements Supplier<T> {
        T obj;

        @Override
        public T get() {
            return obj;
        }

        public void set(T obj) {
            this.obj = obj;
        }
    }
}
