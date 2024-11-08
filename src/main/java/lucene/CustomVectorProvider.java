package lucene;

import org.apache.lucene.util.Bits;
import org.apache.lucene.util.hnsw.RandomAccessVectorValues;

import java.io.IOException;
import java.util.Arrays;
import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

// TODO - reuse this one similar to ListRandomAccessVectorValues ?? 

public class CustomVectorProvider implements RandomAccessVectorValues {

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
    public RandomAccessVectorValues copy() throws IOException {
        return null;
    }

    @Override
    public int ordToDoc(int ord) {
        return RandomAccessVectorValues.super.ordToDoc(ord);
    }

    @Override
    public Bits getAcceptOrds(Bits acceptDocs) {
        return RandomAccessVectorValues.super.getAcceptOrds(acceptDocs);
    }
    

    @Override
    public int dimension() {
        return data[0].length;
    }

    @Override
    public int size() {
        return data.length;
    }

//    @Override
    public float[] vectorValue() throws IOException {
        return vectorValue(doc);
    }

//    @Override
    public int docID() {
        return doc;
    }

//    @Override
    public int nextDoc() throws IOException {
        return advance(doc + 1);
    }

//    @Override
    public int advance(int target) throws IOException {
        if (target >= 0 && target < data.length) {
            doc = target;
        } else {
            doc = NO_MORE_DOCS;
        }
        return doc;
    }


    public void print(int ord) {
        System.out.println(ord + " => " + Arrays.toString(data[ord]));
    }
}
