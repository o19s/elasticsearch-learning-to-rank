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

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CachingTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.index.analysis.NamedAnalyzer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CachedTokenStreams {
    private final Map<Key, CachingTokenFilterWrapper> map = new HashMap<>();
    private static final Logger LOGGER = ESLoggerFactory.getLogger(CachedTokenStreams.class);

    public CachingTokenFilterWrapper get(NamedAnalyzer analyzer, String query) {
        return map.computeIfAbsent(new Key(analyzer, query),
            (k) -> new CachingTokenFilterWrapper(eagerLoad(analyzer, query)));
    }

    private CachingTokenFilter eagerLoad(NamedAnalyzer analyzer, String query) {
        // Not strictly needed but this is to make sure we close the underlying tokenStream
        // in normal condition it'll be closed on the first use but if the first use does happen
        CachingTokenFilter filter = null;
        try (TokenStream stream = analyzer.tokenStream(analyzer.name(), query)){
            filter = new CachingTokenFilter(stream);
            filter.reset();
            // only one call is sufficient to fully load the cache
            filter.incrementToken();
            assert !stream.incrementToken();
        } catch(IOException ioe) {
            throw new RuntimeException("Cannot create the CachingTokenFilter for [" + analyzer.name() + "]", ioe);
        }
        return filter;
    }

    public void close() {
        map.forEach((key, v) -> v.close());
        map.clear();
    }

    /**
     * Evil hack
     */
    private static class CachingTokenFilterWrapper extends Analyzer {
        private static final Tokenizer NULL_TOKENIZER = new Tokenizer() {
            @Override
            public boolean incrementToken()  {
                return false;
            }
        };
        private static final Analyzer.ReuseStrategy REUSE_STRATEGY = new Analyzer.ReuseStrategy() {
            @Override
            public Analyzer.TokenStreamComponents getReusableComponents(Analyzer analyzer, String fieldName) {
                assert analyzer instanceof CachingTokenFilterWrapper;
                TokenStreamComponents components = ((CachingTokenFilterWrapper) analyzer).components;
                try {
                    // Since this one will never be used call close() preemptively
                    // to avoid failures on initReader
                    components.getTokenizer().close();
                } catch (IOException e) {
                    LOGGER.warn("Failed to reset CachingTokenFilterWrapper: ", e);
                }
                return ((CachingTokenFilterWrapper) analyzer).components;
            }
            @Override
            public void setReusableComponents(Analyzer analyzer, String fieldName, Analyzer.TokenStreamComponents components) {
                assert analyzer instanceof CachingTokenFilterWrapper;
                assert components == ((CachingTokenFilterWrapper) analyzer).components;
            }
        };

        private final TokenStreamComponents components;
        CachingTokenFilterWrapper(CachingTokenFilter filter) {
            super(REUSE_STRATEGY);
            components = new TokenStreamComponents(NULL_TOKENIZER, filter);
        }

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            assert false : "Always reused";
            return null;
        }

        /**
         * Frees persistent resources used by this Analyzer
         */
        @Override
        public void close() {
            super.close();
            try {
                components.getTokenStream().close();
            } catch (IOException ioe) {
                LOGGER.warn( "Failed to close analyzer: ", ioe );
            }
        }
    }
    static class Key {
        private final NamedAnalyzer analyzer;
        private final String query;

        Key(NamedAnalyzer analyzer, String query) {
            this.analyzer = analyzer;
            this.query = query;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return Objects.equals(analyzer.name(), key.analyzer.name()) &&
                    Objects.equals(query, key.query);
        }

        @Override
        public int hashCode() {
            return Objects.hash(analyzer.name(), query);
        }
    }
}
