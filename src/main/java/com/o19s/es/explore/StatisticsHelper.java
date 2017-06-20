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
import java.util.Arrays;

public class StatisticsHelper {
    private final ArrayList<Float> data = new ArrayList<>();

    private float min = Float.MAX_VALUE;
    private float max = 0.0f;

    StatisticsHelper() {

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

    float getMax() {
        return max;
    }

    float getMin() {
        return min;
    }

    float getMean() {
        return getSum() / data.size();
    }

    float getSum() {
        float sum = 0.0f;

        for(float a : data) {
            sum += a;
        }

        return sum;
    }

    double getVariance() {
        float mean = getMean();
        float temp = 0.0f;
        for(float a : data)
            temp += (a-mean)*(a-mean);
        return temp/(data.size()-1);
    }

    float getStdDev() {
        return (float) Math.sqrt(getVariance());
    }

    public float median() {
        Integer[] sortedData = data.toArray(new Integer[data.size()]);
        Arrays.sort(sortedData);

        if (sortedData.length % 2 == 0)
        {
            return (sortedData[(sortedData.length / 2) - 1] + sortedData[sortedData.length / 2]) / 2.0f;
        }
        return sortedData[sortedData.length / 2];
    }
}
