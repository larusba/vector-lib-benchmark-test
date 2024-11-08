package util;

import java.nio.file.Path;

public record DataSetHdf5(
        DataSetVector.SimilarityFunction similarityFunction,
        float[][] baseVectorsArray,
        float[][] queryVectorsArray,
        int[][] groundTruth,
        Path path
) {}
