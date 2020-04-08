package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureNormalizerSet;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;

import java.util.List;

public class CompiledFeatureNormalizerSet implements FeatureNormalizerSet {

    private List<Normalizer> ftrNorms;

    public CompiledFeatureNormalizerSet(List<Normalizer> ftrNorms) {
        this.ftrNorms = ftrNorms;
    }

    @Override
    public Normalizer getNomalizer(int featureOrd) {
        return ftrNorms.get(featureOrd);
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
