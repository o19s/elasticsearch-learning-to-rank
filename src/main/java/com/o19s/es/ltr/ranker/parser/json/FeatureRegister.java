package com.o19s.es.ltr.ranker.parser.json;

import com.o19s.es.ltr.feature.FeatureSet;

import java.util.HashSet;
import java.util.Set;

// Used to track which features are used
// by a given model by tracking how models
// access features
public class FeatureRegister
{
    public FeatureRegister (FeatureSet set) {
        _featureSet = set;
    }

    public int useFeature(String featureName) {
        int featureOrd = _featureSet.featureOrdinal(featureName);
        _usedFeatures.add(featureOrd);
        return featureOrd;
    }

    public int numFeaturesUsed() {
        return _usedFeatures.size();
    }

    public int numFeaturesAvail() {return _featureSet.size();}

    private Set<Integer> _usedFeatures = new HashSet<Integer>();
    private FeatureSet _featureSet;
}

