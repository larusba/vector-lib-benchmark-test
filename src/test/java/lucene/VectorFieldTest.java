package lucene;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Random;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;

import org.apache.lucene.search.KnnVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/** See also https://github.com/mocobeta/lucene-solr-mirror/tree/jira/LUCENE-9004-aknn-2 */
public class VectorFieldTest {
    public static void main(String[] args) {
        String indexDir = "/tmp/vector-field";
        try {
            //cleanUp(indexDir);

            Directory dir = FSDirectory.open(Paths.get(indexDir));
            IndexWriterConfig config = new IndexWriterConfig();
            config.setUseCompoundFile(false);
            IndexWriter writer = new IndexWriter(dir, config);
            int numDimensions = 100;  // the number of dimensions of vector values
            int numDocs = 100000;     // the number of docs

            // indexing vectors
            long _start = System.currentTimeMillis();
            for (int i = 0; i < numDocs; i++) {
                Document doc = new Document();
                // add a vector field (with randomly generated vector value)
                // here, the Manhattan distance is used for similarity calculation
                doc.add(new KnnVectorField("vector", generateRandomVector(numDimensions), VectorSimilarityFunction.EUCLIDEAN));
                writer.addDocument(doc);
            }
            long _end = System.currentTimeMillis();
            System.out.println(numDocs + " docs were written. Num dims=" + numDimensions + ", Elapsed=" + (_end - _start) + " msec, RamBytesUsed=" + writer.ramBytesUsed());
            writer.close();

            // searching vectors
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            // query vector; this must have the same dimensions to indexed vectors
            float[] queryVector = generateRandomVector(numDimensions);
            // Knn graph query

            KnnVectorQuery vectorQuery = new KnnVectorQuery("vector", queryVector, 2);
//            KnnGraphQuery query = new KnnGraphQuery("vector", queryVector, KnnGraphQuery.DEFAULT_EF, reader);
            System.out.println("Query: " + Arrays.toString(queryVector) + "\n RamBytesUsed=");
            long _start2 = System.currentTimeMillis();
            // executes the query and collects top 5 results (same as ordinary Lucene query)
            TopDocs hits = searcher.search(vectorQuery, 5);
            long _end2 = System.currentTimeMillis();
            System.out.println("Total hits: " + hits.totalHits + " (elapsed: " + (_end2 - _start2) + " msec)");
            int rank = 0;
            // show result documents with scores
            for (ScoreDoc sd : hits.scoreDocs) {
                System.out.println("Rank " + ++rank + ": doc=" + sd.doc +  " score=" + sd.score);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void cleanUp(String dir) throws IOException {
        Path path = Paths.get(dir);
        if (Files.exists(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    static final Random random = new Random(System.currentTimeMillis());
    private static float[] generateRandomVector(int numDims) {
        float[] vector = new float[numDims];
        for (int i = 0; i < numDims; i++) {
            vector[i] = random.nextFloat();
        }
        return vector;
    }
}
