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

import java.util.ArrayList;

public class StatisticsHelper {
    private final ArrayList<Float> data = new ArrayList<>(10);

    private float min = Float.MAX_VALUE;
    private float max = 0.0f;

    public enum AggrType {
        AVG("avg"),
        MAX("max"),
        MIN("min"),
        STDDEV("stddev");

        private String type;

        AggrType(String type){
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    public StatisticsHelper() {

    }

    public void add(float val) {
        data.add(val);

        if(val < this.min) {
            this.min = val;
        }

        if(val > this.max) {
            this.max = val;
        }
    }

    public ArrayList<Float> getData() {
        return data;
    }

    public int getSize(){
        return data.size();
    }

    public float getMax() {
        assert !data.isEmpty();

        return max;
    }

    public float getMin() {
        assert !data.isEmpty();

        return min;
    }

    public float getMean() {
        assert !data.isEmpty();

        return getSum() / data.size();
    }

    public float getSum() {
        assert !data.isEmpty();

        float sum = 0.0f;

        for(float a : data) {
            sum += a;
        }

        return sum;
    }

    public float getVariance() {
        assert !data.isEmpty();

        float mean = getMean();
        float temp = 0.0f;
        for(float a : data)
            temp += (a-mean)*(a-mean);
        return temp/data.size();
    }

    public float getStdDev() {
        assert !data.isEmpty();

        return (float) Math.sqrt(getVariance());
    }

    public float getAggr(AggrType type) {
        switch(type) {
            case AVG:
                return getMean();
            case MAX:
                return getMax();
            case MIN:
                return getMin();
            case STDDEV:
                return getStdDev();
            default:
                return 0.0f;
        }
    }
}
