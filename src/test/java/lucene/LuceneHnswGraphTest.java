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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.CustomVectorProvider;
import utils.MonitoringTask;
import utils.TestUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.Timer;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static utils.TestUtil.CsvUtil.AVG_RAM;
import static utils.TestUtil.CsvUtil.CPU_LOAD;
import static utils.TestUtil.CsvUtil.DATASET_SIZE_KEY;
import static utils.TestUtil.CsvUtil.ELAPSED_TIME;
import static utils.TestUtil.CsvUtil.FILE_SIZE_KEY;
import static utils.TestUtil.CsvUtil.QUEUE_SIZE;
import static utils.TestUtil.CsvUtil.SINGLE_VECTOR_SIZE_KEY;
import static utils.TestUtil.CsvUtil.TOPK_KEY;
import static utils.TestUtil.DATASET_FOLDER;
import static utils.TestUtil.OP_CPU_LOAD;
import static utils.TestUtil.OP_RAM_USAGE;
import static utils.TestUtil.deleteAllTempFiles;
import static utils.TestUtil.getFileSizeInMB;
import static utils.TestUtil.readStat;
import static utils.TestUtil.trackTimeElapsed;
import static utils.TestUtil.writeCSV;

// https://github.com/apache/lucene/blob/fc67d6aa6e2bf2ec8ff4b2b8e4a763f3f706de29/lucene/core/src/test/org/apache/lucene/util/hnsw/KnnGraphTester.java

public class LuceneHnswGraphTest {

    private static final int dim = 100;
    public static final String FILENAME = "glove-100-angular.hdf5";
//    public static final String FILENAME = "glove-25-angular.hdf5";
    private static float[] query = getQuery();
    private static final int TOPK = 10;

    private static final Path indexPath = Paths.get("index-lucene-tre");
//    private static final Path indexPath = Paths.get("index-kevin/idx");
    private static final VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.EUCLIDEAN;
    private static final int maxConn = 14;
    private static final int beamWidth = 5;
    private static final long seed = HnswGraphBuilder.randSeed;

    private static VectorProvider vectors;
    private static CustomVectorProvider customVectorProvider;

    private List<String[]> stats = new ArrayList<>();
    private Map<String, String> csvData = new HashMap<>();

    @BeforeEach
    public void setupIndexDir() throws IOException {
        File file = indexPath.toFile();
        if (file.exists()) {
            FileUtils.deleteDirectory(file);
        }
        customVectorProvider = initDataset(FILENAME, csvData);
        stats.add(new String[] { DATASET_SIZE_KEY, SINGLE_VECTOR_SIZE_KEY, FILE_SIZE_KEY, CPU_LOAD, AVG_RAM, TOPK_KEY, QUEUE_SIZE, ELAPSED_TIME });
    }

    @Test
    public void testWriteAndQueryIndex() throws IOException {
        Instant start = Instant.now();
        // CODE HERE
        // Persist and read the data
        try (MMapDirectory dir = new MMapDirectory(indexPath)) {

            // Write index
            int indexedDoc = writeIndex(dir, customVectorProvider);

            System.out.println("indexedDoc = " + indexedDoc);

            // Read index
//            readAndQuery(dir, customVectorProvider, indexedDoc);
        }
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("Total execution time = " + timeElapsed + "ms");
    }

    @Test
    public void testSearchViaHnswGraph() throws IOException, InterruptedException {
        new Timer().scheduleAtFixedRate(new MonitoringTask(), 0, 300);

        Instant start = Instant.now();
    public void testSearchViaHnswGraph() throws IOException {
        Instant start = Instant.now();
        // Build the graph manually and run the query
        HnswGraphBuilder builder = new HnswGraphBuilder(customVectorProvider, similarityFunction, maxConn, beamWidth, seed);

        HnswGraph hnsw = builder.build(customVectorProvider.randomAccess());

        int queueSize = 5;

        // Run a search
        NeighborQueue nn = HnswGraph.search(
                query,
                TOPK, // search result size
                queueSize,
                customVectorProvider.randomAccess(), // ? Why do I need to specify the graph values again?
                similarityFunction, // ? Why can I specify a different similarityFunction for search. Should that not be the same that was used for graph creation?
                hnsw,
                null,
                new SplittableRandom(RandomUtils.nextLong())); // Random seed to entry vector of the search

        // Print the results
        printResults(nn, customVectorProvider, query);

        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("testSearchViaHnswGraph elapsed time = " + timeElapsed + "ms");

        // Write CSV
        String cpuload = String.valueOf(readStat(OP_CPU_LOAD));
        String ramusage = String.valueOf(readStat(OP_RAM_USAGE) / 1024);

        String[] line = new String[] {
                csvData.get(DATASET_SIZE_KEY),
                csvData.get(SINGLE_VECTOR_SIZE_KEY),
                csvData.get(FILE_SIZE_KEY),
                cpuload,
                ramusage,
                String.valueOf(TOPK),
                String.valueOf(queueSize),
                String.valueOf(timeElapsed)
        };

        stats.add(line);
        writeCSV(stats, "lucene-hnsw-results.csv");
        deleteAllTempFiles();
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
//                assertEquals(indexedDoc, values.size());
//                assertEquals(vectorData.size(), ctx.reader().maxDoc());
//                assertEquals(vectorData.size(), ctx.reader().numDocs());
                // KnnGraphValues graphValues = ((Lucene90HnswVectorsReader) ((PerFieldKnnVectorsFormat.FieldsReader) ((CodecReader) ctx.reader())
                // .getVectorReader())
                // .getFieldReader("field"))
                // .getGraphValues("field");

                TopDocs results = doKnnSearch(ctx.reader(), "field", query, 2, indexedDoc);
                System.out.println();
                System.out.println("Doc Based Search:");
                System.out.println(String.format("Searching for NN of %s", Arrays.toString(query)));
                System.out.println("TotalHits: " + results.totalHits.value);
                for (int i = 0; i < results.scoreDocs.length; i++) {
                    ScoreDoc doc = results.scoreDocs[i];
                     System.out.println("Matches: " + doc.doc + " = " + doc.score);
                    customVectorProvider.print(doc.doc);
                }
            }
        }
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("Read and Query elapsed time = " + timeElapsed + "ms");
    }

    private static void printResults(NeighborQueue nn, CustomVectorProvider customVectorProvider, float[] query) {
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
            customVectorProvider.print(id);
        }
        System.out.println("Read and Query elapsed time = " + timeElapsed + "ms");
    }

    public static float[] getQuery() {
        float[] arr = new float[dim];
        for (int i = 0; i < dim; i++) {
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
        System.out.println("writeIndex elapsed time = " + trackTimeElapsed(start, finish) + "ms");
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


    public static float[][] readDataset() {
        /*
         * caratteristiche dataset
         * numero vettori,
         * dimensione vettori,
         * grandezza in MB (opzionale)
         */
        String filename = FILENAME;
        URI uri = Objects.requireNonNull(Paths.get(DATASET_FOLDER + filename)).toUri();
        try (HdfFile hdfFile = new HdfFile(Paths.get(uri))) {
            // per recuperare il path => hdfFile.getChildren()
            Dataset dataset = hdfFile.getDatasetByPath("/train");
            // data will be a Java array with the dimensions of the HDF5 dataset
            return (float[][]) dataset.getData();
        }
    }

    public static float[][] readDataset(String filename) {
        URI uri = Objects.requireNonNull(Paths.get(DATASET_FOLDER + filename)).toUri();
        try (HdfFile hdfFile = new HdfFile(Paths.get(uri))) {
            // per recuperare il path => hdfFile.getChildren()
            Dataset dataset = hdfFile.getDatasetByPath("/train");
            // data will be a Java array with the dimensions of the HDF5 dataset
            return (float[][]) dataset.getData();
        }
    }

    public static <T> T[] addToFirstPosOfArray(T[] elements, T element) {
        T[] newArray = Arrays.copyOf(elements, elements.length + 1);
        newArray[0] = element;
        System.arraycopy(elements, 0, newArray, 1, elements.length);

        return newArray;
    }

    private CustomVectorProvider initDataset(String filename, Map<String, String> csvData) {
        URI uri = Objects.requireNonNull(Paths.get(DATASET_FOLDER + filename)).toUri();
        try (HdfFile hdfFile = new HdfFile(Paths.get(uri))) {
            // per recuperare il path => hdfFile.getChildren()
            Dataset dataset = hdfFile.getDatasetByPath("/train");
            // data will be a Java array with the dimensions of the HDF5 dataset
            float[][] data = (float[][]) dataset.getData();

            var datasetSize = data.length;
            var vectorDimension = data[0].length;
            var filesize = getFileSizeInMB(filename);

            csvData.put(DATASET_SIZE_KEY, String.valueOf(datasetSize));
            csvData.put(TestUtil.CsvUtil.SINGLE_VECTOR_SIZE_KEY, String.valueOf(vectorDimension));
            csvData.put(TestUtil.CsvUtil.FILE_SIZE_KEY, String.valueOf(filesize));

            addToFirstPosOfArray(data, query);
            return new CustomVectorProvider(data);
        }
    }
}


