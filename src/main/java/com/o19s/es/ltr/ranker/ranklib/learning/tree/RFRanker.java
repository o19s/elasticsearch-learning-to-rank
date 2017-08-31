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
import com.o19s.es.ltr.ranker.ranklib.learning.RANKER_TYPE;
import com.o19s.es.ltr.ranker.ranklib.learning.RankLibError;
import com.o19s.es.ltr.ranker.ranklib.learning.RankList;
import com.o19s.es.ltr.ranker.ranklib.learning.Ranker;
import com.o19s.es.ltr.ranker.ranklib.metric.MetricScorer;
import com.o19s.es.ltr.ranker.ranklib.parsing.ModelLineProducer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class RFRanker extends Ranker {
    //Parameters
    //[a] general bagging parameters
    public static int nBag = 300;
    public static float subSamplingRate = 1.0f;//sampling of samples (*WITH* replacement)
    public static float featureSamplingRate = 0.3f;//sampling of features (*WITHOUT* replacement)
    //[b] what to do in each bag
    public static RANKER_TYPE rType = RANKER_TYPE.MART;//which algorithm to bag
    //how many trees in each bag. If nTree > 1 ==> each bag will contain an ensemble of gradient boosted trees.
    public static int nTrees = 1;
    public static int nTreeLeaves = 100;
    public static float learningRate = 0.1F;//or shrinkage. *ONLY* matters if nTrees > 1.
    public static int nThreshold = 256;
    public static int minLeafSupport = 1;
    
    //Variables
    protected Ensemble[] ensembles = null;//bag of ensembles, each can be a single tree or an ensemble of gradient boosted trees
    
    public RFRanker()
    {        
    }
    public RFRanker(List<RankList> samples, int[] features, MetricScorer scorer)
    {
        super(samples, features, scorer);
    }

    public void init()
    {
        PRINT("Initializing... ");
        ensembles = new Ensemble[nBag];
        //initialize parameters for the tree(s) built in each bag
        LambdaMART.nTrees = nTrees;
        LambdaMART.nTreeLeaves = nTreeLeaves;
        LambdaMART.learningRate = learningRate;
        LambdaMART.nThreshold = nThreshold;
        LambdaMART.minLeafSupport = minLeafSupport;
        LambdaMART.nRoundToStopEarly = -1;//no early-stopping since we're doing bagging
        //turn on feature sampling
        FeatureHistogram.samplingRate = featureSamplingRate;
        PRINTLN("[Done]");
    }

    public double eval(DataPoint dp)
    {
        double s = 0;
        for(int i=0;i<ensembles.length;i++)
            s += ensembles[i].eval(dp);
        return s/ensembles.length;
    }
    public Ranker createNew()
    {
        return new RFRanker();
    }
    public String toString()
    {
        String str = "";
        for(int i=0;i<nBag;i++)
            str += ensembles[i].toString() + "\n";
        return str;
    }
    public String model()
    {
        String output = "## " + name() + "\n";
        output += "## No. of bags = " + nBag + "\n";
        output += "## Sub-sampling = " + subSamplingRate + "\n";
        output += "## Feature-sampling = " + featureSamplingRate + "\n";
        output += "## No. of trees = " + nTrees + "\n";
        output += "## No. of leaves = " + nTreeLeaves + "\n";
        output += "## No. of threshold candidates = " + nThreshold + "\n";
        output += "## Learning rate = " + learningRate + "\n";
        output += "\n";
        output += toString();
        return output;
    }

    @Override
    public void loadFromString(String fullText, FeatureSet set, FEATURE_TYPE type)
    {
        try {
            String content = "";
            List<Ensemble> ens = new ArrayList<Ensemble>();

            ModelLineProducer lineByLine = new ModelLineProducer();

            lineByLine.parse(fullText, (StringBuilder model, boolean maybeEndEns) -> {
                if (maybeEndEns) {
                    String modelAsStr = model.toString();
                    if (modelAsStr.endsWith("</ensemble>")) {
                        ens.add(new Ensemble(modelAsStr, set, type));
                        model.setLength(0);
                    }
                }
            });


            HashSet<Integer> uniqueFeatures = new HashSet<Integer>();
            ensembles = new Ensemble[ens.size()];
            for(int i=0;i<ens.size();i++)
            {
                ensembles[i] = ens.get(i);
                //obtain used features
                int[] fids = ens.get(i).getFeatures();
                for(int f=0;f<fids.length;f++)
                    if(!uniqueFeatures.contains(fids[f]))
                        uniqueFeatures.add(fids[f]);
            }
            int fi = 0;
            features = new int[uniqueFeatures.size()];
            for(Integer f : uniqueFeatures)
                features[fi++] = f.intValue();

            //System.out.println("Other Loading Done");

        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in RFRanker::load(): ", ex);
        }
    }
    public void printParameters()
    {
        PRINTLN("No. of bags: " + nBag);
        PRINTLN("Sub-sampling: " + subSamplingRate);
        PRINTLN("Feature-sampling: " + featureSamplingRate);
        PRINTLN("No. of trees: " + nTrees);
        PRINTLN("No. of leaves: " + nTreeLeaves);
        PRINTLN("No. of threshold candidates: " + nThreshold);
        PRINTLN("Learning rate: " + learningRate);
    }
    public String name()
    {
        return "Random Forests";
    }
    
    public Ensemble[] getEnsembles()
    {
        return ensembles;
    }
}
