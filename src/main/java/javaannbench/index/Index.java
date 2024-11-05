package javaannbench.index;

import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import javaannbench.util.Bytes;
import com.google.common.base.Preconditions;
import jvector.util.DataSetJVector;
import util.DataSetInterfaceVector;
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

    Bytes size() throws IOException;

//    static Builder fromDescription(Datasets.Dataset dataset, Path indexesPath, String description)
//        throws IOException {
//      var parameters = Parameters.parse(description);
//      return fromBuilderParameters(dataset, indexesPath, parameters);
//    }

    static Builder fromParameters(
            DataSetInterfaceVector dataset,
        Path indexesPath,
        String provider,
        String type,
        Map<String, String> buildParameters)
        throws IOException {
      var parameters = new Parameters(provider, type, buildParameters);
//      return fromBuilderParameters(dataset, indexesPath, parameters);
//    }
//
//    private static Builder fromBuilderParameters(
//            DataSet dataset, Path indexesPath, Parameters parameters) throws IOException {
      var datasetPath = indexesPath.resolve(dataset.name());
      Files.createDirectories(datasetPath);
      
      
      if (dataset instanceof DataSetLucene dataSet) {
        return new LuceneIndex.Builder(
//                  dataSet,
                datasetPath, dataSet.baseVectorsArray(), dataSet.similarityFunction, parameters);
      } else if (dataset instanceof DataSetJVector dataSet) {
        return new JVectorIndex.Builder(
                datasetPath, dataSet.getBaseRavv(), dataSet.similarityFunction(), parameters);
      } else {
        throw new RuntimeException("unknown index provider: " + parameters.type);
      }
      
//      return switch (parameters.provider) {
//        case (dataset instanceof DataSetLucene dataSet) -> {
////          DataSet dataset1 = (DataSet) dataset;
//          new LuceneIndex.Builder(
////                  dataSet,
//                  datasetPath, dataSet.baseVectorsArray(), dataSet.similarityFunction, parameters);
//        }
//        // todo - instance of jvector
//        case (dataset instanceof DataSet dataSet) -> {
////        case "jvector" -> 
//          new JVectorIndex.Builder(
//                  datasetPath, (RandomAccessVectorValues) dataSet.getBaseRavv(), dataSet.similarityFunction(), parameters);
//        }
//        default -> throw new RuntimeException("unknown index provider: " + parameters.type);
//      };
    }

    record BuildSummary(List<BuildPhase> phases) {}

    record BuildPhase(String description, Duration duration) {}

    record Parameters(String provider, String type, Map<String, String> buildParameters) {

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

        return new Parameters(provider, type, buildParameters);
      }
    }
  }

  interface Querier extends Index {

    List<Integer> query(Object vector, int k, boolean ensureIds) throws IOException;

//    static Querier fromDescription(Datasets.Dataset dataset, Path indexesPath, String description)
//        throws IOException {
//      var parameters = Parameters.parse(description);
//      return fromQuerierParameters(dataset, indexesPath, parameters);
//    }

    static Querier fromParameters(
            DataSetInterfaceVector dataset,
        Path indexesPath,
        String provider,
        String type,
        Map<String, String> buildParameters,
        Map<String, String> queryParameters)
        throws IOException {
      var parameters = new Parameters(provider, type, buildParameters, queryParameters);
      return fromQuerierParameters(dataset, indexesPath, parameters);
    }

    private static Querier fromQuerierParameters(
            DataSetInterfaceVector dataset, Path indexesPath, Parameters parameters) throws IOException {
      var datasetPath = indexesPath.resolve(dataset.name());

      return switch (parameters.provider) {
        case "lucene" -> LuceneIndex.Querier.create(
            indexesPath.resolve(dataset.name()), parameters);
        case "jvector" -> JVectorIndex.Querier.create(
                (DataSetJVector) dataset, datasetPath, (VectorSimilarityFunction) dataset.similarityFunction(), dataset.getDimension(), parameters);
        default -> throw new RuntimeException("unknown index provider: " + parameters.provider);
      };
    }

    record Parameters(
        String provider,
        String type,
        Map<String, String> buildParameters,
        Map<String, String> queryParameters) {

      public static Parameters parse(String description) {
        var parts = description.split("_");
        Preconditions.checkArgument(
            parts.length == 4, "unexpected query description format: %s", description);

        var provider = parts[0];
        var type = parts[1];
        var buildParametersString = parts[2];
        var queryParametersString = parts[3];

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

        var queryParameters =
            Arrays.stream(queryParametersString.split("-"))
                .map(s -> s.split(":"))
                .peek(
                    p ->
                        Preconditions.checkArgument(
                            p.length == 2,
                            "unexpected query parameter description format: %s",
                            String.join("-", p)))
                .collect(Collectors.toMap(a -> a[0], a -> a[1]));

        return new Parameters(provider, type, buildParameters, queryParameters);
      }
    }
  }
}
