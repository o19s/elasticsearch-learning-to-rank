package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.query.NormalizedFeatureQuery;
import org.apache.lucene.search.Query;

import java.util.Map;

/**
 * A Feature that's normalized by a normalizer
 */
public class NormalizedFeature implements Feature {

    private final Feature wrapped;
    private final Normalizer ftrNorm;

    public NormalizedFeature(Feature wrapped, Normalizer ftrNorm) {
        this.wrapped = wrapped;
        this.ftrNorm = ftrNorm;
    }

    @Override
    public String name() {
        return "normalized: (" + wrapped.name() + ")";
    }

    @Override
    public Query doToQuery(LtrQueryContext context, FeatureSet set, Map<String, Object> params) {
        Query wrappedQuery =  wrapped.doToQuery(context, set, params);
        return new NormalizedFeatureQuery(wrappedQuery, ftrNorm);
    }

    @Override
    public int hashCode() {
        return 31 * this.wrapped.hashCode() + this.ftrNorm.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof NormalizedFeature)) {
            return false;
        }

        NormalizedFeature that = (NormalizedFeature)obj;
        return (this.wrapped.equals(that.wrapped) && this.ftrNorm.equals(that.ftrNorm));
    }
}
