package benchmark;

import io.prometheus.client.CollectorRegistry;
import util.ProgressBar;
import index.Index;
import util.Config;
import util.Exceptions;
import com.google.common.base.Preconditions;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jvector.DataSetJVector;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OSThread;
import util.DataSetVector;
import util.DataSetLucene;
import util.StatsUtil;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class QueryBench {

    private static final int DEFAULT_WARMUP_ITERATIONS = 1;
    private static final int DEFAULT_TEST_ITERATIONS = 2;
    private static final int DEFAULT_BLOCK_DEVICE_STATS_INTERVAL_MS = 10;


    public static void test(Config.QuerySpec spec, Path datasetsPath, Path indexesPath, Path reportsPath)
            throws Exception {
        var dataset = DataSetVector.load(spec.provider(), datasetsPath, spec.dataset());
        try (
                var index = Index.Querier
                        .fromParameters(dataset, indexesPath, spec.provider(), spec.build(), spec.query())
        ) {

            var queryThreads = queryThreads(spec.runtime());
            var concurrent = queryThreads != 1;
            var systemInfo = new SystemInfo();
            var warmup = warmup(spec.runtime());
            var test = test(spec.runtime());
            var testOnTrain = testOnTrain(spec.runtime());
            var trainTestQueries = trainTestQueries(spec.runtime());
            var k = spec.k();
            var jfr = jfr(spec.runtime());
            var recall = recall(spec.runtime());
            var recallWar = recall(spec.runtime());
            var threadStats = threadStats(spec.runtime());
            var random = random(spec.runtime());
            var numQueries = testOnTrain ? trainTestQueries : getSize(dataset);
            var queries = new ArrayList(numQueries);

            Preconditions.checkArgument(!(testOnTrain && recall));
            try (var prom = startPromServer(spec, numQueries * test)) {

                for (int i = 0; i < numQueries; i++) {
                    Object vectorFloat = getVectorFloat(testOnTrain, dataset, random, i);
                    queries.add(vectorFloat);
                }

                var recalls = new SynchronizedDescriptiveStatistics();
                var executionDurations = new SynchronizedDescriptiveStatistics();
                var minorFaults = new SynchronizedDescriptiveStatistics();
                var majorFaults = new SynchronizedDescriptiveStatistics();

                try (var pool = new ForkJoinPool(queryThreads)) {
                    try (var progress = ProgressBar.create("warmup", warmup * numQueries)) {
                        if (concurrent) {
                            pool.submit(
                                    () -> IntStream.range(0, warmup)
                                            .parallel()
                                            .forEach(
                                                    _ -> IntStream.range(0, numQueries)
                                                            .parallel()
                                                            .forEach(j -> Exceptions.wrap(
                                                                    () -> {
                                                                        var query = queries.get(j);
                                                                        index.query(query, k, recallWar);
                                                                        progress.inc();
                                                                    })
                                                            )
                                            )
                                    ).join();
                        } else {
                            for (int i = 0; i < warmup; i++) {
                                for (int j = 0; j < numQueries; j++) {
                                    var query = queries.get(j);
                                    index.query(query, k, recall);
                                    progress.inc();
                                }
                            }
                        }
                    }

                    Recording recording = null;
                    if (jfr) {
                        var jfrPath = reportsPath.resolve(
                                String.format(
                                        "%s_%s-query-%s-%s-%s-k:%s.jfr",
                                        spec.provider(),
                                        Instant.now().getEpochSecond(),
                                        spec.dataset(),
                                        spec.buildString(),
                                        spec.queryString(),
                                        spec.k()
                                )
                        );
                        System.out.println("starting jfr, will dump to {}" + jfrPath);
                        Configuration config = Configuration.getConfiguration("profile");
                        recording = new Recording(config);
                        recording.setDestination(jfrPath);
                        recording.setDumpOnExit(true);
                        recording.start();
                    }

                    try (var progress = ProgressBar.create("testing", test * numQueries)) {
                        if (concurrent) {
                            pool.submit(
                                    () -> IntStream.range(0, test)
                                            .parallel()
                                            .forEach(i -> IntStream
                                                    .range(0, numQueries)
                                                    .parallel()
                                                    .forEach(
                                                            j -> Exceptions.wrap(
                                                                    () -> prepairRunQuery(spec, j, queries, dataset, index, systemInfo, recalls, executionDurations, minorFaults, majorFaults, concurrent, recall, threadStats, progress, prom)
                                                            )
                                                    )
                                            )
                                    ).join();
                        } else {
                            for (int i = 0; i < test; i++) {
                                for (int j = 0; j < numQueries; j++) {
                                    prepairRunQuery(spec, j, queries, dataset, index, systemInfo, recalls, executionDurations, minorFaults, majorFaults, concurrent, recall, threadStats, progress, prom);
                                }
                            }
                        }
                    }
                    if (jfr) {
                        System.out.println("wrote jfr recording");
                        recording.stop();
                        recording.close();
                    }
                }

                String fileName = STR."\{spec.provider()}-\{spec.dataset()}";
                StatsUtil.appendToQueryCsv(
                        fileName ,
                        index.description(), recalls, testOnTrain,
                        recall, executionDurations, minorFaults,
                        majorFaults, threadStats, spec.k()
                );

//                System.out.println("completed recall test for {}:" + index.description());
//                System.out.println("\ttotal queries {}" + recalls.getN());
//                if (recall && !testOnTrain) {
//                    System.out.println("\taverage recall {}" + recalls.getMean());
//                    System.out.println("\trecall {}" + recalls.getMean() * recalls.getN());
//                }
//                System.out.println("\taverage duration in ns {}" + executionDurations.getMean());
//                System.out.println("\ttotal duration in ns {}" + executionDurations.getSum());
//
//                if (threadStats && !testOnTrain) {
//                    System.out.println("\taverage minor faults {}" + minorFaults.getMean());
//                    System.out.println("\taverage major faults {}" + majorFaults.getMean());
//                }
//                System.out.println("\tmax duration {}" + Duration.ofNanos((long) executionDurations.getMax()));
//                if (threadStats && !testOnTrain) {
//                    System.out.println("\tmax minor faults {}" + minorFaults.getMax());
//                    System.out.println("\tmax major faults {}" + majorFaults.getMax());
//                    System.out.println("\ttotal minor faults {}" + minorFaults.getSum());
//                    System.out.println("\ttotal major faults {}" + majorFaults.getSum());
//                }

            }
        }
    }

    private static void prepairRunQuery(Config.QuerySpec spec, int j, ArrayList queries, DataSetVector dataset, Index.Querier index, SystemInfo systemInfo, SynchronizedDescriptiveStatistics recalls, SynchronizedDescriptiveStatistics executionDurations, SynchronizedDescriptiveStatistics minorFaults, SynchronizedDescriptiveStatistics majorFaults, boolean concurrent, boolean collectRecall, boolean threadStats, ProgressBar progress, Prom prom) throws Exception {
        var query = queries.get(j);
        var groundTruth = getGroundTruth(j, dataset);
        int k = spec.k();

        boolean collectThreadStats = systemInfo.getOperatingSystem().getFamily() != "macOS";

        StatsCollector statsCollector =
                threadStats
                        ? (collectThreadStats && concurrent)
                        ? new ThreadStatsCollector(systemInfo)
                        : new ProcessStatsCollector(systemInfo)
                        : null;
        var startMinorFaults = 0L;
        var startMajorFaults = 0L;
        if (threadStats && collectThreadStats) {
            Preconditions.checkArgument(statsCollector.update(), "failed to update stats");
            startMinorFaults = statsCollector.minorFaults();
            startMajorFaults = statsCollector.majorFaults();
        }

        var start = Instant.now();
        var results = index.query(query, k, collectRecall);
        var end = Instant.now();

        var endMinorFaults = 0L;
        var endMajorFaults = 0L;
        if (threadStats && collectThreadStats) {
            Preconditions.checkArgument(statsCollector.update(), "failed to update thread stats");
            endMinorFaults = statsCollector.minorFaults();
            endMajorFaults = statsCollector.majorFaults();
        }

        var duration = Duration.between(start, end);
        executionDurations.addValue(duration.toNanos());

        if (collectRecall) {
//            Preconditions.checkArgument(
//                    results.size() <= k,
//                    "query %s in round %s returned %s results, expected less than k=%s",
//                    j,
//                    i,
//                    results.size(),
//                    k);

            var truePositives = getStream(groundTruth).limit(k).filter(results::contains).count();
            if (truePositives > 0) {
                System.out.println("truePositives = " + truePositives);
            }
            var recall = (double) truePositives / k;
            recalls.addValue(recall);
        }

        if (threadStats) {
            minorFaults.addValue(endMinorFaults - startMinorFaults);
            majorFaults.addValue(endMajorFaults - startMajorFaults);
        }

        prom.queryDurationSeconds.inc((double) duration.toNanos() / (1000 * 1000 * 1000));
        progress.inc();
        
        prom.queries.inc();
    }

    private static <T> T getGroundTruth(int j, DataSetVector dataset) {
        if (dataset instanceof DataSetLucene lucene) {
            return (T) lucene.groundTruth()[j];
        } else if (dataset instanceof DataSetJVector jVector) {
            return (T) jVector.groundTruth().get(j);
        } else {
            throw new RuntimeException("todo");
        }
    }

    private static <T> T getVectorFloat(boolean testOnTrain, DataSetVector dataset, Random random, int i) throws IOException {
        if (testOnTrain) {
            if (dataset instanceof DataSetLucene lucene) {
                return (T) lucene.baseVectorsArray().vectorValue(random.nextInt(lucene.baseVectorsArray().size()));
            } else if (dataset instanceof DataSetJVector jVector) {
                return (T) jVector.baseVectorsArray().get(jVector.baseVectorsArray().size());
            }
        }

        if (dataset instanceof DataSetLucene lucene) {
            return (T) lucene.queryVectorsArray().get(i);
        } else if (dataset instanceof DataSetJVector jVector) {
            return (T) jVector.queryVectorsArray().get(i);
        }
        throw new RuntimeException("Unrecognized vector dataset: " + dataset.name());
    }

    private static int getSize(DataSetVector dataset) {
        if (dataset instanceof DataSetLucene lucene) {
            return lucene.queryVectorsArray().size();
        } else if (dataset instanceof DataSetJVector jVector) {
            return jVector.queryVectorsArray().size();
        } else {
            throw new RuntimeException("Unrecognized vector size: " + dataset.name());
        }

    }

    private static <V> Stream getStream(V groundTruth) {
        if (groundTruth instanceof Collection<?> set) {
            return set.stream();
        }
        int[] groundTruthArray = (int[]) groundTruth;
        return Arrays.stream(groundTruthArray).boxed();
    }

    private static Prom startPromServer(Config.QuerySpec spec, int numQueries) throws Exception {
        DefaultExports.initialize();

        Map<String, String> labels = new HashMap<>();
        labels.put("run_id", UUID.randomUUID().toString());
        labels.put("provider", spec.provider());
        labels.put("dataset", spec.dataset());
        labels.put("k", Integer.toString(spec.k()));
        spec.build().forEach((key, value) -> labels.put("build_" + key, value));
        spec.query().forEach((key, value) -> labels.put("query_" + key, value));
        spec.runtime().forEach((key, value) -> labels.put("runtime_" + key, value));
        String[] labelNames = labels.keySet().stream().sorted().toArray(String[]::new);
        String[] labelValues =
                labels.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue)
                        .toArray(String[]::new);

        Gauge.Child queries =
                Gauge.build()
                        .labelNames(labelNames)
                        .name("queries_total")
                        .help("queries")
                        .register()
                        .labels(labelValues);

        Gauge.Child queryDurationSeconds =
                Gauge.build()
                        .labelNames(labelNames)
                        .name("query_duration_seconds")
                        .help("queries")
                        .register()
                        .labels(labelValues);

        Gauge.build()
                .labelNames(labelNames)
                .name("num_queries")
                .help("num_queries")
                .register()
                .labels(labelValues)
                .set(numQueries);

        HTTPServer server = new HTTPServer(20000);
        return new Prom(server, queries, queryDurationSeconds, labelValues);
    }

    record Prom(
            HTTPServer server, Gauge.Child queries, Gauge.Child queryDurationSeconds, String[] labels)
            implements Closeable {

        @Override
        public void close() throws IOException {
            server.close();
            CollectorRegistry.defaultRegistry.clear();
        }
    }

    private interface StatsCollector {
        boolean update();

        long minorFaults();

        long majorFaults();
    }

    private static class ThreadStatsCollector implements StatsCollector {

        private final OSThread thread;

        public ThreadStatsCollector(SystemInfo info) {
            this.thread = info.getOperatingSystem().getCurrentThread();
        }

        @Override
        public boolean update() {
            return thread.updateAttributes();
        }

        @Override
        public long minorFaults() {
            return thread.getMinorFaults();
        }

        @Override
        public long majorFaults() {
            return thread.getMajorFaults();
        }
    }

    private static class ProcessStatsCollector implements StatsCollector {

        private final OSProcess process;

        public ProcessStatsCollector(SystemInfo info) {
            this.process = info.getOperatingSystem().getCurrentProcess();
        }

        @Override
        public boolean update() {
            return process.updateAttributes();
        }

        @Override
        public long minorFaults() {
            return process.getMinorFaults();
        }

        @Override
        public long majorFaults() {
            return process.getMajorFaults();
        }
    }

    private static int queryThreads(Map<String, String> runtime) {
        return Optional.ofNullable(runtime.get("queryThreads")).map(Integer::parseInt).orElse(5);
    }

    private static int warmup(Map<String, String> runtime) {
        return Optional.ofNullable(runtime.get("warmup"))
                .map(Integer::parseInt)
                .orElse(DEFAULT_WARMUP_ITERATIONS);
    }

    private static int test(Map<String, String> runtime) {
        return Optional.ofNullable(runtime.get("test"))
                .map(Integer::parseInt)
                .orElse(DEFAULT_TEST_ITERATIONS);
    }

    private static boolean testOnTrain(Map<String, String> runtime) {
        return Optional.ofNullable(runtime.get("testOnTrain")).map(Boolean::parseBoolean).orElse(false);
    }

    private static int trainTestQueries(Map<String, String> runtime) {
        return Optional.ofNullable(runtime.get("trainTestQueries"))
                .map(Integer::parseInt)
                .orElse(100000);
    }

    private static boolean recall(Map<String, String> runtime) {
        return Optional.ofNullable(runtime.get("recall")).map(Boolean::parseBoolean).orElse(true);
    }

    private static boolean threadStats(Map<String, String> runtime) {
        return Optional.ofNullable(runtime.get("threadStats")).map(Boolean::parseBoolean).orElse(true);
    }

    private static boolean jfr(Map<String, String> runtime) {
        return Optional.ofNullable(runtime.get("jfr")).map(Boolean::parseBoolean).orElse(false);
    }

    private static Random random(Map<String, String> runtime) {
        int seed = Optional.ofNullable(runtime.get("seed")).map(Integer::parseInt).orElse(0);
        return new Random(seed);
    }

    private static Optional<String> blockDevice(Map<String, String> runtime) {
        return Optional.ofNullable(runtime.get("blockDevice"));
    }

    private static int blockDeviceStatsIntervalMs(Map<String, String> runtime) {
        return Optional.ofNullable(runtime.get("blockDeviceStatsIntervalMs"))
                .map(Integer::parseInt)
                .orElse(DEFAULT_BLOCK_DEVICE_STATS_INTERVAL_MS);
    }
}
