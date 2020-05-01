package com.o19s.es.termstat;

import com.o19s.es.termstat.TermStatQuery;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.lucene.Lucene;
import org.junit.After;
import org.junit.Before;

import static org.hamcrest.Matchers.equalTo;

public class TermStatQueryTests extends LuceneTestCase {
    private Directory dir;
    private IndexReader reader;
    private IndexSearcher searcher;

    // Some simple documents to index
    private final String[] docs = new String[] {
            "how now brown cow",
            "brown is the color of cows",
            "brown cow",
            "banana cows are yummy",
            "dance with monkeys and do not stop to dance",
            "break on through to the other side... break on through to the other side... break on through to the other side"
    };

    @Before
    public void setupIndex() throws Exception {
        dir = new ByteBuffersDirectory();

        try(IndexWriter indexWriter = new IndexWriter(dir, new IndexWriterConfig(Lucene.STANDARD_ANALYZER))) {
            for (int i = 0; i < docs.length; i++) {
                Document doc = new Document();
                doc.add(new Field("_id", Integer.toString(i + 1), StoredField.TYPE));
                doc.add(newTextField("text", docs[i], Field.Store.YES));
                indexWriter.addDocument(doc);
            }
        }

        reader = DirectoryReader.open(dir);
        searcher = new IndexSearcher(reader);
    }

    @After
    public void cleanup() throws Exception {
        try {
            reader.close();
        } finally {
            dir.close();
        }
    }

    public void testQuery() throws Exception {
        Query q = new TermQuery(new Term("text", "cow"));
        String expr = "one()";

        TermStatQuery tsq = new TermStatQuery(q, expr);

        // Ditching the query and seeing how far we can get. should filter/query model?
        /*
        // Basic query check, should match 2 docs
        assertThat(searcher.count(tsq), equalTo(2));
        */

        // Verify explain
        TopDocs docs = searcher.search(tsq, 4);
        Explanation explanation = searcher.explain(tsq, docs.scoreDocs[0].doc);
        assertThat(explanation.toString().trim(), equalTo("1.0 = Expression: " + expr));
    }
}
