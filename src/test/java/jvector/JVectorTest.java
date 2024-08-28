package jvector;

import io.github.jbellis.jvector.disk.RandomAccessReader;
import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.disk.SimpleMappedReader;
import io.github.jbellis.jvector.graph.GraphIndex;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.ScoreFunction;
import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
import io.github.jbellis.jvector.pq.PQVectors;
import io.github.jbellis.jvector.pq.ProductQuantization;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.util.ExceptionUtils;
import io.github.jbellis.jvector.util.ExplicitThreadLocal;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorUtil;
import io.github.jbellis.jvector.vector.types.ByteSequence;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import jvector.util.MMapReader;
import jvector.util.SiftLoader;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import static io.github.jbellis.jvector.graph.similarity.BuildScoreProvider.vts;
import static java.lang.Math.min;

public class JVectorTest {

    @Test
    void testJVectorSiftSmall() throws IOException {
        var siftPath = "siftsmall";
        var baseVectors = SiftLoader.readFvecs(String.format("%s/siftsmall_base.fvecs", siftPath));
        var queryVectors = SiftLoader.readFvecs(String.format("%s/siftsmall_query.fvecs", siftPath));
        var groundTruth = SiftLoader.readIvecs(String.format("%s/siftsmall_groundtruth.ivecs", siftPath));
        System.out.format("%d base and %d query vectors loaded, dimensions %d%n",
                baseVectors.size(), queryVectors.size(), baseVectors.get(0).length());

        siftInMemory(baseVectors);
        siftDiskAnn(baseVectors, queryVectors, groundTruth);
    }

    public static void siftInMemory(ArrayList<VectorFloat<?>> baseVectors) throws IOException {
        // infer the dimensionality from the first vector
        int originalDimension = baseVectors.get(0).length();
        // wrap the raw vectors in a RandomAccessVectorValues
        RandomAccessVectorValues ravv = new ListRandomAccessVectorValues(baseVectors, originalDimension);

        // score provider using the raw, in-memory vectors
        BuildScoreProvider bsp = BuildScoreProvider.randomAccessScoreProvider(ravv, VectorSimilarityFunction.EUCLIDEAN);
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
                    VectorSimilarityFunction.EUCLIDEAN, // how to score
                    index,
                    Bits.ALL); // valid ordinals to consider
            for (SearchResult.NodeScore ns : sr.getNodes()) {
                System.out.println(ns);
            }
        }
    }

    // diskann-style index with PQ
    public static void siftDiskAnn(List<VectorFloat<?>> baseVectors, List<VectorFloat<?>> queryVectors, List<Set<Integer>> groundTruth) throws IOException {
        int originalDimension = baseVectors.get(0).length();
        RandomAccessVectorValues ravv = new ListRandomAccessVectorValues(baseVectors, originalDimension);

        BuildScoreProvider bsp = BuildScoreProvider.randomAccessScoreProvider(ravv, VectorSimilarityFunction.EUCLIDEAN);
        Path indexPath = Files.createTempFile("siftsmall", ".inline");
        try (GraphIndexBuilder builder = new GraphIndexBuilder(bsp, ravv.dimension(), 16, 100, 1.2f, 1.2f)) {
            OnHeapGraphIndex index = builder.build(ravv);
            OnDiskGraphIndex.write(index, ravv, indexPath);
        }

        // compute and write compressed vectors to disk
        Path pqPath = Files.createTempFile("siftsmall", ".pq");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(pqPath)))) {
            // Compress the original vectors using PQ. this represents a compression ratio of 128 * 4 / 16 = 32x
            ProductQuantization pq = ProductQuantization.compute(ravv,
                    16, // number of subspaces
                    256, // number of centroids per subspace
                    true); // center the dataset
            ByteSequence<?>[] compressed = pq.encodeAll(ravv);
            // write the compressed vectors to disk
            PQVectors pqv = new PQVectors(pq, compressed);
            pqv.write(out);
        }

        ReaderSupplier rs = new MMapReader.Supplier(indexPath);
        OnDiskGraphIndex index = OnDiskGraphIndex.load(rs);
        // load the PQVectors that we just wrote to disk
        try (RandomAccessReader in = new SimpleMappedReader(pqPath)) {
            PQVectors pqv = PQVectors.load(in);
            // SearchScoreProvider that does a first pass with the loaded-in-memory PQVectors,
            // then reranks with the exact vectors that are stored on disk in the index
            Function<VectorFloat<?>, SearchScoreProvider> sspFactory = q -> {
                ScoreFunction.ApproximateScoreFunction asf = pqv.precomputedScoreFunctionFor(q, VectorSimilarityFunction.EUCLIDEAN);
                ScoreFunction.ExactScoreFunction reranker = index.getView().rerankerFor(q, VectorSimilarityFunction.EUCLIDEAN);
                return new SearchScoreProvider(asf, reranker);
            };
            // measure our recall against the (exactly computed) ground truth
            testRecall(index, queryVectors, groundTruth, sspFactory);
        }
    }

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
