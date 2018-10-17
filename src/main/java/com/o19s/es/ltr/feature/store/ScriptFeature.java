package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.query.FeatureVectorWeight;
import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.common.lucene.search.function.LeafScoreFunction;
import org.elasticsearch.common.lucene.search.function.ScriptScoreFunction;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.Script;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ScriptFeature implements Feature {
    public static final String TEMPLATE_LANGUAGE = "script_feature";
    public static final String FEATURE_VECTOR = "feature_vector";
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
            String names = missingParams.stream().collect(Collectors.joining(","));
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

        Map<String, Object> nparams = new HashMap<>();
        FeatureSupplier featureSupplier = new FeatureSupplier(featureSet);
        nparams.putAll(baseScriptParams);
        nparams.putAll(queryTimeParams);
        nparams.putAll(extraQueryTimeParams);
        nparams.put(FEATURE_VECTOR, featureSupplier);

        ScoreScript.Factory scoreScript = context.getQueryShardContext().getScriptService().compile(script, ScoreScript.CONTEXT);
        return new LtrScript(new ScriptScoreFunction(script, scoreScript.newFactory(nparams,
                context.getQueryShardContext().lookup())), featureSupplier);
    }

    static class LtrScript extends Query {
        private final ScriptScoreFunction scoreFunction;
        private final FeatureSupplier featureSupplier;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LtrScript ltrScript = (LtrScript) o;
            return Objects.equals(scoreFunction, ltrScript.scoreFunction) &&
                    Objects.equals(featureSupplier, ltrScript.featureSupplier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scoreFunction, featureSupplier);
        }

        LtrScript(ScriptScoreFunction scoreFunction, FeatureSupplier featureSupplier) {
            this.scoreFunction = scoreFunction;
            this.featureSupplier = featureSupplier;
        }

        @Override
        public String toString(String field) {
            return "LtrScript:" + field;
        }


        @Override
        public Weight createWeight(IndexSearcher searcher, boolean needsScores, float boost) throws IOException {
            return new LtrScriptWeight(this);
        }


        class LtrScriptWeight extends FeatureVectorWeight {

            protected LtrScriptWeight(Query query) {
                super(query);
            }

            @Override
            public Explanation explain(LeafReaderContext context, LtrRanker.FeatureVector vector, int doc) throws IOException {
                LtrScript.this.featureSupplier.set(() -> vector);
                Scorer scorer = this.scorer(context, () -> vector);
                int newDoc = scorer.iterator().advance(doc);
                if (newDoc == doc) {
                    return Explanation.match(scorer.score(), "weight(" + this.getQuery() + " in doc " + newDoc + ")");
                }
                return Explanation.noMatch("no matching term");
            }

            @Override
            public Scorer scorer(LeafReaderContext context, Supplier<LtrRanker.FeatureVector> vectorSupplier) throws IOException {
                LtrScript.this.featureSupplier.set(vectorSupplier);
                LeafScoreFunction leafScoreFunction = scoreFunction.getLeafScoreFunction(context);
                DocIdSetIterator iterator = DocIdSetIterator.all(context.reader().maxDoc());
                return new Scorer(this) {
                    @Override
                    public int docID() {
                        return iterator.docID();
                    }

                    @Override
                    public float score() throws IOException {
                        return (float) leafScoreFunction.score(iterator.docID(), 0F);
                    }

                    @Override
                    public DocIdSetIterator iterator() {
                        return iterator;
                    }
                };
            }

            @Override
            public void extractTerms(Set<Term> terms) {
            }

            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return false;
            }
        }
    }

}