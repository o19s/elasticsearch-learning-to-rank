/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.o19s.es.explore;

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
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.lucene.Lucene;
import org.junit.After;
import org.junit.Before;

import static org.hamcrest.Matchers.equalTo;

public class ExplorerQueryTests extends LuceneTestCase {
    private Directory dir;
    private IndexReader reader;
    private IndexSearcher searcher;

    // Some simple documents to index
    private final String[] docs = new String[] {
            "how now brown cow",
            "brown is the color of cows",
            "brown cow",
            "banana cows are yummy"
    };

    @Before
    public void setupIndex() throws Exception {
        dir = new RAMDirectory();
        IndexWriter indexWriter = new IndexWriter(dir, new IndexWriterConfig(Lucene.STANDARD_ANALYZER));

        for(int i = 0; i < docs.length; i++) {
            Document doc = new Document();
            doc.add(new Field("_id", Integer.toString(i + 1), StoredField.TYPE));
            doc.add(newTextField("text", docs[i], Field.Store.YES));
            indexWriter.addDocument(doc);
        }

        indexWriter.close();

        reader = DirectoryReader.open(dir);
        searcher = new IndexSearcher(reader);
    }

    @After
    public void cleanup() throws Exception {
        reader.close();
        dir.close();
    }

    public void testQuery() throws Exception {
        Query q = new TermQuery(new Term("text", "cow"));
        String field = "text";
        String statsType = "max_raw_tf";

        ExplorerQuery eq = new ExplorerQuery(q, field, statsType);

        // Basic query check, should match 2 docs
        assertThat(searcher.count(eq), equalTo(2));

        // Verify explain
        TopDocs docs = searcher.search(eq, 4);
        Explanation explanation = searcher.explain(eq, docs.scoreDocs[0].doc);
        assertThat(explanation.toString().trim(), equalTo("1.0 = Stat Score: sum_raw_tf of text"));
    }
}
