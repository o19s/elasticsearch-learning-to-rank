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

package com.o19s.es.ltr.ranker.ranklib.learning.tree;

import com.o19s.es.ltr.ranker.ranklib.learning.RankList;
import com.o19s.es.ltr.ranker.ranklib.learning.Ranker;
import com.o19s.es.ltr.ranker.ranklib.metric.MetricScorer;

import java.util.List;

/**
 * @author vdang
 *
 *  This class implements MART for (point-wise) ranking:
 *  J.H. Friedman. Greedy function approximation: A gradient boosting machine. 
 *  Technical Report, IMS Reitz Lecture, Stanford, 1999; see also Annals of Statistics, 2001.
 */
public class MART extends LambdaMART {
    //Parameters
    //Inherits *ALL* parameters from LambdaMART
    
    public MART()
    {        
    }
    public MART(List<RankList> samples, int[] features, MetricScorer scorer)
    {
        super(samples, features, scorer);
    }
    
    public Ranker createNew()
    {
        return new MART();
    }
    public String name()
    {
        return "MART";
    }
    protected void computePseudoResponses()
    {
        for(int i=0;i<martSamples.length;i++)
            pseudoResponses[i] = martSamples[i].getLabel() - modelScores[i];
    }
    protected void updateTreeOutput(RegressionTree rt)
    {
        List<Split> leaves = rt.leaves();
        for(int i=0;i<leaves.size();i++)
        {
            float s1 = 0.0F;
            Split s = leaves.get(i);
            int[] idx = s.getSamples();
            for(int j=0;j<idx.length;j++)
            {
                int k = idx[j];
                s1 += pseudoResponses[k];
            }
            s.setOutput(s1/idx.length);
        }
    }
}
