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

package com.o19s.es.ltr.ranker.ranklib.utils;

/**
 * @author vdang
 */
public class SimpleMath {
    
    private static double LOG2 = Math.log(2);
    private static double LOG10 = Math.log(10);
    private static double LOGE = Math.log(Math.E);
    
    public static double logBase2(double value)
    {
        return Math.log(value)/LOG2;
    }
    public static double logBase10(double value)
    {
        return Math.log(value)/LOG10;
    }
    public static double ln(double value)
    {
        return Math.log(value)/LOGE;
    }
    public static int min(int a, int b)
    {
        return (a>b)?b:a;
    }
    public static double p(long count, long total)
    {
        return ((double)count+0.5)/(total+1);
    }
    public static double round(double val)
    {
        int precision = 10000; //keep 4 digits
        return Math.floor(val * precision +.5)/precision;
    }
    public static double round(float val)
    {
        int precision = 10000; //keep 4 digits
        return Math.floor(val * precision +.5)/precision;
    }
    public static double round(double val, int n)
    {
        int precision = 1; 
        for(int i=0;i<n;i++)
            precision *= 10;
        return Math.floor(val * precision +.5)/precision;
    }
    public static float round(float val, int n)
    {
        int precision = 1; 
        for(int i=0;i<n;i++)
            precision *= 10;
        return (float) (Math.floor(val * precision +.5)/precision);
    }
}
