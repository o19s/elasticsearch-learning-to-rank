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

package com.o19s.es.ltr.ranker.ranklib.metric;


import com.o19s.es.ltr.ranker.ranklib.learning.RankList;

import java.util.List;

/**
 * @author vdang
 * A generic retrieval measure computation interface. 
 */
public abstract class MetricScorer {

    /** The depth parameter, or how deep of a ranked list to use to score the measure. */
    protected int k = 10;
    
    public MetricScorer() 
    {
        
    }

    /**
     * The depth parameter, or how deep of a ranked list to use to score the measure.
     * @param k the new depth for this measure.
     */
    public void setK(int k)
    {
        this.k = k;
    }
    /** The depth parameter, or how deep of a ranked list to use to score the measure. */
    public int getK()
    {
        return k;
    }
    public void loadExternalRelevanceJudgment(String qrelFile)
    {
        
    }
    public double score(List<RankList> rl)
    {
        double score = 0.0;
        for(int i=0;i<rl.size();i++)
            score += score(rl.get(i));
        return score/rl.size();
    }
    
    protected int[] getRelevanceLabels(RankList rl)
    {
        int[] rel = new int[rl.size()];
        for(int i=0;i<rl.size();i++)
            rel[i] = (int)rl.get(i).getLabel();
        return rel;
    }
    
    public abstract double score(RankList rl);
    public abstract MetricScorer copy();
    public abstract String name();
    public abstract double[][] swapChange(RankList rl);
}
