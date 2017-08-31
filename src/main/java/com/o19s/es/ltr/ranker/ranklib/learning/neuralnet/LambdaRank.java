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

import com.o19s.es.ltr.ranker.ranklib.learning.RankList;
import com.o19s.es.ltr.ranker.ranklib.learning.Ranker;
import com.o19s.es.ltr.ranker.ranklib.metric.MetricScorer;

import java.util.List;

public class LambdaRank extends RankNet {
    //Parameters
    //Inherits *ALL* parameters from RankNet
    
    //Variables
    protected float[][] targetValue = null; 
    
    public LambdaRank()
    {
        
    }
    public LambdaRank(List<RankList> samples, int [] features, MetricScorer scorer)
    {
        super(samples, features, scorer);
    }
    protected int[][] batchFeedForward(RankList rl)
    {
        int[][] pairMap = new int[rl.size()][];
        targetValue = new float[rl.size()][];
        for(int i=0;i<rl.size();i++)
        {
            addInput(rl.get(i));
            propagate(i);
            
            int count = 0;
            for(int j=0;j<rl.size();j++)
                if(rl.get(i).getLabel() > rl.get(j).getLabel() || rl.get(i).getLabel() < rl.get(j).getLabel())
                    count++;
            
            pairMap[i] = new int[count];
            targetValue[i] = new float[count];
            
            int k=0;
            for(int j=0;j<rl.size();j++)
                if(rl.get(i).getLabel() > rl.get(j).getLabel() || rl.get(i).getLabel() < rl.get(j).getLabel())
                {
                    pairMap[i][k] = j;
                    if(rl.get(i).getLabel() > rl.get(j).getLabel())
                        targetValue[i][k] = 1;
                    else
                        targetValue[i][k] = 0;
                    k++;
                }
        }
        return pairMap;
    }
    protected void batchBackPropagate(int[][] pairMap, float[][] pairWeight)
    {
        for(int i=0;i<pairMap.length;i++)
        {
            PropParameter p = new PropParameter(i, pairMap, pairWeight, targetValue);
            //back-propagate
            outputLayer.computeDelta(p);//starting at the output layer
            for(int j=layers.size()-2;j>=1;j--)//back-propagate to the first hidden layer
                layers.get(j).updateDelta(p);
            
            //weight update
            outputLayer.updateWeight(p);
            for(int j=layers.size()-2;j>=1;j--)
                layers.get(j).updateWeight(p);
        }
    }
    protected RankList internalReorder(RankList rl)
    {
        return rank(rl);
    }
    protected float[][] computePairWeight(int[][] pairMap, RankList rl)
    {
        double[][] changes = scorer.swapChange(rl);
        float[][] weight = new float[pairMap.length][];
        for(int i=0;i<weight.length;i++)
        {
            weight[i] = new float[pairMap[i].length];
            for(int j=0;j<pairMap[i].length;j++)
            {
                int sign = (rl.get(i).getLabel() > rl.get(pairMap[i][j]).getLabel())?1:-1;
                weight[i][j] = (float)Math.abs(changes[i][pairMap[i][j]])*sign;
            }
        }
        return weight;
    }
    protected void estimateLoss() 
    {
        misorderedPairs = 0;
        for(int j=0;j<samples.size();j++)
        {
            RankList rl = samples.get(j);
            for(int k=0;k<rl.size()-1;k++)
            {
                double o1 = eval(rl.get(k));
                for(int l=k+1;l<rl.size();l++)
                {
                    if(rl.get(k).getLabel() > rl.get(l).getLabel())
                    {
                        double o2 = eval(rl.get(l));
                        //error += crossEntropy(o1, o2, 1.0f);
                        if(o1 < o2)
                            misorderedPairs++;
                    }
                }
            }
        }
        error = 1.0 - scoreOnTrainingData;
        if(error > lastError)
        {
            //Neuron.learningRate *= 0.8;
            straightLoss++;
        }
        else
            straightLoss = 0;
        lastError = error;
    }
    
    public Ranker createNew()
    {
        return new LambdaRank();
    }
    public String name()
    {
        return "LambdaRank";
    }
}
