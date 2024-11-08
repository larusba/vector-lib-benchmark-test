package lucene;

import benchmark.BuildBench;
import benchmark.QueryBench;
import util.Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import util.StatsUtil;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static util.TestUtil.*;


class LuceneTest {

    public static final String YML_CONF_PATTERN = Optional.ofNullable(System.getenv("LUCINE_YAML_LIST")).orElse("test-jvector-*.yml");
    public static final Set<Config.BuildSpec> BUILD_SPEC_LOAD = new HashSet<>();
    public static final Set<Config.QuerySpec> QUERY_SPEC_LOAD = new HashSet<>();

    @BeforeAll
    static void setUp() throws IOException {
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
    
    @Test
    @Order(1)
    void testLuceneBuild() throws Exception {
        BUILD_SPEC_LOAD.stream()
                .map(spec -> STR."\{spec.provider()}-\{spec.dataset()}")
                .forEach(StatsUtil::initBuildStatsCsv);
        BUILD_SPEC_LOAD.forEach(
                load -> Assertions.assertDoesNotThrow(
                        () -> BuildBench.build(load, datasetPath, indexesPath, reportsPath)
                )
        );
    }
    
    @Test
    void testLuceneQuery() throws Exception {
        QUERY_SPEC_LOAD.stream()
                .map(spec -> STR."\{spec.provider()}-\{spec.dataset()}")
                .forEach(StatsUtil::initQueryStatsCsv);
        QUERY_SPEC_LOAD.forEach(
                load -> Assertions.assertDoesNotThrow(
                        () -> QueryBench.test(load, datasetPath, indexesPath, reportsPath)
                )
        );
    }
}
