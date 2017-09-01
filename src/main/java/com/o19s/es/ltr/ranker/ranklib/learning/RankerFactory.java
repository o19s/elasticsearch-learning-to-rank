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

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.ranklib.learning.boosting.AdaRank;
import com.o19s.es.ltr.ranker.ranklib.learning.boosting.RankBoost;
import com.o19s.es.ltr.ranker.ranklib.learning.neuralnet.LambdaRank;
import com.o19s.es.ltr.ranker.ranklib.learning.neuralnet.ListNet;
import com.o19s.es.ltr.ranker.ranklib.learning.neuralnet.RankNet;
import com.o19s.es.ltr.ranker.ranklib.learning.tree.LambdaMART;
import com.o19s.es.ltr.ranker.ranklib.learning.tree.MART;
import com.o19s.es.ltr.ranker.ranklib.learning.tree.RFRanker;
import org.elasticsearch.common.io.FastStringReader;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Locale;

/**
 * @author vdang
 * 
 * This class implements the Ranker factory. All ranking algorithms implemented have to be recognized in this class. 
 */
public class RankerFactory {

    protected Ranker[] rFactory = new Ranker[]{
            new MART(), new RankBoost(), new RankNet(), new AdaRank(), new CoorAscent(), new LambdaRank(),
            new LambdaMART(), new ListNet(), new RFRanker(), new LinearRegRank()};
    protected static HashMap<String, RANKER_TYPE> map = new HashMap<String, RANKER_TYPE>();
    
    public RankerFactory()
    {
        map.put(createRanker(RANKER_TYPE.MART).name().toUpperCase(Locale.ROOT), RANKER_TYPE.MART);
        map.put(createRanker(RANKER_TYPE.RANKNET).name().toUpperCase(Locale.ROOT), RANKER_TYPE.RANKNET);
        map.put(createRanker(RANKER_TYPE.RANKBOOST).name().toUpperCase(Locale.ROOT), RANKER_TYPE.RANKBOOST);
        map.put(createRanker(RANKER_TYPE.ADARANK).name().toUpperCase(Locale.ROOT), RANKER_TYPE.ADARANK);
        map.put(createRanker(RANKER_TYPE.COOR_ASCENT).name().toUpperCase(Locale.ROOT), RANKER_TYPE.COOR_ASCENT);
        map.put(createRanker(RANKER_TYPE.LAMBDARANK).name().toUpperCase(Locale.ROOT), RANKER_TYPE.LAMBDARANK);
        map.put(createRanker(RANKER_TYPE.LAMBDAMART).name().toUpperCase(Locale.ROOT), RANKER_TYPE.LAMBDAMART);
        map.put(createRanker(RANKER_TYPE.LISTNET).name().toUpperCase(Locale.ROOT), RANKER_TYPE.LISTNET);
        map.put(createRanker(RANKER_TYPE.RANDOM_FOREST).name().toUpperCase(Locale.ROOT), RANKER_TYPE.RANDOM_FOREST);
        map.put(createRanker(RANKER_TYPE.LINEAR_REGRESSION).name().toUpperCase(Locale.ROOT), RANKER_TYPE.LINEAR_REGRESSION);
    }    
    public Ranker createRanker(RANKER_TYPE type)
    {
        return rFactory[type.ordinal() - RANKER_TYPE.MART.ordinal()].createNew();
    }

    public Ranker loadRankerFromString(String fullText, FeatureSet set, FEATURE_TYPE type)
    {
        try (BufferedReader in = new BufferedReader(new FastStringReader(fullText))) {
            Ranker r;
            String content = in.readLine();//read the first line to get the name of the ranking algorithm
              content = content.replace("## ", "").trim();
              //System.out.println("Model:\t\t" + content);
              r = createRanker(map.get(content.toUpperCase(Locale.ROOT)));
              r.loadFromString(fullText, set, type);

              return r;
        }
        catch(Exception ex)
        {
            throw RankLibError.create(ex);
        }
      }
}
