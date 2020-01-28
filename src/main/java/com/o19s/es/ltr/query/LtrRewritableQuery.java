package com.o19s.es.ltr.query;

import org.apache.lucene.search.Query;

import java.io.IOException;

public interface LtrRewritableQuery {
    /**
     * Rewrite the query so that it holds the vectorSupplier and provide extra logging support
     */
    Query ltrRewrite(LtrRewriteContext context) throws IOException;
}
