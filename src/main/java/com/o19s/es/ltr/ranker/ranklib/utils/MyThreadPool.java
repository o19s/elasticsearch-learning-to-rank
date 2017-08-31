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

import com.o19s.es.ltr.ranker.ranklib.learning.RankLibError;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author vdang
 *
 */
public class MyThreadPool extends ThreadPoolExecutor {

    private final Semaphore semaphore;
    private int size = 0;
    
    private MyThreadPool(int size)
    {
        super(size, size, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        semaphore = new Semaphore(size, true);
        this.size = size;
    }
    
    private static MyThreadPool singleton = null;
    public static MyThreadPool getInstance()
    {
        if(singleton == null)
            init(Runtime.getRuntime().availableProcessors());
        return singleton;
    }
    
    public static void init(int poolSize)
    {
        singleton = new MyThreadPool(poolSize);
    }
    public int size()
    {
        return size;
    }
    public WorkerThread[] execute(WorkerThread worker, int nTasks)
    {
        MyThreadPool p = MyThreadPool.getInstance();
        int[] partition = p.partition(nTasks);
        WorkerThread[] workers = new WorkerThread[partition.length-1];
        for(int i=0;i<partition.length-1;i++)
        {
            WorkerThread w = worker.clone();
            w.set(partition[i], partition[i+1]-1);
            workers[i] = w;
            p.execute(w);
        }
        await();
        return workers;
    }
    
    public void await()
    {
        for(int i=0;i<size;i++)
        {
            try {
                semaphore.acquire();                
            }
            catch(Exception ex)
            {
                throw RankLibError.create("Error in MyThreadPool.await(): ", ex);
            }
        }
        for(int i=0;i<size;i++)
            semaphore.release();
    }
    public int[] partition(int listSize)
    {
        int nChunks = Math.min(listSize, size);
        int chunkSize = listSize/nChunks;
        int mod = listSize % nChunks;
        int[] partition = new int[nChunks+1];
        partition[0] = 0;
        for(int i=1;i<=nChunks;i++)
            partition[i] = partition[i-1] + chunkSize + ((i<=mod)?1:0);
        return partition;
    }
    
    
    public void execute(Runnable task) 
    {
        try {
            semaphore.acquire();
            super.execute(task);
        }
        catch(Exception ex)
        {
            throw RankLibError.create("Error in MyThreadPool.execute(): ", ex);
        }
    }
    protected void afterExecute(Runnable r, Throwable t)
    {
        super.afterExecute(r, t);
        semaphore.release();
    }
}
