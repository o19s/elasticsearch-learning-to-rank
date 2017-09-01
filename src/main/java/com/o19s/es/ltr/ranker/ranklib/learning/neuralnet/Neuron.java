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

import java.util.ArrayList;
import java.util.List;

/**
 * @author vdang
 * 
 * This class implements individual neurons in the network.
 */
public class Neuron {
    public static double momentum = 0.9;
    public static double learningRate = 0.001;//0.001;
    
    //protected TransferFunction tfunc = new HyperTangentFunction(); 
    protected TransferFunction tfunc = new LogiFunction();
    
    protected double output;//sigmoid(wsum) (range from 0.0 to 1.0): output for the current input
    protected List<Double> outputs = null;
    protected double delta_i = 0.0; 
    protected double[] deltas_j = null; 
    
    protected List<Synapse> inLinks = null;
    protected List<Synapse> outLinks = null;
    
    public Neuron()
    {
        output = 0.0;
        inLinks = new ArrayList<Synapse>();
        outLinks = new ArrayList<Synapse>();
        
        outputs = new ArrayList<Double>();
        delta_i = 0.0;
    }
    public double getOutput()
    {
        return output;
    }
    public double getOutput(int k)
    {
        return outputs.get(k);
    }
    public List<Synapse> getInLinks()
    {
        return inLinks;
    }
    public List<Synapse> getOutLinks()
    {
        return outLinks;
    }
    public void setOutput(double output)
    {
        this.output = output;
    }
    public void addOutput(double output)
    {
        outputs.add(output);
    }
    public void computeOutput()
    {
        Synapse s = null;
        double wsum = 0.0;
        for(int j=0;j<inLinks.size();j++)
        {
            s = inLinks.get(j);
            wsum += s.getSource().getOutput() * s.getWeight();
        }
        output = tfunc.compute(wsum);//using the specified transfer function to compute the output
    }
    
    public void computeOutput(int i)
    {
        Synapse s = null;
        double wsum = 0.0;
        for(int j=0;j<inLinks.size();j++)
        {
            s = inLinks.get(j);
            wsum += s.getSource().getOutput(i) * s.getWeight();
        }
        output = tfunc.compute(wsum);//using the specified transfer function to compute the output
        outputs.add(output);
    }
    
    public void clearOutputs()
    {
        outputs.clear();
    }
    
    /**
     * Compute delta for neurons in the output layer. ONLY for neurons in the output layer.
     */
    public void computeDelta(PropParameter param)
    {
        /*double pij = (double) (1.0 / (1.0 + Math.exp(-(prev_output-output))));
        prev_delta = (targetValue-pij) * tfunc.computeDerivative(prev_output);
        delta =      (targetValue-pij) * tfunc.computeDerivative(output);*/
        int[][] pairMap = param.pairMap;
        int current = param.current;
        
        delta_i = 0.0;
        deltas_j = new double[pairMap[current].length];
        for(int k=0;k<pairMap[current].length;k++)
        {
            int j = pairMap[current][k];
            float weight = 1;
            double pij = 0;
            if(param.pairWeight == null)//RankNet, no pair-weight needed
            {
                weight = 1;
                //this is in fact not "pij", but "targetValue-pij":  1 - 1/(1+e^{-o_ij})
                pij = (1.0 / (1.0 + Math.exp(outputs.get(current)-outputs.get(j))));
            }
            else//LambdaRank
            {
                weight = param.pairWeight[current][k];
                pij = ( param.targetValue[current][k]  -  1.0 / (1.0 + Math.exp(-(outputs.get(current)-outputs.get(j)))));
            }
            double lambda = weight * pij;
            delta_i += lambda;
            deltas_j[k] = lambda * tfunc.computeDerivative(outputs.get(j));
        }
        delta_i *= tfunc.computeDerivative(outputs.get(current));
        /*
          (delta_i * input_i) - (sum_{delta_j} * input_j) is the *negative* of the gradient,
          which is the amount of weight should be added to the current weight
          associated to the input_i
        */
    }
    /**
     * Update delta from neurons in the next layer (back-propagate)
     */
    public void updateDelta(PropParameter param)
    {
        /*double errorSum = 0.0;
        Synapse s = null;
        for(int i=0;i<outLinks.size();i++)
        {
            s = outLinks.get(i);
            errorSum += (s.getTarget().getPrevDelta()-s.getTarget().getDelta()) * s.getWeight();
        }
        prev_delta = errorSum * tfunc.computeDerivative(prev_output);
        delta =      errorSum * tfunc.computeDerivative(output);*/
        int[][] pairMap = param.pairMap;
        float[][] pairWeight = param.pairWeight;
        int current = param.current;
        delta_i = 0;
        deltas_j = new double[pairMap[current].length];
        for(int k=0;k<pairMap[current].length;k++)
        {
            int j = pairMap[current][k];
            float weight = (pairWeight!=null)?pairWeight[current][k]:1.0F;
            double errorSum = 0.0;
            for(int l=0;l<outLinks.size();l++)
            {
                Synapse s = outLinks.get(l);
                errorSum += s.getTarget().deltas_j[k] * s.weight;
                if(k==0)
                    delta_i += s.getTarget().delta_i * s.weight;
            }
            if(k==0)
                delta_i *= weight * tfunc.computeDerivative(outputs.get(current));
            deltas_j[k] = errorSum * weight * tfunc.computeDerivative(outputs.get(j));
        }
    }

    /**
     * Update weights of incoming links.
     */
    public void updateWeight(PropParameter param)
    {
        Synapse s = null;
        for(int k=0;k<inLinks.size();k++)
        {
            s = inLinks.get(k);
            double sum_j = 0.0;
            for(int l=0;l<deltas_j.length;l++)
                sum_j += deltas_j[l] * s.getSource().getOutput(param.pairMap[param.current][l]);
            double dw = learningRate * (delta_i * s.getSource().getOutput(param.current) - sum_j);
            s.setWeightAdjustment(dw);
            s.updateWeight();
        }
    }
}
