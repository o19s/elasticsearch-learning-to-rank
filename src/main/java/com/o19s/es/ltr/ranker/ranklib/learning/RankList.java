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

package com.o19s.es.ltr.ranker.ranklib.learning;

import com.o19s.es.ltr.ranker.ranklib.utils.Sorter;

import java.util.List;

/**
 * @author vdang
 * 
 * This class implement the list of objects (each of which is a DataPoint) to be ranked. 
 */
public class RankList {

    protected DataPoint[] rl = null;
    
    public RankList(List<DataPoint> rl)
    {
        this.rl = new DataPoint[rl.size()];
        for(int i=0;i<rl.size();i++)
            this.rl[i] = rl.get(i);
    }
    public RankList(RankList rl)
    {
        this.rl = new DataPoint[rl.size()];
        for(int i=0;i<rl.size();i++)
            this.rl[i] = rl.get(i);
    }
    public RankList(RankList rl, int[] idx)
    {
        this.rl = new DataPoint[rl.size()];
        for(int i=0;i<idx.length;i++)
            this.rl[i] = rl.get(idx[i]);
    }
    public RankList(RankList rl, int[] idx, int offset)
    {
        this.rl = new DataPoint[rl.size()];
        for(int i=0;i<idx.length;i++)
            this.rl[i] = rl.get(idx[i]-offset);
    }    
    public String getID()
    {
        return get(0).getID();
    }
    public int size()
    {
        return rl.length;
    }
    public DataPoint get(int k)
    {
        return rl[k];
    }
    public void set(int k, DataPoint p)
    {
        rl[k] = p;
    }
    public RankList getCorrectRanking()
    {
        double[] score = new double[rl.length];
        for(int i=0;i<rl.length;i++)
            score[i] = rl[i].getLabel();
        int[] idx = Sorter.sort(score, false);
        return new RankList(this, idx);
    }
    
    public RankList getRanking(short fid)
    {
        double[] score = new double[rl.length];
        for(int i=0;i<rl.length;i++)
            score[i] = rl[i].getFeatureValue(fid);
        int[] idx = Sorter.sort(score, false);
        return new RankList(this, idx);
    }
}
