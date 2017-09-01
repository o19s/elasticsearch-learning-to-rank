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

import java.util.ArrayList;
import java.util.List;

/**
 * This class contains the implementation of some simple sorting algorithms.
 * @author Van Dang
 * @version 1.3 (July 29, 2008)
 */
public class Sorter {
    /**
     * Sort a double array using Interchange sort.
     * @param sortVal The double array to be sorted. 
     * @param asc TRUE to sort ascendingly, FALSE to sort descendingly.
     * @return The sorted indexes.
     */
    public static int[] sort(double[] sortVal, boolean asc)
    {
        int[] freqIdx = new int[sortVal.length];
        for(int i=0;i<sortVal.length;i++)
            freqIdx[i] = i;
        for(int i=0;i<sortVal.length-1;i++)
        {
            int max = i;
            for(int j=i+1;j<sortVal.length;j++)
            {
                if(asc)
                {
                    if(sortVal[freqIdx[max]] > sortVal[freqIdx[j]])
                        max = j;
                }
                else
                {
                    if(sortVal[freqIdx[max]] <  sortVal[freqIdx[j]])
                        max = j;
                }
            }
            //swap
            int tmp = freqIdx[i];
            freqIdx[i] = freqIdx[max];
            freqIdx[max] = tmp;
        }
        return freqIdx;
    }
    public static int[] sort(float[] sortVal, boolean asc)
    {
        int[] freqIdx = new int[sortVal.length];
        for(int i=0;i<sortVal.length;i++)
            freqIdx[i] = i;
        for(int i=0;i<sortVal.length-1;i++)
        {
            int max = i;
            for(int j=i+1;j<sortVal.length;j++)
            {
                if(asc)
                {
                    if(sortVal[freqIdx[max]] > sortVal[freqIdx[j]])
                        max = j;
                }
                else
                {
                    if(sortVal[freqIdx[max]] <  sortVal[freqIdx[j]])
                        max = j;
                }
            }
            //swap
            int tmp = freqIdx[i];
            freqIdx[i] = freqIdx[max];
            freqIdx[max] = tmp;
        }
        return freqIdx;
    }
    /**
     * Sort an integer array using Quick Sort.
     * @param sortVal The integer array to be sorted.
     * @param asc TRUE to sort ascendingly, FALSE to sort descendingly.
     * @return The sorted indexes.
     */
    public static int[] sort(int[] sortVal, boolean asc)
    {
        return qSort(sortVal, asc);
    }
    /**
     * Sort an integer array using Quick Sort.
     * @param sortVal The integer array to be sorted.
     * @param asc TRUE to sort ascendingly, FALSE to sort descendingly.
     * @return The sorted indexes.
     */
    public static int[] sort(List<Integer> sortVal, boolean asc)
    {
        return qSort(sortVal, asc);
    }
    public static int[] sortString(List<String> sortVal, boolean asc)
    {
        return qSortString(sortVal, asc);
    }
    /**
     * Sort an long array using Quick Sort.
     * @param sortVal The long array to be sorted.
     * @param asc TRUE to sort ascendingly, FALSE to sort descendingly.
     * @return The sorted indexes.
     */
    public static int[] sortLong(List<Long> sortVal, boolean asc)
    {
        return qSortLong(sortVal, asc);
    }
    /**
     * Sort an double array using Quick Sort.
     * @param sortVal The double array to be sorted.
     * @return The sorted indexes.
     */
    public static int[] sortDesc(List<Double> sortVal)
    {
        return qSortDouble(sortVal, false);
    }
    
    private static long count = 0;
    /**
     * Quick sort internal
     * @param l The list to sort.
     * @param asc Ascending/Descendingly parameter.
     * @return The sorted indexes.
     */
    private static int[] qSort(List<Integer> l, boolean asc)
    {
        count = 0;
        int[] idx = new int[l.size()];
        List<Integer> idxList = new ArrayList<Integer>();
        for(int i=0;i<l.size();i++)
            idxList.add(i);
        //System.out.print("Sorting...");
        idxList = qSort(l, idxList, asc);
        for(int i=0;i<l.size();i++)
            idx[i] = idxList.get(i);
        //System.out.println("[Done.]");
        return idx;
    }
    private static int[] qSortString(List<String> l, boolean asc)
    {
        count = 0;
        int[] idx = new int[l.size()];
        List<Integer> idxList = new ArrayList<Integer>();
        for(int i=0;i<l.size();i++)
            idxList.add(i);
        //System.out.print("Sorting...");
        idxList = qSortString(l, idxList, asc);
        for(int i=0;i<l.size();i++)
            idx[i] = idxList.get(i);
        //System.out.println("[Done.]");
        return idx;
    }
    /**
     * Quick sort internal
     * @param l The list to sort.
     * @param asc Ascending/Descendingly parameter.
     * @return The sorted indexes.
     */
    private static int[] qSortLong(List<Long> l, boolean asc)
    {
        count = 0;
        int[] idx = new int[l.size()];
        List<Integer> idxList = new ArrayList<Integer>();
        for(int i=0;i<l.size();i++)
            idxList.add(i);
        //System.out.print("Sorting...");
        idxList = qSortLong(l, idxList, asc);
        for(int i=0;i<l.size();i++)
            idx[i] = idxList.get(i);
        //System.out.println("[Done.]");
        return idx;
    }
    /**
     * Quick sort internal
     * @param l The list to sort.
     * @param asc Ascending/Descendingly parameter.
     * @return The sorted indexes.
     */
    private static int[] qSortDouble(List<Double> l, boolean asc)
    {
        count = 0;
        int[] idx = new int[l.size()];
        List<Integer> idxList = new ArrayList<Integer>();
        for(int i=0;i<l.size();i++)
            idxList.add(i);
        //System.out.print("Sorting...");
        idxList = qSortDouble(l, idxList, asc);
        for(int i=0;i<l.size();i++)
            idx[i] = idxList.get(i);
        //System.out.println("[Done.]");
        return idx;
    }
    /**
     * Sort an integer array using Quick Sort.
     * @param l The integer array to be sorted.
     * @param asc TRUE to sort ascendingly, FALSE to sort descendingly.
     * @return The sorted indexes.
     */
    private static int[] qSort(int[] l, boolean asc)
    {
        count = 0;
        int[] idx = new int[l.length];
        List<Integer> idxList = new ArrayList<Integer>();
        for(int i=0;i<l.length;i++)
            idxList.add(i);
        //System.out.print("Sorting...");
        idxList = qSort(l, idxList, asc);
        for(int i=0;i<l.length;i++)
            idx[i] = idxList.get(i);
        //System.out.println("[Done.]");
        return idx;
    }
    /**
     * Quick sort internal.
     * @return  The sorted indexes.
     */
    private static List<Integer> qSort(List<Integer> l, List<Integer> idxList, boolean asc)
    {
        int mid = idxList.size()/2;
        List<Integer> left = new ArrayList<Integer>();
        List<Integer> right = new ArrayList<Integer>();
        List<Integer> pivot = new ArrayList<Integer>();
        for(int i=0;i<idxList.size();i++)
        {
            if(l.get(idxList.get(i)) > l.get(idxList.get(mid)))
            {
                if(asc)
                    right.add(idxList.get(i));
                else
                    left.add(idxList.get(i));
            }
            else if(l.get(idxList.get(i)) < l.get(idxList.get(mid)))
            {
                if(asc)
                    left.add(idxList.get(i));
                else
                    right.add(idxList.get(i));
            }
            else
                pivot.add(idxList.get(i));
        }
        count++;
        if(left.size() > 1)
            left = qSort(l, left, asc);
        count++;
        if(right.size() > 1)
            right = qSort(l, right, asc);
        List<Integer> newIdx = new ArrayList<Integer>();
        newIdx.addAll(left);
        newIdx.addAll(pivot);
        newIdx.addAll(right);
        return newIdx;
    }
    private static List<Integer> qSortString(List<String> l, List<Integer> idxList, boolean asc)
    {
        int mid = idxList.size()/2;
        List<Integer> left = new ArrayList<Integer>();
        List<Integer> right = new ArrayList<Integer>();
        List<Integer> pivot = new ArrayList<Integer>();
        for(int i=0;i<idxList.size();i++)
        {
            if(l.get(idxList.get(i)).compareTo(l.get(idxList.get(mid)))>0)
            {
                if(asc)
                    right.add(idxList.get(i));
                else
                    left.add(idxList.get(i));
            }
            else if(l.get(idxList.get(i)).compareTo(l.get(idxList.get(mid)))<0)
            {
                if(asc)
                    left.add(idxList.get(i));
                else
                    right.add(idxList.get(i));
            }
            else
                pivot.add(idxList.get(i));
        }
        count++;
        if(left.size() > 1)
            left = qSortString(l, left, asc);
        count++;
        if(right.size() > 1)
            right = qSortString(l, right, asc);
        List<Integer> newIdx = new ArrayList<Integer>();
        newIdx.addAll(left);
        newIdx.addAll(pivot);
        newIdx.addAll(right);
        return newIdx;
    }
    /**
     * Quick sort internal.
     * @return The sorted indexes.
     */
    private static List<Integer> qSort(int[] l, List<Integer> idxList, boolean asc)
    {
        int mid = idxList.size()/2;
        List<Integer> left = new ArrayList<Integer>();
        List<Integer> right = new ArrayList<Integer>();
        List<Integer> pivot = new ArrayList<Integer>();
        for(int i=0;i<idxList.size();i++)
        {
            if(l[idxList.get(i)] > l[idxList.get(mid)])
            {
                if(asc)
                    right.add(idxList.get(i));
                else
                    left.add(idxList.get(i));
            }
            else if(l[idxList.get(i)] < l[idxList.get(mid)])
            {
                if(asc)
                    left.add(idxList.get(i));
                else
                    right.add(idxList.get(i));
            }
            else
                pivot.add(idxList.get(i));
        }
        count++;
        if(left.size() > 1)
            left = qSort(l, left, asc);
        count++;
        if(right.size() > 1)
            right = qSort(l, right, asc);
        List<Integer> newIdx = new ArrayList<Integer>();
        newIdx.addAll(left);
        newIdx.addAll(pivot);
        newIdx.addAll(right);
        return newIdx;
    }
    /**
     * Quick sort internal.
     * @return  The sorted indexes.
     */
    private static List<Integer> qSortDouble(List<Double> l, List<Integer> idxList, boolean asc)
    {
        int mid = idxList.size()/2;
        List<Integer> left = new ArrayList<Integer>();
        List<Integer> right = new ArrayList<Integer>();
        List<Integer> pivot = new ArrayList<Integer>();
        for(int i=0;i<idxList.size();i++)
        {
            if(l.get(idxList.get(i)) > l.get(idxList.get(mid)))
            {
                if(asc)
                    right.add(idxList.get(i));
                else
                    left.add(idxList.get(i));
            }
            else if(l.get(idxList.get(i)) < l.get(idxList.get(mid)))
            {
                if(asc)
                    left.add(idxList.get(i));
                else
                    right.add(idxList.get(i));
            }
            else
                pivot.add(idxList.get(i));
        }
        count++;
        if(left.size() > 1)
            left = qSortDouble(l, left, asc);
        count++;
        if(right.size() > 1)
            right = qSortDouble(l, right, asc);
        List<Integer> newIdx = new ArrayList<Integer>();
        newIdx.addAll(left);
        newIdx.addAll(pivot);
        newIdx.addAll(right);
        return newIdx;
    }
    /**
     * Quick sort internal.
     * @return The sorted indexes.
     */
    private static List<Integer> qSortLong(List<Long> l, List<Integer> idxList, boolean asc)
    {
        int mid = idxList.size()/2;
        List<Integer> left = new ArrayList<Integer>();
        List<Integer> right = new ArrayList<Integer>();
        List<Integer> pivot = new ArrayList<Integer>();
        for(int i=0;i<idxList.size();i++)
        {
            if(l.get(idxList.get(i)) > l.get(idxList.get(mid)))
            {
                if(asc)
                    right.add(idxList.get(i));
                else
                    left.add(idxList.get(i));
            }
            else if(l.get(idxList.get(i)) < l.get(idxList.get(mid)))
            {
                if(asc)
                    left.add(idxList.get(i));
                else
                    right.add(idxList.get(i));
            }
            else
                pivot.add(idxList.get(i));
        }
        count++;
        if(left.size() > 1)
            left = qSortLong(l, left, asc);
        count++;
        if(right.size() > 1)
            right = qSortLong(l, right, asc);
        List<Integer> newIdx = new ArrayList<Integer>();
        newIdx.addAll(left);
        newIdx.addAll(pivot);
        newIdx.addAll(right);
        return newIdx;
    }
}
