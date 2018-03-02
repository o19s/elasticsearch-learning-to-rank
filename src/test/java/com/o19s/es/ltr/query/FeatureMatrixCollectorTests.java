package com.o19s.es.ltr.query;

import com.o19s.es.ltr.feature.PrebuiltFeature;
import com.o19s.es.ltr.feature.PrebuiltFeatureSet;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.ranker.DenseFeatureMatrix;
import com.o19s.es.ltr.ranker.FeatureMatrix;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import org.apache.commons.codec.Charsets;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FeatureMatrixCollectorTests extends LuceneTestCase {

    private IndexSearcher searcher;
    private RandomIndexWriter writer;
    private IndexReader reader;
    private Directory directory;
    private List<String> queries;

    @Before
    public void init() throws IOException {
        directory = new ByteBuffersDirectory();
        writer = new RandomIndexWriter(random(), directory);
        queries = new ArrayList<>();
        Function<String, Document> docBuilder = (data) -> {
            Document doc = new Document();
            doc.add(newTextField("txt", data, Field.Store.NO));
            if (random().nextInt(4) == 2) {
                String query = Arrays.stream(data.split(" +", 300))
                        .filter((s) -> random().nextInt(4) == 2)
                        .limit(10)
                        .collect(Collectors.joining(" "));
                if (!query.isEmpty()) {
                    queries.add(query);
                }
            }
            return doc;
        };

        try (InputStream res = this.getClass().getResourceAsStream("LouiseMichel.txt")) {
            List<Document> docs = new BufferedReader(new InputStreamReader(res, Charsets.UTF_8)).lines().map(docBuilder)
                    .collect(Collectors.toList());
            writer.addDocuments(docs);
        }
        writer.commit();
        writer.forceMerge(random().nextInt(10) + 1);

        reader = writer.getReader();
        searcher = newSearcher(reader);

    }

    public void testEmptyInitial() throws IOException {
        ScoreDoc[] docs = new ScoreDoc[0];
        TopDocs tdocs = new TopDocs(0, docs, 0F);
        RankerQuery query = RankerQuery.build(new PrebuiltLtrModel("name", new LinearRanker(new float[0]),
                new PrebuiltFeatureSet("set", Collections.emptyList())));
        FeatureMatrixCollector collector = new FeatureMatrixCollector((RankerQuery.RankerWeight) query.createWeight(searcher, true, 1F),
                searcher.getIndexReader(), tdocs.scoreDocs, 0);
        FeatureMatrix matrix = new DenseFeatureMatrix(10,0);
        assertEquals(0, collector.collect(matrix));
    }

    public void testRandom() throws IOException {
        int nFeat = random().nextInt(500) + 100;
        List<Query> features = new ArrayList<>(nFeat);
        QueryBuilder qb = new QueryBuilder(new StandardAnalyzer());
        Supplier<Query> randomQuery = () -> {
            Query q = qb.createBooleanQuery("txt", this.queries.get(random().nextInt(this.queries.size())));
            return q != null ? q : new BoostQuery(new ConstantScoreQuery(new MatchAllDocsQuery()), random().nextFloat());
        };
        for (int i = 0; i < nFeat; i++) {
            features.add(randomQuery.get());
        }
        List<PrebuiltFeature> pfeatures = new ArrayList<>();
        for (int i = 0; i < features.size(); i++) {
            pfeatures.add(new PrebuiltFeature("feature"+i, features.get(i)));
        }
        PrebuiltLtrModel model = new PrebuiltLtrModel("test",
                new LinearRanker(new float[features.size()]), new PrebuiltFeatureSet("set", pfeatures));

        Query mainQuery = randomQuery.get();
        TopDocs docs = searcher.search(mainQuery, random().nextInt(1000) + 1);
        int windowSize = Math.min(docs.scoreDocs.length, random().nextInt(500) + 1);

        Arrays.sort(docs.scoreDocs, Comparator.comparingInt(a -> a.doc));
        RankerQuery.RankerWeight weight = (RankerQuery.RankerWeight) RankerQuery.build(model).createWeight(searcher, true, 1F);
        FeatureMatrixCollector collector = new FeatureMatrixCollector(weight, searcher.getIndexReader(), docs.scoreDocs, windowSize);
        Map<Integer, float[]> rawMatrix = new HashMap<>();


        int chunkSize = random().nextInt(150) + 1;
        DenseFeatureMatrix matrix = new DenseFeatureMatrix(chunkSize, nFeat);;
        for (int i = 0; i < windowSize; ) {
            matrix.reset();
            int collected = collector.collect(matrix);
            for (int j = 0; j < collected; j++) {
                float[] vector = new float[nFeat];
                for (int jj = 0; jj < nFeat; jj++) {
                    vector[jj] = matrix.getFeatureScoreForDoc(j, jj);
                }
                if ( docs.scoreDocs[i + j].doc == 197 ) {
                    //System.out.println("Copying");
                }
                rawMatrix.put(docs.scoreDocs[i + j].doc, vector);
            }
            i += collected;
        }

        for (int f = 0; f < nFeat; f++) {
            Query q = features.get(f);
            Set<Integer> seen = new HashSet<>();
            int featureIdx = f;
            searcher.search(q, new SimpleCollector() {
                int base;
                Scorer scorer;

                @Override
                public void collect(int doc) throws IOException {
                    int globDoc = doc+base;
                    if (rawMatrix.containsKey(globDoc)) {
                        float score = this.scorer.score();
                        assertEquals(score, rawMatrix.get(globDoc)[featureIdx], Math.ulp(score));
                    }
                    seen.add(globDoc);
                }

                @Override
                protected void doSetNextReader(LeafReaderContext context) throws IOException {
                    base = context.docBase;
                }

                @Override
                public void setScorer(Scorer scorer) throws IOException {
                    this.scorer = scorer;
                }

                @Override
                public boolean needsScores() {
                    return true;
                }
            });
            Arrays.stream(docs.scoreDocs).limit(windowSize).filter((sd) -> !seen.contains(sd.doc)).forEach((sd) -> {
                Assert.assertEquals(0.0F, rawMatrix.get(sd.doc)[featureIdx], Math.ulp(0F));
            });
        }
    }

    @After
    public void closeStuff() throws IOException {
        reader.close();
        writer.close();
        directory.close();
    }
}
