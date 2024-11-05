package util;

import javaannbench.dataset.Datasets;

import java.nio.file.Path;

public record DataSetHdf5(
        Datasets.SimilarityFunction similarityFunction,
        float[][] baseVectorsArray,
        float[][] queryVectorsArray,
        int[][] groundTruth,
        Path path
) {}
