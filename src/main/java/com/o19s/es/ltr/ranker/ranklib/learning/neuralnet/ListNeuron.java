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

public class ListNeuron extends Neuron {
    
    protected double[] d1;
    protected double[] d2;    
    
    public void computeDelta(PropParameter param)
    {
        double sumLabelExp = 0;
        double sumScoreExp = 0;
        for(int i=0;i<outputs.size();i++)//outputs[i] ==> the output of the current neuron on the i-th document
        {
            sumLabelExp += Math.exp(param.labels[i]);
            sumScoreExp += Math.exp(outputs.get(i));
        }

        d1 = new double[outputs.size()];
        d2 = new double[outputs.size()];
        for(int i=0;i<outputs.size();i++)
        {
            d1[i] = Math.exp(param.labels[i])/sumLabelExp;
            d2[i] = Math.exp(outputs.get(i))/ sumScoreExp;
        }
    }
    public void updateWeight(PropParameter param)
    {
        Synapse s = null;
        for(int k=0;k<inLinks.size();k++)
        {
            s = inLinks.get(k);
            double dw = 0;
            for(int l=0;l<d1.length;l++)
                dw += (d1[l] - d2[l]) * s.getSource().getOutput(l);
            
            dw *= learningRate;
            s.setWeightAdjustment(dw);
            s.updateWeight();
        }
    }
}
