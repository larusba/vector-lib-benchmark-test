//package jvector.util;
//
//import io.github.jbellis.jvector.vector.VectorizationProvider;
//import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
//import io.jhdf.HdfFile;
//import io.jhdf.api.Dataset;
//import io.jhdf.object.datatype.FloatingPoint;
//import javaannbench.dataset.Datasets;
//
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.stream.IntStream;
//
///**
// * Taken from https://github.com/jbellis/jvector/blob/main/jvector-examples/src/main/java/io/github/jbellis/jvector/example/util/Hdf5Loader.java
// * 
// * Just changed {@link io.github.jbellis.jvector.vector.VectorSimilarityFunction}  to  {@link Datasets.SimilarityFunction},
// *  * in order to be agnostic and handle Lucene Index as well
// */
//public class Hdf5Loader2 {
//    public static final String HDF5_DIR = "hdf5/";
//    private static final VectorTypeSupport vectorTypeSupport = VectorizationProvider.getInstance().getVectorTypeSupport();
//
//    public static DataSet load(String filename) {
//        // infer the similarity
//        Datasets.SimilarityFunction similarityFunction;
//        if (filename.contains("-angular") || filename.contains("-dot")) {
//            similarityFunction = Datasets.SimilarityFunction.COSINE;
//        }
//        else if (filename.contains("-euclidean")) {
//            similarityFunction = Datasets.SimilarityFunction.EUCLIDEAN;
//        }
//        else {
//            throw new IllegalArgumentException("Unknown similarity function -- expected angular or euclidean for " + filename);
//        }
//
//        // read the data
////        VectorFloat<?>[] baseVectors;
////        VectorFloat<?>[] queryVectors;
//        Path path = Path.of(HDF5_DIR).resolve(filename);
//        var gtSets = new ArrayList<Set<Integer>>();
//        
//        float[][] baseVectorsArray;
//        float[][] queryVectorsArray;
//        
//        try (HdfFile hdf = new HdfFile(path)) {
//            baseVectorsArray  = (float[][]) hdf.getDatasetByPath("train").getData();
////            baseVectors = IntStream.range(0, baseVectorsArray.length).parallel().mapToObj(i -> vectorTypeSupport.createFloatVector(baseVectorsArray[i])).toArray(VectorFloat<?>[]::new);
//            Dataset queryDataset = hdf.getDatasetByPath("test");
//            if (((FloatingPoint) queryDataset.getDataType()).getBitPrecision() == 64) {
//                // lastfm dataset contains f64 queries but f32 everything else
//                var doubles = ((double[][]) queryDataset.getData());
//                queryVectorsArray = IntStream.range(0, doubles.length).parallel().mapToObj(i -> {
//                    var a = new float[doubles[i].length];
//                    for (int j = 0; j < doubles[i].length; j++) {
//                        a[j] = (float) doubles[i][j];
//                    }
//                    return a;
////                    return vectorTypeSupport.createFloatVector(a);
//                }).toArray(float[][]::new);
//            } else {
//                queryVectorsArray = (float[][]) queryDataset.getData();
////                queryVectors = IntStream.range(0, queryVectorsArray.length).parallel().mapToObj(i -> vectorTypeSupport.createFloatVector(queryVectorsArray[i])).toArray(VectorFloat<?>[]::new);
//            }
//            int[][] groundTruth = (int[][]) hdf.getDatasetByPath("neighbors").getData();
//            gtSets = new ArrayList<>(groundTruth.length);
//            for (int[] i : groundTruth) {
//                var gtSet = new HashSet<Integer>(i.length);
//                for (int j : i) {
//                    gtSet.add(j);
//                }
//                gtSets.add(gtSet);
//            }
//        }
//
//        return DataSet.getScrubbedDataSet(path.getFileName().toString(), similarityFunction, baseVectorsArray, queryVectorsArray, gtSets);
////        return DataSet.getScrubbedDataSet(path.getFileName().toString(), similarityFunction, Arrays.asList(baseVectors), Arrays.asList(queryVectors), gtSets);
//    }
//}
