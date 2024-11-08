package index;

import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import com.google.common.base.Preconditions;
import jvector.DataSetJVector;
import util.DataSetVector;
import util.DataSetLucene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface Index extends AutoCloseable {

  String description();

  interface Builder extends Index {
    BuildSummary build() throws IOException;

    long size() throws IOException;


    static Builder fromParameters(
            DataSetVector dataset,
        Path indexesPath,
        String provider,
        Map<String, String> buildParameters)
        throws IOException {
      var parameters = new Parameters(provider, buildParameters);
      var datasetPath = indexesPath.resolve(dataset.name());
      Files.createDirectories(datasetPath);
      
      
      if (dataset instanceof DataSetLucene dataSet) {
        return new LuceneIndex.Builder(
                datasetPath, dataSet.baseVectorsArray(), dataSet.similarityFunction, parameters);
      } else if (dataset instanceof DataSetJVector dataSet) {
        return new JVectorIndex.Builder(
                datasetPath, dataSet.getBaseRavv(), dataSet.similarityFunction(), parameters);
      } else {
        throw new RuntimeException("unknown index provider: " + parameters);
      }
    }

    record BuildSummary(List<BuildPhase> phases) {}

    record BuildPhase(String description, Duration duration) {}

    record Parameters(String provider, Map<String, String> buildParameters) {

      public static Parameters parse(String description) {
        var parts = description.split("_");
        Preconditions.checkArgument(
            parts.length == 3, "unexpected build description format: %s", description);

        var provider = parts[0];
        var type = parts[1];
        var buildParametersString = parts[2];

        var buildParameters =
            Arrays.stream(buildParametersString.split("-"))
                .map(s -> s.split(":"))
                .peek(
                    p ->
                        Preconditions.checkArgument(
                            p.length == 2,
                            "unexpected build parameter description format: %s",
                            String.join("-", p)))
                .collect(Collectors.toMap(a -> a[0], a -> a[1]));

        return new Parameters(provider, buildParameters);
      }
    }
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
                (DataSetJVector) dataset, datasetPath, (VectorSimilarityFunction) dataset.similarityFunction(), dataset.getDimension(), parameters);
        default -> throw new RuntimeException("unknown index provider: " + parameters.provider);
      };
    }

    record Parameters(
        String provider,
        Map<String, String> buildParameters,
        Map<String, String> queryParameters) { }
  }
}
