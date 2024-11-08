package jvector;

import benchmark.BuildBench;
import benchmark.QueryBench;
import util.Config;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import util.StatsUtil;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static util.TestUtil.*;

class JVectorTest {

    public static final String YML_CONF_PATTERN = Optional.ofNullable(System.getenv("J_VECTOR_YAML_LIST")).orElse("test-jvector-*.yml");
    public static final Set<Config.BuildSpec> BUILD_SPEC_LOAD = new HashSet<>();
    public static final Set<Config.QuerySpec> QUERY_SPEC_LOAD = new HashSet<>();

    @BeforeAll
    public static void setUp() throws IOException {
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
    void testJVectorBuild() {
        QUERY_SPEC_LOAD.stream()
                .map(spec -> STR."\{spec.provider()}-\{spec.dataset()}")
                .forEach(StatsUtil::initBuildStatsCsv);

        BUILD_SPEC_LOAD.forEach(
                load -> Assertions.assertDoesNotThrow(
                        () -> {
                            System.out.println(STR."loading \{load.dataset()} dataset...");
                            BuildBench.build(load, datasetPath, indexesPath, reportsPath);
                        }
                )
        );
    }

    @Test
    void testJVectorQuery() {
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
