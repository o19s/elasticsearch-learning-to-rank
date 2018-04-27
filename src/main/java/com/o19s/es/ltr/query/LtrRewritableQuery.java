package com.o19s.es.ltr.query;

import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.function.Supplier;

public interface LtrRewritableQuery {
    /**
     * Rewrite the query so that it holds the vectorSupplier
     */
    Query ltrRewrite(Supplier<LtrRanker.FeatureVector> vectorSuppler) throws IOException;
}
