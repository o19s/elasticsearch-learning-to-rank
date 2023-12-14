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

import com.o19s.es.ltr.ranker.LtrRanker;
import org.elasticsearch.core.Assertions;
import org.elasticsearch.common.CheckedSupplier;

import java.util.Objects;
import java.util.function.Supplier;

public final class Suppliers {
    /**
     * Utility class
     */
    private Suppliers() {}

    /**
     * @param supplier the original supplier to store
     * @param <E> the supplied type
     * @return a supplier storing and returning the same instance
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

    /**
     * Simple wrapper to make sure we run on the same thread
     */
    public static class FeatureVectorSupplier extends MutableSupplier<LtrRanker.FeatureVector> {
        private final long threadId = Assertions.ENABLED ? Thread.currentThread().getId() : 0;

        public LtrRanker.FeatureVector get() {
            assert threadId == Thread.currentThread().getId();
            return super.get();
        }

        @Override
        public void set(LtrRanker.FeatureVector obj) {
            assert threadId == Thread.currentThread().getId();
            super.set(obj);
        }
    }

    /**
     * memoize the return value of the checked supplier (thread unsafe)
     */
    public static <R, E extends Exception> CheckedSupplier<R, E> memoizeCheckedSupplier(CheckedSupplier<R, E> supplier) {
        return new CheckedMemoizeSupplier<R, E>(supplier);
    }

    private static class CheckedMemoizeSupplier<R, E extends Exception> implements CheckedSupplier<R, E> {
        private final CheckedSupplier<R, E> supplier;
        private R value;

        private CheckedMemoizeSupplier(CheckedSupplier<R, E> supplier) {
            this.supplier = supplier;
        }

        @Override
        public R get() throws E {
            if (value == null) {
                value = supplier.get();
            }
            return value;
        }
    }
}
