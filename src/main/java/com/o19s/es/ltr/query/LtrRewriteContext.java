package com.o19s.es.ltr.query;

import com.o19s.es.ltr.ranker.LogLtrRanker;
import com.o19s.es.ltr.ranker.LtrRanker;

import java.util.function.Supplier;

/**
 * Contains context needed to rewrite queries to holds the vectorSupplier and provide extra logging support
 */
public class LtrRewriteContext {
    private final Supplier<LtrRanker.FeatureVector> vectorSupplier;
    private final LtrRanker ranker;

    public LtrRewriteContext(LtrRanker ranker, Supplier<LtrRanker.FeatureVector> vectorSupplier) {
        this.ranker = ranker;
        this.vectorSupplier = vectorSupplier;
    }

    public Supplier<LtrRanker.FeatureVector> getFeatureVectorSupplier() {
        return vectorSupplier;
    }

    /**
     * Get LogConsumer used during the LoggingFetchSubPhase
     *
     * The returned consumer will only be non-null during the logging fetch phase
     */
    public LogLtrRanker.LogConsumer getLogConsumer() {
        if (ranker instanceof LogLtrRanker) {
            return ((LogLtrRanker)ranker).getLogConsumer();
        }
        return null;
    }
}
