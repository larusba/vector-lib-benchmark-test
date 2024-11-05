package javaannbench.dataset;

//import javaannbench.util.MMapRandomAccessVectorValues;
import jvector.util.Hdf5Loader;
import util.DataSetInterfaceVector;
import util.DataSetVector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static jvector.util.Hdf5Loader.getResult;

public enum Datasets {
  COHERE_WIKI_22_12_EN_768(
      "cohere-wiki-22-12-en-768-euclidean", 3495254, 10000, 768, SimilarityFunction.EUCLIDEAN),
  COHERE_WIKI_EN_768(
      "cohere-wiki-en-768-euclidean", 35157920, 10000, 768, SimilarityFunction.EUCLIDEAN),
  COHERE_WIKI_ES_768(
      "cohere-wiki-es-768-euclidean", 10114929, 10000, 768, SimilarityFunction.EUCLIDEAN),
  COHERE_WIKI_SIMPLE_768(
      "cohere-wiki-simple-768-euclidean", 475859, 10000, 768, SimilarityFunction.EUCLIDEAN),
  GIST_960("gist-960-euclidean", 1000000, 1000, 960, SimilarityFunction.EUCLIDEAN),
    
  GLOVE_100("glove-100-angular", 1183514, 10000, 100, SimilarityFunction.EUCLIDEAN),
  
    GLOVE_25("glove-25-angular", 1183514, 10000, 25, SimilarityFunction.COSINE),
  MNIST_784("mnist-784-euclidean", 60000, 10000, 784, SimilarityFunction.EUCLIDEAN),
  NYTIMES_256("nytimes-256-angular", 290000, 10000, 256, SimilarityFunction.COSINE),
  SIFT_128("sift-128-euclidean", 1000000, 10000, 128, SimilarityFunction.EUCLIDEAN);

  public final String name;
  public final int numTrainVectors;
  public final int numTestVectors;
  public final int dimensions;
  public final SimilarityFunction similarityFunction;

  Datasets(
      String name,
      int numTrainVectors,
      int numTestVectors,
      int dimensions,
      SimilarityFunction similarityFunction) {
    this.name = name;
    this.numTrainVectors = numTrainVectors;
    this.numTestVectors = numTestVectors;
    this.dimensions = dimensions;
    this.similarityFunction = similarityFunction;
  }

  public static DataSetInterfaceVector load(String provider, Path datasetsPath, String name)
      throws IOException, InterruptedException {
//      var description =
//              switch (name) {
//                  case "cohere-wiki-22-12-en-768-euclidean" -> COHERE_WIKI_22_12_EN_768;
//                  case "cohere-wiki-en-768-euclidean" -> COHERE_WIKI_EN_768;
//                  case "cohere-wiki-es-768-euclidean" -> COHERE_WIKI_ES_768;
//                  case "cohere-wiki-simple-768-euclidean" -> COHERE_WIKI_SIMPLE_768;
//                  case "gist-960-euclidean" -> GIST_960;
//                  case "glove-100-angular" -> GLOVE_100;
//                  case "glove-25-angular" -> GLOVE_25;
//                  case "mnist-784-euclidean" -> MNIST_784;
//                  case "nytimes-256-angular" -> NYTIMES_256;
//                  case "sift-128-euclidean" -> SIFT_128;
//                  default -> throw new RuntimeException("unknown dataset " + name);
//              };

      String fileName = "glove-100-angular.hdf5";
      DataSetVector result = getResult(fileName);
      if (provider.equals("lucene")) {
          return Hdf5Loader.loadLucene(result);
      } else if (provider.equals("jvector")) {
          return Hdf5Loader.loadJvector(result);
      } else {
          throw new RuntimeException("ex");
      }


////    S3.downloadAndExtract(datasetsPath, Path.of("datasets").resolve(name + Tarball.GZIPPED_FORMAT));
////    var datasetPath = datasetsPath.resolve(name);
//      try (HdfFile nodes = new HdfFile(Path.of("datasets").resolve("glove-100-angular.hdf5"))) {
////          var datasetPath = new HdfFile(Path.of("datasets"));
//
////          var trainPath = datasetPath.resolve("glove-100-angular.hdf5");
////    var trainPath = datasetPath.resolve("train.fvecs");
////    Preconditions.checkArgument(trainPath.toFile().exists());
//          io.jhdf.api.Dataset datasetByPath = nodes.getDatasetByPath("train");
//          var train = new MMapRandomAccessVectorValues(datasetByPath, description.numTrainVectors, description.dimensions);
//
////          var testPath = datasetPath.resolve("glove-100-angular.hdf5/test");
////    var testPath = datasetPath.resolve("test.fvecs");
////    Preconditions.checkArgument(testPath.toFile().exists());
//
//
//          var test = new MMapRandomAccessVectorValues(nodes.getDatasetByPath("test"), description.numTestVectors, description.dimensions);
//
////          var neighborsPath = datasetPath.resolve("glove-100-angular.hdf5/neighbors");
////    var neighborsPath = datasetPath.resolve("neighbors.ivecs");
////    Preconditions.checkArgument(neighborsPath.toFile().exists());
//          int[][] data = (int[][]) nodes.getDatasetByPath("neighbors").getData();
//
//          var neighbors = convertToListIntegerInteger(data);
//                  //IVecs.load((Path) datasetByPath, description.numTestVectors, 100);
//
//          SimilarityFunction similarityFunction1 = description.similarityFunction;
//          return new Dataset(
//                  name, similarityFunction1, datasetByPath.getDimensions()[1], train, test, neighbors);
//      }
  }
  
  public static List<List<Integer>> convertToListIntegerInteger(int[][] array) {
      // Convert to List<List<Integer>>
      List<List<Integer>> list = new ArrayList<>();

      for (int[] row : array) {
          // Convert each row (int[]) into a List<Integer>
          List<Integer> rowList = new ArrayList<>();
          for (int num : row) {
              rowList.add(num);
          }
          list.add(rowList);
      }
      
      return list;
  }

//    public record Dataset(
//        String name,
//        SimilarityFunction similarityFunction,
//        int dimensions,
//        MMapRandomAccessVectorValues train,
//        MMapRandomAccessVectorValues test,
//        List<List<Integer>> groundTruth) {}

    public enum SimilarityFunction {
      COSINE,
      DOT_PRODUCT,
      EUCLIDEAN,
    }
}
