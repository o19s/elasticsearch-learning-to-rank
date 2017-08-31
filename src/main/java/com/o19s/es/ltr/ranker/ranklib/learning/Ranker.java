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
 * Copyright (c) 2010-2015 University of Massachusetts.  All Rights Reserved.
 *
 * Use of the RankLib package is subject to the terms of the software license set 
 * forth in the LICENSE file included with this software, and also available at
 * http://people.cs.umass.edu/~vdang/ranklib_license.html
 *===============================================================================
 */

package com.o19s.es.ltr.ranker.ranklib.learning;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.ranklib.metric.MetricScorer;
import com.o19s.es.ltr.ranker.ranklib.utils.MergeSorter;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

//- Some Java 7 file utilities for creating directories


/**
 * @author vdang
 * 
 * This class implements the generic Ranker interface. Each ranking algorithm implemented has to extend this class. 
 */
public abstract class Ranker {
    public static boolean verbose = true;

    protected List<RankList> samples = new ArrayList<RankList>();//training samples
    protected int[] features = null;
    protected MetricScorer scorer = null;
    protected double scoreOnTrainingData = 0.0;
    protected double bestScoreOnValidationData = 0.0;
    
    protected List<RankList> validationSamples = null;

    private static final Logger logger = Loggers.getLogger(Ranker.class);
    
    protected Ranker()
    {

    }
    protected Ranker(List<RankList> samples, int[] features, MetricScorer scorer)
    {
        this.samples = samples;
        this.features = features;
        this.scorer = scorer;
    }

    public void setFeatures(int[] features)
    {
        this.features = features;    
    }

    public int[] getFeatures()
    {
        return features;
    }
    
    public RankList rank(RankList rl)
    {
        double[] scores = new double[rl.size()];
        for(int i=0;i<rl.size();i++)
            scores[i] = eval(rl.get(i));
        int[] idx = MergeSorter.sort(scores, false);
        return new RankList(rl, idx);
    }

    public List<RankList> rank(List<RankList> l)
    {
        List<RankList> ll = new ArrayList<RankList>();
        for(int i=0;i<l.size();i++)
            ll.add(rank(l.get(i)));
        return ll;
    }

    protected void PRINT(String msg)
    {
        if(verbose)
            logger.debug(msg);
    }

    protected void PRINTLN(String msg)
    {
        if(verbose)
            logger.debug(msg);
    }

    protected void PRINT(int[] len, String[] msgs)
    {
        if(verbose)
        {
            for(int i=0;i<msgs.length;i++)
            {
                String msg = msgs[i];
                if(msg.length() > len[i])
                    msg = msg.substring(0, len[i]);
                else
                    while(msg.length() < len[i])
                        msg += " ";
                logger.debug(msg);
            }
        }
    }
    protected void PRINTLN(int[] len, String[] msgs)
    {
        PRINT(len, msgs);
        PRINTLN("");
    }
    protected void PRINTTIME()
    {
        DateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.ROOT);
        Date date = new Date();
        logger.debug(dateFormat.format(date));
    }
    protected void PRINT_MEMORY_USAGE()
    {
        logger.debug("***** " + Runtime.getRuntime().freeMemory() + " / " + Runtime.getRuntime().maxMemory());
    }
    
    protected void copy(double[] source, double[] target)
    {
        for(int j=0;j<source.length;j++)
            target[j] = source[j];
    }
    
    /**
     * HAVE TO BE OVER-RIDDEN IN SUB-CLASSES
     */
    public abstract void init();
    public double eval(DataPoint p)
    {
        return -1.0;
    }

  public abstract Ranker createNew();
  public abstract String toString();
  public abstract String model();
  public abstract void loadFromString(String fullText, FeatureSet set, FEATURE_TYPE type);
  public abstract String name();
  public abstract void printParameters();
}
