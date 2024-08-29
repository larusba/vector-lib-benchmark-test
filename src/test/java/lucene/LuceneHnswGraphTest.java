package lucene;

import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene90.Lucene90Codec;
import org.apache.lucene.codecs.lucene90.Lucene90HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.RandomAccessVectorValues;
import org.apache.lucene.index.RandomAccessVectorValuesProducer;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.index.VectorValues;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.hnsw.HnswGraph;
import org.apache.lucene.util.hnsw.HnswGraphBuilder;
import org.apache.lucene.util.hnsw.NeighborQueue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.SplittableRandom;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;
import static org.junit.jupiter.api.Assertions.assertEquals;

// https://github.com/apache/lucene/blob/fc67d6aa6e2bf2ec8ff4b2b8e4a763f3f706de29/lucene/core/src/test/org/apache/lucene/util/hnsw/KnnGraphTester.java

public class LuceneHnswGraphTest {

    //    public static final int dim = 2;
    public static final int dim = 100;
    public static float[] query = new float[] { 0.98f, 0.01f };

    // The goal vector will be inserted into the graph which is very close to the actual query vector.
    public static final Vector2D goalVector = new Vector2D(query[0] - 0.01f, query[1] + 0.01f);
    public static final Path indexPath = Paths.get("target/index");
    public static final VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.EUCLIDEAN;
    public static final int maxConn = 14;
    public static final int beamWidth = 5;
    public static final long seed = HnswGraphBuilder.randSeed;

    private static VectorProvider vectors;
    private static CustomVectorProvider customVectorProvider;

    @BeforeAll
    public static void setupIndexDir() throws IOException, URISyntaxException {
        File file = indexPath.toFile();
        if (file.exists()) {
            FileUtils.deleteDirectory(file);
        }

        // Prepare the test data (10 entries)
        float[][] dataset = readDataset();

        // TODO: aggiungere array in prima posizione
        // public static final Vector2D goalVector = new Vector2D(query[0] - 0.01f, query[1] + 0.01f);

        customVectorProvider = new CustomVectorProvider(dataset);
    }

    @Test
    public void testWriteAndQueryIndex() throws IOException {
        Instant start = Instant.now();
        // CODE HERE
        // Persist and read the data
        try (MMapDirectory dir = new MMapDirectory(indexPath)) {

            // Write index
            int indexedDoc = writeIndex(dir, customVectorProvider);

            // Read index
            readAndQuery(dir, customVectorProvider, indexedDoc);
        }
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("Total execution time = " + timeElapsed + "ms");
    }

    @Test
    public void testSearchViaHnswGraph() throws IOException {
        Instant start = Instant.now();
        // Build the graph manually and run the query
        HnswGraphBuilder builder = new HnswGraphBuilder(customVectorProvider, similarityFunction, maxConn, beamWidth, seed);

        HnswGraph hnsw = builder.build(customVectorProvider.randomAccess());

        query = getQuery();
        // Run a search
        NeighborQueue nn = HnswGraph.search(
                query,
                10, // search result size
                5,
                customVectorProvider.randomAccess(), // ? Why do I need to specify the graph values again?
                similarityFunction, // ? Why can I specify a different similarityFunction for search. Should that not be the same that was used for graph creation?
                hnsw,
                null,
                new SplittableRandom(RandomUtils.nextLong())); // Random seed to entry vector of the search

        // Print the results
        System.out.println();
        System.out.println(String.format("Searching for NN of %s", Arrays.toString(query)));
        System.out.println("Top: " + nn.topNode() + " - score: " + nn.topScore() + " Visited: " + nn.visitedCount());
        float[] topVec = customVectorProvider.get(nn.topNode());

        System.out.println("Top vector");
        customVectorProvider.print(nn.topNode());
        System.out.println(topVec[0]);
        System.out.println("---------");
        for (int i = 0; i < nn.size(); i++) {
            int id = nn.pop();
//            Vector2D vec = vectors.get(id);
//            vec.print(id);
            customVectorProvider.print(id);
        }
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("testSearchViaHnswGraph elapsed time = " + timeElapsed + "ms");
    }

    private void readAndQuery(MMapDirectory dir, CustomVectorProvider vectorData, int indexedDoc) throws IOException {
        Instant start = Instant.now();
        try (IndexReader reader = DirectoryReader.open(dir)) {
            for (LeafReaderContext ctx : reader.leaves()) {
                VectorValues values = ctx.reader().getVectorValues("field");
                assertEquals(dim, values.dimension());
                assertEquals(indexedDoc, values.size());
                assertEquals(vectorData.size(), ctx.reader().maxDoc());
                assertEquals(vectorData.size(), ctx.reader().numDocs());
                // KnnGraphValues graphValues = ((Lucene90HnswVectorsReader) ((PerFieldKnnVectorsFormat.FieldsReader) ((CodecReader) ctx.reader())
                // .getVectorReader())
                // .getFieldReader("field"))
                // .getGraphValues("field");

                query = getQuery();

                TopDocs results = doKnnSearch(ctx.reader(), "field", query, 2, indexedDoc);
                System.out.println();
                System.out.println("Doc Based Search:");
//                System.out.println(String.format("Searching for NN of [%.2f | %.2f]", query[0], query[1]));
                System.out.println(String.format("Searching for NN of %s", Arrays.toString(query)));
                System.out.println("TotalHits: " + results.totalHits.value);
                for (int i = 0; i < results.scoreDocs.length; i++) {
                    ScoreDoc doc = results.scoreDocs[i];
                    // System.out.println("Matches: " + doc.doc + " = " + doc.score);
//                    Vector2D vec = vectorData.get(doc.doc);
//                    vec.print(doc.doc);
                    customVectorProvider.print(doc.doc);
                }
            }
        }
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("Read and Query elapsed time = " + timeElapsed + "ms");
    }

    private float[] getQuery() {
        float[] arr = new float[100];
        for (int i = 0; i < 100; i++) {
            arr[i] = new Random().nextFloat();
        }
        return arr;
    }

    private int writeIndex(MMapDirectory dir, CustomVectorProvider vectorProvider) throws IOException {
        Instant start = Instant.now();
        int indexedDoc = 0;
        IndexWriterConfig iwc = new IndexWriterConfig()
                .setCodec(
                        new Lucene90Codec() {
                            @Override
                            public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
                                return new Lucene90HnswVectorsFormat(maxConn, beamWidth);
                            }
                        });
        try (IndexWriter iw = new IndexWriter(dir, iwc)) {
            while (vectorProvider.nextDoc() != NO_MORE_DOCS) {
                while (indexedDoc < vectorProvider.docID()) {
                    // increment docId in the index by adding empty documents
                    iw.addDocument(new Document());
                    indexedDoc++;
                }
                Document doc = new Document();
                // System.out.println("Got: " + v2.vectorValue()[0] + ":" + v2.vectorValue()[1] + "@" + v2.docID());
                doc.add(new KnnVectorField("field", vectorProvider.vectorValue(), similarityFunction));
                doc.add(new StoredField("id", vectorProvider.docID()));
                iw.addDocument(doc);
                indexedDoc++;
            }
        }
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("writeIndex elapsed time = " + timeElapsed + "ms");
        return indexedDoc;
    }

    private TopDocs doKnnSearch(
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


    public static float[][] readDataset() throws URISyntaxException {
        ClassLoader classLoader = HnswGraphTest.class.getClassLoader();
//        String filename = "kosarak-jaccard.hdf5";
//        String filename = "lastfm-64-dot.hdf5";
        String filename = "glove-25-angular.hdf5";
        URI testFileUri = Objects.requireNonNull(classLoader.getResource(filename)).toURI();
        try (HdfFile hdfFile = new HdfFile(Paths.get(testFileUri))) {
            // per recuperare il path => hdfFile.getChildren()
            Dataset dataset = hdfFile.getDatasetByPath("/distances");
            // data will be a Java array with the dimensions of the HDF5 dataset
            return (float[][]) dataset.getData();
//            assertEquals(500, data.length);
        }
    }
}

class VectorMulti {
    float[][] arr;

    public VectorMulti(int row, int totalRows, float[] values) {
        if (arr == null) {
            arr = new float[totalRows][values.length];
        }
        arr[row] = values;
    }

    public float[] toArray(int row) {
        return arr[row];
    }
}

class CustomVectorProvider extends VectorValues implements RandomAccessVectorValues, RandomAccessVectorValuesProducer {

    int doc = -1;
    private final float[][] data;

    public CustomVectorProvider(float[][] data) {
        this.data = data;
    }

    public float[] get(int idx) {
        return data[idx];
    }

    @Override
    public float[] vectorValue(int i) throws IOException {
        return data[i];
    }

    @Override
    public BytesRef binaryValue(int i) throws IOException {
        return null;
    }

    @Override
    public RandomAccessVectorValues randomAccess() {
        return new CustomVectorProvider(data);
    }

    @Override
    public int dimension() {
        return data[0].length;
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public float[] vectorValue() throws IOException {
        return vectorValue(doc);
    }

    @Override
    public int docID() {
        return doc;
    }

    @Override
    public int nextDoc() throws IOException {
        return advance(doc + 1);
    }

    @Override
    public int advance(int target) throws IOException {
        if (target >= 0 && target < data.length) {
            doc = target;
        } else {
            doc = NO_MORE_DOCS;
        }
        return doc;
    }

    @Override
    public long cost() {
        return data.length;
    }


    public void print(int ord) throws IOException {
        System.out.println(Arrays.toString(vectorValue(ord)) + " => " + Arrays.toString(data[ord]));
    }
}
