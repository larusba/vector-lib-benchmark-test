package util;

import index.Index;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static index.JVectorIndex.JVECTOR_PREFIX;
import static index.LuceneIndex.LUCENE_PREFIX;
import static util.FileUtils.mkDirIfNotExists;

public class StatsUtil {
    
    private static final String STATS_DIR = "stats/";
    
    private static final String INDEX_CONFIG_KEY = "Index configs";
    private static final String RAM_USAGE_KEY = "Ram Usage (GB)";
    private static final String AVAILABLE_MEMORY_KEY = "Available Memory (GB)";
    
    private static final String[] buildHeader = new String[]{
            INDEX_CONFIG_KEY,
            "Total Duration (sec)",
            "Phases",
            "Build Phase Duration (sec)",
            "Commit Phase Duration (sec)",
            "Merge Phase Duration (sec)",
            "Index Dir. Size",
            RAM_USAGE_KEY,
            AVAILABLE_MEMORY_KEY
    };

    private static final String[] queryHeader = new String[]{
            INDEX_CONFIG_KEY,
            "Total Duration (ns)",
            "Avg Recall",
            "Avg Precision",
            "k",
            "Total Queries",
            "Queries Per Second",
            "Avg Duration",
            "Avg Minor Faults",
            "Avg Major Faults",
            "Maximum Query Duration",
            "Maximum Minor Faults",
            "Maximum Major Faults",
            "Total Minor Faults",
            "Total Major Faults",
            RAM_USAGE_KEY,
            AVAILABLE_MEMORY_KEY
    };
    
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

    public static String convertToCSV(String[] data) {
        return Stream.of(data)
                .map(StatsUtil::escapeSpecialCharacters)
                .collect(Collectors.joining(","));
    }

    public static void writeCSV(String[] dataLines, String filename) {
        File csvOutputFile = new File(filename);
        if (csvOutputFile.exists()) {
            return;
        }
        try {
            csvOutputFile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvOutputFile, true))) {
//        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            String csv = convertToCSV(dataLines);
            bw.append(csv);
            bw.newLine();
            bw.flush();
//            Arrays.stream(dataLines)
//                    .map(StatsUtil::convertToCSV)
//                    .forEach(pw::println);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void appendToQueryCsv(
            String fileName, String indexDescriptions,
            SynchronizedDescriptiveStatistics recalls, 
            SynchronizedDescriptiveStatistics precisions, 
            boolean testOnTrain, boolean recall,
            SynchronizedDescriptiveStatistics executionDurations,
            SynchronizedDescriptiveStatistics minorFaults,
            SynchronizedDescriptiveStatistics majorFaults, boolean threadStats,
            int k

    ){
        long totalQueriesValue = recalls.getN();
        String totalQueries = String.valueOf(totalQueriesValue);

        boolean recallAndNotOnTrain = recall && !testOnTrain;
        String avgRecall = recallAndNotOnTrain ? String.valueOf(recalls.getMean()) : "";
        String avgPrecision = recallAndNotOnTrain ? String.valueOf(precisions.getMean()) : "";

        String avgDuration = STR."\{(long) executionDurations.getMean()}";
        long totalDurationValue = (long) executionDurations.getSum();
        String totalDuration = STR."\{totalDurationValue}";

        float durationInSeconds = (float) totalDurationValue / 1_000_000_000L;
        String queryPerSecond = String.valueOf(totalQueriesValue / durationInSeconds);

        boolean threadStatsAndNotOnTrain = threadStats && !testOnTrain;
        String avgMinFaults = String.valueOf(threadStatsAndNotOnTrain ? minorFaults.getMean() : "");
        String avgMajFaults = String.valueOf(threadStatsAndNotOnTrain ? majorFaults.getMean() : "");
        String maxDuration = STR."\{(long) executionDurations.getMax()} ns";
        String maxMinFaults = String.valueOf(threadStatsAndNotOnTrain ? minorFaults.getMax() : "");
        String maxMajFaults = String.valueOf(threadStatsAndNotOnTrain ? majorFaults.getMax() : "");
        String totMinFaults = String.valueOf(threadStatsAndNotOnTrain ? minorFaults.getSum() : "");
        String totMajFaults = String.valueOf(threadStatsAndNotOnTrain ? majorFaults.getSum() : "");
        String kVal = String.valueOf(k);
        String[] csvStatLine = new String[]{
                indexDescriptions,
                totalDuration,
                avgRecall,
                avgPrecision,
                kVal,
                totalQueries,
                queryPerSecond,
                avgDuration,
                avgMinFaults,
                avgMajFaults,
                maxDuration,
                maxMinFaults,
                maxMajFaults,
                totMinFaults,
                totMajFaults,
                "",
                ""
        };
        appendRamAndAvailableMemoryLines(csvStatLine, STR."query-\{fileName}");
    }

    public static void appendToBuildCsv(
            String fileName, String indexDescriptions,
            Index.Builder.BuildSummary summary, String size
    ){
        var totalTime = summary.phases().stream()
                .map(Index.Builder.BuildPhase::duration)
                .reduce(Duration.ZERO, Duration::plus);

        AtomicReference<String> buildPhase = new AtomicReference<>("");
        AtomicReference<String> commitPhase = new AtomicReference<>("");
        AtomicReference<String> mergePhase = new AtomicReference<>("");
        String phases = summary.phases().stream()
                .map(phase -> {
                    String seconds = String.valueOf(phase.duration().getSeconds());
                    Index.Builder.Phase description = phase.description();
                    
                    switch (description) {
                        case build -> buildPhase.set(seconds);
                        case commit -> commitPhase.set(seconds);
                        case merge -> mergePhase.set(seconds);
                    }
                    
                    return STR."\{description.name() }:\{seconds} sec";
                })
                .collect(Collectors.joining("; "));
        String totalDuration = String.valueOf(totalTime.getSeconds());
        String[] csvStatLine = new String[]{
                indexDescriptions,
                totalDuration,
                phases,
                buildPhase.get(),
                commitPhase.get(),
                mergePhase.get(),
                size,
                "",
                ""
        };
        
        appendRamAndAvailableMemoryLines(csvStatLine, STR."build-\{fileName}");
    }

    public static void appendRamAndAvailableMemoryLines(String[] dataLine, String fileName) {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        double conversionUnitFromByteToGB = 1024.0 * 1024 * 1024;
        double availableMemoryInGB = hal.getMemory().getAvailable() / conversionUnitFromByteToGB;
        double ramUsageInGB = (hal.getMemory().getTotal() / conversionUnitFromByteToGB) - availableMemoryInGB ;
        dataLine[dataLine.length-1] = String.valueOf(availableMemoryInGB);
        dataLine[dataLine.length-2] = String.valueOf(ramUsageInGB);

        File csvOutputFile = new File(STR."\{STATS_DIR}\{fileName}.csv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvOutputFile, true))) {
            bw.append(StatsUtil.convertToCSV(dataLine));
            bw.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void initQueryStatsCsv(String fileName){
        initStatsCsv(queryHeader, STR."query-\{fileName}");
    }

    public static void initBuildStatsCsv(String fileName){
        initStatsCsv(buildHeader, STR."build-\{fileName}");
    }

    public static void initStatsCsv(String[] header, String fileName){
        mkDirIfNotExists(STATS_DIR);
        
        writeCSV(header, STR."\{STATS_DIR}\{fileName}.csv" );
    }

    public static String getCsvDescription(String index) {
        return index
                .replace(LUCENE_PREFIX, "")
                .replace(JVECTOR_PREFIX, "");
    }

}
