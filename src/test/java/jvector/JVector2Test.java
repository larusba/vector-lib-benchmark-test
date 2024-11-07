//package jvector;
//
//import io.github.jbellis.jvector.disk.RandomAccessReader;
//import io.github.jbellis.jvector.disk.ReaderSupplier;
//import io.github.jbellis.jvector.disk.SimpleMappedReader;
//import io.github.jbellis.jvector.graph.GraphIndex;
//import io.github.jbellis.jvector.graph.GraphIndexBuilder;
//import io.github.jbellis.jvector.graph.GraphSearcher;
//import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
//import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
//import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
//import io.github.jbellis.jvector.graph.SearchResult;
//import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
//import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
//import io.github.jbellis.jvector.graph.similarity.ScoreFunction;
//import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
//import io.github.jbellis.jvector.pq.PQVectors;
//import io.github.jbellis.jvector.pq.ProductQuantization;
//import io.github.jbellis.jvector.util.Bits;
//import io.github.jbellis.jvector.util.ExceptionUtils;
//import io.github.jbellis.jvector.util.ExplicitThreadLocal;
//import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
//import io.github.jbellis.jvector.vector.VectorUtil;
//import io.github.jbellis.jvector.vector.types.ByteSequence;
//import io.github.jbellis.jvector.vector.types.VectorFloat;
//import io.jhdf.HdfFile;
//import io.jhdf.api.Dataset;
//import jvector.util.Hdf5Loader;
//import jvector.util.MMapReader;
//import jvector.util.SiftLoader;
//import org.apache.commons.io.FileUtils;
//import org.apache.lucene.util.hnsw.HnswGraphBuilder;
//import org.junit.Before;
//import org.junit.jupiter.api.Disabled;
//import org.junit.jupiter.api.Test;
//import utils.CustomVectorProvider;
//import utils.TestUtil;
//
//import java.io.BufferedOutputStream;
//import java.io.DataOutputStream;
//import java.io.File;
//import java.io.IOException;
//import java.io.UncheckedIOException;
//import java.net.URI;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.Duration;
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//import java.util.Random;
//import java.util.Set;
//import java.util.concurrent.ThreadLocalRandom;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.function.Function;
//import java.util.stream.IntStream;
//
//import static io.github.jbellis.jvector.graph.similarity.BuildScoreProvider.vts;
//import static java.lang.Math.min;
//import static lucene.LuceneHnswGraphTest.addToFirstPosOfArray;
//import static lucene.LuceneHnswGraphTest.getQuery;
//import static utils.TestUtil.CsvUtil.AVG_RAM;
//import static utils.TestUtil.CsvUtil.CPU_LOAD;
//import static utils.TestUtil.CsvUtil.DATASET_SIZE_KEY;
//import static utils.TestUtil.CsvUtil.ELAPSED_TIME;
//import static utils.TestUtil.CsvUtil.FILE_SIZE_KEY;
//import static utils.TestUtil.CsvUtil.QUEUE_SIZE;
//import static utils.TestUtil.CsvUtil.SINGLE_VECTOR_SIZE_KEY;
//import static utils.TestUtil.CsvUtil.TOPK_KEY;
//import static utils.TestUtil.DATASET_FOLDER;
//import static utils.TestUtil.getFileSizeInMB;
//
//public class JVector2Test {
//
//    public static final String FILENAME = "glove-25-angular.hdf5";
//    public static final VectorSimilarityFunction SIMILARITY_FUNCTION = VectorSimilarityFunction.COSINE;
//    private static final int maxConn = 14;
//    private static final int beamWidth = 5;
//    private static final long seed = HnswGraphBuilder.randSeed;
//
////    private static VectorProvider vectors;
//    private static CustomVectorProvider customVectorProvider;
//    private static final Path indexPath = Paths.get("target/luceneIndex");
//    private static float[] query = getQuery();
//
//    private List<String[]> stats = new ArrayList<>();
//    private Map<String, String> csvData = new HashMap<>();
//
//    @Before
//    public void setupIndexDir() throws IOException {
//        File file = indexPath.toFile();
//        if (file.exists()) {
//            FileUtils.deleteDirectory(file);
//        }
//        customVectorProvider = initDataset(FILENAME, csvData);
//        stats.add(new String[] { DATASET_SIZE_KEY, SINGLE_VECTOR_SIZE_KEY, FILE_SIZE_KEY, CPU_LOAD, AVG_RAM, TOPK_KEY, QUEUE_SIZE, ELAPSED_TIME });
//    }
//
//    private CustomVectorProvider initDataset(String filename, Map<String, String> csvData) {
//        URI uri = Objects.requireNonNull(Paths.get(DATASET_FOLDER + filename)).toUri();
//        try (HdfFile hdfFile = new HdfFile(Paths.get(uri))) {
//            // per recuperare il path => hdfFile.getChildren()
//            Dataset dataset = hdfFile.getDatasetByPath("/train");
//            // data will be a Java array with the dimensions of the HDF5 dataset
//            float[][] data = (float[][]) dataset.getData();
//
//            var datasetSize = data.length;
//            var vectorDimension = data[0].length;
//            var filesize = getFileSizeInMB(filename);
//
//            csvData.put(DATASET_SIZE_KEY, String.valueOf(datasetSize));
//            csvData.put(TestUtil.CsvUtil.SINGLE_VECTOR_SIZE_KEY, String.valueOf(vectorDimension));
//            csvData.put(TestUtil.CsvUtil.FILE_SIZE_KEY, String.valueOf(filesize));
//
//            addToFirstPosOfArray(data, query);
//            return new RandomAccessVectorValues(data);
//        }
//    }
//
//    @Test
//    void testJVectorHdf5InMemory() throws IOException {
//        var dataset = Hdf5Loader.load(FILENAME);
//        execInMemory((ArrayList<VectorFloat<?>>) dataset.baseVectors);
//    }
//
////    @Test
////    void testJVectorHdf5DiskANN() throws IOException {
////        var dataset = Hdf5Loader.load(FILENAME);
////        execDiskAnn(dataset.baseVectors, dataset.queryVectors, (List<Set<Integer>>) dataset.groundTruth);
////    }
//
//    @Test
//    void testJVectorSiftSmall() throws IOException {
//        var siftPath = "siftsmall";
//        var baseVectors = SiftLoader.readFvecs(String.format("%s/siftsmall_base.fvecs", siftPath));
//        var queryVectors = SiftLoader.readFvecs(String.format("%s/siftsmall_query.fvecs", siftPath));
//        var groundTruth = SiftLoader.readIvecs(String.format("%s/siftsmall_groundtruth.ivecs", siftPath));
//        System.out.format("%d base and %d query vectors loaded, dimensions %d%n",
//                baseVectors.size(), queryVectors.size(), baseVectors.get(0).length());
//
//        execInMemory(baseVectors);
//        execDiskAnn(baseVectors, queryVectors, groundTruth);
//    }
//
//    @Test
//    @Disabled()
//    void testJVectorSiftSmall1M() throws IOException {
//        // Taken from http://corpus-texmex.irisa.fr/
//        // ftp://ftp.irisa.fr/local/texmex/corpus/sift.tar.gz
//        var siftPath = "siftsmall";
//        var baseVectors = SiftLoader.readFvecs(String.format("%s/1M/sift_base.fvecs", siftPath));
//        var queryVectors = SiftLoader.readFvecs(String.format("%s/1M/sift_query.fvecs", siftPath));
//        var groundTruth = SiftLoader.readIvecs(String.format("%s/1M/sift_groundtruth.ivecs", siftPath));
//        System.out.format("%d base and %d query vectors loaded, dimensions %d%n",
//                baseVectors.size(), queryVectors.size(), baseVectors.get(0).length());
//
//        execInMemory(baseVectors);
//        execDiskAnn(baseVectors, queryVectors, groundTruth);
//    }
//
//    public static void execInMemory(ArrayList<VectorFloat<?>> baseVectors) throws IOException {
//        Instant start = Instant.now();
//        // infer the dimensionality from the first vector
//        int originalDimension = baseVectors.get(0).length();
//        // wrap the raw vectors in a RandomAccessVectorValues
//        RandomAccessVectorValues ravv = new ListRandomAccessVectorValues(baseVectors, originalDimension);
//
//        // score provider using the raw, in-memory vectors
//        BuildScoreProvider bsp = BuildScoreProvider.randomAccessScoreProvider(ravv, SIMILARITY_FUNCTION);
//        try (GraphIndexBuilder builder = new GraphIndexBuilder(bsp,
//                ravv.dimension(),
//                16, // graph degree
//                100, // construction search depth
//                1.2f, // allow degree overflow during construction by this factor
//                1.2f)) // relax neighbor diversity requirement by this factor
//        {
//            // build the index (in memory)
//            OnHeapGraphIndex index = builder.build(ravv);
//
//
//            // search for a random vector
//            VectorFloat<?> q = randomVector(originalDimension);
//            SearchResult sr = GraphSearcher.search(q,
//                    10, // number of results
//                    ravv, // vectors we're searching, used for scoring
//                    SIMILARITY_FUNCTION, // how to score
//                    index,
//                    Bits.ALL); // valid ordinals to consider
//            for (SearchResult.NodeScore ns : sr.getNodes()) {
//                System.out.println(ns);
//            }
//        }
//        Instant finish = Instant.now();
//        long timeElapsed = Duration.between(start, finish).toMillis();
//        System.out.println("In-memory test elapsed = " + timeElapsed + "ms");
//    }
//
//    // diskann-style index with PQ
//    public static void execDiskAnn(List<VectorFloat<?>> baseVectors, List<VectorFloat<?>> queryVectors, List<Set<Integer>> groundTruth) throws IOException {
//        Instant start = Instant.now();
//        int originalDimension = baseVectors.get(0).length();
//        RandomAccessVectorValues ravv = new ListRandomAccessVectorValues(baseVectors, originalDimension);
//
//        BuildScoreProvider bsp = BuildScoreProvider.randomAccessScoreProvider(ravv, SIMILARITY_FUNCTION);
//        Path indexPath = Files.createTempFile("siftsmall", ".inline");
//        try (GraphIndexBuilder builder = new GraphIndexBuilder(bsp, ravv.dimension(), 16, 100, 1.2f, 1.2f)) {
//            OnHeapGraphIndex index = builder.build(ravv);
//            OnDiskGraphIndex.write(index, ravv, indexPath);
//        }
//
//        // compute and write compressed vectors to disk
//        Path pqPath = Files.createTempFile("siftsmall", ".pq");
//        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(pqPath)))) {
//            // Compress the original vectors using PQ. this represents a compression ratio of 128 * 4 / 16 = 32x
//            ProductQuantization pq = ProductQuantization.compute(ravv,
//                    64, // number of subspaces
//                    256, // number of centroids per subspace
//                    true); // center the dataset
//            ByteSequence<?>[] compressed = pq.encodeAll(ravv);
//            // write the compressed vectors to disk
//            PQVectors pqv = new PQVectors(pq, compressed);
//            pqv.write(out);
//        }
//
//        ReaderSupplier rs = new MMapReader.Supplier(indexPath);
//        OnDiskGraphIndex index = OnDiskGraphIndex.load(rs);
//        // load the PQVectors that we just wrote to disk
//        try (RandomAccessReader in = new SimpleMappedReader(pqPath)) {
//            PQVectors pqv = PQVectors.load(in);
//            // SearchScoreProvider that does a first pass with the loaded-in-memory PQVectors,
//            // then reranks with the exact vectors that are stored on disk in the index
//            Function<VectorFloat<?>, SearchScoreProvider> sspFactory = q -> {
//                ScoreFunction.ApproximateScoreFunction asf = pqv.precomputedScoreFunctionFor(q, SIMILARITY_FUNCTION);
//                ScoreFunction.ExactScoreFunction reranker = index.getView().rerankerFor(q, SIMILARITY_FUNCTION);
//                return new SearchScoreProvider(asf, reranker);
//            };
//            // measure our recall against the (exactly computed) ground truth
//            testRecall(index, queryVectors, groundTruth, sspFactory);
//        }
//        Instant finish = Instant.now();
//        long timeElapsed = Duration.between(start, finish).toMillis();
//        System.out.println("DiskANN test elapsed = " + timeElapsed + "ms");
//    }
//
//    private static void testRecall(GraphIndex graph,
//                                   List<VectorFloat<?>> queryVectors,
//                                   List<Set<Integer>> groundTruth,
//                                   Function<VectorFloat<?>,
//                                           SearchScoreProvider> sspFactory)
//            throws IOException
//    {
//        AtomicInteger topKfound = new AtomicInteger(0);
//        int topK = 100;
//        String graphType = graph.getClass().getSimpleName();
//        try (ExplicitThreadLocal<GraphSearcher> searchers = ExplicitThreadLocal.withInitial(() -> new GraphSearcher(graph))) {
//            IntStream.range(0, queryVectors.size()).parallel().forEach(i -> {
//                VectorFloat<?> queryVector = queryVectors.get(i);
//                try (GraphSearcher searcher = searchers.get()) {
//                    SearchScoreProvider ssp = sspFactory.apply(queryVector);
//                    int rerankK = ssp.scoreFunction().isExact() ? topK : 2 * topK; // hardcoded overquery factor of 2x when reranking
//                    SearchResult.NodeScore[] nn = searcher.search(ssp, rerankK, Bits.ALL).getNodes();
//
//                    Set<Integer> gt = groundTruth.get(i);
//                    long n = IntStream.range(0, min(topK, nn.length)).filter(j -> gt.contains(nn[j].node)).count();
//                    topKfound.addAndGet((int) n);
//                } catch (IOException e) {
//                    throw new UncheckedIOException(e);
//                }
//            });
//        } catch (Exception e) {
//            ExceptionUtils.throwIoException(e);
//        }
//        System.out.printf("(%s) Recall: %.4f%n", graphType, (double) topKfound.get() / (queryVectors.size() * topK));
//    }
//
//    public static VectorFloat<?> randomVector(int dim) {
//        Random R = ThreadLocalRandom.current();
//        VectorFloat<?> vec = vts.createFloatVector(dim);
//        for (int i = 0; i < dim; i++) {
//            vec.set(i, R.nextFloat());
//            if (R.nextBoolean()) {
//                vec.set(i, -vec.get(i));
//            }
//        }
//        VectorUtil.l2normalize(vec);
//        return vec;
//    }
//}
