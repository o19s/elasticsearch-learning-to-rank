package com.o19s.es.ltr.ranker.normalizer;

import com.o19s.es.ltr.ranker.LtrRanker;

import java.util.Map;

public class FeatureNormalizingRanker implements LtrRanker {

    LtrRanker wrappedRanker;
    Map<Integer, FeatureNormalizer> ftrNorms;

    FeatureNormalizingRanker(LtrRanker wrappedRanker, Map<Integer, FeatureNormalizer> ftrNorms) {
        this.wrappedRanker = wrappedRanker;
        this.ftrNorms = ftrNorms;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public FeatureVector newFeatureVector(FeatureVector reuse) {
        FeatureVector wrapped = this.wrappedRanker.newFeatureVector(reuse);
        return new NormalizedFeatureVector(this.ftrNorms, wrapped);
    }

    @Override
    public float score(FeatureVector point) {
        return this.wrappedRanker.score(point);
    }

    public static class NormalizedFeatureVector implements LtrRanker.FeatureVector {

        private Map<Integer, FeatureNormalizer> ftrNorms;
        private FeatureVector wrapped;


        NormalizedFeatureVector(Map<Integer, FeatureNormalizer> ftrNorms, FeatureVector wrapped) {
            this.ftrNorms = ftrNorms;
            this.wrapped = wrapped;
        }

        @Override
        public void setFeatureScore(int featureId, float score) {
            wrapped.setFeatureScore(featureId, score);
        }

        @Override
        public float getFeatureScore(int featureId) {
            FeatureNormalizer ftrNorm = ftrNorms.get(featureId);
            if (ftrNorm != null) {
                return ftrNorm.normalize(wrapped.getFeatureScore(featureId));
            } else {
                return wrapped.getFeatureScore(featureId);
            }
        }

    }
}
