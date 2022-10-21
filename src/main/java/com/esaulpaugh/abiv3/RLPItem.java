/*
   Copyright 2022 Evan Saulpaugh

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.esaulpaugh.abiv3;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * An immutable view of a portion of a (possibly mutable) byte array containing RLP-encoded data, starting at {@code index}
 * (inclusive) and ending at {@code endIndex} (exclusive), representing a single item (either a string or list). Useful
 * when decoding or otherwise manipulating RLP items.
 *
 * Created by Evo on 1/19/2017.
 */
public final class RLPItem implements Iterable<RLPItem> {

    final byte[] buffer;
    public final int index;

    public final int dataIndex;
    public final int dataLength;
    public final int endIndex;

    RLPItem(byte[] buffer, int index, int dataIndex, int dataLength, int endIndex) {
        this.buffer = buffer;
        this.index = index;
        this.dataIndex = dataIndex;
        this.dataLength = dataLength;
        this.endIndex = endIndex;
    }

    public List<RLPItem> elements() {
        List<RLPItem> arrayList = new ArrayList<>();
        RLPItem next = nextElement(dataIndex);
        while (next != null) {
            arrayList.add(next);
            next = nextElement(next.endIndex);
        }
        return arrayList;
    }

    private RLPItem nextElement(int idx) {
        if(idx >= endIndex) return null;
        return wrap(buffer, idx, endIndex);
    }

    /**
     * Returns the payload portion of this item only, and not the prefix.
     *
     * @return  the data part of the encoding
     */
    public byte[] data() {
        return Arrays.copyOfRange(buffer, dataIndex, endIndex);
    }

    public int asInt() {
        return Integers.getInt(buffer, dataIndex, dataLength);
    }

    public BigInteger asBigInt() {
        return Integers.getBigInt(buffer, dataIndex, dataLength);
    }

    public BigInteger asBigIntSigned() {
        return new BigInteger(data());
    }

    public static RLPItem wrap(byte[] buffer, int index, int containerEnd) {
        final byte lead = buffer[index];
        if(lead == V3.VERSION_IDENTIFIER && containerEnd == index + 1) {
            return null;
        }
        final DataType type = DataType.type(lead);
        switch (type) {
        case SINGLE_BYTE: return newSingleByte(buffer, index, containerEnd);
        case STRING_SHORT: return newStringShort(buffer, index, lead, containerEnd);
        case LIST_SHORT: return newListShort(buffer, index, lead, containerEnd);
        case STRING_LONG:
        case LIST_LONG: return newLongItem(lead, type, buffer, index, containerEnd);
        default: throw new AssertionError();
        }
    }

    private static RLPItem newSingleByte(byte[] buffer, int index, int containerEnd) {
        final int endIndex = requireInBounds(index + 1L, containerEnd, index);
        return new RLPItem(buffer, index, index, 1, endIndex);
    }

    private static RLPItem newStringShort(byte[] buffer, int index, byte lead, int containerEnd) {
        final int dataIndex = index + 1;
        final int dataLength = lead - DataType.STRING_SHORT.offset;
        final int endIndex = requireInBounds((long) dataIndex + dataLength, containerEnd, index);
        if (dataLength == 1 && DataType.type(buffer[dataIndex]) == DataType.SINGLE_BYTE) {
            throw new IllegalArgumentException("invalid rlp for single byte @ " + index);
        }
        return new RLPItem(buffer, index, dataIndex, dataLength, endIndex);
    }

    private static RLPItem newListShort(byte[] buffer, int index, byte lead, int containerEnd) {
        final int dataIndex = index + 1;
        final int dataLength = lead - DataType.LIST_SHORT.offset;
        final int endIndex = requireInBounds((long) dataIndex + dataLength, containerEnd, index);
        return new RLPItem(buffer, index, dataIndex, dataLength, endIndex);
    }

    private static RLPItem newLongItem(byte lead, DataType type, byte[] buffer, int index, int containerEnd) {
        final int diff = lead - type.offset;
        final int lengthIndex = index + 1;
        final int dataIndex = requireInBounds((long) lengthIndex + diff, containerEnd, index);
        final long dataLength = Integers.getLong(buffer, lengthIndex, diff);
        if(dataLength < DataType.MIN_LONG_DATA_LEN) {
            throw new IllegalArgumentException("long element data length must be " + DataType.MIN_LONG_DATA_LEN
                    + " or greater; found: " + dataLength + " for element @ " + index);
        }
        final int dataLen = requireInBounds(dataLength, containerEnd, index);
        final int endIndex = requireInBounds(dataIndex + dataLength, containerEnd, index);
        return new RLPItem(buffer, index, dataIndex, dataLen, endIndex);
    }

    private static int requireInBounds(long val, int containerEnd, int index) {
        if (val > containerEnd) {
            String msg = "element @ index " + index + " exceeds its container: " + val + " > " + containerEnd;
            throw new IllegalArgumentException(msg);
        }
        return (int) val;
    }

    @Override
    public String toString() {
        byte[] rlp = new byte[endIndex - index];
        System.arraycopy(buffer, index, rlp, 0, rlp.length);
        return new BigInteger(1, rlp).toString(16);
    }

    @Override
    public Iterator<RLPItem> iterator() {
        return new ABIv3Iterator(buffer, dataIndex, endIndex);
    }

    static final class ABIv3Iterator implements Iterator<RLPItem> {

        final byte[] buffer;
        int index;
        final int containerEnd;

        RLPItem next;

        private ABIv3Iterator(byte[] buffer, int index, int containerEnd) {
            this.buffer = buffer;
            this.index = index;
            this.containerEnd = containerEnd;
        }

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            if (index < containerEnd) {
                next = wrap(buffer, index, containerEnd);
                if(next == null) {
                    return false;
                }
                this.index = next.endIndex;
                return true;
            }
            return false;
        }

        @Override
        public RLPItem next() {
            if(hasNext()) {
                RLPItem item = next;
                next = null;
                index = item.endIndex;
                return item;
            }
            throw new NoSuchElementException();
        }

        static ABIv3Iterator sequenceIterator(byte[] buffer, int index) {
            return new ABIv3Iterator(buffer, index, buffer.length);
        }
    }
}
