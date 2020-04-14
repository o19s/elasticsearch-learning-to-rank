package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.query.NormalizedFeatureQuery;
import com.o19s.es.ltr.ranker.normalizer.NormalizedFeature;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import org.apache.lucene.search.Query;

import java.util.List;
import java.util.Map;

/**
 * A Normalizer set compiled from a model specification
 */
public class NormalizedFeatureSet implements FeatureSet {

    /**
     * Normalizers indexed by ord
     */
    private FeatureSet wrapped;
    private final Map<Integer, Normalizer> normedFtrs;

    /**
     * Track which features we're normalizing. This is pre-build
     * to avoid doing it in a Lucene Scorer or having to deal with a
     * Set implementaiton or something
     */

    public NormalizedFeatureSet(FeatureSet wrapped, Map<Integer, Normalizer> normedFtrs)
    {
        this.normedFtrs = normedFtrs;
        this.wrapped = wrapped;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NormalizedFeatureSet)) return false;
        NormalizedFeatureSet that = (NormalizedFeatureSet) other;

        if (!this.normedFtrs.equals(that.normedFtrs)) {return false;}
        if (!this.wrapped.equals(that.wrapped)) {return false;}

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = this.normedFtrs.hashCode() * 31;
        hashCode += this.wrapped.hashCode();
        return hashCode;
    }

    @Override
    public String name() {
        return "Normalized Feature Set";
    }

    @Override
    public List<Query> toQueries(LtrQueryContext context, Map<String, Object> params) {

        List<Query> wrappedQueries =  wrapped.toQueries(context, params);
        for (int i = 0; i < wrappedQueries.size(); i++) {
            Normalizer ftrNorm = normedFtrs.get(i);
            if (ftrNorm != null) {
                Query wrappedQ = wrappedQueries.get(i);
                wrappedQueries.set(i, new NormalizedFeatureQuery(wrappedQ, ftrNorm));
            }
        }
        return wrappedQueries;
    }

    @Override
    public int featureOrdinal(String featureName) {
        return wrapped.featureOrdinal(featureName);
    }

    @Override
    public Feature feature(int ord) {
        Feature wrappedFeature = wrapped.feature(ord);
        Normalizer ftrNorm = this.normedFtrs.get(ord);
        if (ftrNorm != null) {
            return new NormalizedFeature(wrappedFeature, ftrNorm);
        }
        return wrappedFeature;
    }

    @Override
    public Feature feature(String featureName) {
        int ord = featureOrdinal(featureName);
        return this.feature(ord);
    }

    @Override
    public boolean hasFeature(String featureName) {
        return wrapped.hasFeature(featureName);
    }

    @Override
    public int size() {
        return wrapped.size();
    }
}
