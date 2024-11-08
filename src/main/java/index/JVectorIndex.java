package index;

import io.github.jbellis.jvector.graph.disk.CachingGraphIndex;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.pq.PQVectors;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import util.ProgressBar;
import util.Records;
import com.google.common.base.Preconditions;
import com.indeed.util.mmap.MMapBuffer;
import io.github.jbellis.jvector.disk.RandomAccessReader;
import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.graph.GraphIndex;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.pq.CompressedVectors;
import io.github.jbellis.jvector.pq.ProductQuantization;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import jvector.DataSetJVector;
import jvector.MMapReader;
import org.apache.commons.io.FileUtils;
import oshi.SystemInfo;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JVectorIndex {
  private static final String GRAPH_FILE = "graph.bin";
  private static final String COMPRESSED_VECTOR_FILE_FORMAT = "compressed-vectors-%s.bin";

  public record BuildParameters(int M, int beamWidth, float neighborOverflow, float alpha) {}

  public record QueryParameters(int numCandidates, int pqFactor) {}

  public static final class Builder implements Index.Builder {

    private final Path indexPath;
    private final RandomAccessVectorValues vectors;
    private final GraphIndexBuilder indexBuilder;
    private final BuildParameters buildParams;
    private final int numThreads;

    public Builder(
        Path indexesPath,
        RandomAccessVectorValues vectors,
        VectorSimilarityFunction vectorSimilarityFunction,
        Parameters parameters)
        throws IOException {

      var buildParams =
          Records.fromMap(parameters.buildParameters(), BuildParameters.class, "build parameters");

      var indexBuilder =
          new GraphIndexBuilder(
              vectors,
              vectorSimilarityFunction,
              buildParams.M,
              buildParams.beamWidth,
              buildParams.neighborOverflow,
              buildParams.alpha);

      var numThreads =
          Optional.ofNullable(System.getenv("JVECTOR_NUM_THREADS"))
              .map(Integer::parseInt)
              .orElseGet(
                  () -> new SystemInfo().getHardware().getProcessor().getPhysicalProcessorCount());

      var path = indexesPath.resolve(buildDescription(buildParams));
      Files.createDirectories(path);

      this.indexPath = path;
      this.vectors = vectors;
      this.indexBuilder = indexBuilder;
      this.buildParams = buildParams;
      this.numThreads = numThreads;
    }

    @Override
    public BuildSummary build() throws IOException {
      
      var pool = new ForkJoinPool(this.numThreads);
      var size = this.vectors.size();

      var buildStart = Instant.now();
      try (var progress = ProgressBar.create("building", size)) {
        pool.submit(
                () -> {
                  IntStream.range(0, size)
                      .parallel()
                      .forEach(
                          i -> {
                            this.indexBuilder.addGraphNode(i, this.vectors);
                            progress.inc();
                          });
                })
            .join();
      }

      this.indexBuilder.cleanup();
      var buildEnd = Instant.now();

      System.out.println("finished building index, committing");
      var commitStart = Instant.now();
      var path = indexPath.resolve(GRAPH_FILE);
      
      var graph = this.indexBuilder.getGraph();
      OnDiskGraphIndex.write(graph, vectors, path);
      var commitEnd = Instant.now();

      return new BuildSummary(
          List.of(
              new BuildPhase("build", Duration.between(buildStart, buildEnd)),
              new BuildPhase("commit", Duration.between(commitStart, commitEnd))));
    }

    @Override
    public String description() {
      return buildDescription(this.buildParams);
    }

    @Override
    public long size() throws IOException {
      return FileUtils.sizeOfDirectory(this.indexPath.toFile());
    }

    @Override
    public void close() throws Exception {}

    private static String buildDescription(BuildParameters buildParams) {
      return String.format(
          "jvector_vamana_M:%s-beamWidth:%s-neighborOverflow:%s-alpha:%s",
          buildParams.M, buildParams.beamWidth, buildParams.neighborOverflow, buildParams.alpha);
    }
  }

  public static class Querier implements Index.Querier {
    private final ReaderSupplier readerSupplier;
    private final GraphIndex graph;
    private final Optional<CompressedVectors> compressedVectors;
    private final VectorSimilarityFunction similarityFunction;
    private final BuildParameters buildParams;
    private final QueryParameters queryParams;
    private final DataSetJVector dataSet;

    public Querier(
            DataSetJVector dataSet,
            ReaderSupplier readerSupplier,
        GraphIndex graph,
        Optional<CompressedVectors> compressedVectors,
        VectorSimilarityFunction similarityFunction,
        BuildParameters buildParams,
        QueryParameters queryParams) {
      this.readerSupplier = readerSupplier;
      this.graph = graph;
      this.compressedVectors = compressedVectors;
      this.similarityFunction = similarityFunction;
      this.buildParams = buildParams;
      this.queryParams = queryParams;
      this.dataSet = dataSet;
    }

    public static Index.Querier create(
            DataSetJVector queryVectors, 
            Path indexesPath,
            VectorSimilarityFunction similarityFunction,
            int dimensions,
            Parameters parameters)
        throws IOException {

      var buildParams =
          Records.fromMap(parameters.buildParameters(), BuildParameters.class, "build parameters");
      var queryParams =
          Records.fromMap(parameters.queryParameters(), QueryParameters.class, "query parameters");

      var buildDescription = JVectorIndex.Builder.buildDescription(buildParams);
      var indexPath = indexesPath.resolve(buildDescription);
      var path = indexPath.resolve(GRAPH_FILE);
      Preconditions.checkArgument(path.toFile().exists(), "index does not exist at {}" +  path);

      var vectorSimilarityFunction =
          switch (similarityFunction) {
            case COSINE -> VectorSimilarityFunction.COSINE;
            case DOT_PRODUCT -> VectorSimilarityFunction.DOT_PRODUCT;
            case EUCLIDEAN -> VectorSimilarityFunction.EUCLIDEAN;
          };

      var readerSupplier = new MMapReaderSupplier(path);
      var onDiskGraph = OnDiskGraphIndex.load(readerSupplier, 0);
      
      // TODO ... check this rows
      var cachingGraph = new CachingGraphIndex(onDiskGraph);
      var compressedVectors =
          compressedVectors(
                  queryVectors.baseVectorsArray(),
              indexPath, dimensions, queryParams.pqFactor, cachingGraph, vectorSimilarityFunction);

      return new JVectorIndex.Querier(
              queryVectors,
          readerSupplier,
          cachingGraph,
          compressedVectors,
          vectorSimilarityFunction,
          buildParams,
          queryParams);
    }

    @Override
    public List<Integer> query(Object vectorObj, int k, boolean ensureIds) throws IOException {
      VectorFloat<?> vector = (VectorFloat<?>) vectorObj;

      var results =
          GraphSearcher.search(
                  vector, 
                  queryParams.numCandidates,
                  dataSet.getBaseRavv(),
                  similarityFunction, 
                  graph,
                  Bits.ALL);

      SearchResult.NodeScore[] nodes = results.getNodes();
      
      // TODO - WHY ALWAYS 1???
      
      System.out.println("nodes.length = " + nodes.length);
      System.out.println("nodes = " + nodes[0].score);
      System.out.println("nodes = " + nodes[0].node);
      return Arrays.stream(results.getNodes())
          .map(nodeScore -> {
            System.out.println("nodeScore = " + nodeScore);
            return nodeScore.node;
          })
          .limit(k)
          .collect(Collectors.toList());
    }

    @Override
    public void close() throws Exception {
      this.graph.close();
      this.readerSupplier.close();
    }

    @Override
    public String description() {
      return String.format(
          "jvector_vamana_M:%s-beamWidth:%s-neighborOverflow:%s-alpha:%s_numCandidates:%s-pqFactor:%s",
          buildParams.M,
          buildParams.beamWidth,
          buildParams.neighborOverflow,
          buildParams.alpha,
          queryParams.numCandidates,
          queryParams.pqFactor);
    }

    private static Optional<CompressedVectors> compressedVectors(
            List<VectorFloat<?>> baseVectors,
            Path indexPath,
            int dimensions,
            int pqFactor,
            GraphIndex graph,
            VectorSimilarityFunction vectorSimilarityFunction)
        throws IOException {
      if (pqFactor <= 0) {
        return Optional.empty();
      }

      var compressedVectorsFile =
          indexPath.resolve(String.format(COMPRESSED_VECTOR_FILE_FORMAT, pqFactor));
      if (compressedVectorsFile.toFile().exists()) {
        try (var reader = new MMapReaderSupplier(compressedVectorsFile)) {
          return Optional.of(PQVectors.load(reader.get(), 0));
        }
      }

      var pqDims = dimensions / pqFactor;

      System.out.println(
          "index configured with pqFactor of {}, building codebook with {} dimensions" +
          pqFactor +
          pqDims);

      // todo - this is needed?
//      var graphVectors = graphViewVectors(graph.getView(), dimensions);
//      var size = graph.size();
//
//      List<VectorFloat<?>> vectors;
//      try (var progress = ProgressBar.create("loading vectors from graph", size)) {
//        vectors =
//            IntStream.range(0, size)
//                .sequential()
//                .mapToObj(graphVectors::vectorValue)
//                .peek(ignored -> progress.inc())
//                .collect(Collectors.toList());
//      }
//
//      System.out.println("building codebooks");
      
      // TODO - CONFIG
      int clusterCount = 256;
      
      var pqStart = Instant.now();
      var pq =
          ProductQuantization.compute(
              new ListRandomAccessVectorValues(baseVectors, dimensions),
              pqDims,
              clusterCount,
              vectorSimilarityFunction == VectorSimilarityFunction.EUCLIDEAN);
      var pqEnd = Instant.now();
      System.out.println("built codebooks in {}" +  Duration.between(pqStart, pqEnd));

      System.out.println("beginning to quantize vectors");
      var quantizeStart = Instant.now();
      // todo - check it
      var quantizedVectors = pq.encodeAll(baseVectors);
      
      // todo : try with BQVectors
      var compressedVectors = new PQVectors(pq, quantizedVectors);
      var quantizeEnd = Instant.now();
      System.out.println(
          "quantized vectors in {}, compressed size: {}" + 
          Duration.between(quantizeStart, quantizeEnd) +
          compressedVectors.getCompressedSize()
      );

      System.out.println("writing quantized vectors to disk");
      var writeStart = Instant.now();
      try (var output =
          new DataOutputStream(new FileOutputStream(compressedVectorsFile.toFile()))) {
        compressedVectors.write(output);
      }
      var writeEnd = Instant.now();
      System.out.println("wrote quantized vectors in {}" +  Duration.between(writeStart, writeEnd));

      return Optional.of(compressedVectors);
    }
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
}