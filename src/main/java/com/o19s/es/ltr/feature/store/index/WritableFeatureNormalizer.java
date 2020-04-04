package com.o19s.es.ltr.feature.store.index;

import com.o19s.es.ltr.ranker.normalizer.FeatureNormalizer;
import org.elasticsearch.common.io.stream.Writeable;

public interface WritableFeatureNormalizer extends FeatureNormalizer, Writeable {
}
