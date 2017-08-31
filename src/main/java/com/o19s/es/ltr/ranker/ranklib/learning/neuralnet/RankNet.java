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

/**
 * @author vdang
 *
 *  This class implements RankNet.
 *  C.J.C. Burges, T. Shaked, E. Renshaw, A. Lazier, M. Deeds, N. Hamilton and G. Hullender. Learning to rank using gradient descent.
 *  In Proc. of ICML, pages 89-96, 2005.
 */
public class RankNet extends Ranker {

    //Parameters
    public static int nIteration = 100;
    public static int nHiddenLayer = 1;
    public static int nHiddenNodePerLayer = 10;
    public static double learningRate = 0.00005;
    
    //Variables
    protected List<Layer> layers = new ArrayList<Layer>();
    protected Layer inputLayer = null;
    protected Layer outputLayer = null;
    
    //to store the best model on validation data (if specified)
    protected List<List<Double>> bestModelOnValidation = new ArrayList<List<Double>>();
    
    protected int totalPairs = 0;
    protected int misorderedPairs = 0; 
    protected double error = 0.0;
    protected double lastError = Double.MAX_VALUE;
    protected int straightLoss = 0;
    
    public RankNet()
    {
        
    }
    public RankNet(List<RankList> samples, int [] features, MetricScorer scorer)
    {
        super(samples, features, scorer);
    }
    
    /**
     * Setting up the Neural Network
     */
    protected void setInputOutput(int nInput, int nOutput)
    {
        inputLayer = new Layer(nInput+1);//plus the "bias" (output threshold)
        outputLayer = new Layer(nOutput);
        layers.clear();
        layers.add(inputLayer);
        layers.add(outputLayer);
    }
    protected void setInputOutput(int nInput, int nOutput, int nType)
    {
        inputLayer = new Layer(nInput+1, nType);//plus the "bias" (output threshold)
        outputLayer = new Layer(nOutput, nType);
        layers.clear();
        layers.add(inputLayer);
        layers.add(outputLayer);
    }
    protected void addHiddenLayer(int size)
    {
        layers.add(layers.size()-1, new Layer(size));
    }
    protected void wire()
    {
        //wire the input layer to the first hidden layer
        for(int i=0;i<inputLayer.size()-1;i++)//don't touch the "bias" input (the last item in the list)
            for(int j=0;j<layers.get(1).size();j++)
                connect(0, i, 1, j);
        
        //wire one layer to the next, starting at layer 1 (the first hidden layer)
        for(int i=1;i<layers.size()-1;i++)
            for(int j=0;j<layers.get(i).size();j++)
                for(int k=0;k<layers.get(i+1).size();k++)
                    connect(i, j, i+1, k);
        
        //wire the "bias" neuron to all others (in all layers)
        for(int i=1;i<layers.size();i++)
            for(int j=0;j<layers.get(i).size();j++)
                connect(0, inputLayer.size()-1, i, j);
        
        //initialize weights
        /*Random random = new Random();
        for(int i=1;i<layers.size();i++)
        {
            for(int j=0;j<layers.get(i).size();j++)
            {
                Neuron n = layers.get(i).get(j);
                int s = n.getInLinks().size();
                //if weight is drawn from Uniform(-b, b) ==> the standard deviation of weights will be 1.0/sqrt(m)
                double b = Math.sqrt(3.0/s);
                for(int k=0;k<s;k++)
                    n.getInLinks().get(k).setWeight(b*random.nextDouble()*(random.nextInt(2)==0?1:-1));
            }
        }*/
    }
    protected void connect(int sourceLayer, int sourceNeuron, int targetLayer, int targetNeuron)
    {
        new Synapse(layers.get(sourceLayer).get(sourceNeuron), layers.get(targetLayer).get(targetNeuron));
    }
    
    /**
     *  Auxiliary functions for pair-wise preference network learning.
     */
    protected void addInput(DataPoint p)
    {
        for(int k=0;k<inputLayer.size()-1;k++)//not the "bias" node
            inputLayer.get(k).addOutput(p.getFeatureValue(features[k]));
        //  and now the bias node with a fix "1.0"
        inputLayer.get(inputLayer.size()-1).addOutput(1.0f);
    }
    protected void propagate(int i)
    {
        for(int k=1;k<layers.size();k++)//skip the input layer
            layers.get(k).computeOutput(i);
    }

    protected double crossEntropy(double o1, double o2, double targetValue)
    {
        double oij = o1 - o2;
        double ce = -targetValue * oij + SimpleMath.logBase2(1+Math.exp(oij));
        return ce;
    }
    protected void estimateLoss() 
    {
        misorderedPairs = 0;
        error = 0.0;
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
                        error += crossEntropy(o1, o2, 1.0f);
                        if(o1 < o2)
                            misorderedPairs++;
                    }
                }
            }
        }
        error = SimpleMath.round(error/totalPairs, 4);
        
        //if(error > lastError)
            //Neuron.learningRate *= 0.8;
        lastError = error;
    }
    
    /**
     * Main public functions
     */
    public void init()
    {
        PRINT("Initializing... ");
        
        //Set up the network
        setInputOutput(features.length, 1);
        for(int i=0;i<nHiddenLayer;i++)
            addHiddenLayer(nHiddenNodePerLayer);
        wire();
        
        totalPairs = 0;
        for(int i=0;i<samples.size();i++)
        {
            RankList rl = samples.get(i).getCorrectRanking();
            for(int j=0;j<rl.size()-1;j++)
                for(int k=j+1;k<rl.size();k++)
                    if(rl.get(j).getLabel() > rl.get(k).getLabel())//strictly ">"
                        totalPairs++;
        }
        
        if(validationSamples != null)
            for(int i=0;i<layers.size();i++)
                bestModelOnValidation.add(new ArrayList<Double>());
        
        Neuron.learningRate = learningRate;
        PRINTLN("[Done]");
    }

    public double eval(DataPoint p)
    {
        //feed input
        for(int k=0;k<inputLayer.size()-1;k++)//not the "bias" node
            inputLayer.get(k).setOutput(p.getFeatureValue(features[k]));
        //and now the bias node with a fix "1.0"
        inputLayer.get(inputLayer.size()-1).setOutput(1.0f);        
        //propagate
        for(int k=1;k<layers.size();k++)//skip the input layer
            layers.get(k).computeOutput();        
        return outputLayer.get(0).getOutput();
    }    
    public Ranker createNew()
    {
        return new RankNet();
    }
    public String toString()
    {
        String output = "";
        for(int i=0;i<layers.size()-1;i++)//loop through all layers
        {
            for(int j=0;j<layers.get(i).size();j++)//loop through all neurons on in the current layer
            {
                output += i + " " + j + " ";
                Neuron n = layers.get(i).get(j);
                for(int k=0;k<n.getOutLinks().size();k++)//loop through all out links (synapses) of the current neuron
                    output += n.getOutLinks().get(k).getWeight() + ((k==n.getOutLinks().size()-1)?"":" ");
                output += "\n";
            }
        }
        return output;
    }
    public String model()
    {
        String output = "## " + name() + "\n";
        output += "## Epochs = " + nIteration + "\n";
        output += "## No. of features = " + features.length + "\n";
        output += "## No. of hidden layers = " + (layers.size()-2) + "\n";
        for(int i=1;i<layers.size()-1;i++)
            output += "## Layer " + i + ": " + layers.get(i).size() + " neurons\n";
        
        //print used features
        for(int i=0;i<features.length;i++)
            output += features[i] + ((i==features.length-1)?"":" ");
        output += "\n";
        //print network information
        output += layers.size()-2 + "\n";//[# hidden layers]
        for(int i=1;i<layers.size()-1;i++)
            output += layers.get(i).size() + "\n";//[#neurons]
        //print learned weights
        output += toString();
        return output;
    }
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
                if(type == FEATURE_TYPE.ORDINAL) {
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
            throw RankLibError.create("Error in RankNet::load(): ", ex);
        }
    }
    public void printParameters()
    {
        PRINTLN("No. of epochs: " + nIteration);
        PRINTLN("No. of hidden layers: " + nHiddenLayer);
        PRINTLN("No. of hidden nodes per layer: " + nHiddenNodePerLayer);
        PRINTLN("Learning rate: " + learningRate);
    }
    public String name()
    {
        return "RankNet";
    }
    /**
     * FOR DEBUGGING PURPOSE ONLY
     */
    protected void printNetworkConfig()
    {
        for(int i=1;i<layers.size();i++)
        {
            PRINTLN("Layer-" + (i+1));
            for(int j=0;j<layers.get(i).size();j++)
            {
                Neuron n = layers.get(i).get(j);
                PRINT("Neuron-" + (j+1) + ": " + n.getInLinks().size() + " inputs\t");
                for(int k=0;k<n.getInLinks().size();k++)
                    PRINT(n.getInLinks().get(k).getWeight() + "\t");
                PRINTLN("");
            }
        }
    }
    protected void printWeightVector()
    {
        /*double[] w = new double[features.length];
        for(int j=0;j<inputLayer.size()-1;j++)
        {
            w[j] = inputLayer.get(j).getOutLinks().get(0).getWeight();
            System.out.print(w[j] + " ");
        }*/
        for(int j=0;j<outputLayer.get(0).getInLinks().size();j++)
            PRINT(outputLayer.get(0).getInLinks().get(j).getWeight() + " ");
        PRINTLN("");
    }
}
