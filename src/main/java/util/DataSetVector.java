package util;

import jvector.Hdf5Loader;

import java.io.IOException;
import java.nio.file.Path;

import static jvector.Hdf5Loader.getResult;

public interface DataSetVector<T,V,W,Y> {
    T baseVectorsArray();
    V queryVectorsArray();
    W groundTruth();
    Y similarityFunction();
    String name();
    int getDimension();
    
    static DataSetVector load(String provider, Path datasetsPath, String name)
            throws IOException, InterruptedException {

        String fileName = name.endsWith(".hdf5") ? name : (name + ".hdf5");
        DataSetHdf5 result = getResult(fileName);
        if (provider.equals("lucene")) {
            return Hdf5Loader.loadLucene(result);
        } else if (provider.equals("jvector")) {
            return Hdf5Loader.loadJvector(result);
        } else {
            throw new RuntimeException("ex");
        }
    }
    
    enum SimilarityFunction {
        COSINE,
        DOT_PRODUCT,
        EUCLIDEAN,
    }
}
