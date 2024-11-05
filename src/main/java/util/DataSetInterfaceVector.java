package util;

public interface DataSetInterfaceVector<T,V,W,Y> {
    T baseVectorsArray();
    V queryVectorsArray();
    W groundTruth();
    Y similarityFunction();
    String name();
    int getDimension();
}
