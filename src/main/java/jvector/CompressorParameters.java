package jvector;

import io.github.jbellis.jvector.pq.BinaryQuantization;
import io.github.jbellis.jvector.pq.ProductQuantization;
import io.github.jbellis.jvector.pq.VectorCompressor;

public abstract class CompressorParameters {
    public static final CompressorParameters NONE = new NoCompressionParameters();

    public boolean supportsCaching() {
        return false;
    }

    public String idStringFor(DataSetJVector ds) {
        // only required when supportsCaching() is true
        throw new UnsupportedOperationException();
    }

    public abstract VectorCompressor<?> computeCompressor(DataSetJVector ds);

    public static class PQParameters extends CompressorParameters {
        private final int m;
        private final int k;
        private final boolean isCentered;
        private final float anisotropicThreshold;

        public PQParameters(int m, int k, boolean isCentered, float anisotropicThreshold) {
            this.m = m;
            this.k = k;
            this.isCentered = isCentered;
            this.anisotropicThreshold = anisotropicThreshold;
        }

        @Override
        public VectorCompressor<?> computeCompressor(DataSetJVector ds) {
            return ProductQuantization.compute(ds.getBaseRavv(), m, k, isCentered, anisotropicThreshold);
        }

        @Override
        public String idStringFor(DataSetJVector ds) {
            return String.format("PQ_%s_%d_%d_%s_%s", ds.name, m, k, isCentered, anisotropicThreshold);
        }

        @Override
        public boolean supportsCaching() {
            return true;
        }
    }

    public static class BQParameters extends CompressorParameters {
        @Override
        public VectorCompressor<?> computeCompressor(DataSetJVector ds) {
            return new BinaryQuantization(ds.getDimension());
        }
    }

    private static class NoCompressionParameters extends CompressorParameters {
        @Override
        public VectorCompressor<?> computeCompressor(DataSetJVector ds) {
            return null;
        }
    }
}
