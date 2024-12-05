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

import static util.FileUtils.mkDirIfNotExists;

public class BuildBench {
    
    public static void build(Config.BuildSpec spec, Path datasetPath, Path indexesPath, Path reportsPath)
            throws Exception {
        mkDirIfNotExists(reportsPath.toFile().getName());
        
        StatsUtil.initBuildStatsCsv(STR."\{spec.provider()}-\{spec.dataset()}");
        
        var dataset = DataSetVector.load(spec.provider(), datasetPath, spec.dataset());
        var jfr =
                Optional.ofNullable(spec.runtime().get("jfr")).map(Boolean::parseBoolean).orElse(false);

        Recording recording = null;

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
                var index = Index.Builder.fromParameters(dataset, indexesPath, spec)
        ) {
            var summary = index.build();

            String description = StatsUtil.getCsvDescription(index.description());
            StatsUtil.appendToBuildCsv(
                    STR."\{spec.provider()}-\{spec.dataset()}",
                    description, summary,
                    String.valueOf(index.size())
            );

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
