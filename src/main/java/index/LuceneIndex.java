package index;

import com.google.common.base.Preconditions;
import org.apache.lucene.codecs.lucene90.Lucene90Codec;
import org.apache.lucene.codecs.lucene90.Lucene90HnswVectorsFormat;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnVectorQuery;
import util.Exceptions;
import util.ProgressBar;
import util.Records;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import lucene.CustomVectorProvider;
import util.DataSetVector;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public final class LuceneIndex {

  public static final String LUCENE_PREFIX = "LUCENE-";

  public record HnswBuildParameters(
      int maxConn, int beamWidth, int forceMerge) {}

  public record HnswQueryParameters(int numCandidates) {}

  private static final String VECTOR_FIELD = "vector";
  private static final String ID_FIELD = "id";

  public static final class Builder implements Index.Builder {

    private final CustomVectorProvider vectors;
    private final MMapDirectory directory;
    private final IndexWriter writer;
    private final HnswBuildParameters hnswParams;
    private final VectorSimilarityFunction similarityFunction;
    private final int numThreads;

    public Builder(
            Path indexesPath,
            CustomVectorProvider vectors,
            DataSetVector.SimilarityFunction similarityFunction,
            Parameters parameters,
            int numThreads)
        throws IOException {

      var hnswParams = parseBuildPrams(parameters.buildParameters());

      var similarity =
          switch (similarityFunction) {
            case COSINE -> VectorSimilarityFunction.COSINE;
            case DOT_PRODUCT -> VectorSimilarityFunction.DOT_PRODUCT;
            case EUCLIDEAN -> VectorSimilarityFunction.EUCLIDEAN;
          };

      var description = buildDescription(hnswParams);
      var path = indexesPath.resolve(description);
      Preconditions.checkArgument(!path.toFile().exists(), "index already exists at %s", path);

      var directory = new MMapDirectory(path);

      var codec = new Lucene90Codec() {
        @Override
        public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
          return new Lucene90HnswVectorsFormat(hnswParams.maxConn, hnswParams.beamWidth);
        }
      };

      IndexWriterConfig config = new IndexWriterConfig()
              .setCodec(codec)
              .setUseCompoundFile(false)
              .setMaxBufferedDocs(1000000000)
              .setRAMBufferSizeMB(40 * 1024);
      
      if (hnswParams.forceMerge != 0) {
        TieredMergePolicy mergePolicy = new TieredMergePolicy();
        mergePolicy.setMaxMergedSegmentMB(2048.0);
        mergePolicy.setSegmentsPerTier(hnswParams.forceMerge);
        config.setMergePolicy(mergePolicy);
      }
      
      var writer = new IndexWriter( directory, config );

      this.vectors = vectors;
      this.directory = directory;
      this.writer = writer;
      this.similarityFunction = similarity;
      this.hnswParams = hnswParams;
      this.numThreads = numThreads;
    }

    @Override
    public BuildSummary build() throws IOException {
      System.out.println("vectors size = " + vectors.size());
      var size = this.vectors.size();
      
      var buildStart = Instant.now();
      
      try (var pool = new ForkJoinPool(numThreads)) {
        try (var progress = ProgressBar.create("building", size)) {

          pool.submit(() -> IntStream.range(0, size)
                  .parallel()
                  .forEach(i -> {
                    Exceptions.wrap(() -> {
                      var doc = new Document();
                          doc.add(new StoredField(ID_FIELD, i));
                          doc.add(
                              new KnnVectorField(
                                  VECTOR_FIELD,
                                      this.vectors.vectorValue(i),
                                  this.similarityFunction));
                      try {
                          this.writer.addDocument(doc);
                      } catch (IOException e) {
                        System.out.println("e = " + e);
                          throw new RuntimeException(e);
                      }
                    });
                    progress.inc();
                  })).join();
        }
      }

      System.out.println("build end");
      var buildEnd = Instant.now();

      ArrayList<BuildPhase> build = new ArrayList<>();
      build.add( new BuildPhase(Phase.build, Duration.between(buildStart, buildEnd)) );
      
      if (hnswParams.forceMerge != 0) {
        var mergeStart = Instant.now();
        this.writer.forceMerge(1);
        var mergeEnd = Instant.now();
        build.add(new BuildPhase(Phase.merge, Duration.between(mergeStart, mergeEnd)));
      }

      var commitStart = Instant.now();
      writer.commit();
      var commitEnd = Instant.now();
      build.add(new BuildPhase(Phase.commit, Duration.between(commitStart, commitEnd)));
      
      return new BuildSummary(build);
    }

    public long size() {
      return FileUtils.sizeOfDirectory(this.directory.getDirectory().toFile());
    }

    @Override
    public String description() {
      return buildDescription(this.hnswParams);
    }

    @Override
    public void close() throws Exception {
      this.writer.close();
      this.directory.close();
    }

    private static String buildDescription(HnswBuildParameters params) {
      return buildParamString(params);
    }

    private static String buildParamString(HnswBuildParameters params) {
      return String.format(
              "%smaxConn:%s-beamWidth:%s-forceMerge:%s",
              LUCENE_PREFIX,
              params.maxConn,
              params.beamWidth,
              params.forceMerge);
    }
  }

  public static final class Querier implements Index.Querier {

    private final Directory directory;
    private final IndexReader reader;
    private final HnswBuildParameters buildParams;
    private final HnswQueryParameters queryParams;
    private final IndexSearcher searcher;

    private Querier(
        Directory directory,
        IndexReader reader,
        HnswBuildParameters buildParams,
        HnswQueryParameters queryParams,
        IndexSearcher searcher) {
      this.directory = directory;
      this.reader = reader;
      this.buildParams = buildParams;
      this.queryParams = queryParams;
      this.searcher = searcher;
    }

    public static Index.Querier create(Path indexesPath, Parameters parameters) throws IOException {

      var buildParams = parseBuildPrams(parameters.buildParameters());
      var queryParams = parseQueryPrams(parameters.queryParameters());

      var buildDescription = LuceneIndex.Builder.buildDescription(buildParams);
      var path = indexesPath.resolve(buildDescription);
      Preconditions.checkArgument(path.toFile().exists(), "index does not exist at {}" +  path);

      var directory = new MMapDirectory(indexesPath.resolve(buildDescription));
      var reader = DirectoryReader.open(directory);

      var searcher = new IndexSearcher(reader);
      return new LuceneIndex.Querier(
          directory, reader, buildParams, queryParams, searcher);
    }

    @Override
    public List<Integer> query(Object vectorObj, int k, boolean ensureIds) throws IOException {
      float[] vector = (float[]) vectorObj;

      var ids = new ArrayList<Integer>(k);

      var query = new KnnVectorQuery(VECTOR_FIELD, vector, queryParams.numCandidates);
      var results = this.searcher.search(query, queryParams.numCandidates);
      
//      for (LeafReaderContext ctx : reader.leaves()) {
//        TopDocs results = LuceneUtil.doKnnSearch(ctx.reader(), VECTOR_FIELD, vector, queryParams.numCandidates, 1);

        for (int i = 0; i < k && i < results.scoreDocs.length; i++) {
          var result = results.scoreDocs[i];
          var id =
                  ensureIds
                      ? this.searcher
                          .doc(result.doc)
                          .getField(ID_FIELD)
                          .numericValue()
                          .intValue()
                      : result.doc;
//          var id = result.doc;
          ids.add(id);
        }
//      }

      return ids;//.stream().limit(k).toList();
    }

    @Override
    public String description() {
      return String.format(
          "%s_%s",
          LuceneIndex.Builder.buildParamString(buildParams),
          queryParamString()
      );
    }

    @Override
    public void close() throws Exception {
      this.directory.close();
      this.reader.close();
    }

    private String queryParamString() {
      return String.format("numCandidates:%s", queryParams.numCandidates);
    }
  }
  
  private static HnswBuildParameters parseBuildPrams(Map<String, String> parameters) {
    return Records.fromMap(parameters, HnswBuildParameters.class, "build parameters");
  }

  private static HnswQueryParameters parseQueryPrams(Map<String, String> parameters) {
      return Records.fromMap(parameters, HnswQueryParameters.class, "query parameters");
  }
}
