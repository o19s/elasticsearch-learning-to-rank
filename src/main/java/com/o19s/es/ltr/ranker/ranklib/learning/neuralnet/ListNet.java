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

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.ranklib.learning.DataPoint;
import com.o19s.es.ltr.ranker.ranklib.learning.FEATURE_TYPE;
import com.o19s.es.ltr.ranker.ranklib.learning.RankLibError;
import com.o19s.es.ltr.ranker.ranklib.learning.RankList;
import com.o19s.es.ltr.ranker.ranklib.learning.Ranker;
import com.o19s.es.ltr.ranker.ranklib.metric.MetricScorer;
import com.o19s.es.ltr.ranker.ranklib.utils.SimpleMath;
import org.elasticsearch.common.io.FastStringReader;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

public class ListNet extends RankNet {
    
    //Parameters
    public static int nIteration = 1500;
    public static double learningRate = 0.00001; 
    public static int nHiddenLayer = 0;//FIXED, it doesn't work with hidden layer
    
    public ListNet()
    {        
    }
    public ListNet(List<RankList> samples, int [] features, MetricScorer scorer)
    {
        super(samples, features, scorer);
    }
    
    protected float[] feedForward(RankList rl)
    {
        float[] labels = new float[rl.size()];
        for(int i=0;i<rl.size();i++)
        {
            addInput(rl.get(i));
            propagate(i);
            labels[i] = rl.get(i).getLabel();
        }
        return labels;
    }
    protected void backPropagate(float[] labels)
    {
        //back-propagate
        PropParameter p = new PropParameter(labels);
        outputLayer.computeDelta(p);//starting at the output layer
        
        //weight update
        outputLayer.updateWeight(p);
    }
    protected void estimateLoss() 
    {
        error = 0.0;
        double sumLabelExp = 0;
        double sumScoreExp = 0;
        for(int i=0;i<samples.size();i++)
        {
            RankList rl = samples.get(i);
            double[] scores = new double[rl.size()];
            double err = 0;
            for(int j=0;j<rl.size();j++)
            {
                scores[j] = eval(rl.get(j));
                sumLabelExp += Math.exp(rl.get(j).getLabel());
                sumScoreExp += Math.exp(scores[j]);                
            }
            for(int j=0;j<rl.size();j++)
            {
                double p1 = Math.exp(rl.get(j).getLabel())/sumLabelExp;
                double p2 = (Math.exp(scores[j])/sumScoreExp); 
                err +=  - p1 * SimpleMath.logBase2(p2) ;
            }
            error += err/rl.size();
        }
        //if(error > lastError && Neuron.learningRate > 0.0000001)
            //Neuron.learningRate *= 0.9;
        lastError = error;
    }
    
    public void init()
    {
        PRINT("Initializing... ");
        
        //Set up the network
        setInputOutput(features.length, 1, 1);
        wire();
        
        if(validationSamples != null)
            for(int i=0;i<layers.size();i++)
                bestModelOnValidation.add(new ArrayList<Double>());
        
        Neuron.learningRate = learningRate;
        PRINTLN("[Done]");
    }

    public double eval(DataPoint p)
    {
        return super.eval(p);
    }
    public Ranker createNew()
    {
        return new ListNet();
    }
    public String toString()
    {
        return super.toString();
    }
    public String model()
    {
        String output = "## " + name() + "\n";
        output += "## Epochs = " + nIteration + "\n";
        output += "## No. of features = " + features.length + "\n";
        
        //print used features
        for(int i=0;i<features.length;i++)
            output += features[i] + ((i==features.length-1)?"":" ");
        output += "\n";
        //print network information
        output += "0\n";//[# hidden layers, *ALWAYS* 0 since we're using linear net]
        //print learned weights
        output += toString();
        return output;
    }
  @Override
    public void loadFromString(String fullText, FeatureSet set, FEATURE_TYPE type)
    {
        try {
            String content = "";
            BufferedReader in = new BufferedReader(new FastStringReader(fullText));

            List<String> l = new ArrayList<String>();
            while((content = in.readLine()) != null)
            {
                content = content.trim();
                if(content.length() == 0)
                    continue;
                if(content.indexOf("##")==0)
                    continue;
                l.add(content);
            }
            in.close();
            //load the network
            //the first line contains features information
            String[] tmp = l.get(0).split(" ");
            features = new int[tmp.length];
            for(int i=0;i<tmp.length;i++) {
                if (type == FEATURE_TYPE.ORDINAL) {
                    features[i] = Integer.parseInt(tmp[i]);
                } else {
                    if(!set.hasFeature(tmp[i])) {
                        throw RankLibError.create("Feature [" + tmp[i] + "] is unknown.");
                    }

                    features[i] = set.featureOrdinal(tmp[i]);
                }
            }
            //the 2nd line is a scalar indicating the number of hidden layers
            int nHiddenLayer = Integer.parseInt(l.get(1));
            int[] nn = new int[nHiddenLayer];
            //the next @nHiddenLayer lines contain the number of neurons in each layer
            int i=2;
            for(;i<2+nHiddenLayer;i++)
                nn[i-2] = Integer.parseInt(l.get(i));
            //create the network
            setInputOutput(features.length, 1);
            for(int j=0;j<nHiddenLayer;j++)
                addHiddenLayer(nn[j]);
            wire();
            //fill in weights
            for(;i<l.size();i++)//loop through all layers
            {
                String[] s = l.get(i).split(" ");
                int iLayer = Integer.parseInt(s[0]);//which layer?
                int iNeuron = Integer.parseInt(s[1]);//which neuron?
                Neuron n = layers.get(iLayer).get(iNeuron);
                for(int k=0;k<n.getOutLinks().size();k++)//loop through all out links (synapses) of the current neuron
                    n.getOutLinks().get(k).setWeight(Double.parseDouble(s[k+2]));
            }
        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in ListNet::load(): ", ex);
        }
    }
    public void printParameters()
    {
        PRINTLN("No. of epochs: " + nIteration);
        PRINTLN("Learning rate: " + learningRate);
    }
    public String name()
    {
        return "ListNet";
    }
}
