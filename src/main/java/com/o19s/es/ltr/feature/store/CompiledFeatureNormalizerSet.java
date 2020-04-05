package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureNormalizerSet;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;

import java.util.List;

public class CompiledFeatureNormalizerSet implements FeatureNormalizerSet {

    List<Normalizer> ftrNorms;

    CompiledFeatureNormalizerSet(List<Normalizer> ftrNorms) {
        this.ftrNorms = ftrNorms;
    }

    @Override
    public Normalizer getNomalizer(int featureOrd) {
        return ftrNorms.get(featureOrd);
    }
}
