package javaannbench;

import javaannbench.dataset.Datasets;
import javaannbench.index.Index;
import javaannbench.index.Index.Builder.BuildPhase;
import javaannbench.util.Bytes;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BuildBench {

  private static final Logger LOGGER = LoggerFactory.getLogger(BuildBench.class);

  public static void build(BuildSpec spec, Path datasetPath, Path indexesPath, Path reportsPath)
      throws Exception {
    var dataset = Datasets.load(datasetPath, spec.dataset());
    var jfr =
        Optional.ofNullable(spec.runtime().get("jfr")).map(Boolean::parseBoolean).orElse(false);

    Recording recording = null;
    
    // todo
    if (jfr) {
      var formatter =
          DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
              .withZone(ZoneId.of("America/Los_Angeles"));
      var jfrFileName = formatter.format(Instant.now()) + ".jfr";
      var jfrPath = reportsPath.resolve(jfrFileName);
      System.out.println("starting jfr, will log to {}" + jfrPath);

      Configuration config = Configuration.getConfiguration("profile");
      recording = new Recording(config);
      recording.setDestination(jfrPath);
      recording.setDumpOnExit(true);
      recording.start();
    }

    try (var index =
        Index.Builder.fromParameters(
            dataset, indexesPath, spec.provider(), spec.type(), spec.build())) {
      var summary = index.build();
      var totalTime =
          summary.phases().stream().map(BuildPhase::duration).reduce(Duration.ZERO, Duration::plus);

      System.out.println("completed building index for " + index.description());
//      summary
//          .phases()
//          .forEach(phase -> System.out.println("\t{} phase: {}" +  phase.description() + phase.duration()));
//      System.out.println("\ttotal time seconds and nanos: {}" +  totalTime.getSeconds() + " - " + totalTime.getNano());
//      System.out.println("\tsize: {}" +  index.size());

//      new Report(index.description(), spec, totalTime, summary.phases(), index.size())
//          .write(reportsPath);
      System.out.println("BuildBench.build");
    } finally {

      System.out.println("BuildBench.build");
      if (false) {
        recording.stop();
        recording.close();
        System.out.println("wrote jfr recording");
      }
    }
  }

  private record Report(
      String indexDescription,
      BuildSpec spec,
      Duration total,
      List<BuildPhase> phases,
      Bytes size) {

    void write(Path reportsPath) throws Exception {
      System.out.println("reportsPath = " + reportsPath);
      
      var now = Instant.now().getEpochSecond();
      var path =
          reportsPath.resolve(
              String.format("%s-build-%s-%s", now, spec.dataset(), indexDescription));
      var data =
          new String[] {
            "v1",
            indexDescription,
            spec.dataset(),
            spec.provider(),
            spec.type(),
            spec.buildString(),
            Long.toString(total.toNanos()),
            phases.stream()
                .map(phase -> phase.description() + ":" + phase.duration().toNanos())
                .collect(Collectors.joining("-")),
            Long.toString(size.toBytes()),
          };

      try (var writer = Files.newBufferedWriter(path);
          var printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
        printer.printRecord((Object[]) data);
        printer.flush();
      }
    }
  }
}
