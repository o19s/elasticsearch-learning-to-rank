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

/*===============================================================================
 * Copyright (c) 2010-2012 University of Massachusetts.  All Rights Reserved.
 *
 * Use of the RankLib package is subject to the terms of the software license set 
 * forth in the LICENSE file included with this software, and also available at
 * http://people.cs.umass.edu/~vdang/ranklib_license.html
 *===============================================================================
 */

package com.o19s.es.ltr.ranker.ranklib.learning.neuralnet;

public class PropParameter {
    //RankNet
    public int current = -1;//index of current data point in the ranked list
    public int[][] pairMap = null;
    public PropParameter(int current, int[][] pairMap)
    {
        this.current = current;
        this.pairMap = pairMap;
    }
    //LambdaRank: RankNet + the following
    public float[][] pairWeight = null;
    public float[][] targetValue = null;
    public PropParameter(int current, int[][] pairMap, float[][] pairWeight, float[][] targetValue)
    {
        this.current = current;
        this.pairMap = pairMap;
        this.pairWeight = pairWeight;
        this.targetValue = targetValue;
    }
    //ListNet
    public float[] labels = null;//relevance label
    public PropParameter(float[] labels)
    {
        this.labels = labels;
    }
}
