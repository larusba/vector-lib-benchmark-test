package utils;

import org.apache.lucene.index.RandomAccessVectorValues;
import org.apache.lucene.index.RandomAccessVectorValuesProducer;
import org.apache.lucene.index.VectorValues;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Arrays;

// TODO - reuse this one similar to ListRandomAccessVectorValues ?? 

public class CustomVectorProvider extends VectorValues implements RandomAccessVectorValues, RandomAccessVectorValuesProducer {

    int doc = -1;
    private final float[][] data;

    public CustomVectorProvider(float[][] data) {
        this.data = data;
    }

    public float[] get(int idx) {
        return data[idx];
    }

    @Override
    public float[] vectorValue(int i) throws IOException {
        return data[i];
    }

    @Override
    public BytesRef binaryValue(int i) throws IOException {
        return null;
    }

    @Override
    public RandomAccessVectorValues randomAccess() {
        return new CustomVectorProvider(data);
    }

    @Override
    public int dimension() {
        return data[0].length;
    }

    @Override
    public int size() {
        return data.length;
    }

    @Override
    public float[] vectorValue() throws IOException {
        return vectorValue(doc);
    }

    @Override
    public int docID() {
        return doc;
    }

    @Override
    public int nextDoc() throws IOException {
        return advance(doc + 1);
    }

    @Override
    public int advance(int target) throws IOException {
        if (target >= 0 && target < data.length) {
            doc = target;
        } else {
            doc = NO_MORE_DOCS;
        }
        return doc;
    }

    @Override
    public long cost() {
        return data.length;
    }


    public void print(int ord) {
        System.out.println(ord + " => " + Arrays.toString(data[ord]));
    }
}
