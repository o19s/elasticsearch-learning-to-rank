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

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.ranklib.learning.DataPoint;
import com.o19s.es.ltr.ranker.ranklib.learning.FEATURE_TYPE;
import com.o19s.es.ltr.ranker.ranklib.learning.RankLibError;
import com.o19s.es.ltr.ranker.ranklib.learning.RankList;
import com.o19s.es.ltr.ranker.ranklib.learning.Ranker;
import com.o19s.es.ltr.ranker.ranklib.metric.MetricScorer;
import com.o19s.es.ltr.ranker.ranklib.utils.MergeSorter;
import org.elasticsearch.common.io.FastStringReader;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vdang
 * 
 * This class implements RankBoost.
 *  Y. Freund, R. Iyer, R. Schapire, and Y. Singer. An efficient boosting algorithm for combining preferences. 
 *  The Journal of Machine Learning Research, 4: 933-969, 2003.
 */
public class RankBoost extends Ranker {
    public static int nIteration = 300;//number of rounds
    public static int nThreshold = 10;
    
    protected double[][][] sweight = null;//sample weight D(x_0, x_1) -- the weight of x_1 ranked above x_2
    protected double[][] potential = null;//pi(x)
    protected List<List<int[]>> sortedSamples = new ArrayList<List<int[]>>();
    protected double[][] thresholds = null;//candidate values for weak rankers' threshold, selected from feature values
    protected int[][] tSortedIdx = null;//sorted (descend) index for @thresholds
    
    protected List<RBWeakRanker> wRankers = null;//best weak rankers at each round
    protected List<Double> rWeight = null;//alpha (weak rankers' weight)
    
    //to store the best model on validation data (if specified)
    protected List<RBWeakRanker> bestModelRankers = new ArrayList<RBWeakRanker>();
    protected List<Double> bestModelWeights = new ArrayList<Double>();
    
    private double R_t = 0.0;
    private double Z_t = 1.0;
    private int totalCorrectPairs = 0;//crucial pairs
    
    public RankBoost()
    {
        
    }
    public RankBoost(List<RankList> samples, int[] features, MetricScorer scorer)
    {
        super(samples, features, scorer);
    }
    
    private int[] reorder(RankList rl, int fid)
    {
        double[] score = new double[rl.size()];
        for(int i=0;i<rl.size();i++)
            score[i] = rl.get(i).getFeatureValue(fid);
        return MergeSorter.sort(score, false);
    }

    public void init()
    {
        PRINT("Initializing... ");
        
        wRankers = new ArrayList<RBWeakRanker>();
        rWeight = new ArrayList<Double>();
        
        //for each (true) ranked list, we only care about correctly ranked pair (e.g. L={1,2,3} => <1,2>, <1,3>, <2,3>)
        //    count the number of correctly ranked pairs from sample ranked list
        totalCorrectPairs = 0;
        for(int i=0;i<samples.size();i++)
        {
            samples.set(i, samples.get(i).getCorrectRanking());//make sure the training samples are in correct ranking
            RankList rl = samples.get(i);
            for(int j=0;j<rl.size()-1;j++)
                //faster than the for-if below
                for(int k=rl.size()-1;k>=j+1 && rl.get(j).getLabel() > rl.get(k).getLabel();k--)
                //for(int k=j+1;k<rl.size();k++)
                    //if(rl.get(j).getLabel() > rl.get(k).getLabel())
                        totalCorrectPairs++;
        }
        
        //compute weight for all correctly ranked pairs
        sweight = new double[samples.size()][][];
        for(int i=0;i<samples.size();i++)
        {
            RankList rl = samples.get(i);
            sweight[i] = new double[rl.size()][];
            for(int j=0;j<rl.size()-1;j++)
            {
                sweight[i][j] = new double[rl.size()];
                for(int k=j+1;k<rl.size();k++)
                    if(rl.get(j).getLabel() > rl.get(k).getLabel())//strictly "greater than" ==> crucial pairs
                        sweight[i][j][k] = 1.0 / totalCorrectPairs;
                    else
                        sweight[i][j][k] = 0.0;//not crucial pairs
            }
        }
        
        //init potential matrix
        potential = new double[samples.size()][];
        for(int i=0;i<samples.size();i++)
            potential[i] = new double[samples.get(i).size()];
        
        if(nThreshold <= 0)
        {
            //create a table of candidate thresholds (for each feature) for weak rankers (all possible feature values)
            int count = 0;
            for(int i=0;i<samples.size();i++)
                count += samples.get(i).size();
            
            thresholds = new double[features.length][];
            for(int i=0;i<features.length;i++)
                thresholds[i] = new double[count];
            
            int c = 0;
            for(int i=0;i<samples.size();i++)
            {
                RankList rl = samples.get(i);
                for(int j=0;j<rl.size();j++)
                {
                    for(int k=0;k<features.length;k++)
                        thresholds[k][c] = rl.get(j).getFeatureValue(features[k]);
                    c++;
                }
            }
        }
        else
        {
            double[] fmax = new double[features.length];
            double[] fmin = new double[features.length];
            for(int i=0;i<features.length;i++)
            {
                fmax[i] = -1E6;
                fmin[i] =  1E6;
            }
            
            for(int i=0;i<samples.size();i++)
            {
                RankList rl = samples.get(i);
                for(int j=0;j<rl.size();j++)
                {
                    for(int k=0;k<features.length;k++)
                    {
                        double f = rl.get(j).getFeatureValue(features[k]);
                        if (f > fmax[k])
                            fmax[k] = f;
                        if (f < fmin[k])
                            fmin[k] = f;
                    }
                }
            }
            
            thresholds = new double[features.length][];
            for(int i=0;i<features.length;i++)
            {
                double step = (Math.abs(fmax[i] - fmin[i]))/nThreshold;
                thresholds[i] = new double[nThreshold+1];
                thresholds[i][0] = fmax[i];
                for(int j=1;j<nThreshold;j++)
                    thresholds[i][j] = thresholds[i][j-1] - step;
                thresholds[i][nThreshold] = fmin[i] - 1.0E8;
            }
        }
        
        //sort this table with respect to each feature (each row of the matrix @thresholds)
        tSortedIdx = new int[features.length][];
        for(int i=0;i<features.length;i++)
            tSortedIdx[i] = MergeSorter.sort(thresholds[i], false);
        
        //now create a sorted lists of every samples ranked list with respect to each feature
        //e.g. Feature f_i <==> all sample ranked list is now ranked with respect to f_i 
        for(int i=0;i<features.length;i++)
        {
            List<int[]> idx = new ArrayList<int[]>();
            for(int j=0;j<samples.size();j++)
                idx.add(reorder(samples.get(j), features[i]));
            sortedSamples.add(idx);
        }
        PRINTLN("[Done]");
    }

    public double eval(DataPoint p)
    {
        double score = 0.0;
        for(int j=0;j<wRankers.size();j++)
            score += rWeight.get(j) * wRankers.get(j).score(p);
        return score;
    }
    public Ranker createNew()
    {
        return new RankBoost();
    }
    public String toString()
    {
        String output = "";
        for(int i=0;i<wRankers.size();i++)
            output += wRankers.get(i).toString() + ":" + rWeight.get(i) + ((i==rWeight.size()-1)?"":" ");
        return output;
    }
    public String model()
    {
        String output = "## " + name() + "\n";
        output += "## Iteration = " + nIteration + "\n";
        output += "## No. of threshold candidates = " + nThreshold + "\n";
        output += toString();
        return output;
    }

    @Override
    public void loadFromString(String fullText, FeatureSet set, FEATURE_TYPE type)
    {
        try {
            String content = "";
            BufferedReader in = new BufferedReader(new FastStringReader(fullText));

            while((content = in.readLine()) != null)
            {
                content = content.trim();
                if(content.length() == 0)
                    continue;
                if(content.indexOf("##")==0)
                    continue;
                break;
            }
            in.close();
            
            rWeight = new ArrayList<>();
            wRankers = new ArrayList<>();
            
            int idx = content.lastIndexOf("#");
            if(idx != -1)//remove description at the end of the line (if any)
                content = content.substring(0, idx).trim();//remove the comment part at the end of the line

            String[] fs = content.split(" ");
            for(int i=0;i<fs.length;i++)
            {
                fs[i] = fs[i].trim();
                if(fs[i].compareTo("")==0)
                    continue;
                String[] strs = fs[i].split(":");

                int fid;
                if(type == FEATURE_TYPE.ORDINAL) {
                    fid = Integer.parseInt(strs[0]);
                } else {
                   if  (!set.hasFeature(strs[0])) {
                       throw RankLibError.create("Feature [" + strs[0] + "] is unknown.");
                   }

                   fid = set.featureOrdinal(strs[0]);
                }

                double threshold = Double.parseDouble(strs[1]);
                double weight = Double.parseDouble(strs[2]);
                rWeight.add(weight);
                wRankers.add(new RBWeakRanker(fid, threshold));
            }
                
            features = new int[rWeight.size()];
            for(int i=0;i<rWeight.size();i++)
                features[i] = wRankers.get(i).getFid();
        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in RankBoost::load(): ", ex);
        }
    }
    public void printParameters()
    {
        PRINTLN("No. of rounds: " + nIteration);
        PRINTLN("No. of threshold candidates: " + nThreshold);
    }
    public String name()
    {
        return "RankBoost";
    }
}
