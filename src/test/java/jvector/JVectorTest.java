package jvector;

import javaannbench.BuildBench;
import javaannbench.util.Config;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static utils.TestUtil.*;

class JVectorTest {
    
    @BeforeAll
    public static void setUp() throws IOException {
        
    }
    
    // todo - parameterized test...
    // todo - benchmark test
    
    @Test
    void testJVectorBuild() throws Exception {
        Config.BuildSpec load = Config.BuildSpec.load("test-jvector.yml");
        
        BuildBench.build(load, datasetPath, indexesPath, reportsPath);
    }
}
