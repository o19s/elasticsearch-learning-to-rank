/*
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
 *
 */
package com.o19s.es.explore;

import org.apache.lucene.util.LuceneTestCase;

public class StatisticsHelperTests extends LuceneTestCase {
    private final float[] dataset = new float[] {
      0.0f, -5.0f, 10.0f, 5.0f
    };

    public void testStats() throws Exception {
        StatisticsHelper stats = new StatisticsHelper();

        for(float f : dataset) {
            stats.add(f);
        }

        assertEquals(10.0f, stats.getMax(), 0.0f);
        assertEquals(-5.0f, stats.getMin(), 0.0f);
        assertEquals(2.5f, stats.getMean(), 0.0f);
        assertEquals(5.59f, stats.getStdDev(), 0.009f);
        assertEquals(31.25f, stats.getVariance(), 0.009f);
    }

    public void testSingleElement() throws Exception {
        StatisticsHelper stats = new StatisticsHelper();

        stats.add(42.0f);

        assertEquals(42.0f, stats.getMax(), 0.0f);
        assertEquals(42.0f, stats.getMin(), 0.0f);
        assertEquals(42.0f, stats.getMean(), 0.0f);
        assertEquals(0.0f, stats.getStdDev(), 0.0f);
        assertEquals(0.0f, stats.getVariance(), 0.0f);
    }
}
