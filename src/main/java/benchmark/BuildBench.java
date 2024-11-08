package benchmark;

import index.Index;
import index.Index.Builder.BuildPhase;
import util.Config;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DataSetVector;
import util.StatsUtil;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class BuildBench {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildBench.class);

    public static void build(Config.BuildSpec spec, Path datasetPath, Path indexesPath, Path reportsPath)
            throws Exception {
        var dataset = DataSetVector.load(spec.provider(), datasetPath, spec.dataset());
        var jfr =
                Optional.ofNullable(spec.runtime().get("jfr")).map(Boolean::parseBoolean).orElse(false);

        Recording recording = null;

        // todo
        if (jfr) {
            var jfrPath = reportsPath.resolve(
                    String.format(
                            "%s_%s-build-%s-%s.jfr",
                            spec.provider(),
                            Instant.now().getEpochSecond(),
                            spec.dataset(),
                            spec.buildString()
                    )
            );
            System.out.println("starting jfr, will log to {}" + jfrPath);

            Configuration config = Configuration.getConfiguration("profile");
            recording = new Recording(config);
            recording.setDestination(jfrPath);
            recording.setDumpOnExit(true);
            recording.start();
        }

        try (
                var index = Index.Builder.fromParameters(dataset, indexesPath, spec.provider(), spec.build())
        ) {
            var summary = index.build();
            var totalTime =
                    summary.phases().stream().map(BuildPhase::duration).reduce(Duration.ZERO, Duration::plus);

            StatsUtil.appendToBuildCsv(
                    STR."\{spec.provider()}-\{spec.dataset()}",
                    index.description(), summary,
                    String.valueOf(index.size())
            );
            System.out.println("completed building index for " + index.description());
            summary
                    .phases()
                    .forEach(phase -> System.out.println("\t{} phase: {}" + phase.description() + phase.duration()));
            System.out.println("\ttotal time seconds and nanos: {}" + totalTime.getSeconds() + " - " + totalTime.getNano());
            System.out.println("\tsize: {}" + index.size());

        } catch (IllegalArgumentException iare) {
            System.out.println(iare.getMessage());
        } finally {
            if (jfr) {
                recording.stop();
                recording.close();
                System.out.println("wrote jfr recording");
            }
        }
    }
}
