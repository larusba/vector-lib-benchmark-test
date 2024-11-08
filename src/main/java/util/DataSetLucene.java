package util;

import org.apache.lucene.index.VectorSimilarityFunction;
import lucene.CustomVectorProvider;

public class DataSetLucene implements DataSetVector<
        CustomVectorProvider, CustomVectorProvider, int[][], VectorSimilarityFunction
        > {
    public final String name;
    public final DataSetVector.SimilarityFunction similarityFunction;
    public final CustomVectorProvider baseVectors;
    public final CustomVectorProvider queryVectors;
    public final int[][] groundTruth;

    public DataSetLucene(
            String name, DataSetVector.SimilarityFunction similarityFunction, 
            float[][] baseVectors, float[][] queryVectors, int[][] groundTruth
    ) {
        this.name = name;
        this.similarityFunction = similarityFunction;
        this.baseVectors = new CustomVectorProvider(baseVectors);
        this.queryVectors = new CustomVectorProvider(queryVectors);
        this.groundTruth = groundTruth;
    }

    @Override
    public CustomVectorProvider baseVectorsArray() {
        return baseVectors;
    }

    @Override
    public CustomVectorProvider queryVectorsArray() {
        return queryVectors;
    }

    @Override
    public int[][] groundTruth() {
        return groundTruth;
    }

    @Override
    public VectorSimilarityFunction similarityFunction() {
        return switch (similarityFunction) {
            case COSINE -> VectorSimilarityFunction.COSINE;
            case DOT_PRODUCT -> VectorSimilarityFunction.DOT_PRODUCT;
            case EUCLIDEAN -> VectorSimilarityFunction.EUCLIDEAN;
        };
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int getDimension() {
        return baseVectors.dimension();
    }

}
