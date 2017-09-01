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

package com.o19s.es.ltr.ranker.ranklib.learning.tree;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.ranklib.learning.DataPoint;
import com.o19s.es.ltr.ranker.ranklib.learning.FEATURE_TYPE;
import com.o19s.es.ltr.ranker.ranklib.learning.RankLibError;
import com.o19s.es.ltr.ranker.ranklib.learning.RankList;
import com.o19s.es.ltr.ranker.ranklib.learning.Ranker;
import com.o19s.es.ltr.ranker.ranklib.metric.MetricScorer;
import com.o19s.es.ltr.ranker.ranklib.parsing.ModelLineProducer;
import com.o19s.es.ltr.ranker.ranklib.utils.MergeSorter;
import com.o19s.es.ltr.ranker.ranklib.utils.MyThreadPool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author vdang
 *
 *  This class implements LambdaMART.
 *  Q. Wu, C.J.C. Burges, K. Svore and J. Gao. Adapting Boosting for Information Retrieval Measures. 
 *  Journal of Information Retrieval, 2007.
 */
public class LambdaMART extends Ranker {
    //Parameters
    public static int nTrees = 1000;//the number of trees
    public static float learningRate = 0.1F;//or shrinkage
    public static int nThreshold = 256;
    //If no performance gain on the *VALIDATION* data is observed in #rounds, stop the training process right away.
    public static int nRoundToStopEarly = 100;
    public static int nTreeLeaves = 10;
    public static int minLeafSupport = 1;
    
    //for debugging
    public static int gcCycle = 100;
    
    //Local variables
    protected float[][] thresholds = null;
    protected Ensemble ensemble = null;
    protected double[] modelScores = null;//on training data
    
    protected double[][] modelScoresOnValidation = null;
    protected int bestModelOnValidation = Integer.MAX_VALUE-2;
    
    //Training instances prepared for MART
    protected DataPoint[] martSamples = null;//Need initializing only once
    protected int[][] sortedIdx = null;//sorted list of samples in @martSamples by each feature -- Need initializing only once 
    protected FeatureHistogram hist = null;
    protected double[] pseudoResponses = null;//different for each iteration
    protected double[] weights = null;//different for each iteration
    protected double[] impacts = null; // accumulated impact of each feature
    
    public LambdaMART()
    {        
    }

    public LambdaMART(List<RankList> samples, int[] features, MetricScorer scorer)
    {
        super(samples, features, scorer);
    }
    
    public void init()
    {
        PRINT("Initializing... ");        
        //initialize samples for MART
        int dpCount = 0;
        for(int i=0;i<samples.size();i++)
        {
            RankList rl = samples.get(i);
            dpCount += rl.size();
        }
        int current = 0;
        martSamples = new DataPoint[dpCount];
        modelScores = new double[dpCount];
        pseudoResponses = new double[dpCount];
        impacts = new double[features.length];
        weights = new double[dpCount];
        for(int i=0;i<samples.size();i++)
        {
            RankList rl = samples.get(i);
            for(int j=0;j<rl.size();j++)
            {
                martSamples[current+j] = rl.get(j);
                modelScores[current+j] = 0.0F;
                pseudoResponses[current+j] = 0.0F;
                weights[current+j] = 0;
            }
            current += rl.size();
        }            
        
        //sort (MART) samples by each feature so that we can quickly retrieve a sorted list of samples by any feature later on.
        sortedIdx = new int[features.length][];
        MyThreadPool p = MyThreadPool.getInstance();
        if(p.size() == 1)//single-thread
            sortSamplesByFeature(0, features.length-1);
        else//multi-thread
        {
            int[] partition = p.partition(features.length);
            for(int i=0;i<partition.length-1;i++)
                p.execute(new SortWorker(this, partition[i], partition[i+1]-1));
            p.await();
        }

        /*
            Create a table of candidate thresholds (for each feature). Later on, we will select the best tree
            split from these candidates
         */
        thresholds = new float[features.length][];
        for(int f=0;f<features.length;f++)
        {
            //For this feature, keep track of the list of unique values and the max/min 
            List<Float> values = new ArrayList<Float>();
            float fmax = Float.NEGATIVE_INFINITY;
            float fmin = Float.MAX_VALUE;
            for(int i=0;i<martSamples.length;i++)
            {
                int k = sortedIdx[f][i];//get samples sorted with respect to this feature
                float fv = martSamples[k].getFeatureValue(features[f]);
                values.add(fv);
                if(fmax < fv)
                    fmax = fv;
                if(fmin > fv)
                    fmin = fv;
                //skip all samples with the same feature value
                int j=i+1;
                while(j < martSamples.length)
                {
                    if(martSamples[sortedIdx[f][j]].getFeatureValue(features[f]) > fv)
                        break;
                    j++;
                }
                i = j-1;//[i, j] gives the range of samples with the same feature value
            }
            
            if(values.size() <= nThreshold || nThreshold == -1)
            {
                thresholds[f] = new float[values.size()+1];
                for(int i=0;i<values.size();i++)
                    thresholds[f][i] = values.get(i);
                thresholds[f][values.size()] = Float.MAX_VALUE;
            }
            else
            {
                float step = (Math.abs(fmax - fmin))/nThreshold;
                thresholds[f] = new float[nThreshold+1];
                thresholds[f][0] = fmin;
                for(int j=1;j<nThreshold;j++)
                    thresholds[f][j] = thresholds[f][j-1] + step;
                thresholds[f][nThreshold] = Float.MAX_VALUE;
            }
        }
        
        if(validationSamples != null)
        {
            modelScoresOnValidation = new double[validationSamples.size()][];
            for(int i=0;i<validationSamples.size();i++)
            {
                modelScoresOnValidation[i] = new double[validationSamples.get(i).size()];
                Arrays.fill(modelScoresOnValidation[i], 0);
            }
        }
        
        //compute the feature histogram (this is used to speed up the procedure of finding the best tree split later on)
        hist = new FeatureHistogram();
        hist.construct(martSamples, pseudoResponses, sortedIdx, features, thresholds, impacts);
        //we no longer need the sorted indexes of samples
        sortedIdx = null;
        
        //System.gc();
        PRINTLN("[Done]");
    }



    public double eval(DataPoint dp)
    {
        return ensemble.eval(dp);
    }    

    public Ranker createNew()
    {
        return new LambdaMART();
    }

    public String toString()
    {
        return ensemble.toString();
    }

    public String model()
    {
        String output = "## " + name() + "\n";
        output += "## No. of trees = " + nTrees + "\n";
        output += "## No. of leaves = " + nTreeLeaves + "\n";
        output += "## No. of threshold candidates = " + nThreshold + "\n";
        output += "## Learning rate = " + learningRate + "\n";
        output += "## Stop early = " + nRoundToStopEarly + "\n";
        output += "\n";
        output += toString();
        return output;
    }

    @Override
    public void loadFromString(String fullText, FeatureSet set, FEATURE_TYPE type)
    {
        ModelLineProducer lineByLine = new ModelLineProducer();

        try {

            lineByLine.parse(fullText, (StringBuilder model, boolean endEns) -> {return;});
            //load the ensemble
            ensemble = new Ensemble(lineByLine.getModel().toString(), set, type);
            features = ensemble.getFeatures();
        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in LambdaMART::load(): ", ex);
        }
    }

    public void printParameters()
    {
        PRINTLN("No. of trees: " + nTrees);
        PRINTLN("No. of leaves: " + nTreeLeaves);
        PRINTLN("No. of threshold candidates: " + nThreshold);
        PRINTLN("Min leaf support: " + minLeafSupport);
        PRINTLN("Learning rate: " + learningRate);
        PRINTLN("Stop early: " + nRoundToStopEarly + " rounds without performance gain on validation data");        
    }    

    public String name()
    {
        return "LambdaMART";
    }

    public Ensemble getEnsemble()
    {
        return ensemble;
    }
    
    protected void computePseudoResponses()
    {
        Arrays.fill(pseudoResponses, 0F);
        Arrays.fill(weights, 0);
        MyThreadPool p = MyThreadPool.getInstance();
        if(p.size() == 1)//single-thread
            computePseudoResponses(0, samples.size()-1, 0);
        else //multi-threading
        {
            List<LambdaComputationWorker> workers = new ArrayList<LambdaComputationWorker>();
            //divide the entire dataset into chunks of equal size for each worker thread
            int[] partition = p.partition(samples.size());
            int current = 0;
            for(int i=0;i<partition.length-1;i++)
            {
                //execute the worker
                LambdaComputationWorker wk = new LambdaComputationWorker(this, partition[i], partition[i+1]-1, current); 
                workers.add(wk);//keep it so we can get back results from it later on
                p.execute(wk);
                
                if(i < partition.length-2)
                    for(int j=partition[i]; j<=partition[i+1]-1;j++)
                        current += samples.get(j).size();
            }
            
            //wait for all workers to complete before we move on to the next stage
            p.await();
        }
    }

    // compute psuedo responses based on the current model's error (another name for psuedo response -- 'force' or
    // 'gradient') "psuedo responses" is the error currently in the model (that we'll attempt to model with features)
    // How do we get that error?
    //  Let's say we have two training sampples (aka docs) for a query. Docs k and j, where k is more relevant than j
    //  (ie label of k is 4, label for j is 0)
    // Then we want two values to help compute a psuedo response to help build a model for the remaining error
    //  1. rho -- a weight for how wrong the previous model is. Higher rho is, the more the prev model is wrong
    //              at dealing with docs k and j by just predicting scores that don't make sense
    //  2. deltaNDCG -- what swapping k and j means for the NDCG for this query.
    //                    even though the variable is called 'NDCG' it really uses whatever relevance metric you
    //                    specify (MAP, precision, ERR,... whatever)
    //
    // We update psuedoResponse[k] += rho * deltaNDCG (higher gradient/force when
    //        (a) -- rho high: previous models are more wrong
    //        (b) -- deltaNDCG high: these two docs being swapped
    //               would be really bad for this particular query
    //     aka psuedoResponse[k] += current error * importance

    // We also update down j (which remember should be left relevant than k) by subtracting out the same val:
    //         psuedoResponse[j] -= current error * importance
    protected void computePseudoResponses(int start, int end, int current)
    {
        int cutoff = scorer.getK();
        //compute the lambda for each document (a.k.a "pseudo response")
        for(int i=start;i<=end;i++)
        {
            RankList orig = samples.get(i);
            // sort based on current model's relevance scores
            int[] idx = MergeSorter.sort(modelScores, current, current+orig.size()-1, false);
            RankList rl = new RankList(orig, idx, current);

            // a table of possible rearrangements of rl
            double[][] changes = scorer.swapChange(rl);
            //NOTE: j, k are indices in the sorted (by modelScore) list, not the original
            // ==> need to map back with idx[j] and idx[k] 
            for(int j=0;j<rl.size();j++)
            {
                DataPoint pointJ = rl.get(j);
                int mj = idx[j]; // index of j in original list
                for(int k=0;k<rl.size();k++)
                {
                    // swapping these pair won't result in any change in target measures since they're below the cut-off
                    if(j > cutoff && k > cutoff)
                        break;
                    DataPoint pointK = rl.get(k);
                    int mk = idx[k];
                    if(pointJ.getLabel() > pointK.getLabel())
                    {
                        // j is better than k according to training data... (ie j has a 4, k a 0)
                        double deltaNDCG = Math.abs(changes[j][k]);
                        if(deltaNDCG > 0)
                        {
                            // rho weighs the delta ndcg by the current model score
                            // in this way, this is acting as a gradient
                            // rho mj's score
                            // if the model scores are close (say 100 for j, k for 99)
                            //   rho is smaller
                            // if model scores are far
                            double rho = 1.0 / (1 + Math.exp(modelScores[mj] - modelScores[mk]));
                            double lambda = rho * deltaNDCG;

                            // response of DataPoint j in original list
                            // which is better than k in original list
                            pseudoResponses[mj] += lambda;
                            pseudoResponses[mk] -= lambda;
                            double delta = rho * (1.0 - rho) * deltaNDCG;
                            weights[mj] += delta;
                            weights[mk] += delta;
                        }
                    }
                }
            }
            current += orig.size();
        }
    }

    protected void updateTreeOutput(RegressionTree rt)
    {
        List<Split> leaves = rt.leaves();
        for(int i=0;i<leaves.size();i++)
        {
            float s1 = 0F;
            float s2 = 0F;
            Split s = leaves.get(i);
            int[] idx = s.getSamples();
            for(int j=0;j<idx.length;j++)
            {
                int k = idx[j];
                s1 += pseudoResponses[k];
                s2 += weights[k];
            }
            if(s2 == 0)
                s.setOutput(0);
            else
                s.setOutput(s1/s2);
        }
    }

    protected int[] sortSamplesByFeature(DataPoint[] samples, int fid)
    {
        double[] score = new double[samples.length];
        for(int i=0;i<samples.length;i++)
            score[i] = samples[i].getFeatureValue(fid);
        int[] idx = MergeSorter.sort(score, true); 
        return idx;
    }

    /**
     * This function is equivalent to the inherited function rank(...), but it uses the cached model's outputs instead
     * of computing them from scratch.
     */
    protected RankList rank(int rankListIndex, int current)
    {
        RankList orig = samples.get(rankListIndex);    
        double[] scores = new double[orig.size()];
        for(int i=0;i<scores.length;i++)
            scores[i] = modelScores[current+i];
        int[] idx = MergeSorter.sort(scores, false);
        return new RankList(orig, idx);
    }

    protected float computeModelScoreOnTraining() 
    {
        /*float s = 0;
        int current = 0;    
        MyThreadPool p = MyThreadPool.getInstance();
        if(p.size() == 1)//single-thread
            s = computeModelScoreOnTraining(0, samples.size()-1, current);
        else
        {
            List<Worker> workers = new ArrayList<Worker>();
            //divide the entire dataset into chunks of equal size for each worker thread
            int[] partition = p.partition(samples.size());
            for(int i=0;i<partition.length-1;i++)
            {
                //execute the worker
                Worker wk = new Worker(this, partition[i], partition[i+1]-1, current);
                workers.add(wk);//keep it so we can get back results from it later on
                p.execute(wk);
                
                if(i < partition.length-2)
                    for(int j=partition[i]; j<=partition[i+1]-1;j++)
                        current += samples.get(j).size();
            }        
            //wait for all workers to complete before we move on to the next stage
            p.await();
            for(int i=0;i<workers.size();i++)
                s += workers.get(i).score;
        }*/
        float s = computeModelScoreOnTraining(0, samples.size()-1, 0);
        s = s / samples.size();
        return s;
    }

    protected float computeModelScoreOnTraining(int start, int end, int current) 
    {
        float s = 0;
        int c = current;

        for(int i=start;i<=end;i++)
        {
            s += scorer.score(rank(i, c));
            c += samples.get(i).size();
        }
        return s;
    }

    protected float computeModelScoreOnValidation() 
    {
        /*float score = 0;
        MyThreadPool p = MyThreadPool.getInstance();
        if(p.size() == 1)//single-thread
            score = computeModelScoreOnValidation(0, validationSamples.size()-1);
        else
        {
            List<Worker> workers = new ArrayList<Worker>();
            //divide the entire dataset into chunks of equal size for each worker thread
            int[] partition = p.partition(validationSamples.size());
            for(int i=0;i<partition.length-1;i++)
            {
                //execute the worker
                Worker wk = new Worker(this, partition[i], partition[i+1]-1);
                workers.add(wk);//keep it so we can get back results from it later on
                p.execute(wk);
            }        
            //wait for all workers to complete before we move on to the next stage
            p.await();
            for(int i=0;i<workers.size();i++)
                score += workers.get(i).score;
        }*/
        float score = computeModelScoreOnValidation(0, validationSamples.size()-1);
        return score/validationSamples.size();
    }

    protected float computeModelScoreOnValidation(int start, int end) 
    {
        float score = 0;
        for(int i=start;i<=end;i++)
        {
            int[] idx = MergeSorter.sort(modelScoresOnValidation[i], false);
            score += scorer.score(new RankList(validationSamples.get(i), idx));
        }
        return score;
    }
    
    protected void sortSamplesByFeature(int fStart, int fEnd)
    {
        for(int i=fStart;i<=fEnd; i++)
            sortedIdx[i] = sortSamplesByFeature(martSamples, features[i]);
    }

    //For multi-threading processing
    class SortWorker implements Runnable {
        LambdaMART ranker = null;
        int start = -1;
        int end = -1;

        SortWorker(LambdaMART ranker, int start, int end)
        {
            this.ranker = ranker;
            this.start = start;
            this.end = end;
        }        

        public void run()
        {
            ranker.sortSamplesByFeature(start, end);
        }
    }

    class LambdaComputationWorker implements Runnable {
        LambdaMART ranker = null;
        int rlStart = -1;
        int rlEnd = -1;
        int martStart = -1;

        LambdaComputationWorker(LambdaMART ranker, int rlStart, int rlEnd, int martStart)
        {
            this.ranker = ranker;
            this.rlStart = rlStart;
            this.rlEnd = rlEnd;
            this.martStart = martStart;
        }        

        public void run()
        {
            ranker.computePseudoResponses(rlStart, rlEnd, martStart);
        }
    }

    class Worker implements Runnable {
        LambdaMART ranker = null;
        int rlStart = -1;
        int rlEnd = -1;
        int martStart = -1;
        int type = -1;
        
        //compute score on validation
        float score = 0;
        
        Worker(LambdaMART ranker, int rlStart, int rlEnd)
        {
            type = 3;
            this.ranker = ranker;
            this.rlStart = rlStart;
            this.rlEnd = rlEnd;
        }

        Worker(LambdaMART ranker, int rlStart, int rlEnd, int martStart)
        {
            type = 4;
            this.ranker = ranker;
            this.rlStart = rlStart;
            this.rlEnd = rlEnd;
            this.martStart = martStart;
        }

        public void run()
        {
            if(type == 4)
                score = ranker.computeModelScoreOnTraining(rlStart, rlEnd, martStart);
            else if(type == 3)
                score = ranker.computeModelScoreOnValidation(rlStart, rlEnd);
        }
    }
}
