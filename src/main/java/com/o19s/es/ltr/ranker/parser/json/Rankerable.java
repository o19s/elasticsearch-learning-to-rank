package com.o19s.es.ltr.ranker.parser.json;

import com.o19s.es.ltr.ranker.LtrRanker;

/**
 * Created by doug on 5/29/17.
 */
public interface Rankerable {

    public LtrRanker toRanker(FeatureRegister register);
}
