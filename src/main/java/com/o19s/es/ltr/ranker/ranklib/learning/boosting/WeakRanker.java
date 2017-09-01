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

package com.o19s.es.ltr.ranker.ranklib.learning.boosting;

import com.o19s.es.ltr.ranker.ranklib.learning.RankList;
import com.o19s.es.ltr.ranker.ranklib.utils.Sorter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vdang
 * 
 * Weak rankers for AdaRank.
 */
public class WeakRanker {
    private int fid = -1;
    
    public WeakRanker(int fid)
    {
        this.fid = fid;
    }
    public int getFID()
    {
        return fid;
    }
    
    public RankList rank(RankList l)
    {
        double[] score = new double[l.size()];
        for(int i=0;i<l.size();i++)
            score[i] = l.get(i).getFeatureValue(fid);
        int[] idx = Sorter.sort(score, false);
        return new RankList(l, idx);
    }
    public List<RankList> rank(List<RankList> l)
    {
        List<RankList> ll = new ArrayList<RankList>();
        for(int i=0;i<l.size();i++)
            ll.add(rank(l.get(i)));
        return ll;
    }
}
