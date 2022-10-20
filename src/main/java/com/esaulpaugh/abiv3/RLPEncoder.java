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

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class RLPEncoder {

    private RLPEncoder() {}

    public static int sumEncodedLen(Iterable<?> rawItems) {
        int sum = 0;
        for (Object raw : rawItems) {
            sum += encodedLen(raw);
        }
        return sum;
    }

    public static int encodedLen(Object raw) {
        if (raw instanceof byte[]) {
            return stringEncodedLen((byte[]) raw);
        }
        if (raw instanceof Iterable<?>) {
            return listEncodedLen((Iterable<?>) raw);
        }
        if(raw instanceof Object[]) {
            return listEncodedLen(Arrays.asList((Object[]) raw));
        }
        if(raw instanceof V3.DynamicBoolArray) {
            V3.DynamicBoolArray dyn = (V3.DynamicBoolArray) raw;
            final int lengthOfLength = stringEncodedLen(dyn.arrayLenBytes);
            if(dyn.dataBytes == null)
                return lengthOfLength;
            return lengthOfLength + stringEncodedLen(dyn.dataBytes);
        }
        if(raw == null) {
            return 0;
        }
        throw new IllegalArgumentException();
    }

    private static int stringEncodedLen(byte[] byteString) {
        final int dataLen = byteString.length;
        return itemLen(dataLen == 1 && DataType.type(byteString[0]) == DataType.SINGLE_BYTE ? 0 : dataLen);
    }

    private static int listEncodedLen(Iterable<?> items) {
        return itemLen(sumEncodedLen(items));
    }

    private static int itemLen(int dataLen) {
        return (dataLen < DataType.MIN_LONG_DATA_LEN ? 1 : 1 + Integers.len(dataLen))
                + dataLen;
    }

    private static void encodeItem(Object raw, ByteBuffer bb) {
        if (raw instanceof byte[]) {
            putString((byte[]) raw, bb);
        } else if (raw instanceof Iterable<?>) {
            Iterable<?> elements = (Iterable<?>) raw;
            encodeList(sumEncodedLen(elements), elements, bb);
        } else if(raw instanceof Object[]) {
            Iterable<?> elements = Arrays.asList((Object[]) raw);
            encodeList(sumEncodedLen(elements), elements, bb);
        } else if(raw instanceof V3.DynamicBoolArray) {
            V3.DynamicBoolArray holder = (V3.DynamicBoolArray) raw;
            putString(holder.arrayLenBytes, bb);
            if(holder.dataBytes != null) putString(holder.dataBytes, bb);
        } else if(raw == null) {
            // skip
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Puts into the destination buffer at its current position the RLP encoding of the given byte string.
     *
     * @param byteString the byte string to be encoded
     * @param dest    the destination for the sequence of RLP encodings
     */
    public static void putString(byte[] byteString, ByteBuffer dest) {
        final int dataLen = byteString.length;
        if (dataLen < DataType.MIN_LONG_DATA_LEN) {
            if (dataLen == 1) {
                encodeLen1String(byteString[0], dest);
                return;
            }
            dest.put((byte) (DataType.STRING_SHORT_OFFSET + dataLen)); // dataLen is 0 or 2-55
        } else { // long string
            dest.put((byte) (DataType.STRING_LONG_OFFSET + Integers.len(dataLen)));
            Integers.putLong(dataLen, dest);
        }
        dest.put(byteString);
    }

    private static void encodeLen1String(byte first, ByteBuffer bb) {
        if (first < 0x00) { // same as (first & 0xFF) >= 0x80
            bb.put((byte) (DataType.STRING_SHORT_OFFSET + 1));
        }
        bb.put(first);
    }

    private static void encodeList(int dataLen, Iterable<?> elements, ByteBuffer bb) {
        insertListPrefix(dataLen, bb);
        putSequence(elements, bb);
    }

    static void insertListPrefix(int dataLen, ByteBuffer bb) {
        if(dataLen < DataType.MIN_LONG_DATA_LEN) {
            bb.put((byte) (DataType.LIST_SHORT_OFFSET + dataLen));
        } else {
            bb.put((byte) (DataType.LIST_LONG_OFFSET + Integers.len(dataLen)));
            Integers.putLong(dataLen, bb);
        }
    }

    /**
     * Puts into the destination buffer at its current position the concatenation of the encodings of the given objects
     * in the given order. The {@link Iterable} containing the objects is not itself encoded.
     *
     * @param objects the raw objects to be encoded
     * @param dest    the destination for the sequence of RLP encodings
     */
    public static void putSequence(Iterable<?> objects, ByteBuffer dest) {
        for (Object raw : objects) {
            encodeItem(raw, dest);
        }
    }
}
