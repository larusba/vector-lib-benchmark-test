package jvector;

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

class JVectorTest {

    public static final String YML_CONF_PATTERN = Optional.ofNullable(System.getenv("J_VECTOR_YAML_LIST"))
            .orElse("test-jvector-glove-3.yml");
    public static final Set<Config.BuildSpec> BUILD_SPEC_LOAD = new HashSet<>();
    public static final Set<Config.QuerySpec> QUERY_SPEC_LOAD = new HashSet<>();

    @BeforeAll
    public static void setUp() throws IOException {
        loadConfigs(BUILD_SPEC_LOAD, QUERY_SPEC_LOAD, YML_CONF_PATTERN);
    }

    @Test
    @Order(1)
    void testJVectorBuild() {
        BUILD_SPEC_LOAD.forEach(
                load -> Assertions.assertDoesNotThrow(
                        () -> BuildBench.build(load, datasetPath, indexesPath, reportsPath)
                )
        );
    }

    @Test
    void testJVectorQuery() {
        QUERY_SPEC_LOAD.forEach(
                load -> Assertions.assertDoesNotThrow(
                        () -> QueryBench.test(load, datasetPath, indexesPath, reportsPath)
                )
        );
    }

}
