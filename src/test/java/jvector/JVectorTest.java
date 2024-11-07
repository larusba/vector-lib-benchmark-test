package jvector;

import javaannbench.BuildBench;
import javaannbench.QueryBench;
import javaannbench.util.Config;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import util.StatsUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static utils.TestUtil.*;

class JVectorTest {

    public static final String YML_LIST = System.getenv("J_VECTOR_YAML_LIST");
    public static final Set<String> TEST_LUCENE_YML = Arrays.stream(
            StringUtils.split(
                    StringUtils.isAllBlank(YML_LIST) ?  "test-jvector-glove.yml,test-jvector-gist.yml": YML_LIST,
                    ","
            )
    ).collect(Collectors.toSet());
    public static final Set<Config.BuildSpec> BUILD_SPEC_LOAD = new HashSet<>();
    public static final Set<Config.QuerySpec> QUERY_SPEC_LOAD = new HashSet<>();

    @BeforeAll
    public static void setUp() throws IOException {
        TEST_LUCENE_YML.forEach(yml -> {
            try {
                BUILD_SPEC_LOAD.add(Config.BuildSpec.load(yml));
                QUERY_SPEC_LOAD.add(Config.QuerySpec.load(yml));
            } catch (Exception e){
                System.out.println(STR."unexpected exception during the \{yml} config load...");
                System.out.println(e.getMessage());
            }
        });
    }
    
    // todo - parameterized test...
    // todo - benchmark test
    
    @Test
    @Order(1)
    void testJVectorBuild() {
        QUERY_SPEC_LOAD.stream()
                .map(spec -> STR."\{spec.provider()}-\{spec.dataset()}")
                .forEach(StatsUtil::initBuildStatsCsv);

        BUILD_SPEC_LOAD.forEach(
                load -> Assertions.assertDoesNotThrow(
                        () -> BuildBench.build(load, datasetPath, indexesPath, reportsPath)
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
