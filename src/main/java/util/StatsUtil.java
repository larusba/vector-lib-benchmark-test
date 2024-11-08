package util;

import index.Index;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.*;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    public static void writeCSV(List<String[]> dataLines, String filename) {
        File csvOutputFile = new File(STATS_DIR + filename);
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            dataLines.stream()
                    .map(StatsUtil::convertToCSV)
                    .forEach(pw::println);
        } catch (FileNotFoundException e) {
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
        String[] csvStatLine = new String[]{
                indexDescriptions,
                String.valueOf(recalls.getN()),
                recall && !testOnTrain ? String.valueOf(recalls.getMean()): "",
                recall && !testOnTrain ? String.valueOf(recalls.getMean()*recalls.getN()): "",
                String.valueOf(executionDurations.getMean()),
                String.valueOf(executionDurations.getSum()),
                String.valueOf(threadStats && !testOnTrain? minorFaults.getMean(): ""),
                String.valueOf(threadStats && !testOnTrain? majorFaults.getMean(): ""),
                String.valueOf(Duration.ofNanos((long) executionDurations.getMax())),
                String.valueOf(threadStats && !testOnTrain? minorFaults.getMax(): ""),
                String.valueOf(threadStats && !testOnTrain? majorFaults.getMax(): ""),
                String.valueOf(threadStats && !testOnTrain? minorFaults.getSum(): ""),
                String.valueOf(threadStats && !testOnTrain? majorFaults.getSum(): ""),
                String.valueOf(k),
                "",""
        };
        appendLine(csvStatLine, StringTemplate.STR."query-\{fileName}");
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
                        .map(phase -> StringTemplate.STR."\{phase.description()}:\{phase.duration()}")
                        .collect(Collectors.joining(";")),
                String.valueOf(totalTime.getNano()),
                size,
                "",""
        };
        appendLine(csvStatLine, StringTemplate.STR."build-\{fileName}");
    }

    public static void appendLine(String[] dataLine, String fileName) {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        double conversionUnitFromByteToGB = 1024.0 * 1024 * 1024;
        double availableMemoryInGB = hal.getMemory().getAvailable() / conversionUnitFromByteToGB;
        double ramUsageInGB = (hal.getMemory().getTotal() / conversionUnitFromByteToGB) - availableMemoryInGB ;
        dataLine[dataLine.length-1] = StringTemplate.STR."\{String.valueOf(availableMemoryInGB)} GB";
        dataLine[dataLine.length-2] = StringTemplate.STR."\{String.valueOf(ramUsageInGB)} GB";

        File csvOutputFile = new File(StringTemplate.STR."\{STATS_DIR}\{fileName}.csv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvOutputFile, true))) {
            bw.append(StatsUtil.convertToCSV(dataLine));
            bw.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void initQueryStatsCsv(String fileName){
        StatsUtil.initStatsCsv(queryHeader, StringTemplate.STR."query-\{fileName}");
    }

    public static void initBuildStatsCsv(String fileName){
        StatsUtil.initStatsCsv(buildHeader, StringTemplate.STR."build-\{fileName}");
    }

    public static void initStatsCsv(String[] header, String fileName){
        List<String[]> dummyHeader = new ArrayList<>();
        dummyHeader.add(header);
        StatsUtil.writeCSV(dummyHeader, StringTemplate.STR."\{fileName}.csv");
    }

//    volevo evitare l'init del csv ma ho solo abbozzato il metodo
//    private static File getStatsCsv(String fileName){
//        File csvOutputFile = new File(STR."\{STATS_DIR}\{fileName}.csv");
//        if(csvOutputFile.exists()){
//            csvOutputFile = new File(STR."\{STATS_DIR}\{fileName}_\{formatter.format(LocalDateTime.now())}.csv" );
//        }
//        return csvOutputFile;
//    }
//
//    private static void init(String provider, String dataset){
//        File csvOutputFile = StatsUtil.getStatsCsv(STR."\{provider}-\{dataset}");
//        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
//            pw.println(StatsUtil.convertToCSV(StatsUtil.getHeader(provider)));
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//    }

//    private static String[] getHeader(String[] base, String provider){
//        String[] header;
//        switch (provider){
//            case "jvector":
//                header = Arrays.copyOf(base, base.length + jvectorHeader.length);
//                System.arraycopy(jvectorHeader, 0, header, base.length, jvectorHeader.length);
//                return header;
//            case "lucene":
//                header = Arrays.copyOf(base, base.length + luceneHeader.length);
//                System.arraycopy(luceneHeader, 0, header, base.length, luceneHeader.length);
//                return header;
//            default:
//                return base;
//        }
//    }

}
