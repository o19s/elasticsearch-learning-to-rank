package com.o19s.es.ltr.query;

import ciir.umass.edu.learning.Ranker;
import ciir.umass.edu.learning.RankerFactory;
import ciir.umass.edu.utilities.FileUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by doug on 12/29/16.
 */
public class CachingRankerFactory extends RankerFactory {

    public CachingRankerFactory() {
        cachedRankers = new HashMap<String, Ranker>();
    }


    private String rankerCacheKey(String model) {
        // Use first two lines as potential hash for caching hint
        // First line as ranker name, used by RankLib
        // Second line as md5 hash of content past initial comment block lines
        String[] arrKey = {};
        int subStrLen = 100;
        while (arrKey.length < 3 && subStrLen < 10000)  {
            arrKey = model.substring(0, Math.min(subStrLen, model.length())).split("##");
            subStrLen *= 10;
        }
        // Expect
        // ## <modeltype>
        // ##  name:yourname
        // ie
        // Expect
        // ## LambdaMART
        // ##  md5:12557271257abc
        String modelType = arrKey[1].trim();
        String modelName = arrKey[2].trim();
        if (modelName.contains("name:")) {
            return modelType + ":" + modelName;
        }
        return null;
    }


    private Map<String, Ranker> cachedRankers;

    // You may name long models to reuse them
    // ie they may come in full, lengthy form initially, ie:
    // ## <modeltype>
    // ##  name:yourname
    // ## <ensemble> (yadda yadd for 20k  lines)
    //
    // then later you simply include just the first two lines
    // ## <modeltype>
    // ##  name:yourname
    @Override
    public Ranker loadRankerFromString(String modelFile) {
        String cacheKey = rankerCacheKey(modelFile);
        Ranker ranker = null;
        if (cacheKey != null) {
            ranker = cachedRankers.get(cacheKey);
        }
        if (ranker == null) {
            ranker = super.loadRankerFromString(modelFile);
            if (ranker != null && cacheKey != null) {
                cachedRankers.put(cacheKey, ranker);
            }
        }
        return ranker;
    }


}
