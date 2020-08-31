package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.query.LtrRewritableQuery;
import com.o19s.es.ltr.query.LtrRewriteContext;
import com.o19s.es.ltr.ranker.LogLtrRanker;
import com.o19s.es.termstat.TermStatSupplier;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.common.lucene.search.function.LeafScoreFunction;
import org.elasticsearch.common.lucene.search.function.ScriptScoreFunction;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.Script;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ScriptFeature implements Feature {
    public static final String TEMPLATE_LANGUAGE = "script_feature";
    public static final String FEATURE_VECTOR = "feature_vector";
    public static final String TERM_STAT = "terms";
    public static final String EXTRA_LOGGING = "extra_logging";
    public static final String EXTRA_SCRIPT_PARAMS = "extra_script_params";

    private final String name;
    private final Script script;
    private final Collection<String> queryParams;
    private final Map<String, Object> baseScriptParams;
    private final Map<String, String> extraScriptParams;

    @SuppressWarnings("unchecked")
    public ScriptFeature(String name, Script script, Collection<String> queryParams) {
        this.name = Objects.requireNonNull(name);
        this.script = Objects.requireNonNull(script);
        this.queryParams = queryParams;
        Map<String, Object> ltrScriptParams = new HashMap<>();
        Map<String, String> ltrExtraScriptParams = new HashMap<>();
        for (Map.Entry<String, Object> entry : script.getParams().entrySet()) {
            if (!entry.getKey().equals(EXTRA_SCRIPT_PARAMS)) {
                ltrScriptParams.put(String.valueOf(entry.getKey()), entry.getValue());
            } else {
                ltrExtraScriptParams = (Map<String, String>) entry.getValue();
            }
        }
        this.baseScriptParams = ltrScriptParams;
        this.extraScriptParams = ltrExtraScriptParams;
    }

    public static ScriptFeature compile(StoredFeature feature) {
        try {
            XContentParser xContentParser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY,
                    LoggingDeprecationHandler.INSTANCE, feature.template());
            return new ScriptFeature(feature.name(), Script.parse(xContentParser, "native"), feature.queryParams());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The feature name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Transform this feature into a lucene query
     */
    @Override
    public Query doToQuery(LtrQueryContext context, FeatureSet featureSet, Map<String, Object> params) {
        List<String> missingParams = queryParams.stream()
                .filter((x) -> !params.containsKey(x))
                .collect(Collectors.toList());
        if (!missingParams.isEmpty()) {
            String names = String.join(",", missingParams);
            throw new IllegalArgumentException("Missing required param(s): [" + names + "]");
        }

        Map<String, Object> queryTimeParams = new HashMap<>();
        Map<String, Object> extraQueryTimeParams = new HashMap<>();
        for (String x : queryParams) {
            if (params.containsKey(x)) {
                /* If extra_script_param then add the appropriate param name for the script else add name:value as is */
                if (extraScriptParams.containsKey(x)) {
                    extraQueryTimeParams.put(extraScriptParams.get(x), params.get(x));
                } else {
                    queryTimeParams.put(x, params.get(x));
                }
            }
        }


        FeatureSupplier supplier = new FeatureSupplier(featureSet);
        ExtraLoggingSupplier extraLoggingSupplier = new ExtraLoggingSupplier();
        TermStatSupplier termstatSupplier = new TermStatSupplier();
        Map<String, Object> nparams = new HashMap<>();

        // Parse terms if set
        Set<Term> terms = new HashSet<>();
        if (baseScriptParams.containsKey("term_stat")) {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> termspec = (HashMap<String, Object>) baseScriptParams.get("term_stat");

            @SuppressWarnings("unchecked")
            ArrayList<String> fields = (ArrayList<String>) termspec.get("fields");

            @SuppressWarnings("unchecked")
            ArrayList<String> termList = (ArrayList<String>) termspec.get("terms");

            String analyzerName = (String) termspec.get("analyzer");

            if (fields == null || termList == null) {
                throw new IllegalArgumentException("Term Stats injection requires fields and terms");
            }

            Analyzer analyzer = null;
            for(String field : fields) {
                if (analyzerName == null) {
                    MappedFieldType fieldType = context.getQueryShardContext().getMapperService().fieldType(field);
                    analyzer = context.getQueryShardContext().getSearchAnalyzer(fieldType);
                } else if (analyzer == null) {
                    analyzer = context.getQueryShardContext().getIndexAnalyzers().get(analyzerName);
                }

                if (analyzer == null) {
                    throw new IllegalArgumentException("No analyzer found for [" + analyzerName + "]");
                }

                for (String termString : termList) {
                    TokenStream ts = analyzer.tokenStream(field, termString);
                    TermToBytesRefAttribute termAtt = ts.getAttribute(TermToBytesRefAttribute.class);

                    try {
                        ts.reset();
                        while (ts.incrementToken()) {
                            terms.add(new Term(field, termAtt.getBytesRef()));
                        }
                        ts.close();
                    } catch (IOException ex) {
                        // No-op
                    }
                }
            }

            nparams.put(TERM_STAT, termstatSupplier);
        }

        nparams.putAll(baseScriptParams);
        nparams.putAll(queryTimeParams);
        nparams.putAll(extraQueryTimeParams);
        nparams.put(FEATURE_VECTOR, supplier);
        nparams.put(EXTRA_LOGGING, extraLoggingSupplier);
        Script script = new Script(this.script.getType(), this.script.getLang(),
            this.script.getIdOrCode(), this.script.getOptions(), nparams);
        ScoreScript.Factory factoryFactory  = context.getQueryShardContext().compile(script, ScoreScript.CONTEXT);
        ScoreScript.LeafFactory leafFactory = factoryFactory.newFactory(nparams, context.getQueryShardContext().lookup());
        ScriptScoreFunction function = new ScriptScoreFunction(script, leafFactory,
                context.getQueryShardContext().index().getName(),
                context.getQueryShardContext().getShardId(),
                context.getQueryShardContext().indexVersionCreated());
        return new LtrScript(function, supplier, extraLoggingSupplier, termstatSupplier, terms);
    }

    static class LtrScript extends Query implements LtrRewritableQuery {
        private final ScriptScoreFunction function;
        private final FeatureSupplier supplier;
        private final ExtraLoggingSupplier extraLoggingSupplier;
        private final TermStatSupplier termStatSupplier;
        private final Set<Term> terms;

        LtrScript(ScriptScoreFunction function,
                  FeatureSupplier supplier,
                  ExtraLoggingSupplier extraLoggingSupplier,
                  TermStatSupplier termStatSupplier,
                  Set<Term> terms) {
            this.function = function;
            this.supplier = supplier;
            this.extraLoggingSupplier = extraLoggingSupplier;
            this.termStatSupplier = termStatSupplier;
            this.terms = terms;
        }

        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            LtrScript ol = (LtrScript) o;
            return sameClassAs(o)
                    && Objects.equals(function, ol.function);
        }

        @Override
        public int hashCode() {
            return Objects.hash(classHash(), function);
        }

        @Override
        public String toString(String field) {
            return "LtrScript:" + field;
        }

        @Override
        public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
            if (!scoreMode.needsScores()) {
                return new MatchAllDocsQuery().createWeight(searcher, scoreMode, 1F);
            }
            return new LtrScriptWeight(this, this.function, termStatSupplier, terms, searcher, scoreMode);
        }

        @Override
        public Query ltrRewrite(LtrRewriteContext context) throws IOException {
            supplier.set(context.getFeatureVectorSupplier());

            LogLtrRanker.LogConsumer consumer = context.getLogConsumer();
            if (consumer != null) {
                extraLoggingSupplier.setSupplier(consumer::getExtraLoggingMap);
            } else {
                extraLoggingSupplier.setSupplier(() -> null);
            }
            return this;
        }
    }

    static class LtrScriptWeight extends Weight {
        private final IndexSearcher searcher;
        private final ScoreMode scoreMode;
        private final ScriptScoreFunction function;
        private final TermStatSupplier termStatSupplier;
        private final Set<Term> terms;

        LtrScriptWeight(Query query, ScriptScoreFunction function,
                        TermStatSupplier termStatSupplier,
                        Set<Term> terms,
                        IndexSearcher searcher,
                        ScoreMode scoreMode) {
            super(query);
            this.function = function;
            this.termStatSupplier = termStatSupplier;
            this.terms = terms;
            this.searcher = searcher;
            this.scoreMode = scoreMode;
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            return function.getLeafScoreFunction(context).explainScore(doc, Explanation.noMatch("none"));
        }

        @Override
        public Scorer scorer(LeafReaderContext context) throws IOException {
            LeafScoreFunction leafScoreFunction = function.getLeafScoreFunction(context);
            DocIdSetIterator iterator = DocIdSetIterator.all(context.reader().maxDoc());
            return new Scorer(this) {
                @Override
                public int docID() {
                    return iterator.docID();
                }

                @Override
                public float score() throws IOException {
                    // Do the terms magic if the user asked for it
                    if (terms.size() > 0) {
                        termStatSupplier.bump(searcher, context, docID(), terms, scoreMode);
                    }

                    return (float) leafScoreFunction.score(iterator.docID(), 0F);
                }

                @Override
                public DocIdSetIterator iterator() {
                    return iterator;
                }

                /**
                 * Return the maximum score that documents between the last {@code target}
                 * that this iterator was {@link #advanceShallow(int) shallow-advanced} to
                 * included and {@code upTo} included.
                 */
                @Override
                public float getMaxScore(int upTo) throws IOException {
                    //TODO??
                    return Float.POSITIVE_INFINITY;
                }
            };
        }

        @Override
        public void extractTerms(Set<Term> terms) {
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            // Never ever cache this query, its parent query is mutable
            return false;
        }
    }
}
