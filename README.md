# vector-lib-performance-test

This project contains tests for benchmarking nearest neighbors with JVector and Lucene which implements approximate nearest neighbor (ANN) search.

## JVector

The test suite BenchTest has **testBench** method.
In order to execute a test you can set the regex variable to `.*` for all dataset or for example `var regex = "ada002-100k";` to execute test against a single dataset.
Datasets will be downloaded from AWS and saved into `fvec` folder.
Product quantization results will be saved into `pq_cache` folder.

Before running the test, enable the Vector API using
`--add-modules jdk.incubator.vector`

Here the result for Wikipedia dataset 100k (ada002-100k)

```shell

WARNING: Using incubator modules: jdk.incubator.vector
Connected to the target VM, address: '127.0.0.1:53206', transport: 'socket'
Heap space available is 4831838208
[main] WARN software.amazon.awssdk.transfer.s3.S3TransferManager - The provided DefaultS3AsyncClient is not an instance of S3CrtAsyncClient, and thus multipart upload/download feature is not enabled and resumable file upload is not supported. To benefit from maximum throughput, consider using S3AsyncClient.crtBuilder().build() instead.
Aug 28, 2024 12:48:50 PM io.github.jbellis.jvector.vector.PanamaVectorizationProvider <init>
INFO: Preferred f32 species is 128
Aug 28, 2024 12:48:50 PM io.github.jbellis.jvector.vector.VectorizationProvider lookup
INFO: Java incubating Vector API enabled. Using PanamaVectorizationProvider.

ada002-100k: 99562 base and 9760 query vectors created, dimensions 1536
Aug 28, 2024 12:48:51 PM jvector.util.ReaderSupplierFactory open
WARNING: MemorySegmentReaderSupplier not available, falling back to MMapReaderSupplier. Reason: java.lang.ClassNotFoundException: io.github.jbellis.jvector.disk.MemorySegmentReader$Supplier
ProductQuantization(M=192, clusters=256) loaded from PQ_ada002-100k_192_256_false_-1.0
Build and write [[INLINE_VECTORS], [INLINE_VECTORS, FUSED_ADC]] in 62.67245025s
Aug 28, 2024 12:49:56 PM jvector.util.ReaderSupplierFactory open
WARNING: MemorySegmentReaderSupplier not available, falling back to MMapReaderSupplier. Reason: java.lang.ClassNotFoundException: io.github.jbellis.jvector.disk.MemorySegmentReader$Supplier
Aug 28, 2024 12:49:56 PM jvector.util.ReaderSupplierFactory open
WARNING: MemorySegmentReaderSupplier not available, falling back to MMapReaderSupplier. Reason: java.lang.ClassNotFoundException: io.github.jbellis.jvector.disk.MemorySegmentReader$Supplier
Uncompressed vectors
Using CachingGraphIndex(graph=OnDiskGraphIndex(size=99562, entryPoint=34960, features=INLINE_VECTORS,FUSED_ADC)):
 Query top 100/100 recall 0.9201 in 3.41s after 24,710,640 nodes visited
 Query top 100/200 recall 0.9586 in 4.83s after 41,278,446 nodes visited
Using CachingGraphIndex(graph=OnDiskGraphIndex(size=99562, entryPoint=34960, features=INLINE_VECTORS)):
 Query top 100/100 recall 0.9201 in 2.90s after 24,710,640 nodes visited
 Query top 100/200 recall 0.9586 in 4.54s after 41,278,446 nodes visited
Aug 28, 2024 12:50:12 PM jvector.util.ReaderSupplierFactory open
WARNING: MemorySegmentReaderSupplier not available, falling back to MMapReaderSupplier. Reason: java.lang.ClassNotFoundException: io.github.jbellis.jvector.disk.MemorySegmentReader$Supplier
ProductQuantization(M=192, clusters=256) loaded from PQ_ada002-100k_192_256_false_-1.0
ProductQuantization(M=192, clusters=256) encoded 99562 vectors [22.77 MB] in 2.00s
Using CachingGraphIndex(graph=OnDiskGraphIndex(size=99562, entryPoint=34960, features=INLINE_VECTORS,FUSED_ADC)):
 Query top 100/100 recall 0.7851 in 11.00s after 23,741,230 nodes visited
 Query top 100/200 recall 0.9391 in 9.67s after 39,449,162 nodes visited
Using CachingGraphIndex(graph=OnDiskGraphIndex(size=99562, entryPoint=34960, features=INLINE_VECTORS)):
 Query top 100/100 recall 0.7854 in 1.89s after 23,747,502 nodes visited
 Query top 100/200 recall 0.9392 in 2.48s after 39,449,366 nodes visited
Build (full res) M=32 ef=100 in 37.63s with avg degree 31.99 and 0.65 short edges
Wrote [INLINE_VECTORS] in 0.67s
Aug 28, 2024 12:51:19 PM jvector.util.ReaderSupplierFactory open
WARNING: MemorySegmentReaderSupplier not available, falling back to MMapReaderSupplier. Reason: java.lang.ClassNotFoundException: io.github.jbellis.jvector.disk.MemorySegmentReader$Supplier
Skipping Fused ADC feature when building in memory
Uncompressed vectors
Using CachingGraphIndex(graph=OnDiskGraphIndex(size=99562, entryPoint=90904, features=INLINE_VECTORS)):
 Query top 100/100 recall 0.9486 in 3.12s after 27,183,150 nodes visited
 Query top 100/200 recall 0.9757 in 5.33s after 47,031,044 nodes visited
Aug 28, 2024 12:51:27 PM jvector.util.ReaderSupplierFactory open
WARNING: MemorySegmentReaderSupplier not available, falling back to MMapReaderSupplier. Reason: java.lang.ClassNotFoundException: io.github.jbellis.jvector.disk.MemorySegmentReader$Supplier
ProductQuantization(M=192, clusters=256) loaded from PQ_ada002-100k_192_256_false_-1.0
ProductQuantization(M=192, clusters=256) encoded 99562 vectors [22.77 MB] in 1.85s
Using CachingGraphIndex(graph=OnDiskGraphIndex(size=99562, entryPoint=90904, features=INLINE_VECTORS)):
 Query top 100/100 recall 0.7947 in 1.59s after 25,934,666 nodes visited
 Query top 100/200 recall 0.9516 in 2.74s after 44,541,126 nodes visited
```

## Lucene

TODO