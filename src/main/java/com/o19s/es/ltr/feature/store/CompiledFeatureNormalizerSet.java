package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureNormalizerSet;
import com.o19s.es.ltr.ranker.normalizer.NoOpNormalizer;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Normalizer set compiled from a model specification
 */
public class CompiledFeatureNormalizerSet implements FeatureNormalizerSet {

    /**
     * Normalizers indexed by ord
     */
    private final List<Normalizer> ftrNorms;

    /**
     * Track which features we're normalizing. This is pre-build
     * to avoid doing it in a Lucene Scorer or having to deal with a
     * Set implementaiton or something
     */
    private final int[] ftrOrds;

    public CompiledFeatureNormalizerSet(List<Normalizer> ftrNorms)
    {
        this.ftrNorms = ftrNorms;

        Set<Integer> ftrOrdSet = new HashSet<Integer>();
        for (int i = 0; i < ftrNorms.size(); i++) {
            if (ftrNorms.get(i).getClass() != NoOpNormalizer.class) {
                ftrOrdSet.add(i);
            }
        }

        ftrOrds = new int[ftrOrdSet.size()];
        int i = 0;
        for (Integer ftrOrd: ftrOrdSet) {
            ftrOrds[i] = ftrOrd.intValue();
            i++;
        }
    }

    @Override
    public Normalizer getNomalizer(int featureOrd) {
        return ftrNorms.get(featureOrd);
    }

    @Override
    public int[] getNormalizedOrds() {
        return ftrOrds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CompiledFeatureNormalizerSet)) return false;
        CompiledFeatureNormalizerSet that = (CompiledFeatureNormalizerSet) other;

        if (this.ftrNorms.size() != that.ftrNorms.size()) return false;

        for (int i = 0; i < this.ftrNorms.size(); i++) {
            if (!this.ftrNorms.get(i).equals(that.ftrNorms.get(i))) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = this.ftrNorms.size();
        for (Normalizer norm: this.ftrNorms) {
            hashCode += norm.hashCode();
        }

        return hashCode;

    }

}
