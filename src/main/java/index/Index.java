package index;

import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import jvector.DataSetJVector;
import util.Config;
import util.DataSetVector;
import util.DataSetLucene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static benchmark.QueryBench.queryThreads;

public interface Index extends AutoCloseable {

  String description();

  interface Builder extends Index {
    BuildSummary build() throws IOException;

    long size() throws IOException;

    static Builder fromParameters(
        DataSetVector dataset,
        Path indexesPath,
        Config.BuildSpec spec
        )
        throws IOException {
      var numThreads = queryThreads(spec.runtime());
      var parameters = new Parameters(spec.provider(), spec.build());
      var datasetPath = indexesPath.resolve(dataset.name());
      Files.createDirectories(datasetPath);
      
      if (dataset instanceof DataSetLucene dataSet) {
        return new LuceneIndex.Builder(datasetPath, dataSet.baseVectorsArray(), dataSet.similarityFunction, parameters, numThreads);
      } else if (dataset instanceof DataSetJVector dataSet) {
        return new JVectorIndex.Builder(
                datasetPath, dataSet.getBaseRavv(), dataSet.similarityFunction(), parameters, numThreads);
      } else {
        throw new RuntimeException("unknown index provider: " + parameters);
      }
    }

    record BuildSummary(List<BuildPhase> phases) {}

    record BuildPhase(Phase description, Duration duration) {}

    record Parameters(String provider, Map<String, String> buildParameters) {}
    
    enum Phase { build, commit, merge }
  }

  interface Querier extends Index {

    List<Integer> query(Object vector, int k, boolean ensureIds) throws IOException;
    
    static Querier fromParameters(
            DataSetVector dataset,
        Path indexesPath,
        String provider,
        Map<String, String> buildParameters,
        Map<String, String> queryParameters)
        throws IOException {
      var parameters = new Parameters(provider, buildParameters, queryParameters);
      var datasetPath = indexesPath.resolve(dataset.name());

      return switch (provider) {
        case "lucene" -> LuceneIndex.Querier.create(
            indexesPath.resolve(dataset.name()), parameters);
        case "jvector" -> JVectorIndex.Querier.create(
                (DataSetJVector) dataset, datasetPath, (VectorSimilarityFunction) dataset.similarityFunction(), parameters);
        default -> throw new RuntimeException("unknown index provider: " + parameters.provider);
      };
    }

    record Parameters(
        String provider,
        Map<String, String> buildParameters,
        Map<String, String> queryParameters) { }
  }
}
