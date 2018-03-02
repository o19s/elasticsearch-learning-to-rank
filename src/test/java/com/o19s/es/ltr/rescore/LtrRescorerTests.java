package com.o19s.es.ltr.rescore;

import com.o19s.es.ltr.feature.PrebuiltFeature;
import com.o19s.es.ltr.feature.PrebuiltFeatureSet;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.query.Normalizer;
import com.o19s.es.ltr.query.RankerQuery;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.valuesource.FloatFieldSource;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.CheckedFunction;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LtrRescorerTests extends LuceneTestCase {
    private IndexSearcher searcher;
    private IndexReader reader;
    private List<Directory> directories;

    static final int[] SEGMENTS = new int[]{1, 10, 25, 50, 100, 200, 500, 1000};

    @Before
    public void init() throws IOException {
        directories = new ArrayList<>(SEGMENTS.length);
        int docId = 0;
        for(int s = 0; s < SEGMENTS.length; s++) {
            Directory directory = new ByteBuffersDirectory();
            directories.add(directory);
            IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig());
            for (; docId < SEGMENTS[s]; docId++) {
                writer.addDocument(buildDoc(s, docId));
            }
            writer.commit();
            writer.forceMerge(1);
            writer.close();
        }

        reader = new MultiReader(directories.stream().map((d) -> {
            try {
                return DirectoryReader.open(d);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }).toArray(IndexReader[]::new));
        searcher = new IndexSearcher(reader);
    }

    private Query newMainQuery(int[] segmentFilter, int[] docIdFilter) {
        Query scoreQuery = new FunctionQuery(new FloatFieldSource("main"));

        BooleanQuery.Builder seg = new BooleanQuery.Builder();
        Arrays.stream(segmentFilter)
                .mapToObj((s) -> new Term("segment", String.valueOf(s)))
                .map(TermQuery::new)
                .map((q) -> new BooleanClause(q, BooleanClause.Occur.SHOULD))
                .forEach(seg::add);

        BooleanQuery.Builder ids = new BooleanQuery.Builder();
        Arrays.stream(docIdFilter)
                .mapToObj((s) -> new Term("docId", String.valueOf(s)))
                .map(TermQuery::new)
                .map((q) -> new BooleanClause(q, BooleanClause.Occur.SHOULD))
                .forEach(ids::add);

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new BooleanClause(scoreQuery, BooleanClause.Occur.MUST));
        if (segmentFilter.length > 0) builder.add(new BooleanClause(seg.build(), BooleanClause.Occur.FILTER));
        if (docIdFilter.length > 0) builder.add(new BooleanClause(ids.build(), BooleanClause.Occur.FILTER));

        return builder.build();
    }

    private PrebuiltFeatureSet newFeatureSet() {
        return new PrebuiltFeatureSet("set", Arrays.asList(
                new PrebuiltFeature("feature1", new FunctionQuery(new FloatFieldSource("feat1"))),
                new PrebuiltFeature("feature2", new FunctionQuery(new FloatFieldSource("feat2")))
        ));
    }

    private RankerQuery newRankerQuery() {
        return RankerQuery.build(new PrebuiltLtrModel("mymodel", newRanker(), newFeatureSet()));
    }

    private LtrRanker newRanker() {
        return new LinearRanker(new float[]{0.5F, 0.5F});
    }

    private Document buildDoc(int seg, int docId) {
        Document doc = new Document();
        doc.add(newTextField("docId", String.valueOf(docId), Field.Store.YES));
        doc.add(newTextField("segment", String.valueOf(seg), Field.Store.YES));
        doc.add(new FloatDocValuesField("feat1", random().nextFloat()));
        doc.add(new FloatDocValuesField("feat2", random().nextFloat()));
        doc.add(new FloatDocValuesField("main", random().nextFloat()));
        return doc;
    }

    private LtrRescorer.LtrRescoreContext prepareLtrContext(int wSize) {
        LtrRescorer.LtrRescoreContext context = new LtrRescorer.LtrRescoreContext(wSize, new LtrRescorer());
        context.setQueryNormalizer(new Normalizer.IntervalNormalizer(0D, 1D, false, new Normalizer.Saturation(0.5D, 1D)));
        context.setRescoreQueryNormalizer(new Normalizer.IntervalNormalizer(1D, 2D, false, new Normalizer.Saturation(0.5D, 1D)));
        context.setScoreMode(LtrRescorer.LtrRescoreMode.Replace);
        context.setQuery(newRankerQuery());
        return context;
    }

    public void testNoDocs() throws IOException {
        LtrRescorer.LtrRescoreContext ctx = prepareLtrContext(1);
        TopDocs docs = searcher.search(newMainQuery(new int[0], new int[]{-1}), 10);
        assertEquals(0, docs.totalHits);
        TopDocs rescored = ctx.rescorer().rescore(docs, searcher, ctx);
        assertEquals(0, docs.totalHits);
        assertEquals(0, docs.scoreDocs.length);
    }

    public void testClassicSingleDocSingleSeg() throws IOException {
        LtrRescorer.LtrRescoreContext ctx = prepareLtrContext(1);
        TopDocs docs = searcher.search(newMainQuery(new int[0], new int[]{0}), 10);
        assertEquals(1, docs.totalHits);
        TopDocs rescored = ctx.rescorer().rescore(docs, searcher, ctx);
        assertEquals(rescored.scoreDocs[0].score, ctx.normalizedRescoreQueryScore(modelScore(0)), Math.ulp(rescored.scoreDocs[0].score));
    }

    public void testTwoDocsSameWindow() throws IOException {
        LtrRescorer.LtrRescoreContext ctx = prepareLtrContext(2);
        TopDocs docs = searcher.search(newMainQuery(new int[0], new int[]{0,1}), 10);
        assertEquals(2, docs.totalHits);
        TopDocs rescored = ((LtrRescorer) ctx.rescorer()).bulkRescore(docs, searcher, ctx);
        for (ScoreDoc sd : rescored.scoreDocs) {
            assertEquals(sd.score, ctx.normalizedRescoreQueryScore(modelScore(sd)), Math.ulp(sd.score));
        }

        rescored = ((LtrRescorer) ctx.rescorer()).classicRescore(docs, searcher, ctx);
        for (ScoreDoc sd : rescored.scoreDocs) {
            assertEquals(sd.score, ctx.normalizedRescoreQueryScore(modelScore(sd)), Math.ulp(sd.score));
        }
    }

    public void testTwoDocsThreeInWindow() throws IOException {
        LtrRescorer.LtrRescoreContext ctx = prepareLtrContext(2);
        List<CheckedFunction<TopDocs, TopDocs, IOException>> recorers = new ArrayList<>();
        recorers.add((docs) -> ((LtrRescorer) ctx.rescorer()).bulkRescore(docs, searcher, ctx));
        recorers.add((docs) -> ((LtrRescorer) ctx.rescorer()).classicRescore(docs, searcher, ctx));
        for (CheckedFunction<TopDocs, TopDocs, IOException> sup : recorers) {
            TopDocs docs = searcher.search(newMainQuery(new int[0], new int[]{0,1,2}), 10);
            assertEquals(3, docs.totalHits);
            TopDocs rescored = sup.apply(docs);
            assertEquals(3, rescored.totalHits);

            for (ScoreDoc sd : Arrays.copyOf(rescored.scoreDocs, 2)) {
                assertEquals(sd.score, ctx.normalizedRescoreQueryScore(modelScore(sd)), Math.ulp(sd.score));
            }

            for (ScoreDoc sd : Arrays.copyOfRange(rescored.scoreDocs, 2, 3)) {
                assertEquals(sd.score, ctx.normalizedQueryScore(mainQueryScore(sd)), Math.ulp(sd.score));
            }
        }
    }

    public void testLargeWindowCustomBulkSize() throws IOException {
        LtrRescorer.LtrRescoreContext ctx = prepareLtrContext(700);
        ctx.setBatchSize(20);
        List<CheckedFunction<TopDocs, TopDocs, IOException>> recorers = new ArrayList<>();
        recorers.add((docs) -> ((LtrRescorer) ctx.rescorer()).bulkRescore(docs, searcher, ctx));
        recorers.add((docs) -> ((LtrRescorer) ctx.rescorer()).classicRescore(docs, searcher, ctx));
        Query mainQuery = newMainQuery(new int[0], new int[0]);
        for (CheckedFunction<TopDocs, TopDocs, IOException> sup : recorers) {
            TopDocs docs = searcher.search(mainQuery, 1000);
            assertEquals(1000, docs.totalHits);
            TopDocs rescored = sup.apply(docs);
            assertEquals(1000, rescored.totalHits);

            for (ScoreDoc sd : Arrays.copyOf(rescored.scoreDocs, 700)) {
                assertEquals(sd.score, ctx.normalizedRescoreQueryScore(modelScore(sd)), Math.ulp(sd.score));
                Explanation explanation = ctx.rescorer().explain(sd.doc, searcher, ctx, searcher.explain(mainQuery, sd.doc));
                assertEquals(sd.score, explanation.getValue(), Math.ulp(sd.score));
                assertEquals(ctx.getScoreMode().toString(), explanation.getDescription());
                assertEquals("product of:", explanation.getDetails()[0].getDescription());
                assertEquals("primaryWeight", explanation.getDetails()[0].getDetails()[1].getDescription());
                assertEquals(ctx.getQueryWeight(), explanation.getDetails()[0].getDetails()[1].getValue(),
                        Math.ulp(ctx.getQueryWeight()));
                assertEquals("product of:", explanation.getDetails()[1].getDescription());
                assertEquals("secondaryWeight", explanation.getDetails()[1].getDetails()[1].getDescription());
                assertEquals(ctx.getQueryWeight(), explanation.getDetails()[1].getDetails()[1].getValue(),
                        Math.ulp(ctx.getRescoreQueryWeight()));
            }

            for (ScoreDoc sd : Arrays.copyOfRange(rescored.scoreDocs, 700, rescored.scoreDocs.length)) {
                assertEquals(sd.score, ctx.normalizedQueryScore(mainQueryScore(sd)), Math.ulp(sd.score));
            }
        }
    }

    public void testNormalization() {
        float qscore = random().nextFloat();
        float rscore = random().nextFloat();
        float qweight = random().nextFloat();
        float rweight = random().nextFloat();
        LtrRescorer.LtrRescoreContext ctx = new LtrRescorer.LtrRescoreContext(1, new LtrRescorer());
        ctx.setQueryNormalizer(Normalizer.NOOP);
        ctx.setRescoreQueryNormalizer(Normalizer.NOOP);
        ctx.setQueryWeight(qweight);
        ctx.setRescoreQueryWeight(rweight);
        assertEquals(qscore*qweight, ctx.normalizedQueryScore(qscore), Math.ulp(qscore));
        assertEquals(rscore*rweight, ctx.normalizedRescoreQueryScore(rscore), Math.ulp(rscore));

        Normalizer qnorm = new Normalizer.Logistic(random().nextFloat(), random().nextFloat());
        Normalizer rnorm = new Normalizer.Logistic(random().nextFloat(), random().nextFloat());
        ctx.setQueryNormalizer(qnorm);
        ctx.setRescoreQueryNormalizer(rnorm);
        assertEquals(qnorm.normalize(qscore)*qweight, ctx.normalizedQueryScore(qscore), Math.ulp(qscore));
        assertEquals(rnorm.normalize(rscore)*rweight, ctx.normalizedRescoreQueryScore(rscore), Math.ulp(rscore));
    }

    public void testCombine() {
        float qscore = random().nextFloat();
        float rscore = random().nextFloat();
        double qweight = random().nextDouble();
        double rweight = random().nextDouble();
        LtrRescorer.LtrRescoreContext ctx = new LtrRescorer.LtrRescoreContext(1, new LtrRescorer());
        ctx.setQueryWeight(qweight);
        ctx.setRescoreQueryWeight(rweight);
        Normalizer qnorm = new Normalizer.Logistic(random().nextDouble(), random().nextDouble());
        Normalizer rnorm = new Normalizer.Logistic(random().nextDouble(), random().nextDouble());
        ctx.setQueryNormalizer(qnorm);
        ctx.setRescoreQueryNormalizer(rnorm);
        for (LtrRescorer.LtrRescoreMode mode : LtrRescorer.LtrRescoreMode.values()) {
            ctx.setScoreMode(mode);
            float expected = (float) mode.combine(ctx.normalizedQueryScore(qscore), ctx.normalizedRescoreQueryScore(rscore));
            assertEquals(expected, ((LtrRescorer)ctx.rescorer()).combine(qscore, rscore, ctx), Math.ulp(expected));
        }
    }

    public void testWindowLarger() throws IOException {
        LtrRescorer.LtrRescoreContext ctx = prepareLtrContext(4);
        List<CheckedFunction<TopDocs, TopDocs, IOException>> recorers = new ArrayList<>();
        recorers.add((docs) -> ((LtrRescorer) ctx.rescorer()).bulkRescore(docs, searcher, ctx));
        recorers.add((docs) -> ((LtrRescorer) ctx.rescorer()).classicRescore(docs, searcher, ctx));
        for (CheckedFunction<TopDocs, TopDocs, IOException> sup : recorers) {
            TopDocs docs = searcher.search(newMainQuery(new int[0], new int[]{0,1,2}), 10);
            assertEquals(3, docs.totalHits);
            TopDocs rescored = sup.apply(docs);
            assertEquals(3, rescored.totalHits);

            for (ScoreDoc sd : rescored.scoreDocs) {
                assertEquals(sd.score, ctx.normalizedRescoreQueryScore(modelScore(sd)), Math.ulp(sd.score));
            }
        }
    }

    public float modelScore(ScoreDoc doc) throws IOException {
        return modelScore(Integer.parseInt(searcher.doc(doc.doc).get("docId")));
    }

    public float modelScore(int doc) throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new BooleanClause(new TermQuery(new Term("docId", String.valueOf(doc))), BooleanClause.Occur.FILTER));
        builder.add(new BooleanClause(newRankerQuery(), BooleanClause.Occur.MUST));
        return searcher.search(builder.build(), 1).scoreDocs[0].score;
    }

    public float mainQueryScore(ScoreDoc sd) throws IOException {
        int doc = Integer.parseInt(searcher.doc(sd.doc).get("docId"));
        return searcher.search(newMainQuery(new int[0], new int[]{doc}), 1).scoreDocs[0].score;
    }

    public void testScoreMode() {
        double q = random().nextDouble();
        double r = random().nextDouble();
        assertEquals((q+r)/2, LtrRescorer.LtrRescoreMode.Avg.combine(q, r), Math.ulp(q));
        assertEquals(q > r ? q : r, LtrRescorer.LtrRescoreMode.Max.combine(q, r), Math.ulp(q));
        assertEquals(q > r ? r : q, LtrRescorer.LtrRescoreMode.Min.combine(q, r), Math.ulp(q));
        assertEquals(q*r, LtrRescorer.LtrRescoreMode.Multiply.combine(q, r), Math.ulp(q));
        assertEquals(q+r, LtrRescorer.LtrRescoreMode.Total.combine(q, r), Math.ulp(q));
        assertEquals(r, LtrRescorer.LtrRescoreMode.Replace.combine(q, r), Math.ulp(q));

        assertEquals( "avg", LtrRescorer.LtrRescoreMode.Avg.toString());
        assertEquals( LtrRescorer.LtrRescoreMode.Avg, LtrRescorer.LtrRescoreMode.fromString("avg"));

        assertEquals( "min", LtrRescorer.LtrRescoreMode.Min.toString());
        assertEquals( LtrRescorer.LtrRescoreMode.Min, LtrRescorer.LtrRescoreMode.fromString("min"));

        assertEquals( "max", LtrRescorer.LtrRescoreMode.Max.toString());
        assertEquals( LtrRescorer.LtrRescoreMode.Max, LtrRescorer.LtrRescoreMode.fromString("max"));

        assertEquals( "total", LtrRescorer.LtrRescoreMode.Total.toString());
        assertEquals( LtrRescorer.LtrRescoreMode.Total, LtrRescorer.LtrRescoreMode.fromString("total"));

        assertEquals( "multiply", LtrRescorer.LtrRescoreMode.Multiply.toString());
        assertEquals( LtrRescorer.LtrRescoreMode.Multiply, LtrRescorer.LtrRescoreMode.fromString("multiply"));

        assertEquals( "replace", LtrRescorer.LtrRescoreMode.Replace.toString());
        assertEquals( LtrRescorer.LtrRescoreMode.Replace, LtrRescorer.LtrRescoreMode.fromString("replace"));
    }


    @After
    public void closeStuff() throws IOException {
        reader.close();
        for (Directory directory : directories) {
            directory.close();
        }
    }
}