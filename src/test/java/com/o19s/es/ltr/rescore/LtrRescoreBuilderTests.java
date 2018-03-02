package com.o19s.es.ltr.rescore;

import com.o19s.es.ltr.LtrQueryParserPlugin;
import com.o19s.es.ltr.feature.PrebuiltFeatureSet;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.MemStore;
import com.o19s.es.ltr.query.Normalizer;
import com.o19s.es.ltr.query.RankerQuery;
import com.o19s.es.ltr.query.StoredLtrQueryBuilder;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import com.o19s.es.ltr.utils.FeatureStoreLoader;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.NamedWriteableAwareStreamInput;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.rescore.RescorerBuilder;
import org.elasticsearch.test.AbstractBuilderTestCase;
import org.hamcrest.CoreMatchers;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import static com.o19s.es.ltr.LtrTestUtils.wrapMemStore;

public class LtrRescoreBuilderTests extends AbstractBuilderTestCase {
    private static final MemStore STORE = new MemStore("mem");

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(TestPlugin.class);
    }

    @BeforeClass
    public static void init() {
        STORE.add(new CompiledLtrModel("foo", new PrebuiltFeatureSet("set", new ArrayList<>()), new LinearRanker(new float[0])));
    }

    @Override
    protected NamedXContentRegistry xContentRegistry() {
        List<NamedXContentRegistry.Entry> entries = new ArrayList<>(Normalizer.getNamedXContent());
        entries.add(new NamedXContentRegistry.Entry(QueryBuilder.class, new ParseField(StoredLtrQueryBuilder.NAME),
                (p) -> StoredLtrQueryBuilder.fromXContent(wrapMemStore(STORE), p)));
        entries.add(new NamedXContentRegistry.Entry(RescorerBuilder.class,
                LtrRescoreBuilder.NAME, LtrRescoreBuilder::parse));
        return new NamedXContentRegistry(entries);
    }

    @Override
    protected NamedWriteableRegistry namedWriteableRegistry() {
        List<NamedWriteableRegistry.Entry> entries = new ArrayList<>(Normalizer.getNamedWriteables());
        entries.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, StoredLtrQueryBuilder.NAME,
                (sr) -> new StoredLtrQueryBuilder(wrapMemStore(STORE), sr)));
        entries.add(new NamedWriteableRegistry.Entry(RescorerBuilder.class,
                LtrRescoreBuilder.NAME.getPreferredName(), LtrRescoreBuilder::new));
        return new NamedWriteableRegistry(entries);
    }

    public void testToContext() throws IOException {
        Supplier<LtrRescoreBuilder> supplier = () -> new LtrRescoreBuilder()
                .setQuery(new StoredLtrQueryBuilder(wrapMemStore(STORE)).modelName("foo").params(new HashMap<>()))
                .setQueryNormalizer(Normalizer.NOOP)
                .setRescoreQueryNormalizer(new Normalizer.Logistic(1D, 2D))
                .setQueryWeight(0.3D)
                .setRescoreQueryWeight(0.4D)
                .setScoreMode(LtrRescorer.LtrRescoreMode.Avg)
                .setScoringBatchSize(4)
                .windowSize(25);
        LtrRescorer.LtrRescoreContext context = (LtrRescorer.LtrRescoreContext) supplier.get().buildContext(createShardContext());
        assertEquals(Normalizer.NOOP, context.getQueryNormalizer());
        assertEquals(new Normalizer.Logistic(1D, 2D), context.getRescoreQueryNormalizer());
        assertEquals(0.3D, context.getQueryWeight(), Math.ulp(0.3D));
        assertEquals(0.4D, context.getRescoreQueryWeight(), Math.ulp(0.4D));
        assertEquals(LtrRescorer.LtrRescoreMode.Avg, context.getScoreMode());
        assertEquals(4, context.getBatchSize());
        assertEquals(25, context.getWindowSize());
        assertThat(context.getQuery(), CoreMatchers.instanceOf(RankerQuery.class));
        assertEquals("linear", ((RankerQuery) context.getQuery()).getRanker().name());
    }

    public void testSerialization() throws IOException {
        Supplier<LtrRescoreBuilder> supplier = () -> new LtrRescoreBuilder()
                .setQuery(new StoredLtrQueryBuilder(wrapMemStore(STORE)).modelName("foo").params(new HashMap<>()))
                .setQueryNormalizer(Normalizer.NOOP)
                .setRescoreQueryNormalizer(Normalizer.NOOP)
                .setQueryWeight(0.3D)
                .setRescoreQueryWeight(0.4D)
                .setScoreMode(LtrRescorer.LtrRescoreMode.Avg)
                .setScoringBatchSize(4)
                .windowSize(25);
        BytesStreamOutput output = new BytesStreamOutput();
        LtrRescoreBuilder original = supplier.get();
        original.writeTo(output);
        LtrRescoreBuilder builder = new LtrRescoreBuilder(new NamedWriteableAwareStreamInput(output.bytes().streamInput(),
                namedWriteableRegistry()));
        assertEquals(original, builder);
    }


    public void testDefaults() throws IOException {
        String json = "{" +
                "\"ltr_rescore\":{" +
                "   \"ltr_query\": {\"sltr\": {\"model\":\"foo\", \"params\": {}}}" +
                "}}";
        LtrRescoreBuilder builder = parse(json);
        assertEquals(Normalizer.NOOP, builder.getQueryNormalizer());
        assertEquals(Normalizer.NOOP, builder.getRescoreQueryNormalizer());
        assertNull(builder.windowSize());
        assertEquals(1D, builder.getQueryWeight(), Math.ulp(1D));
        assertEquals(1D, builder.getRescoreQueryWeight(), Math.ulp(1D));
        assertEquals(-1, builder.getScoringBatchSize());
        assertEquals(LtrRescorer.LtrRescoreMode.Total, builder.getScoreMode());
        assertEquals(new StoredLtrQueryBuilder(wrapMemStore(STORE)).modelName("foo").params(new HashMap<>()),
                builder.getQuery());
        assertNull(builder.windowSize());
    }

    public void testParse() throws IOException {
        String json = "{" +
                "\"window_size\":2," +
                "\"ltr_rescore\":{" +
                "\"query_normalizer\": {\"minmax\":{\"min\": -0.5, \"max\":0.5}}," +
                "\"rescore_query_normalizer\": {\"saturation\":{\"k\": 2.3, \"a\":0.8}}," +
                "\"query_weight\": 2.3," +
                "\"rescore_query_weight\": 2.5," +
                "\"ltr_query\": {\"sltr\": {\"model\":\"foo\", \"params\": {}}}," +
                "\"scoring_batch_size\": 23," +
                "\"score_mode\": \"avg\"" +
                "}}";
        LtrRescoreBuilder builder = parse(json);
        assertEquals(new Normalizer.MinMax(-0.5D, 0.5D), builder.getQueryNormalizer());
        assertEquals(new Normalizer.Saturation(2.3D, 0.8D), builder.getRescoreQueryNormalizer());
        assertEquals(2.3D, builder.getQueryWeight(), Math.ulp(2.3D));
        assertEquals(2.5D, builder.getRescoreQueryWeight(), Math.ulp(2.5D));
        assertEquals(23, builder.getScoringBatchSize());
        assertEquals(LtrRescorer.LtrRescoreMode.Avg, builder.getScoreMode());
        assertEquals(new StoredLtrQueryBuilder(wrapMemStore(STORE)).modelName("foo").params(new HashMap<>()),
                builder.getQuery());
        assertEquals(Integer.valueOf(2), builder.windowSize());
        LtrRescoreBuilder reparsed = parse(Strings.toString(builder));
        assertEquals(builder, reparsed);
    }

    public void testEquals() {
        Supplier<LtrRescoreBuilder> supplier = () -> new LtrRescoreBuilder()
                .setQuery(new StoredLtrQueryBuilder(wrapMemStore(STORE)).modelName("foo").params(new HashMap<>()))
                .setQueryNormalizer(Normalizer.NOOP)
                .setRescoreQueryNormalizer(Normalizer.NOOP)
                .setQueryWeight(0.3D)
                .setRescoreQueryWeight(0.4D)
                .setScoreMode(LtrRescorer.LtrRescoreMode.Avg)
                .setScoringBatchSize(4)
                .windowSize(12);

        assertEquals(supplier.get(), supplier.get());
        assertEquals(supplier.get().hashCode(), supplier.get().hashCode());

        assertNotEquals(supplier.get()
                .setQuery(new StoredLtrQueryBuilder(wrapMemStore(STORE))
                        .modelName("moo").params(new HashMap<>())), supplier.get());
        assertNotEquals(supplier.get().setQueryNormalizer(new Normalizer.Saturation(1D, 2D)), supplier.get());
        assertNotEquals(supplier.get().setRescoreQueryNormalizer(new Normalizer.Saturation(1D, 2D)), supplier.get());
        assertNotEquals(supplier.get().setQueryWeight(1D), supplier.get());
        assertNotEquals(supplier.get().setRescoreQueryWeight(1D), supplier.get());
        assertNotEquals(supplier.get().setScoreMode(LtrRescorer.LtrRescoreMode.Total), supplier.get());
        assertNotEquals(supplier.get().setScoringBatchSize(1), supplier.get());
        assertNotEquals(supplier.get().windowSize(21), supplier.get());
    }

    public void testRewrite() throws IOException {
        Supplier<LtrRescoreBuilder> supplier = () -> new LtrRescoreBuilder()
                .setQuery(new WrapperQueryBuilder(new StoredLtrQueryBuilder(wrapMemStore(STORE))
                        .modelName("foo").params(new HashMap<>()).toString()))
                .setQueryNormalizer(new Normalizer.Saturation(randomDouble()+Double.MIN_VALUE, randomDouble()+Double.MIN_VALUE))
                .setRescoreQueryNormalizer(new Normalizer.Logistic(randomDouble()+Double.MIN_VALUE, randomDouble()))
                .setQueryWeight(randomDouble())
                .setRescoreQueryWeight(randomDouble())
                .setScoreMode(randomFrom(LtrRescorer.LtrRescoreMode.values()))
                .setScoringBatchSize(randomInt(100))
                .windowSize(randomInt(100));
        LtrRescoreBuilder original = supplier.get();
        LtrRescoreBuilder rewritten = (LtrRescoreBuilder) original.rewrite(createShardContext());
        assertNotSame(original, rewritten);
        assertNotSame(original.getQuery(), rewritten.getQuery());
        assertSame(original.getQueryNormalizer(), rewritten.getQueryNormalizer());
        assertSame(original.getRescoreQueryNormalizer(), rewritten.getRescoreQueryNormalizer());
        assertSame(original.getScoreMode(), rewritten.getScoreMode());
        assertEquals(original.getScoringBatchSize(), rewritten.getScoringBatchSize());
        assertEquals(original.windowSize(), rewritten.windowSize());
        assertEquals(original.getQueryWeight(), rewritten.getQueryWeight(), Math.ulp(original.getQueryWeight()));
        assertEquals(original.getRescoreQueryWeight(), rewritten.getRescoreQueryWeight(), Math.ulp(original.getRescoreQueryWeight()));
    }

    private LtrRescoreBuilder parse(String json) throws IOException {
        XContentParser parser = JsonXContent.jsonXContent
                .createParser(xContentRegistry(), DeprecationHandler.THROW_UNSUPPORTED_OPERATION, json);
        // Consume the first START_OBJECT...
        assertSame(parser.nextToken(), XContentParser.Token.START_OBJECT);
        return (LtrRescoreBuilder) RescorerBuilder.parseFromXContent(parser);
    }

    public static class TestPlugin extends LtrQueryParserPlugin {
        public TestPlugin(Settings settings) {
            super(settings);
        }

        @Override
        protected FeatureStoreLoader getFeatureStoreLoader() {
            return wrapMemStore(STORE);
        }
    }
}