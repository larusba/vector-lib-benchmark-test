//package jvector.util;
//
//import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
//import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
//import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
//import io.github.jbellis.jvector.vector.VectorUtil;
//import io.github.jbellis.jvector.vector.types.VectorFloat;
//import javaannbench.dataset.Datasets;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.TreeSet;
//
///**
// * Taken from here: https://github.com/jbellis/jvector/blob/main/jvector-examples/src/main/java/io/github/jbellis/jvector/example/util/DataSet.java
// * 
// * Just changed {@link VectorSimilarityFunction}  to  {@link Datasets.SimilarityFunction},
// * in order to be agnostic and handle Lucene Index as well
// */
//public class DataSet2 {
//    public final String name;
//    public final Datasets.SimilarityFunction similarityFunction;
//    public final List<VectorFloat<?>> baseVectors;
//    public final List<VectorFloat<?>> queryVectors;
//    public final List<? extends Set<Integer>> groundTruth;
//    private RandomAccessVectorValues baseRavv;
//
//    public DataSet2(String name,
//                    Datasets.SimilarityFunction similarityFunction,
//                    List<VectorFloat<?>> baseVectors,
//                    List<VectorFloat<?>> queryVectors,
//                    List<? extends Set<Integer>> groundTruth)
//    {
//        if (baseVectors.isEmpty()) {
//            throw new IllegalArgumentException("Base vectors must not be empty");
//        }
//        if (queryVectors.isEmpty()) {
//            throw new IllegalArgumentException("Query vectors must not be empty");
//        }
//        if (groundTruth.isEmpty()) {
//            throw new IllegalArgumentException("Ground truth vectors must not be empty");
//        }
//
//        if (baseVectors.get(0).length() != queryVectors.get(0).length()) {
//            throw new IllegalArgumentException("Base and query vectors must have the same dimensionality");
//        }
//        if (queryVectors.size() != groundTruth.size()) {
//            throw new IllegalArgumentException("Query and ground truth lists must be the same size");
//        }
//
//        this.name = name;
//        this.similarityFunction = similarityFunction;
//        this.baseVectors = baseVectors;
//        this.queryVectors = queryVectors;
//        this.groundTruth = groundTruth;
//
//        System.out.format("%n%s: %d base and %d query vectors created, dimensions %d%n",
//                name, baseVectors.size(), queryVectors.size(), baseVectors.get(0).length());
//    }
//
//    /**
//     * Return a dataset containing the given vectors, scrubbed free from zero vectors and normalized to unit length.
//     * Note: This only scrubs and normalizes for dot product similarity.
//     */
//    public static DataSet2 getScrubbedDataSet(String pathStr,
//                                              VectorSimilarityFunction vsf,
//                                              float[][] baseVectors,
//                                              float[][] queryVectors,
//                                              List<Set<Integer>> groundTruth)
//    {
//        // remove zero vectors and duplicates, noting that this will change the indexes of the ground truth answers
//        List<VectorFloat<?>> scrubbedBaseVectors;
//        List<VectorFloat<?>> scrubbedQueryVectors;
//        List<HashSet<Integer>> gtSet;
//        scrubbedBaseVectors = new ArrayList<>(baseVectors.length);
//        scrubbedQueryVectors = new ArrayList<>(queryVectors.length);
//        gtSet = new ArrayList<>(groundTruth.size());
//        var uniqueVectors = new TreeSet<VectorFloat<?>>((a, b) -> {
//            assert a.length() == b.length();
//            for (int i = 0; i < a.length(); i++) {
//                if (a.get(i) < b.get(i)) {
//                    return -1;
//                }
//                if (a.get(i) > b.get(i)) {
//                    return 1;
//                }
//            }
//            return 0;
//        });
//        Map<Integer, Integer> rawToScrubbed = new HashMap<>();
//        {
//            int j = 0;
//            for (int i = 0; i < baseVectors.length; i++) {
//                VectorFloat<?> v = baseVectors[i];
//                var valid = (vsf == VectorSimilarityFunction.EUCLIDEAN) || Math.abs(normOf(v)) > 1e-5;
//                if (valid && uniqueVectors.add(v)) {
//                    scrubbedBaseVectors.add(v);
//                    rawToScrubbed.put(i, j++);
//                }
//            }
//        }
//        // also remove zero query vectors
//        for (int i = 0; i < queryVectors.size(); i++) {
//            VectorFloat<?> v = queryVectors.get(i);
//            var valid = (vsf == VectorSimilarityFunction.EUCLIDEAN) || Math.abs(normOf(v)) > 1e-5;
//            if (valid) {
//                scrubbedQueryVectors.add(v);
//                var gt = new HashSet<Integer>();
//                for (int j : groundTruth.get(i)) {
//                    gt.add(rawToScrubbed.get(j));
//                }
//                gtSet.add(gt);
//            }
//        }
//
//        // now that the zero vectors are removed, we can normalize if it looks like they aren't already
//        if (vsf == VectorSimilarityFunction.DOT_PRODUCT) {
//            if (Math.abs(normOf(baseVectors.get(0)) - 1.0) > 1e-5) {
//                normalizeAll(scrubbedBaseVectors);
//                normalizeAll(scrubbedQueryVectors);
//            }
//        }
//
//        assert scrubbedQueryVectors.size() == gtSet.size();
//        return new DataSet2(pathStr, vsf, scrubbedBaseVectors, scrubbedQueryVectors, gtSet);
//    }
//
//    private static void normalizeAll(Iterable<VectorFloat<?>> vectors) {
//        for (VectorFloat<?> v : vectors) {
//            VectorUtil.l2normalize(v);
//        }
//    }
//
//    private static float normOf(VectorFloat<?> baseVector) {
//        float norm = 0;
//        for (int i = 0; i < baseVector.length(); i++) {
//            norm += baseVector.get(i) * baseVector.get(i);
//        }
//        return (float) Math.sqrt(norm);
//    }
//
//    public int getDimension() {
//        return baseVectors.get(0).length();
//    }
//
//    public RandomAccessVectorValues getBaseRavv() {
//        if (baseRavv == null) {
//            baseRavv = new ListRandomAccessVectorValues(baseVectors, getDimension());
//        }
//        return baseRavv;
//    }
//}
