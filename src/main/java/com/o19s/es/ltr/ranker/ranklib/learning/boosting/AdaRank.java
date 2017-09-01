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
import com.o19s.es.ltr.ranker.ranklib.utils.KeyValuePair;
import org.elasticsearch.common.io.FastStringReader;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author vdang
 * 
 * This class implements the AdaRank algorithm. Here's the paper:
 *  J. Xu and H. Li. AdaRank: a boosting algorithm for information retrieval. In Proc. of SIGIR, pages 391-398, 2007.
 */
public class AdaRank extends Ranker {
    
    //Paramters
    public static int nIteration = 500;
    public static double tolerance = 0.002;
    public static boolean trainWithEnqueue = true;
    public static int maxSelCount = 5;//the max. number of times a feature can be selected consecutively before being removed
    
    protected HashMap<Integer, Integer> usedFeatures = new HashMap<Integer, Integer>();
    protected double[] sweight = null;//sample weight
    protected List<WeakRanker> rankers = null;//alpha
    protected List<Double> rweight = null;//weak rankers' weight
    //to store the best model on validation data (if specified)
    protected List<WeakRanker> bestModelRankers = null;
    protected List<Double> bestModelWeights = null;
    
    //For the implementation of tricks
    List<Integer> featureQueue = null;
    protected double[] backupSampleWeight = null;
    protected double lastTrainedScore = -1.0;
    
    public AdaRank()
    {
        
    }
    public AdaRank(List<RankList> samples, int[] features, MetricScorer scorer)
    {
        super(samples, features, scorer);
    }

    public void init()
    {
        PRINT("Initializing... ");
        //initialization
        usedFeatures.clear();
        //assign equal weight to all samples
        sweight = new double[samples.size()];
        for(int i=0;i<sweight.length;i++)
            sweight[i] = 1.0f/samples.size();
        backupSampleWeight = new double[sweight.length];
        copy(sweight, backupSampleWeight);
        lastTrainedScore = -1.0;
        
        rankers = new ArrayList<WeakRanker>();
        rweight = new ArrayList<Double>();
        
        featureQueue = new ArrayList<Integer>();
        
        bestScoreOnValidationData = 0.0;
        bestModelRankers = new ArrayList<WeakRanker>();
        bestModelWeights = new ArrayList<Double>();
        
        PRINTLN("[Done]");
    }

    public double eval(DataPoint p)
    {
        double score = 0.0;
        for(int j=0;j<rankers.size();j++)
            score += rweight.get(j) * p.getFeatureValue(rankers.get(j).getFID());
        return score;
    }
    public Ranker createNew()
    {
        return new AdaRank();
    }
    public String toString()
    {
        String output = "";
        for(int i=0;i<rankers.size();i++)
            output += rankers.get(i).getFID() + ":" + rweight.get(i) + ((i==rankers.size()-1)?"":" ");
        return output;
    }
    public String model()
    {
        String output = "## " + name() + "\n";
        output += "## Iteration = " + nIteration + "\n";
        output += "## Train with enqueue: " + ((trainWithEnqueue)?"Yes":"No") + "\n";
        output += "## Tolerance = " + tolerance + "\n";
        output += "## Max consecutive selection count = " + maxSelCount + "\n";
        output += toString();
        return output;
    }

    @Override
    public void loadFromString(String fullText, FeatureSet set, FEATURE_TYPE type)
    {
        try (BufferedReader in = new BufferedReader(new FastStringReader(fullText))) {
            String content = "";

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

            assert(kvp != null);
            
            List<String> keys = kvp.keys();
            List<String> values = kvp.values();
            rweight = new ArrayList<>();
            rankers = new ArrayList<>();
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
                rankers.add(new WeakRanker(features[i]));
                rweight.add(Double.parseDouble(values.get(i)));
            }
        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in AdaRank::load(): ", ex);
        }
    }
    public void printParameters()
    {
        PRINTLN("No. of rounds: " + nIteration);
        PRINTLN("Train with 'enequeue': " + ((trainWithEnqueue)?"Yes":"No"));
        PRINTLN("Tolerance: " + tolerance);
        PRINTLN("Max Sel. Count: " + maxSelCount);
    }
    public String name()
    {
        return "AdaRank";
    }
}
