//package javaannbench.util;
//
//import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
//import io.jhdf.api.Dataset;
//
//import java.io.IOException;
//
//// TODO - try changing with RandomAccessVectorValues of lucene...
//
//public class MMapRandomAccessVectorValues implements RandomAccessVectorValues<float[]> {
////  private final MemorySegment segment;
//  private final int size;
//  private final int dimension;
//  private final float[][] data;
// 
//  public MMapRandomAccessVectorValues(Dataset path, int size, int dimension) throws IOException {
//    /*
//    TODO
//    
//    https://github.com/jamesmudd/jhdf
//     
//    Dataset datasetByPath = new HdfFile(Path.of("datasets/glove-100-angular.hdf5")).getDatasetByPath("train");
//(float[][])datasetByPath.getData();
//     */
//    
//    // TODO - dataset has getDimensions...
//    data = (float[][]) path.getData();
//    
////    try (var channel = FileChannel.open(Path.of("a"))) {
////      this.segment =
////          channel.map(MapMode.READ_ONLY, 0, (long) size * dimension * Float.BYTES, Arena.global());
////    }
//    this.size = path.getDimensions()[0];
//    this.dimension = path.getDimensions()[1];
//  }
//
//  @Override
//  public int size() {
//    return size;
//  }
//
//  @Override
//  public int dimension() {
//    return dimension;
//  }
//
//  @Override
//  public float[] vectorValue(int targetOrd) {
//    if (targetOrd < 0 || targetOrd >= size) {
//      throw new IllegalArgumentException("Invalid ordinal");
//    }
//
//    float[] datum = data[targetOrd];
//    return datum;
//
////    float[] result = new float[dimension];
////    for (int i = 0; i < dimension; i++) {
////      result[i] =
////          segment.getAtIndex(ValueLayout.JAVA_FLOAT_UNALIGNED, (long) targetOrd * dimension + i);
////    }
////
////    return result;
//  }
//
//  @Override
//  public boolean isValueShared() {
//    return false;
//  }
//
//  @Override
//  public RandomAccessVectorValues<float[]> copy() {
//    return this;
//  }
//
//  // TODO - WHAT IS THIS???
////  public void advise(Madvise.Advice advice) {
////    Madvise.advise(this.segment, this.segment.byteSize(), advice);
////  }
//}
