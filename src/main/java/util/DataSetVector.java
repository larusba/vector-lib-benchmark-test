package util;

import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import javaannbench.dataset.Datasets;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record DataSetVector(
        Datasets.SimilarityFunction similarityFunction,
        float[][] baseVectorsArray,
        float[][] queryVectorsArray,
        int[][] groundTruth,
        Path path
) {}
