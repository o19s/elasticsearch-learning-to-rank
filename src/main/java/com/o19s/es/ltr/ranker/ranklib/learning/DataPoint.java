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

import java.util.Arrays;

/**
 * @author vdang
 * 
 * This class implements objects to be ranked. In the context of Information retrieval, each instance is a
 * query-url pair represented by a n-dimentional feature vector.
 *
 * It should be general enough for other ranking applications as well (not limited to just IR I hope). 
 */
public abstract class DataPoint {
    
    public static int MAX_FEATURE = 51;
    public static int FEATURE_INCREASE = 10;
    protected static int featureCount = 0;
    
    protected static float UNKNOWN = Float.NaN;
    
    //attributes
    //[ground truth] the real label of the data point (e.g. its degree of relevance according to the relevance judgment)
    protected float label = 0.0f;
    protected String id = "";//id of this data point (e.g. query-id)
    protected String description = "";
    protected float[] fVals = null; //fVals[0] is un-used. Feature id MUST start from 1
    
    //helper attributes
    protected int knownFeatures; // number of known feature values
    
    //internal to learning procedures
    protected double cached = -1.0;//the latest evaluation score of the learned model on this data point
    
    protected static boolean isUnknown(float fVal)
    {
        return Float.isNaN(fVal);
    }
    protected static String getKey(String pair)
    {
        return pair.substring(0, pair.indexOf(":"));
    }
    protected static String getValue(String pair)
    {
        return pair.substring(pair.lastIndexOf(":")+1);
    }    
    
    /**
     * Parse the given line of text to construct a dense array of feature values and reset metadata.
     * @param text The text to parse
     * @return Dense array of feature values
     */
    protected float[] parse(String text)
    {
        float[] fVals = new float[MAX_FEATURE];
        Arrays.fill(fVals, UNKNOWN);
        int lastFeature = -1;
        try {
            int idx = text.indexOf("#");
            if(idx != -1)
            {
                description = text.substring(idx);
                text = text.substring(0, idx).trim();//remove the comment part at the end of the line
            }
            String[] fs = text.split("\\s+");
            label = Float.parseFloat(fs[0]);
            if(label < 0)
            {
                throw new IllegalArgumentException("Relevance label cannot be negative.");
            }
            id = getValue(fs[1]);
            String key = "";
            String val = "";
            for(int i=2;i<fs.length;i++)
            {
                knownFeatures++;
                key = getKey(fs[i]);
                val = getValue(fs[i]);
                int f = Integer.parseInt(key);
                if(f <= 0) throw RankLibError.create(
                        "Cannot use feature numbering less than or equal to zero. Start your features at 1.");
                if(f >= MAX_FEATURE)
                {
                    while(f >= MAX_FEATURE)
                        MAX_FEATURE += FEATURE_INCREASE;
                    float[] tmp = new float [MAX_FEATURE];
                    System.arraycopy(fVals, 0, tmp, 0, fVals.length);
                    Arrays.fill(tmp, fVals.length, MAX_FEATURE, UNKNOWN);
                    fVals = tmp;
                }
                fVals[f] = Float.parseFloat(val);
                
                if(f > featureCount)//#feature will be the max_id observed
                    featureCount = f;

                /*
                    note that lastFeature is the max_id observed for this current data point,
                    whereas featureCount is the max_id observed on the entire dataset
                 */
                if(f > lastFeature)
                    lastFeature = f;
            }
            //shrink fVals
            float[] tmp = new float[lastFeature+1];
            System.arraycopy(fVals, 0, tmp, 0, lastFeature+1);
            fVals = tmp;
        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in DataPoint::parse()", ex);
        }
        return fVals;
    }
    
    /**
    * Get the value of the feature with the given feature ID
    * @param fid The ID of the feature
    * @return The value of the specified feature
    */
    public abstract float getFeatureValue(int fid);
    
    /**
    * Set the value of the feature with the given feature ID
    * @param fid The ID of the feature to set
    * @param fval The value to set for the feature
    */
    public abstract void setFeatureValue(int fid, float fval);
    
    /**
    * Sets the value of all features with the provided dense array of feature values
    */
    public abstract void setFeatureVector(float[] dfVals);
    
    /**
    * Gets the value of all features as a dense array of feature values.
    */
    public abstract float[] getFeatureVector();
    
    /**
    * Default constructor. No-op.
    */
    protected DataPoint() {};
    
    /**
    * The input must have the form: 
    * @param text The text to parse
    */
    protected DataPoint(String text)
    {
        float[] fVals = parse(text);
        setFeatureVector(fVals);
    }
    
    public String getID()
    {
        return id;
    }
    public void setID(String id)
    {
        this.id = id;
    }
    public float getLabel()
    {
        return label;
    }
    public void setLabel(float label)
    {
        this.label = label;
    }
    public String getDescription()
    {
        return description;
    }
    public void setDescription(String description) {
        assert(description.contains("#"));
        this.description = description;
    }
    public void setCached(double c)
    {
        cached = c;
    }
    public double getCached()
    {
        return cached;

    }
    public void resetCached()
    {
        cached = -100000000.0f;;
    }
    
    public String toString()
    {
        float[] fVals = getFeatureVector();
        String output = ((int)label) + " " + "qid:" + id + " ";
        for(int i=1;i<fVals.length;i++)
            if(!isUnknown(fVals[i]))
                output += i + ":" + fVals[i] + ((i==fVals.length-1)?"":" ");
        output += " " + description;
        return output;
    }
    
    public static int getFeatureCount()
    {
        return featureCount;
    }    
}