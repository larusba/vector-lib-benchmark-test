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

    public static final String YML_CONF_PATTERN = Optional.ofNullable(System.getenv("LUCENE_YAML_LIST"))
            .orElse("test-lucene-glove.yml");
    public static final Set<Config.BuildSpec> BUILD_SPEC_LOAD = new HashSet<>();
    public static final Set<Config.QuerySpec> QUERY_SPEC_LOAD = new HashSet<>();

    @BeforeAll
    static void setUp() throws IOException {
        loadConfigs(BUILD_SPEC_LOAD, QUERY_SPEC_LOAD, YML_CONF_PATTERN);
    }
    
    @Test
    @Order(1)
    void testLuceneBuild() throws Exception {
        BUILD_SPEC_LOAD.forEach(
                spec -> Assertions.assertDoesNotThrow(
                        () -> BuildBench.build(spec, datasetPath, indexesPath, reportsPath)
                )
        );
    }
    
    @Test
    void testLuceneQuery() throws Exception {
        QUERY_SPEC_LOAD.forEach(
                load -> Assertions.assertDoesNotThrow(
                        () -> QueryBench.test(load, datasetPath, indexesPath, reportsPath)
                )
        );
    }
}
