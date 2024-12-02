package util;

import index.Index;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.*;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StatsUtil {

    private static final String STATS_DIR = "stats/";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd_HH:mm:ss");
    private static final String[] buildHeader = new String[]{
            "Index", "Phases", "TotDuration", "Size",
            "RamUsage", "AvailableMemory"
    };
    private static final String[] queryHeader = new String[]{
            "Index", "TotalQueries", "AvgRecall", "Recall",
            "AvgDuration", "TotDuration", "AvgMinFaults", "AvgMajFault", "MaxDuration",
            "MaxMinFaults", "MaxMajFaults", "TotMinFaults", "TotMajFaults", "k",
            "RamUsage", "AvailableMemory"
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
        } else {
            try {
                csvOutputFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
            SynchronizedDescriptiveStatistics recalls, boolean testOnTrain, boolean recall,
            SynchronizedDescriptiveStatistics executionDurations,
            SynchronizedDescriptiveStatistics minorFaults,
            SynchronizedDescriptiveStatistics majorFaults, boolean threadStats,
            int k

    ){
        String totalQueries = String.valueOf(recalls.getN());

        String avgRecall = recall && !testOnTrain ? String.valueOf(recalls.getMean()) : "";
        String totalRecall = recall && !testOnTrain ? String.valueOf(recalls.getMean() * recalls.getN()) : "";


        String avgDuration = STR."\{Duration.ofNanos((long) executionDurations.getMean())} s";

        String totalDuration = STR."\{Duration.ofNanos((long) executionDurations.getSum())} s";

        String avgMinFaults = String.valueOf(threadStats && !testOnTrain ? minorFaults.getMean() : "");
        String avgMajFaults = String.valueOf(threadStats && !testOnTrain ? majorFaults.getMean() : "");
        String maxDuration = STR."\{Duration.ofNanos((long) executionDurations.getMax())} s";
        String maxMinFaults = String.valueOf(threadStats && !testOnTrain ? minorFaults.getMax() : "");
        String maxMajFaults = String.valueOf(threadStats && !testOnTrain ? majorFaults.getMax() : "");
        String totMinFaults = String.valueOf(threadStats && !testOnTrain ? minorFaults.getSum() : "");
        String totMajFaults = String.valueOf(threadStats && !testOnTrain ? majorFaults.getSum() : "");
        String kVal = String.valueOf(k);
        String[] csvStatLine = new String[]{
                indexDescriptions,
                totalQueries,
                avgRecall,
                totalRecall,
                avgDuration ,
                totalDuration ,
                avgMinFaults,
                avgMajFaults,
                maxDuration ,
                maxMinFaults,
                maxMajFaults,
                totMinFaults,
                totMajFaults,
                kVal,
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

        String[] csvStatLine = new String[]{
                indexDescriptions,
                summary.phases().stream()
                        .map(phase -> STR."\{phase.description()}:\{phase.duration()}")
                        .collect(Collectors.joining(";")),
                STR."\{totalTime.getSeconds()} s",
                size,
                "",""
        };
        appendRamAndAvailableMemoryLines(csvStatLine, STR."build-\{fileName}");
    }

    public static void appendRamAndAvailableMemoryLines(String[] dataLine, String fileName) {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        double conversionUnitFromByteToGB = 1024.0 * 1024 * 1024;
        double availableMemoryInGB = hal.getMemory().getAvailable() / conversionUnitFromByteToGB;
        double ramUsageInGB = (hal.getMemory().getTotal() / conversionUnitFromByteToGB) - availableMemoryInGB ;
        dataLine[dataLine.length-1] = STR."\{String.valueOf(availableMemoryInGB)} GB";
        dataLine[dataLine.length-2] = STR."\{String.valueOf(ramUsageInGB)} GB";

        File csvOutputFile = new File(STR."\{STATS_DIR}\{fileName}.csv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvOutputFile, true))) {
            bw.append(StatsUtil.convertToCSV(dataLine));
            bw.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void initQueryStatsCsv(String fileName){
        StatsUtil.initStatsCsv(queryHeader, STR."query-\{fileName}");
    }

    public static void initBuildStatsCsv(String fileName){
        StatsUtil.initStatsCsv(buildHeader, STR."build-\{fileName}");
    }

    public static void initStatsCsv(String[] header, String fileName){
//        List<String[]> dummyHeader = new ArrayList<>();
//        dummyHeader.add(header);
        writeCSV(header, STR."\{STATS_DIR}\{fileName}.csv" );
    }

}
