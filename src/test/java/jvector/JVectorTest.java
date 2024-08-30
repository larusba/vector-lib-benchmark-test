package jvector;

import com.indeed.util.mmap.MMapBuffer;
import io.github.jbellis.jvector.disk.RandomAccessReader;
import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.graph.GraphIndex;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.graph.disk.CachingGraphIndex;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.util.ExceptionUtils;
import io.github.jbellis.jvector.util.ExplicitThreadLocal;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorUtil;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import jvector.util.Hdf5Loader;
import jvector.util.MMapReader;
import jvector.util.SiftLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
//import org.junit.Before;
//import org.junit.jupiter.api.Disabled;
//import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import static io.github.jbellis.jvector.graph.similarity.BuildScoreProvider.vts;
import static java.lang.Math.min;

public class JVectorTest {

    public static final String FILENAME = "glove-100-angular.hdf5";
    public static final VectorSimilarityFunction SIMILARITY_FUNCTION = VectorSimilarityFunction.COSINE;
    private static final Path GRAPH_FILE = Path.of("test");

    @BeforeEach
    public void beforeSet() {
        System.setProperty(
                "jvector.physical_core_count",
                Integer.toString(Runtime.getRuntime().availableProcessors()));
    }
    
    public static final Path indexPath = Paths.get("target/index-jvector-2");
    
    @Test
    public void testJVectorHdf5InMemory() throws IOException {
        var dataset = Hdf5Loader.load(FILENAME);
        execInMemory((ArrayList<VectorFloat<?>>) dataset.baseVectors);
    }

    @Test
    public void testJVectorHdf5DiskANN() throws IOException {
        var dataset = Hdf5Loader.load(FILENAME);
        execDiskAnn(dataset.baseVectors, dataset.queryVectors, (List<Set<Integer>>) dataset.groundTruth);
    }

    @Test
    public void testJVectorSiftSmall() throws IOException {
        var siftPath = "siftsmall";
        var baseVectors = SiftLoader.readFvecs(String.format("%s/siftsmall_base.fvecs", siftPath));
        var queryVectors = SiftLoader.readFvecs(String.format("%s/siftsmall_query.fvecs", siftPath));
        var groundTruth = SiftLoader.readIvecs(String.format("%s/siftsmall_groundtruth.ivecs", siftPath));
        System.out.format("%d base and %d query vectors loaded, dimensions %d%n",
                baseVectors.size(), queryVectors.size(), baseVectors.get(0).length());

        execInMemory(baseVectors);
        execDiskAnn(baseVectors, queryVectors, groundTruth);
    }

    @Test
//    @Disabled()
    public void testJVectorSiftSmall1M() throws IOException {
        // Taken from http://corpus-texmex.irisa.fr/
        // ftp://ftp.irisa.fr/local/texmex/corpus/sift.tar.gz
        var siftPath = "siftsmall";
        var baseVectors = SiftLoader.readFvecs(String.format("%s/1M/sift_base.fvecs", siftPath));
        var queryVectors = SiftLoader.readFvecs(String.format("%s/1M/sift_query.fvecs", siftPath));
        var groundTruth = SiftLoader.readIvecs(String.format("%s/1M/sift_groundtruth.ivecs", siftPath));
        System.out.format("%d base and %d query vectors loaded, dimensions %d%n",
                baseVectors.size(), queryVectors.size(), baseVectors.get(0).length());

        execInMemory(baseVectors);
        execDiskAnn(baseVectors, queryVectors, groundTruth);
    }

    public static void execInMemory(ArrayList<VectorFloat<?>> baseVectors) throws IOException {
        Instant start = Instant.now();
        // infer the dimensionality from the first vector
        int originalDimension = baseVectors.get(0).length();
        // wrap the raw vectors in a RandomAccessVectorValues
        RandomAccessVectorValues ravv = new ListRandomAccessVectorValues(baseVectors, originalDimension);

        // score provider using the raw, in-memory vectors
        BuildScoreProvider bsp = BuildScoreProvider.randomAccessScoreProvider(ravv, SIMILARITY_FUNCTION);
        try (GraphIndexBuilder builder = new GraphIndexBuilder(bsp,
                ravv.dimension(),
                16, // graph degree
                100, // construction search depth
                1.2f, // allow degree overflow during construction by this factor
                1.2f)) // relax neighbor diversity requirement by this factor
        {
            // build the index (in memory)
            OnHeapGraphIndex index = builder.build(ravv);

            // search for a random vector
            VectorFloat<?> q = randomVector(originalDimension);
            SearchResult sr = GraphSearcher.search(q,
                    10, // number of results
                    ravv, // vectors we're searching, used for scoring
                    SIMILARITY_FUNCTION, // how to score
                    index,
                    Bits.ALL); // valid ordinals to consider
            for (SearchResult.NodeScore ns : sr.getNodes()) {
                System.out.println(ns);
            }
        }
        Instant finish = Instant.now();
        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("In-memory test elapsed = " + timeElapsed + "ms");
    }

    // diskann-style index with PQ
    public static void execDiskAnn(List<VectorFloat<?>> baseVectors, List<VectorFloat<?>> queryVectors, List<Set<Integer>> groundTruth) throws IOException {
        int originalDimension = baseVectors.get(0).length();
        RandomAccessVectorValues vectors = new ListRandomAccessVectorValues(baseVectors, originalDimension);

//        beamWidth: 100
//        M: 1
//        neighborOverflow: 0.1
//        alpha: 0.2
                
        var indexBuilder =
                new GraphIndexBuilder(
                        vectors,
                        SIMILARITY_FUNCTION,
                        1,
                        100,
                        0.1F,
                        0.2F);
        
        var pool = new ForkJoinPool(5);
        
        var size = vectors.size();

        var buildStart = Instant.now();
//        try (var progress = ProgressBar.create("building", size)) {

//        }

        if (!Files.exists(indexPath)) {
            Files.createDirectory(indexPath);
        }
        
        var path = indexPath.resolve(GRAPH_FILE);

        pool.submit(
                        () -> {
                            IntStream.range(0, size)
                                    .parallel()
                                    .forEach(
                                            i -> {
                                                indexBuilder.addGraphNode(i, vectors);
//                                                    progress.inc();
                                            });
                        })
                .join();

//        this.indexBuilder.cleanup();
        var buildEnd = Instant.now();

//        System.out.println("finished building index, committing");
        var commitStart = Instant.now();
//        try (var output =
//                     new DataOutputStream(
//                             new BufferedOutputStream(
//                                     Files.newOutputStream(indexPath.resolve(GRAPH_FILE))))) {
            var graph = indexBuilder.getGraph();
            OnDiskGraphIndex.write(graph, vectors, path);
//        }
        var commitEnd = Instant.now();

        System.out.println("commitEnd = " + commitEnd);
        
        
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

        // todo - READ
        var start = Instant.now();
        
//        ReaderSupplier rs = new MMapReader.Supplier(path);

        var readerSupplier = new MMapReaderSupplier(path);
        var onDiskGraph = OnDiskGraphIndex.load(readerSupplier, 0);
        
        var cachingGraph = new CachingGraphIndex(onDiskGraph);


        var view = cachingGraph.getView();

//        NeighborSimilarity.ExactScoreFunction scoreFunction =
//                i -> this.similarityFunction.compare(vector, view.getVector(i));

        

        queryVectors.forEach(queryVector -> {
            
        // search for a random vector
//        VectorFloat<?> q = randomVector(originalDimension);
        SearchResult sr = GraphSearcher.search(queryVector,
                80, // number of results
                vectors, // vectors we're searching, used for scoring
                SIMILARITY_FUNCTION, // how to score
                cachingGraph,
                Bits.ALL); // valid ordinals to consider
//        for (SearchResult.NodeScore ns : sr.getNodes()) {
//            System.out.println("ajeje" + ns);
//        }

        SearchResult.NodeScore[] nodes = sr.getNodes();
        System.out.println("nodes.length = " + nodes.length);
        System.out.println("nodes = " + nodes[0].score);
        System.out.println("nodes = " + nodes[0].node);
//        Arrays.stream(sr.getNodes())
//                .map(nodeScore -> {
//                    System.out.println("nodeScore = " + nodeScore);
//                    return nodeScore.node;
//                })
////          .peek(i -> System.out.println("i = " + i))
////                .limit(k)
//                .collect(Collectors.toList());
        });
        
        // todo - check this code 
//                var searcher = new GraphSearcher.Builder<>(view).build();
//                var querySimilarity = querySimilarity(vector, view);
//                var results =
//                        searcher.search(
//                                querySimilarity.scoreFunction,
//                                querySimilarity.reRanker,
//                                queryParams.numCandidates,
//                                Bits.ALL);
//        
//                SearchResult.NodeScore[] nodes = results.getNodes();
//                System.out.println("nodes = " + nodes[0].score);
//                System.out.println("nodes = " + nodes[0].node);
//                return Arrays.stream(results.getNodes())
//                        .map(nodeScore -> {
//                            System.out.println("nodeScore = " + nodeScore);
//                            return nodeScore.node;
//                        })
//        //          .peek(i -> System.out.println("i = " + i))
//                        .limit(k)
//                        .collect(Collectors.toList());
        // todo - check the above code
        
//        var compressedVectors =
//                compressedVectors(
//                        indexPath, dimensions, queryParams.pqFactor, cachingGraph, vectorSimilarityFunction);
        
//        OnDiskGraphIndex index = OnDiskGraphIndex.load(rs);
//        // load the PQVectors that we just wrote to disk
//        try (RandomAccessReader in = new SimpleMappedReader(indexPath)) {
////        try (RandomAccessReader in = new SimpleMappedReader(pqPath)) {
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
    }

    private static class MMapReaderSupplier implements ReaderSupplier {
        private final MMapBuffer buffer;

        public MMapReaderSupplier(Path path) throws IOException {
            buffer = new MMapBuffer(path, FileChannel.MapMode.READ_ONLY, ByteOrder.BIG_ENDIAN);
        }

        @Override
        public RandomAccessReader get() {
            return new MMapReader(buffer);
        }

        @Override
        public void close() throws IOException {
            buffer.close();
        }
    }

//    private static class MMapReader implements RandomAccessReader {
//        private final MMapBuffer buffer;
//        private long position;
//        private byte[] floatsScratch = new byte[0];
//        private byte[] intsScratch = new byte[0];
//
//        MMapReader(MMapBuffer buffer) {
//            this.buffer = buffer;
//        }
//
//        @Override
//        public void seek(long offset) {
//            position = offset;
//        }
//
//        public int readInt() {
//            try {
//                return buffer.memory().getInt(position);
//            } finally {
//                position += Integer.BYTES;
//            }
//        }
//
//        public void readFully(byte[] bytes) {
//            read(bytes, 0, bytes.length);
//        }
//
//        private void read(byte[] bytes, int offset, int count) {
//            try {
//                buffer.memory().getBytes(position, bytes, offset, count);
//            } finally {
//                position += count;
//            }
//        }
//
//        @Override
//        public void readFully(float[] floats) {
//            int bytesToRead = floats.length * Float.BYTES;
//            if (floatsScratch.length < bytesToRead) {
//                floatsScratch = new byte[bytesToRead];
//            }
//            read(floatsScratch, 0, bytesToRead);
//            ByteBuffer byteBuffer = ByteBuffer.wrap(floatsScratch).order(ByteOrder.BIG_ENDIAN);
//            byteBuffer.asFloatBuffer().get(floats);
//        }
//
//        @Override
//        public void read(int[] ints, int offset, int count) {
//            int bytesToRead = (count - offset) * Integer.BYTES;
//            if (intsScratch.length < bytesToRead) {
//                intsScratch = new byte[bytesToRead];
//            }
//            read(intsScratch, 0, bytesToRead);
//            ByteBuffer byteBuffer = ByteBuffer.wrap(intsScratch).order(ByteOrder.BIG_ENDIAN);
//            byteBuffer.asIntBuffer().get(ints, offset, count);
//        }
//
//        @Override
//        public void close() {
//            // don't close buffer, let the Supplier handle that
//        }
//    }


    private static void testRecall(GraphIndex graph,
                                   List<VectorFloat<?>> queryVectors,
                                   List<Set<Integer>> groundTruth,
                                   Function<VectorFloat<?>,
                                           SearchScoreProvider> sspFactory)
            throws IOException
    {
        AtomicInteger topKfound = new AtomicInteger(0);
        int topK = 100;
        String graphType = graph.getClass().getSimpleName();
        try (ExplicitThreadLocal<GraphSearcher> searchers = ExplicitThreadLocal.withInitial(() -> new GraphSearcher(graph))) {
            IntStream.range(0, queryVectors.size()).parallel().forEach(i -> {
                VectorFloat<?> queryVector = queryVectors.get(i);
                try (GraphSearcher searcher = searchers.get()) {
                    SearchScoreProvider ssp = sspFactory.apply(queryVector);
                    int rerankK = ssp.scoreFunction().isExact() ? topK : 2 * topK; // hardcoded overquery factor of 2x when reranking
                    SearchResult.NodeScore[] nn = searcher.search(ssp, rerankK, Bits.ALL).getNodes();

                    Set<Integer> gt = groundTruth.get(i);
                    long n = IntStream.range(0, min(topK, nn.length)).filter(j -> gt.contains(nn[j].node)).count();
                    topKfound.addAndGet((int) n);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (Exception e) {
            ExceptionUtils.throwIoException(e);
        }
        System.out.printf("(%s) Recall: %.4f%n", graphType, (double) topKfound.get() / (queryVectors.size() * topK));
    }

    public static VectorFloat<?> randomVector(int dim) {
        Random R = ThreadLocalRandom.current();
        VectorFloat<?> vec = vts.createFloatVector(dim);
        for (int i = 0; i < dim; i++) {
            vec.set(i, R.nextFloat());
            if (R.nextBoolean()) {
                vec.set(i, -vec.get(i));
            }
        }
        VectorUtil.l2normalize(vec);
        return vec;
    }
}
