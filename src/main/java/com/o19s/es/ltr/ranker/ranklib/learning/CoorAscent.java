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

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.ranklib.metric.MetricScorer;
import com.o19s.es.ltr.ranker.ranklib.utils.KeyValuePair;
import com.o19s.es.ltr.ranker.ranklib.utils.MergeSorter;
import org.elasticsearch.common.io.FastStringReader;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.List;

/**
 * @author vdang
 * 
 * This class implements the linear ranking model known as Coordinate Ascent. It was proposed in this paper:
 *  D. Metzler and W.B. Croft. Linear feature-based models for information retrieval. Information Retrieval, 10(3): 257-274, 2007.
 */
public class CoorAscent extends Ranker {

    //Parameters
    public static int nRestart = 5;
    public static int nMaxIteration = 25;
    public static double stepBase = 0.05;
    public static double stepScale = 2.0;
    public static double tolerance = 0.001;
    public static boolean regularized = false;
    public static double slack = 0.001;//regularized parameter
    
    //Local variables
    public double[] weight = null;
    
    protected int current_feature = -1;//used only during learning
    protected double weight_change = -1.0;//used only during learning
    
    public CoorAscent()
    {
        
    }
    public CoorAscent(List<RankList> samples, int[] features, MetricScorer scorer)
    {
        super(samples, features, scorer);
    }
    
    public void init()
    {
        PRINT("Initializing... ");
        weight = new double[features.length];
        Arrays.fill(weight, 1.0 / features.length);
        PRINTLN("[Done]");
    }

    public RankList rank(RankList rl)
    {
        double[] score = new double[rl.size()];
        if(current_feature == -1)
        {
            for(int i=0;i<rl.size();i++)
            {
                for(int j=0;j<features.length;j++)
                    score[i] += weight[j] * rl.get(i).getFeatureValue(features[j]);
                rl.get(i).setCached(score[i]);//use cache of a data point to store its score given the model at this state
            }
        }
        else//This branch is only active during the training process. Here we trade the "clean" codes for efficiency 
        {
            for(int i=0;i<rl.size();i++)
            {
                //cached score = a_1*x_1 + a_2*x_2 + ... + a_n*x_n
                //a_2 ==> a'_2
                //new score = cached score + (a'_2 - a_2)*x_2  ====> NO NEED TO RE-COMPUTE THE WHOLE THING
                score[i] = rl.get(i).getCached() + weight_change * rl.get(i).getFeatureValue(features[current_feature]);
                rl.get(i).setCached(score[i]);
            }
        }
        int[] idx = MergeSorter.sort(score, false);
        return new RankList(rl, idx);
    }
    public double eval(DataPoint p)
    {
        double score = 0.0;
        for(int i=0;i<features.length;i++)
            score += weight[i] * p.getFeatureValue(features[i]);
        return score;
    }
    public Ranker createNew()
    {
        return new CoorAscent();
    }
    public String toString()
    {
        String output = "";
        for(int i=0;i<weight.length;i++)
            output += features[i] + ":" + weight[i] + ((i==weight.length-1)?"":" ");
        return output;
    }
    public String model()
    {
        String output = "## " + name() + "\n";
        output += "## Restart = " + nRestart + "\n";
        output += "## MaxIteration = " + nMaxIteration + "\n";
        output += "## StepBase = " + stepBase + "\n";
        output += "## StepScale = " + stepScale + "\n";
        output += "## Tolerance = " + tolerance + "\n";
        output += "## Regularized = " + regularized + "\n";
        output += "## Slack = " + slack + "\n";
        output += toString();
        return output;
    }
    public void loadFromString(String fullText, FeatureSet set, FEATURE_TYPE type)
    {
        try {
            String content = "";
            BufferedReader in = new BufferedReader(new FastStringReader(fullText));

            KeyValuePair kvp = null;
            while((content = in.readLine()) != null)
            {
                content = content.trim();
                if(content.length() == 0)
                    continue;
                if(content.indexOf("##")==0)
                    continue;
                kvp = new KeyValuePair(content);
                break;
            }
            in.close();
            assert(kvp != null);
            
            List<String> keys = kvp.keys();
            List<String> values = kvp.values();
            weight = new double[keys.size()];
            features = new int[keys.size()];
            for(int i=0;i<keys.size();i++)
            {
                if(type == FEATURE_TYPE.ORDINAL) {
                    features[i] = Integer.parseInt(keys.get(i));
                } else {
                    if(!set.hasFeature(keys.get(i))) {
                        throw RankLibError.create("Feature [" + keys.get(i) + "] is unknown.");
                    }

                    features[i] = set.featureOrdinal(keys.get(i));
                }
                weight[i] = Double.parseDouble(values.get(i));
            }
        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in CoorAscent::load(): ", ex);
        }
    }
    public void printParameters()
    {
        PRINTLN("No. of random restarts: " + nRestart);
        PRINTLN("No. of iterations to search in each direction: " + nMaxIteration);
        PRINTLN("Tolerance: " + tolerance);
        if(regularized)
            PRINTLN("Reg. param: " + slack);
        else
            PRINTLN("Regularization: No");
    }
    public String name()
    {
        return "Coordinate Ascent";
    }
}