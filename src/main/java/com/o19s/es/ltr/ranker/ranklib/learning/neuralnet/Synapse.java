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

import org.elasticsearch.common.Randomness;

import java.util.Random;

/**
 * @author vdang
 */
public class Synapse {
    static Random random = Randomness.get();
    protected double weight = 0.0;
    protected double dW = 0.0;//last weight adjustment
    protected Neuron source = null;
    protected Neuron target = null;
    
    public Synapse(Neuron source, Neuron target)
    {
        this.source = source;
        this.target = target;
        this.source.getOutLinks().add(this);
        this.target.getInLinks().add(this);
        //weight = random.nextDouble()/5;
        weight = (random.nextInt(2)==0?1:-1)*random.nextFloat()/10;
    }
    public Neuron getSource()
    {
        return source;
    }
    public Neuron getTarget()
    {
        return target;
    }
    public void setWeight(double w)
    {
        this.weight = w;
    }
    public double getWeight()
    {
        return weight;
    }
    public double getLastWeightAdjustment()
    {
        return dW;
    }
    public void setWeightAdjustment(double dW)
    {
        this.dW = dW;
    }
    public void updateWeight()
    {
        this.weight += dW;
    }
}
