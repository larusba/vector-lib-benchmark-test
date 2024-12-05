package util;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class TestUtil {
    
    public static Path workingDirectory = Path.of(System.getProperty("user.dir"));
    public static Path datasetPath = workingDirectory.resolve("datasets");
    public static Path indexesPath = workingDirectory.resolve("indexes");
    public static Path reportsPath = workingDirectory.resolve("reports");
    
    
    public static final String DATASET_FOLDER = "hdf5/";
    public static final String FILE_EXT = ".txt";
    public static final String STATS_DIR = "stats/";
    public static final String OP_CPU_LOAD = "cpuload";
    public static final String OP_RAM_USAGE = "ramusage";
    public static final String OP_AVAILABLE_MEMORY = "availableMemory";
    public static final String OP_READS = "reads";
    public static final String OP_WRITES = "reads";
    public static final String[] OPERATIONS = { OP_CPU_LOAD, OP_RAM_USAGE, OP_AVAILABLE_MEMORY, OP_READS, OP_WRITES };

    public static long getFileSizeInMB(String filename) {
        File file = new File(DATASET_FOLDER + filename);
        if (!file.isDirectory() && file.exists()) {
            return (file.length() / 1024) / 1024;
        }
        throw new RuntimeException("Input filename is not a regular file");
    }

    public static long trackTimeElapsed(Instant t0, Instant t1) {
        return Duration.between(t0, t1).toMillis();
    }

    public static long readStat(String filename) {
        List<String> result;
        try (Stream<String> lines = Files.lines(Paths.get(STATS_DIR + filename + FILE_EXT))) {
            result = lines.collect(toList());
            long[] array = result.stream().mapToLong(Long::parseLong).toArray();
            long max = Arrays.stream(array).max().getAsLong();
            long min = Arrays.stream(array).min().getAsLong();
            return max - min;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> readStats(String filename) {
        List<String> result;
        try (Stream<String> lines = Files.lines(Paths.get(STATS_DIR + filename + FILE_EXT))) {
            result = lines.collect(toList());
            long[] array = result.stream().mapToLong(Long::parseLong).toArray();
            Map<String, Object> values = Map.of(
                    "min", Arrays.stream(array).min().getAsLong(),
                    "max", Arrays.stream(array).max().getAsLong(),
                    "avg", Double.valueOf(Arrays.stream(array).average().getAsDouble()).longValue()
            );
            return values;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void loadConfigs(Set<Config.BuildSpec> BUILD_SPEC_LOAD, Set<Config.QuerySpec> QUERY_SPEC_LOAD, String YML_CONF_PATTERN) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("conf/"), YML_CONF_PATTERN)) {
            for (Path yml: stream) {
                try {
                    BUILD_SPEC_LOAD.add(Config.BuildSpec.load(yml.getFileName().toString()));
                    QUERY_SPEC_LOAD.add(Config.QuerySpec.load(yml.getFileName().toString()));
                } catch (Exception e){
                    System.out.println(STR."unexpected exception during the \{yml} config load...");
                    System.out.println(e.getMessage());
                }
            }
        }
    }

    public static void deleteFile(String filename) {
        File file = new File(STATS_DIR + filename + ".txt");
        if (file.exists() && file.delete()) {
            System.out.println("File " + filename + " DELETED");
        }
    }

    public static void deleteAllTempFiles() {
        Arrays.stream(OPERATIONS).forEach(TestUtil::deleteFile);
    }

    public static String escapeSpecialCharacters(String data) {
        if (data == null) {
            throw new IllegalArgumentException("Input data cannot be null");
        }
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public class CsvUtil {
        public static final String DATASET_SIZE_KEY = "Dataset Size";
        public static final String SINGLE_VECTOR_SIZE_KEY = "Vector Size";
        public static final String FILE_SIZE_KEY = "File Size(MB)";
        public static final String CPU_LOAD = "CPU Load(%)";
        public static final String AVG_RAM = "Average RAM(MB)";
        public static final String TOPK_KEY = "Top-k";
        public static final String QUEUE_SIZE = "Queue Size";
        public static final String ELAPSED_TIME = "Elapsed Time(ms)";
    }

}
