package util;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Bits;

import java.io.IOException;

public class LuceneUtil {

    public static TopDocs doKnnSearch(
            IndexReader reader, String field, float[] vector, int docLimit, int fanout) throws IOException {
        TopDocs[] results = new TopDocs[reader.leaves().size()];
        for (LeafReaderContext ctx : reader.leaves()) {
            Bits liveDocs = ctx.reader().getLiveDocs();
            results[ctx.ord] = ctx.reader().searchNearestVectors(field, vector, docLimit + fanout, liveDocs);
            int docBase = ctx.docBase;
            for (ScoreDoc scoreDoc : results[ctx.ord].scoreDocs) {
                scoreDoc.doc += docBase;
            }
        }
        return TopDocs.merge(docLimit, results);
    }

}
