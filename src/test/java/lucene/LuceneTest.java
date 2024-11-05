package lucene;

import javaannbench.BuildBench;
import javaannbench.QueryBench;
import javaannbench.util.Config;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static utils.TestUtil.*;


class LuceneTest {
    public static final String TEST_LUCENE_YML = "test-lucene.yml";

    // todo - parameterized test...
    // todo - benchmark test
    
    // todo - if index exists skip else create
    
    @Test
    @Order(1)
    void testJVectorBuild() throws Exception {
        Config.BuildSpec load = Config.BuildSpec.load(TEST_LUCENE_YML);

        BuildBench.build(load, datasetPath, indexesPath, reportsPath);
    }
    
    @Test
    void testJVectorQuery() throws Exception {
        Config.QuerySpec load = Config.QuerySpec.load(TEST_LUCENE_YML);

        QueryBench.test(load, datasetPath, indexesPath, reportsPath);
    }
}
