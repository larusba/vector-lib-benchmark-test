package jvector;

import io.github.jbellis.jvector.graph.disk.FeatureId;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import jvector.util.CompressorParameters;
import jvector.util.DataSet;
import jvector.util.DataSetCreator;
import jvector.util.DownloadHelper;
import jvector.util.Hdf5Loader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import static io.github.jbellis.jvector.pq.KMeansPlusPlusClusterer.UNWEIGHTED;

public class BenchTest {
    @Test
    void testBench() throws IOException {
        System.out.println("Heap space available is " + Runtime.getRuntime().maxMemory());

        var mGrid = List.of(32); // List.of(16, 24, 32, 48, 64, 96, 128);
        var efConstructionGrid = List.of(100); // List.of(60, 80, 100, 120, 160, 200, 400, 600, 800);
        var efSearchGrid = List.of(1.0, 2.0);
        List<Function<DataSet, CompressorParameters>> buildCompression = Arrays.asList(
                ds -> new CompressorParameters.PQParameters(ds.getDimension() / 8, 256, ds.similarityFunction == VectorSimilarityFunction.EUCLIDEAN, UNWEIGHTED),
                __ -> CompressorParameters.NONE
        );
        List<Function<DataSet, CompressorParameters>> searchCompression = Arrays.asList(
                __ -> CompressorParameters.NONE,
                // ds -> new CompressorParameters.BQParameters(),
                ds -> new CompressorParameters.PQParameters(ds.getDimension() / 8, 256, ds.similarityFunction == VectorSimilarityFunction.EUCLIDEAN, UNWEIGHTED)
        );
        List<EnumSet<FeatureId>> featureSets = Arrays.asList(
                EnumSet.of(FeatureId.INLINE_VECTORS),
                EnumSet.of(FeatureId.INLINE_VECTORS, FeatureId.FUSED_ADC)
        );

        // args is list of regexes, possibly needing to be split by whitespace.
        // generate a regex that matches any regex in args, or if args is empty/null, match everything
//        var regex = args.length == 0 ? ".*" : Arrays.stream(args).flatMap(s -> Arrays.stream(s.split("\\s"))).map(s -> "(?:" + s + ")").collect(Collectors.joining("|"));

        // compile regex and do substring matching using find
//        var regex = ".*";
        var regex = "ada002-100k";
        var pattern = Pattern.compile(regex);

        // large embeddings calculated by Neighborhood Watch.  100k files by default; 1M also available
        var coreFiles = List.of(
                "ada002-100k",
                "cohere-english-v3-100k",
                "openai-v3-small-100k",
                "nv-qa-v4-100k",
                "colbert-1M",
                "gecko-100k");
        executeNw(coreFiles, pattern, buildCompression, featureSets, searchCompression, mGrid, efConstructionGrid, efSearchGrid);

        var extraFiles = List.of(
                "openai-v3-large-3072-100k",
                "openai-v3-large-1536-100k",
                "e5-small-v2-100k",
                "e5-base-v2-100k",
                "e5-large-v2-100k");
        executeNw(extraFiles, pattern, buildCompression, featureSets, searchCompression, mGrid, efConstructionGrid, efSearchGrid);

        // smaller vectors from ann-benchmarks
        var hdf5Files = List.of(
                // large files not yet supported
                // "hdf5/deep-image-96-angular.hdf5",
                // "hdf5/gist-960-euclidean.hdf5",
                "glove-25-angular.hdf5",
                "glove-50-angular.hdf5",
                "lastfm-64-dot.hdf5",
                "glove-100-angular.hdf5",
                "glove-200-angular.hdf5",
                "nytimes-256-angular.hdf5",
                "sift-128-euclidean.hdf5");
        for (var f : hdf5Files) {
            if (pattern.matcher(f).find()) {
                DownloadHelper.maybeDownloadHdf5(f);
                Grid.runAll(Hdf5Loader.load(f), mGrid, efConstructionGrid, featureSets, buildCompression, searchCompression, efSearchGrid);
            }
        }

        // 2D grid, built and calculated at runtime
        if (pattern.matcher("2dgrid").find()) {
            searchCompression = Arrays.asList(__ -> CompressorParameters.NONE,
                    ds -> new CompressorParameters.PQParameters(ds.getDimension(), 256, true, UNWEIGHTED));
            buildCompression = Arrays.asList(__ -> CompressorParameters.NONE);
            var grid2d = DataSetCreator.create2DGrid(4_000_000, 10_000, 100);
            Grid.runAll(grid2d, mGrid, efConstructionGrid, featureSets, buildCompression, searchCompression, efSearchGrid);
        }
    }

    private static void executeNw(List<String> coreFiles, Pattern pattern, List<Function<DataSet, CompressorParameters>> buildCompression, List<EnumSet<FeatureId>> featureSets, List<Function<DataSet, CompressorParameters>> compressionGrid, List<Integer> mGrid, List<Integer> efConstructionGrid, List<Double> efSearchGrid) throws IOException {
        for (var nwDatasetName : coreFiles) {
            if (pattern.matcher(nwDatasetName).find()) {
                var mfd = DownloadHelper.maybeDownloadFvecs(nwDatasetName);
                Grid.runAll(mfd.load(), mGrid, efConstructionGrid, featureSets, buildCompression, compressionGrid, efSearchGrid);
            }
        }
    }

    @Test
    void testSelectFiles() {
        Pattern pattern = Pattern.compile("ada002-100k");
        var coreFiles = List.of(
                "ada002-100k",
                "cohere-english-v3-100k",
                "openai-v3-small-100k",
                "nv-qa-v4-100k",
                "colbert-1M",
                "gecko-100k");

        for (var nwDatasetName : coreFiles) {
            if (pattern.matcher(nwDatasetName).find()) {
                System.out.println(nwDatasetName);
            }
        }
    }
}
