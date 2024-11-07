package jvector.util;

import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;
import io.jhdf.object.datatype.FloatingPoint;
import javaannbench.dataset.Datasets;
import util.DataSetLucene;
import util.DataSetHdf5;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Taken from https://github.com/jbellis/jvector/blob/main/jvector-examples/src/main/java/io/github/jbellis/jvector/example/util/Hdf5Loader.java
 * 
 * Just changed {@link io.github.jbellis.jvector.vector.VectorSimilarityFunction}  to  {@link Datasets.SimilarityFunction},
 * in order to be agnostic and handle Lucene Index as well,
 * and returned a dataset specific for Lucene and JVector 
 */
public class Hdf5Loader {
    public static final String HDF5_DIR = "hdf5/";
    private static final VectorTypeSupport vectorTypeSupport = VectorizationProvider.getInstance().getVectorTypeSupport();


    public static DataSetLucene loadLucene(DataSetHdf5 result) {
        return new DataSetLucene(
                result.path().getFileName().toString(),
                result.similarityFunction(),
                result.baseVectorsArray(),
                result.queryVectorsArray(),
                result.groundTruth()
        );
    }
    
    public static DataSetJVector loadJvector(DataSetHdf5 result) {
        // infer the similarity
        

        VectorFloat<?>[] baseVectors;
        VectorFloat<?>[] queryVectors;
        var gtSets = new ArrayList<Set<Integer>>();
            
            baseVectors = IntStream.range(0, result.baseVectorsArray().length).parallel().mapToObj(i -> vectorTypeSupport.createFloatVector(result.baseVectorsArray()[i])).toArray(VectorFloat<?>[]::new);
            queryVectors = IntStream.range(0, result.queryVectorsArray().length).parallel().mapToObj(i -> vectorTypeSupport.createFloatVector(result.queryVectorsArray()[i])).toArray(VectorFloat<?>[]::new);
            
            gtSets = new ArrayList<>(result.groundTruth().length);
            for (int[] i : result.groundTruth()) {
                var gtSet = new HashSet<Integer>(i.length);
                for (int j : i) {
                    gtSet.add(j);
                }
                gtSets.add(gtSet);
            }

        return DataSetJVector.getScrubbedDataSet(
                result.path().getFileName().toString(), result.similarityFunction(), 
                Arrays.asList(baseVectors), Arrays.asList(queryVectors), 
                gtSets);
    }

    public static DataSetHdf5 getResult(String filename) {
        Datasets.SimilarityFunction similarityFunction = getSimilarityFunction(filename);

        // read the data
        float[][] baseVectorsArray;
        float[][] queryVectorsArray;
        int[][] groundTruth;

        Path path = Path.of(HDF5_DIR).resolve(filename);
        try (HdfFile hdf = new HdfFile(path)) {
            groundTruth = (int[][]) hdf
                    .getDatasetByPath("neighbors")
                    .getData()
            ;

            var dataset = hdf.getDatasetByPath("train");
            int[] dimensions = dataset.getDimensions();
            int batchSize = 100000; // Adjust batch size as needed
            baseVectorsArray = new float[dimensions[0]][dimensions[1]];
            for (int i = 0; i < dimensions[0]; i += batchSize) {
                long[] start = {i, 0}; // Start position for batch read
                int[] blockSize = {Math.min(batchSize, dimensions[0] - i), dimensions[1]}; // Block size for batch

                // Read a batch slice of the dataset
                float[][] batchData = (float[][]) dataset.getData(start, blockSize);
                System.arraycopy(batchData, 0, baseVectorsArray, i, batchData.length);
                // Process batchData as needed
                System.out.println("Read batch starting at row " + i);
            }




            Dataset queryDataset = hdf.getDatasetByPath("test");
            if (((FloatingPoint) queryDataset.getDataType()).getBitPrecision() == 64) {
                // lastfm dataset contains f64 queries but f32 everything else
                var doubles = ((double[][]) queryDataset.getData());
                queryVectorsArray = IntStream.range(0, doubles.length).parallel().mapToObj(i -> {
                    var a = new float[doubles[i].length];
                    for (int j = 0; j < doubles[i].length; j++) {
                        a[j] = (float) doubles[i][j];
                    }
                    return a;
                }).toArray(float[][]::new);
            } else {
                queryVectorsArray = (float[][]) queryDataset.getData();
            }
        }
        return new DataSetHdf5(similarityFunction, baseVectorsArray, queryVectorsArray, groundTruth, path);
    }

    private static Datasets.SimilarityFunction getSimilarityFunction(String filename) {
        Datasets.SimilarityFunction similarityFunction;
        if (filename.contains("-angular") || filename.contains("-dot") || filename.contains("-jaccard")) {
            return Datasets.SimilarityFunction.COSINE;
        }
        if (filename.contains("-euclidean")) {
            return Datasets.SimilarityFunction.EUCLIDEAN;
        }
        
        throw new IllegalArgumentException("Unknown similarity function -- expected angular or euclidean for " + filename);
    }
}
