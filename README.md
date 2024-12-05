# Vector library Benchmark Tests


This project contains tests for benchmarking nearest neighbors with JVector and Lucene which implements approximate nearest neighbor (ANN) search.


It contains 2 tests which run the benchmarks, i.e. `LuceneTest` and `JVectorTest`,
that leverage some yaml files for configuration, which should be placed in the `/conf` folder,
and they print the results in some CSV named `query-<provider>-<dataset>.csv` for query tests and `build-<provider>-<dataset>.csv` for index build tests.

The  `LuceneTest` will read the file `test-lucene-*.yml` files, while the `JVectorTest` the `test-jvector-*.yml` files.

By inserting the yml config `runtime.jfr: true`, 
report files are printed for [Java Flight Recorder (JFR)](https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/about.htm#JFRUH170) 
will be generated in addition, 
with the name `<timestampMillis>-query-<dataset>-<keyConfiguration/valueConfiguration-keyConfiguration2/valueConfiguration2...>.jfr`.

Note that both `.csv` and `.jfr` files are ignored by git.

We can insert environment variables to avoid running all the configuration yml files,
namely `LUCENE_YAML_LIST` and `J_VECTOR_YAML_LIST` for the LuceneTest and JVectorTest, respectively.

The configuration yml files are structured in this way:
```yml
dataset: <datasetName>
provider: <provider>
runtime:
  jfr: <boolean>
build
  <build provider configuration>
query:
  <query provider configuration>
k: <topK>
```

where `<datasetName>` is the name or the dataset in hdf5 format, which needs to be downloaded from [this GitHub page](https://github.com/erikbern/ann-benchmarks/?tab=readme-ov-file#data-sets)
and put into `/hdf5` folder.

So far, the datasets used for this project are:

- `glove-100-angular.hdf5` (which has dimensions: 100, train size 1,183,514, test size 10,000 and use Angular Distance)
- `gist-960-euclidean.hdf5` (which has dimensions: 960, train size 1,000,000, test size 1,000 and use Euclidean Distance)
- `sift-128-euclidean` (which has dimensions: 128, train size 1,000,000, test size 10,000 and use Euclidean Distance)

All these dataset include ground truth data for the top-100 nearest neighbors.

To read hdf5 file, we use [the JHDF Java Library](https://mvnrepository.com/artifact/io.jhdf/jhdf).

The `<provider>` configuration is either 'lucene' or 'jvector'.

The `<topK>` indicates the number of vector to return in query phase.

All the queries use a [ForkJoinPool](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html) 
with a parallelism level equal to 5.

The test are executed using the java arguments: `-ea --enable-preview --add-modules jdk.incubator.vector -Xms10g -Xmx12g`,
set via pom.xml with the `<argLine>` of the `maven-surefire-plugin`.

## JVector

JVector library uses DiskANN algorithm: https://arxiv.org/pdf/2105.09613

See the following pages for more details:

- https://github.com/jbellis/jvector
- https://www.width.ai/post/unleashing-the-power-of-jvector-a-comprehensive-guide-to-the-java-embedded-vector-search-engine

### Build Configuration Options

#### `beamWidth`
This parameter controls the number of paths (or potential solutions) explored at each step in the beam search algorithm, commonly used in ANN searches.

- **High `beamWidth`**: Improves search accuracy by exploring more paths, but requires additional memory and computation.
- **Low `beamWidth`**: Reduces search complexity and memory use, though it may yield less accurate results.

#### `M`
Defines the number of clusters or codewords created or considered in each layer or level of the quantization process. Often associated with quantized data structures (like product quantization), `M` may also refer to the number of neighboring vectors each vector connects with.

- **High `M`**: May improve connectivity and retrieval precision but increases memory and processing needs.
- **Low `M`**: Reduces memory usage and indexing time but might lead to lower connectivity, affecting retrieval accuracy.

#### `neighborOverflow`
Sets a limit on the maximum number of neighbors considered or stored during the search or quantization process.

- **Purpose**: Helps manage memory usage and processing time. If the number of neighbors exceeds `neighborOverflow`, some neighbors may be discarded or deprioritized to maintain the limit.

#### `alpha`
A scaling or weighting factor used in the algorithm to adjust the impact of distance calculations, similarity measures, or other criteria in the search.

- **Use Case**: Can balance between different parts of the search cost function or modify the influence of specific features in distance calculations, offering flexibility in fine-tuning search behavior.

### Query Configuration Options

#### `numCandidates`
Influences the search’s breadth by setting the number of possible neighbors to consider.

- **High `numCandidates`**: Increases the likelihood of retrieving the closest matches but may slow down the query.
- **Low `numCandidates`**: Improves query speed and reduces memory usage, though potentially at the cost of accuracy.



For example, using the `glove-100-angular.hdf5` dataset:
```yml
dataset: glove-100-angular
provider: jvector
runtime:
  jfr: true
build:
  beamWidth: 10
  M: 1
  neighborOverflow: 1.61
  alpha: 1.8
query:
  numCandidates: 23
k: 10
```


which will generate these `.jfr` file for respectively the build and the query steps:

- `jvector_1731079196-build-sift-128-euclidean-M/1-alpha/0.32-beamWidth/88-neighborOverflow/0.43.jfr`
- `jvector_1731079347-query-sift-128-euclidean-M/1-alpha/0.8-beamWidth/10-neighborOverflow/0.61-numCandidates/3-pqFactor/15-k/10.jfr`

## Lucene

Lucene library uses HNSW algorithm: https://arxiv.org/abs/1603.09320.

See the following pages for more details:

- https://github.com/apache/lucene/blob/main/lucene/core/src/java/org/apache/lucene/util/hnsw/HnswGraphSearcher.java
- https://lucene.apache.org/core/9_1_0/core/org/apache/lucene/util/hnsw/HnswGraphSearcher.html
- https://lucene.apache.org/core/9_8_0/core/org/apache/lucene/util/hnsw/HnswGraph.html

### Build Configuration Options

#### `maxConn`
Defines the maximum number of connections (or edges) each node (vector) can have in the graph's layers. In HNSW, the graph is a multi-layered structure where nodes connect to their nearest neighbors.

- **High `maxConn`**: Increases search accuracy by allowing more connections per node, but requires more memory and increases indexing time.
- **Low `maxConn`**: Reduces memory usage and speeds up indexing, but may decrease search accuracy due to limited graph reach.

#### `beamWidth`
Controls the number of paths considered during the search. This parameter dictates how many candidate paths are followed at each step of the search.

- **High `beamWidth`**: Provides a more exhaustive search, improving accuracy at the cost of higher computational demands.
- **Low `beamWidth`**: Reduces memory and computation needs, though it may decrease retrieval accuracy.

#### `forceMerge`
Enables Lucene’s force merge process, which reduces the number of index segments by merging them into fewer or a single segment.

- **Pros**: Optimizes query performance by minimizing segment-level overhead.
- **Cons**: Resource-intensive, especially on large indexes, as it increases CPU and I/O usage. Typically used after indexing is complete to prepare for read-only querying.

### Query Configuration Options

#### `numCandidates`
The primary tuning parameter for balancing search accuracy and efficiency. This parameter influences the number of candidate nodes considered during the nearest-neighbor search.

- **High `numCandidates`**: Increases search accuracy but may slow down query performance.
- **Low `numCandidates`**: Improves query speed and reduces memory usage, though accuracy might be lower.

The optimal `numCandidates` setting will depend on your application’s need for accuracy versus acceptable latency.


For example, using the `gist-960-euclidean.hdf5` dataset:

```yml
dataset: gist-960-euclidean
provider: lucene
runtime:
  jfr: true
  testOnTrain: false
build:
  maxConn: 36
  beamWidth: 50
  scalarQuantization: false
  numThreads: 8
  forceMerge: 0
query:
  numCandidates: 23
k: 10
```

which will generate these `.jfr` file for respectively the build and the query steps:

- `lucene_1731079196-build-sift-128-euclidean-M/1-alpha/0.32-beamWidth/88-neighborOverflow/0.43.jfr`
- `lucene_1731079347-query-sift-128-euclidean-M/1-alpha/0.8-beamWidth/10-neighborOverflow/0.61-numCandidates/3-pqFactor/15-k/10.jfr`

## Results

These are the results that are obtained with a MacBook Air, with Apple M3 processor, total Number of Cores:	8, memory: 16 GB:

- CSV files for Lucene: https://github.com/user-attachments/files/17871280/stats-lucene.zip
- JFR files for Lucene: https://github.com/user-attachments/files/17681671/reports-lucene.zip
- CSV files for JVector: https://github.com/user-attachments/files/17700307/stats-jvector.zip
- JFR files for JVector: https://github.com/user-attachments/files/17700303/reports-jvector.zip


The build CSV output contains:
- `Index configs`: that is `PROVIDER-buildConfig`
- `Total Duration (sec)`
- `Phases`: duration of all phases
- `Build Phase Duration (sec)`
- `Commit Phase Duration (sec)`
- `Merge Phase Duration (sec)`
- `Index Dir. Size`
- `Ram Usage (GB)`
- `Available Memory (GB)`

The query CSV output contains:
- `Index configs`: that is `PROVIDER-<buildConfig>-<queryConfig>`
- `Total Duration (ns)`
- `Avg Recall`: the average value of `relevant results / all retrieved results` 
- `Avg Precision`: the average value of `relevant results / all available results` (i.e. the size of Neighbors of the dataset)
- `k`
- `Total Queries`
- `Queries Per Second`:  `Total Queries / Total Duration (sec)` 
- `Avg Duration`: the average duration of the queries
- `Avg Minor Faults`: in case of failures
- `Avg Major Faults`: in case of failures
- `Maximum Query Duration`
- `Maximum Minor Faults`: in case of failures
- `Maximum Major Faults`: in case of failures
- `Total Minor Faults`: in case of failures
- `Total Major Faults`: in case of failures
- `Ram Usage (GB)`
- `Available Memory (GB)`

