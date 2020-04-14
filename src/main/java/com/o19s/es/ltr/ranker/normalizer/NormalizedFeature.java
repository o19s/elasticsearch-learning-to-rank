package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import org.apache.lucene.search.Query;

import java.util.Map;

public class NormalizedFeature implements Feature {

    private Feature wrapped;

    public NormalizedFeature(Feature wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public String name() {
        return wrapped.name();
    }

    @Override
    public Query doToQuery(LtrQueryContext context, FeatureSet set, Map<String, Object> params) {
        return wrapped.doToQuery(context, set, params);
    }
}
