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
import org.elasticsearch.common.io.FastStringReader;

import java.io.BufferedReader;
import java.util.List;

public class LinearRegRank extends Ranker {

    public static double lambda = 1E-10;//L2-norm regularization parameter
    
    //Local variables
    protected double[] weight = null; 
    
    public LinearRegRank()
    {        
    }
    public LinearRegRank(List<RankList> samples, int[] features, MetricScorer scorer)
    {
        super(samples, features, scorer);
    }
    public void init()
    {
        PRINTLN("Initializing... [Done]");
    }

    public double eval(DataPoint p)
    {
        double score = weight[weight.length-1];
        for(int i=0;i<features.length;i++)
            score += weight[i] * p.getFeatureValue(features[i]);
        return score;
    }
    public Ranker createNew()
    {
        return new LinearRegRank();
    }
    public String toString()
    {
        String output = "0:" + weight[0] + " ";        
        for(int i=0;i<features.length;i++)
            output += features[i] + ":" + weight[i] + ((i==weight.length-1)?"":" ");
        return output;
    }
    public String model()
    {
        String output = "## " + name() + "\n";
        output += "## Lambda = " + lambda + "\n";
        output += toString();
        return output;
    }
    @Override
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
            features = new int[keys.size()-1];//weight = <weight for each feature, constant>
            int idx = 0;
            for(int i=0;i<keys.size();i++)
            {
                int fid;
                if(type == FEATURE_TYPE.ORDINAL) {
                    fid = Integer.parseInt(keys.get(i));
                } else {
                    String fname = keys.get(i);
                    if (!fname.equals("0")) {
                        if  (!set.hasFeature(fname)) {
                            throw RankLibError.create("Feature [" + fname + "] is unknown.");
                        }

                        fid = set.featureOrdinal(fname);
                    } else {
                        fid = 0;
                    }
                }

                if (fid > 0) {
                    features[idx] = fid;
                    weight[idx] = Double.parseDouble(values.get(i));
                    idx++;
                } else {
                    weight[weight.length - 1] = Double.parseDouble(values.get(i));
                }
            }
        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in LinearRegRank::load(): ", ex);
        }
    }
    public void printParameters()
    {
        PRINTLN("L2-norm regularization: lambda = " + lambda);
    }
    public String name()
    {
        return "Linear Regression";
    }
}
