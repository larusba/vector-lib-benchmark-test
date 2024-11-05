package javaannbench.index;

import io.github.jbellis.jvector.vector.types.VectorFloat;
import javaannbench.dataset.Datasets;
import javaannbench.display.ProgressBar;
import javaannbench.util.Bytes;
import javaannbench.util.Exceptions;
import javaannbench.util.Records;
import com.google.common.base.Preconditions;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene90.Lucene90Codec;
import org.apache.lucene.codecs.lucene90.Lucene90HnswVectorsFormat;
//import org.apache.lucene.codecs.lucene912.Lucene912Codec;
//import org.apache.lucene.codecs.lucene99.Lucene99Codec;
//import org.apache.lucene.codecs.lucene99.Lucene99HnswScalarQuantizedVectorsFormat;
//import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
//import org.apache.lucene.codecs.lucene99.Lucene99ScalarQuantizedVectorsFormat;
//import org.apache.lucene.codecs.vectorsandbox.VectorSandboxScalarQuantizedVectorsFormat;
//import org.apache.lucene.codecs.vectorsandbox.VectorSandboxVamanaVectorsFormat;
import org.apache.lucene.document.Document;
//import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.KnnVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
//import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.KnnVectorQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CustomVectorProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

public final class LuceneIndex {
  private static final Logger LOGGER = LoggerFactory.getLogger(LuceneIndex.class);

  public enum Provider {
    HNSW("hnsw");
//    SANDBOX_VAMANA("sandbox-vamana");

    final String description;

    Provider(String description) {
      this.description = description;
    }

    static Provider parse(String description) {
      return switch (description) {
        case "hnsw" -> Provider.HNSW;
//        case "sandbox-vamana" -> Provider.SANDBOX_VAMANA;
        default -> throw new RuntimeException("unexpected lucene index provider " + description);
      };
    }
  }

  public sealed interface BuildParameters permits VamanaBuildParameters, HnswBuildParameters {}

  public record HnswBuildParameters(
      int maxConn, int beamWidth, boolean scalarQuantization, int numThreads, boolean forceMerge)
      implements BuildParameters {}

  public record VamanaBuildParameters(
      int maxConn,
      int beamWidth,
      float alpha,
      int pqFactor,
      boolean inGraphVectors,
      boolean scalarQuantization,
      int numThreads,
      boolean forceMerge)
      implements BuildParameters {}

  public sealed interface QueryParameters permits HnswQueryParameters, VamanaQueryParameters {}

  public record HnswQueryParameters(int numCandidates) implements QueryParameters {}

  public record VamanaQueryParameters(
      int numCandidates,
      String pqRerank,
      boolean mlockGraph,
      boolean mmapPqVectors,
      boolean mlockPqVectors,
      boolean parallelPqVectors,
      boolean parallelNeighborhoods,
      int parallelNeighborhoodsBeamWidth,
      String parallelRerankThreads,
      int nodeCacheDegree)
      implements QueryParameters {}

  private static final String VECTOR_FIELD = "vector";
  private static final String ID_FIELD = "id";

  public static final class Builder implements Index.Builder {

    private final CustomVectorProvider vectors;
    private final MMapDirectory directory;
    private final IndexWriter writer;
    private final AtomicBoolean shouldMerge;
    private final Provider provider;
    private final BuildParameters buildParams;
    private final VectorSimilarityFunction similarityFunction;
//    private final DataSet dataSet;

    public Builder(
//            DataSet dataSet,
//            RandomAccessVectorValues vectors,
//            MMapDirectory directory,
//            IndexWriter writer,
//            AtomicBoolean shouldMerge,
//            Provider provider,
//            BuildParameters buildParams,
//            VectorSimilarityFunction similarityFunction) {

//    }
//
//    public static Index.Builder create(
//            DataSet dataSet,
        Path indexesPath,
            CustomVectorProvider vectors,
        Datasets.SimilarityFunction similarityFunction,
        Parameters parameters)
        throws IOException {
      var provider = Provider.parse(parameters.type());

      var buildParams = parseBuildPrams(provider, parameters.buildParameters());

      var similarity =
          switch (similarityFunction) {
            case COSINE -> VectorSimilarityFunction.COSINE;
            case DOT_PRODUCT -> VectorSimilarityFunction.DOT_PRODUCT;
            case EUCLIDEAN -> VectorSimilarityFunction.EUCLIDEAN;
          };

      var description = buildDescription(provider, buildParams);
      var path = indexesPath.resolve(description);
      Preconditions.checkArgument(!path.toFile().exists(), "index already exists at %s", path);

      var directory = new MMapDirectory(path);

        var hnswParams = (HnswBuildParameters) buildParams;
      var codec =
          switch (provider) {

            case HNSW -> new Lucene90Codec() {
              @Override
              public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
                return new Lucene90HnswVectorsFormat(hnswParams.maxConn, hnswParams.beamWidth);
              }

//              var hnswParams = (HnswBuildParameters) buildParams;
////              Lucene99HnswVectorsFormat
//              yield new Lucene912Codec() {
//                @Override
//                public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
//                  return new Lucene99HnswScalarQuantizedVectorsFormat(
////                      hnswParams.maxConn,
////                      hnswParams.beamWidth,
////                      // TODO -CHANGE IT
////                      16,
//////                      hnswParams.scalarQuantization
//////                          ? new Lucene99ScalarQuantizedVectorsFormat()
//////                          : null,
//////                      hnswParams.numThreads,
////                      hnswParams.numThreads == 1
////                          ? null
////                          : Executors.newFixedThreadPool(hnswParams.numThreads)
//                  );
                };
            };
            // todo - how to do it?? and why
//            case SANDBOX_VAMANA -> {
//              var vamanaParams = (VamanaBuildParameters) buildParams;
//              yield new Lucene912Codec() {
//                @Override
//                public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
//                  return new VectorSandboxVamanaVectorsFormat(
//                      vamanaParams.maxConn,
//                      vamanaParams.beamWidth,
//                      vamanaParams.alpha,
//                      vamanaParams.pqFactor,
//                      vamanaParams.inGraphVectors,
//                      vamanaParams.scalarQuantization
//                          ? new VectorSandboxScalarQuantizedVectorsFormat()
//                          : null,
//                      vamanaParams.forceMerge ? vamanaParams.numThreads : 1,
//                      vamanaParams.numThreads == 1 || !vamanaParams.forceMerge
//                          ? null
//                          : Executors.newFixedThreadPool(vamanaParams.numThreads));
//                }
//              };
//            }
//          };

      var shouldMerge = new AtomicBoolean(false);
//      var mergePolicy =
//          new MergePolicy() {
//
//            @Override
//            public MergeSpecification findMerges(
//                MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext)
//                throws IOException {
//              System.out.println("findMerges triggered");
//
//              if (!shouldMerge.get()) {
//                System.out.println("shouldMerge is false, skipping");
//                return null;
//              }
//
//              var infos = segmentInfos.asList();
//              System.out.println("infos = " + infos);
//              System.out.println("infos.size() = " + infos.size());
//
//              if (infos.size() == 1) {
//                System.out.println("only one segment, skipping");
//                return null;
//              }
//
//              var merge = new OneMerge(infos);
//              var spec = new MergeSpecification();
//              spec.add(merge);
//              return spec;
//            }
//
//            @Override
//            public MergeSpecification findForcedMerges(
//                SegmentInfos segmentInfos,
//                int i,
//                Map<SegmentCommitInfo, Boolean> map,
//                MergeContext mergeContext)
//                throws IOException {
//              System.out.println("findForcedMerges triggered");
//
//              if (!shouldMerge.get()) {
//                System.out.println("shouldMerge is false, skipping");
//                return null;
//              }
//
//              var infos = segmentInfos.asList();
//              System.out.println("infos = " + infos);
//              System.out.println("infos.size() = " + infos.size());
//
//              if (infos.size() == 1) {
//                System.out.println("only one segment, skipping");
//                return null;
//              }
//
//              var merge = new OneMerge(infos);
//              var spec = new MergeSpecification();
//              spec.add(merge);
//              return spec;
//            }
//
//            @Override
//            public MergeSpecification findForcedDeletesMerges(
//                SegmentInfos segmentInfos, MergeContext mergeContext) throws IOException {
//              System.out.println("findForcedDeletesMerges triggered");
//              return null;
//            }
//          };

      var writer =
          new IndexWriter(
              directory,
              new IndexWriterConfig()
                  .setCodec(codec)
//                  .setUseCompoundFile(false)
//                  .setMaxBufferedDocs(1000000000)
//                  .setRAMBufferSizeMB(40 * 1024)
//                  .setMergePolicy(mergePolicy)
//                  .setMergeScheduler(new SerialMergeScheduler())
          );

      this.vectors = vectors;
      this.directory = directory;
      this.writer = writer;
      this.shouldMerge = shouldMerge;
      this.provider = provider;
      this.buildParams = buildParams;
      this.similarityFunction = similarity;
//      this.dataSet = dataSet;
      
//      return new LuceneIndex.Builder(
//          dataSet, vectors, directory, writer, shouldMerge, provider, buildParams, similarity);
    }

    @Override
    public BuildSummary build() throws IOException {
      System.out.println("vectors size = " + vectors.size());
      var size = this.vectors.size();
      var numThreads =
          switch (buildParams) {
            case HnswBuildParameters params -> params.numThreads;
            case VamanaBuildParameters params -> params.numThreads;
          };

      var hnswParams2 = (HnswBuildParameters) buildParams;
      int indexedDoc = 0;
      var start = Instant.now();
//      IndexWriterConfig iwc = new IndexWriterConfig()
//              .setCodec(
//                      new Lucene912Codec() {
//                        @Override
//                        public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
//                          return new Lucene99HnswVectorsFormat(hnswParams2.maxConn, hnswParams2.beamWidth);
//                        }
//                      });
//      try (IndexWriter iw = new IndexWriter(dir, iwc)) {
//        while (vectorProvider.nextDoc() != NO_MORE_DOCS) {
//          while (indexedDoc < vectorProvider.docID()) {
//            // increment docId in the index by adding empty documents
//            iw.addDocument(new Document());
//            indexedDoc++;
//          }
//          Document doc = new Document();
//          // System.out.println("Got: " + v2.vectorValue()[0] + ":" + v2.vectorValue()[1] + "@" + v2.docID());
//          doc.add(new KnnFloatVectorField("field", vectorProvider.vectorValue(), similarityFunction));
//          doc.add(new StoredField("id", vectorProvider.docID()));
//          iw.addDocument(doc);
//          indexedDoc++;
//        }
//      }
      var end = Instant.now();
      System.out.println("index 1:" + Duration.between(start, end).getSeconds());
      
      var buildStart = Instant.now();

//      new CustomVectorProvider(dataSet.)
//        while (vectors. != NO_MORE_DOCS) {
//          while (indexedDoc < vectorProvider.docID()) {
//            // increment docId in the index by adding empty documents
//            iw.addDocument(new Document());
//            indexedDoc++;
//          }
//          Document doc = new Document();
//          // System.out.println("Got: " + v2.vectorValue()[0] + ":" + v2.vectorValue()[1] + "@" + v2.docID());
//          doc.add(new KnnVectorField("field", vectorProvider.vectorValue(), similarityFunction));
//          doc.add(new StoredField("id", vectorProvider.docID()));
//          iw.addDocument(doc);
//          indexedDoc++;
//        }
      
//      try (var pool = new ForkJoinPool(numThreads)) {
        try (var progress = ProgressBar.create("building", size)) {
//          pool.submit(
//                  () -> {
//      for (int i = 0; i < size; i++) {
        
      
                    IntStream.range(0, size)
                        .parallel()
                        .forEach(
                            i -> {
                              Exceptions.wrap(
                                  () -> {
                                    var doc = new Document();
                                    doc.add(new StoredField(ID_FIELD, i));
                                    doc.add(
                                        new KnnVectorField(
                                            VECTOR_FIELD,
                                            // todo - change it???
                                                (float[]) this.vectors.vectorValue(i),
                                            this.similarityFunction));
                                try {
                                    this.writer.addDocument(doc);
                                } catch (IOException e) {
                                  System.out.println("e = " + e);
                                    throw new RuntimeException(e);
                                }
                                  });
                              progress.inc();
//                            });
                  });
//              .join();
//        }
      }

//      }
      var buildEnd = Instant.now();

      System.out.println("index 2: " + Duration.between(start, end).getSeconds());
      
//      var merge =
//          switch (buildParams) {
//            case HnswBuildParameters params -> params.forceMerge;
//            case VamanaBuildParameters params -> params.forceMerge;
//          };
//
//      var mergeStart = Instant.now();
//      if (merge) {
//        System.out.println("merging");
//        this.shouldMerge.set(true);
//        this.writer.forceMerge(1);
//      }
//      var mergeEnd = Instant.now();

//      System.out.println("committing");
//      var commitStart = Instant.now();
//      this.writer.commit();
//      var commitEnd = Instant.now();

      return new BuildSummary(
          List.of(
              new BuildPhase("build", Duration.between(buildStart, buildEnd))
//              new BuildPhase("merge", Duration.between(mergeStart, mergeEnd))
//              new BuildPhase("commit", Duration.between(commitStart, commitEnd)))
          )
      );
    }

    public Bytes size() {
      return Bytes.ofBytes(FileUtils.sizeOfDirectory(this.directory.getDirectory().toFile()));
    }

    @Override
    public String description() {
      return buildDescription(this.provider, this.buildParams);
    }

    @Override
    public void close() throws Exception {
      this.writer.close();
      this.directory.close();
    }

    private static String buildDescription(Provider provider, BuildParameters params) {
      return String.format("lucene_%s_%s", provider.description, buildParamString(params));
    }

    private static String buildParamString(BuildParameters params) {
      return switch (params) {
        case HnswBuildParameters hnsw -> String.format(
            "maxConn:%s-beamWidth:%s-scalarQuantization:%s-numThreads:%s-forceMerge:%s",
            hnsw.maxConn,
            hnsw.beamWidth,
            hnsw.scalarQuantization,
            hnsw.numThreads,
            hnsw.forceMerge);
        case VamanaBuildParameters vamana -> String.format(
            "maxConn:%s-beamWidth:%s-alpha:%s-pqFactor:%s-inGraphVectors:%s-scalarQuantization:%s-numThreads:%s-forceMerge:%s",
            vamana.maxConn,
            vamana.beamWidth,
            vamana.alpha,
            vamana.pqFactor,
            vamana.inGraphVectors,
            vamana.scalarQuantization,
            vamana.numThreads,
            vamana.forceMerge);
      };
    }
  }

  public static final class Querier implements Index.Querier {

    private final Directory directory;
    private final IndexReader reader;
    private final IndexSearcher searcher;
    private final Provider provider;
    private final BuildParameters buildParams;
    private final QueryParameters queryParams;

    private Querier(
        Directory directory,
        IndexReader reader,
        IndexSearcher searcher,
        Provider provider,
        BuildParameters buildParams,
        QueryParameters queryParams) {
      this.directory = directory;
      this.reader = reader;
      this.searcher = searcher;
      this.provider = provider;
      this.buildParams = buildParams;
      this.queryParams = queryParams;
    }

    public static Index.Querier create(Path indexesPath, Parameters parameters) throws IOException {
      var provider = Provider.parse(parameters.type());

      var buildParams = parseBuildPrams(provider, parameters.buildParameters());
      var queryParams = parseQueryPrams(provider, parameters.queryParameters());

      var buildDescription = LuceneIndex.Builder.buildDescription(provider, buildParams);
      var path = indexesPath.resolve(buildDescription);
      Preconditions.checkArgument(path.toFile().exists(), "index does not exist at {}" +  path);

      var directory = new MMapDirectory(indexesPath.resolve(buildDescription));
      var reader = DirectoryReader.open(directory);
      var searcher = new IndexSearcher(reader);
      return new LuceneIndex.Querier(
          directory, reader, searcher, provider, buildParams, queryParams);
    }

    @Override
    public List<Integer> query(Object vectorObj, int k, boolean ensureIds) throws IOException {
      VectorFloat<?> vector = (VectorFloat<?>) vectorObj;
      var numCandidates =
          switch (queryParams) {
            case HnswQueryParameters hnsw -> hnsw.numCandidates;
            case VamanaQueryParameters vamana -> vamana.numCandidates;
          };

      // todo - .get() can be a possible bottleneck??
      var query = new KnnVectorQuery(VECTOR_FIELD, (float[]) vector.get(), numCandidates);
      var results = this.searcher.search(query, numCandidates);
//      System.out.println("results = " + results.scoreDocs[0]);
      var ids = new ArrayList<Integer>(k);

      for (int i = 0; i < k; i++) {
        var result = results.scoreDocs[i];
        var id =
// TODO           ensureIds 
//                ? this.searcher
//                    .storedFields()
//                    .document(result.doc)
//                    .getField(ID_FIELD)
//                    .numericValue()
//                    .intValue()
//                : 
        result.doc;
        ids.add(id);
      }

//      System.out.println("ids = " + ids);
      return ids;
    }

    @Override
    public String description() {
      return String.format(
          "lucene_%s_%s_%s",
          provider.description,
          LuceneIndex.Builder.buildParamString(buildParams),
          queryParamString());
    }

    @Override
    public void close() throws Exception {
      this.directory.close();
      this.reader.close();
    }

    private String queryParamString() {
      return switch (queryParams) {
        case HnswQueryParameters hnsw -> String.format("numCandidates:%s", hnsw.numCandidates);
        case VamanaQueryParameters vamana -> String.format(
            "numCandidates:%s-pqRerank:%s", vamana.numCandidates, vamana.pqRerank);
      };
    }
  }

  private static BuildParameters parseBuildPrams(
      Provider provider, Map<String, String> parameters) {
    return switch (provider) {
      case HNSW -> Records.fromMap(parameters, HnswBuildParameters.class, "build parameters");
//      case SANDBOX_VAMANA -> Records.fromMap(
//          parameters, VamanaBuildParameters.class, "build parameters");
    };
  }

  private static QueryParameters parseQueryPrams(
      Provider provider, Map<String, String> parameters) {
    return switch (provider) {
      case HNSW -> Records.fromMap(parameters, HnswQueryParameters.class, "query parameters");
//      case SANDBOX_VAMANA -> Records.fromMap(
//          parameters, VamanaQueryParameters.class, "query parameters");
    };
  }
}
