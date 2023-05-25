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

import com.joemelsha.crypto.hash.Keccak;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/** Serializes and deserializes tuples through the use of RLP encoding. */
public final class V3 {

    private V3() {} // TODO keyed hashing? heuristics? compressibility?

    private static final byte[] TRUE = new byte[] { 0x1 };
    private static final byte[] FALSE = new byte[] { 0x00 };

    static final byte V3_VERSION_ID = 0;

//    private static final byte[] PREFIX = new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xde, (byte) 0xf1 };

    static final int SELECTOR_LEN = 4;

    public static byte[] toRLP(int functionNumber, V3Type[] schema, Object[] vals) {
        List<Object> tuple = new ArrayList<>();
        byte[][] header = header(functionNumber);
        if (header.length == 2) {
            tuple.add(header[1]);
        }
        tuple.addAll(Arrays.asList(serializeTuple(schema, vals)));
        ByteBuffer encoding = ByteBuffer.allocate(1 + RLPEncoder.sumEncodedLen(tuple));
        encoding.put(header[0]);
        RLPEncoder.putSequence(tuple, encoding);
        return encoding.array();
    }

    public static Object[] fromRLP(V3Type[] schema, byte[] rlp) {
        final byte zeroth = rlp[0];
        final int version = zeroth & 0b1110_0000;
        if (version != V3_VERSION_ID) {
            throw new IllegalArgumentException();
        }
        int sequenceStart = 1;
        int fnNumber = zeroth & 0b0001_1111;
        if (fnNumber >= 31) {
            final RLPItem fnNumberItem = RLPItem.wrap(rlp, 1, rlp.length);
            final DataType type = fnNumberItem.type();
            if (rlp[1] == 0x00 || type == DataType.LIST_SHORT || type == DataType.LIST_LONG) {
                throw new IllegalArgumentException("invalid function ID format");
            }
            fnNumber = fnNumberItem.asInt();
            if (fnNumber < 31) throw new IllegalArgumentException();
            sequenceStart = fnNumberItem.endIndex;
        }
        return deserializeTuple(schema, RLPItem.ABIv3Iterator.sequenceIterator(rlp, sequenceStart));
    }

    private static byte[][] header(int functionNumber) {
        if (functionNumber < 0) throw new IllegalArgumentException();
        final byte[] fnNumber = Integers.toBytes(functionNumber);
        if (functionNumber < 31) {
            if (functionNumber == 0) {
                return new byte[][] { new byte[] { 0 } };
            }
            return new byte[][] { fnNumber };
        }
        return new byte[][] { new byte[] { 0b0001_1111 }, fnNumber };
    }

    private static byte[] generateSelector(String functionName, V3Type[] schema) {
        String signature = createSignature(functionName, schema);
        byte[] signatureBytes = signature.getBytes(StandardCharsets.US_ASCII);
        signatureBytes[signatureBytes.length - 1] = (byte) 0; // zero the final ')' char to calculate v3 selector
        Keccak k = new Keccak(256); // TODO One in every 2^32 functions will hash the same in v2 and v3.
        k.update(signatureBytes);   // TODO Compilers and tools should consider such functions syntactically invalid.
        ByteBuffer selector = ByteBuffer.allocate(V3.SELECTOR_LEN);
        k.digest(selector, V3.SELECTOR_LEN);
        return selector.array();
    }

    public static String createSignature(String functionName, V3Type[] schema) {
        if (schema.length == 0) {
            return functionName + "()";
        }
        StringBuilder sb = new StringBuilder(functionName);
        sb.append('(');
        for (V3Type t : schema) {
            sb.append(t.canonicalType).append(',');
        }
        return sb.deleteCharAt(sb.length() - 1).append(')').toString(); // replace trailing comma
    }

    private static void checkSelector(byte[] expectedSelector, byte[] rlp) {
        for (int i = 0; i < expectedSelector.length; i++) {
            if (rlp[i] != expectedSelector[i]) {
                throw new IllegalArgumentException("bad selector");
            }
        }
    }

    private static Object[] serializeTuple(V3Type[] tupleType, Object[] tuple) {
        validateLength(tupleType.length, tuple.length);
        final Object[] out = new Object[tupleType.length];
        for(int i = 0; i < out.length; i++) {
            out[i] = serialize(tupleType[i], tuple[i]);
        }
        return out;
    }

    private static Object[] deserializeTuple(V3Type[] tupleType, Iterator<RLPItem> sequenceIterator) {
        final Object[] elements = new Object[tupleType.length];
        for(int i = 0; i < elements.length; i++) {
            elements[i] = deserialize(tupleType[i], sequenceIterator);
        }
        if (sequenceIterator.hasNext()) {
            throw new IllegalArgumentException("trailing unconsumed items");
        }
        return elements;
    }

    private static Object serialize(V3Type type, Object obj) {
        switch (type.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return serializeBoolean((boolean) obj);
        case V3Type.TYPE_CODE_BIG_INTEGER: return serializeBigInteger(type, (BigInteger) obj);
        case V3Type.TYPE_CODE_ARRAY: return serializeArray(type, obj);
        case V3Type.TYPE_CODE_TUPLE: return serializeTuple(type.elementTypes, (Object[]) obj);
        default: throw new AssertionError();
        }
    }

    private static Object deserialize(V3Type type, Iterator<RLPItem> sequenceIterator) {
        switch (type.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return deserializeBoolean(sequenceIterator);
        case V3Type.TYPE_CODE_BIG_INTEGER: return deserializeBigInteger(type, sequenceIterator);
        case V3Type.TYPE_CODE_ARRAY: return deserializeArray(type, sequenceIterator);
        case V3Type.TYPE_CODE_TUPLE: return deserializeTuple(type.elementTypes, sequenceIterator.next().iterator());
        default: throw new AssertionError();
        }
    }

    private static byte[] serializeBoolean(boolean val) {
        return val ? TRUE : FALSE;
    }

    private static Boolean deserializeBoolean(Iterator<RLPItem> sequenceIterator) {
        return sequenceIterator.next().asBool();
    }

    private static byte[] serializeBigInteger(V3Type ut, BigInteger val) {
        if (val.signum() != 0) {
            final byte[] bytes = val.toByteArray();
            return val.signum() < 0
                    ? signExtendNegative(bytes, ut.bitLen / Byte.SIZE)
                    : bytes[0] != 0
                        ? bytes
                        : Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return new byte[0];
    }

    private static byte[] signExtendNegative(final byte[] negative, final int newWidth) {
        final byte[] extended = new byte[newWidth];
        Arrays.fill(extended, (byte) 0xff);
        System.arraycopy(negative, 0, extended, newWidth - negative.length, negative.length);
        return extended;
    }

    private static BigInteger deserializeBigInteger(V3Type ut, Iterator<RLPItem> sequenceIterator) {
        RLPItem item = sequenceIterator.next();
        return ut.unsigned || item.dataLength * Byte.SIZE < ut.bitLen
                ? item.asBigInt()
                : item.asBigIntSigned();
    }

    private static Object serializeArray(V3Type type, Object arr) {
        final V3Type et = type.elementType;
        switch (et.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return serializeBooleanArray(type, (boolean[]) arr);
        case V3Type.TYPE_CODE_BYTE: return serializeByteArray(type, arr);
        case V3Type.TYPE_CODE_BIG_INTEGER: return serializeBigIntegerArray(type, (BigInteger[]) arr);
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: return serializeObjectArray(type, (Object[]) arr, 0);
        default: throw new AssertionError();
        }
    }

    private static Object deserializeArray(V3Type type, Iterator<RLPItem> sequenceIterator) {
        final V3Type et = type.elementType;
        switch (et.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return deserializeBooleanArray(type, sequenceIterator);
        case V3Type.TYPE_CODE_BYTE: return deserializeByteArray(type, sequenceIterator);
        case V3Type.TYPE_CODE_BIG_INTEGER: return deserializeBigIntegerArray(type, sequenceIterator.next());
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: return deserializeObjectArray(type, sequenceIterator.next(), false);
        default: throw new AssertionError();
        }
    }

    static class DynamicBoolArray {
        final byte[] arrayLenBytes;
        final byte[] dataBytes;

        DynamicBoolArray(byte[] arrayLenBytes, byte[] dataBytes) {
            this.arrayLenBytes = arrayLenBytes;
            this.dataBytes = dataBytes;
        }
    }

    private static Object serializeBooleanArray(V3Type type, boolean[] booleans) {
        validateLength(type.arrayLen, booleans.length);
        final byte[] bytes;
        if (booleans.length == 0) {
            bytes = null;
        } else {
            StringBuilder binary = new StringBuilder("+");
            for (boolean b : booleans) {
                binary.append(b ? '1' : '0');
            }
            bytes = serializeBigInteger(type, new BigInteger(binary.toString(), 2));
        }
        return type.arrayLen == -1
                    ? new DynamicBoolArray(Integers.toBytes(booleans.length), bytes)
                    : bytes;
    }

    private static boolean[] deserializeBooleanArray(final V3Type type, final Iterator<RLPItem> sequenceIterator) {
        final int len = type.arrayLen == -1 ? sequenceIterator.next().asInt() : type.arrayLen;
        if (len == 0) return new boolean[0];
        final String binaryStr = sequenceIterator.next().asBigInt().toString(2);
        final int numChars = binaryStr.length();
        final int impliedZeros = len - numChars;
        final boolean[] booleans = new boolean[len];
        for (int c = 0; c < numChars; c++) {
            if (binaryStr.charAt(c) == '1') {
                booleans[impliedZeros + c] = true;
            }
        }
        return booleans;
    }

    private static byte[] serializeByteArray(V3Type type, Object arr) {
        byte[] bytes = type.isString ? ((String) arr).getBytes(StandardCharsets.UTF_8) : (byte[]) arr;
        validateLength(type.arrayLen, bytes.length);
        return bytes;
    }

    private static Object deserializeByteArray(V3Type type, Iterator<RLPItem> sequenceIterator) {
        return type.isString
                ? new String(sequenceIterator.next().data(), StandardCharsets.UTF_8)
                : sequenceIterator.next().data();
    }

    private static Object serializeBigIntegerArray(V3Type type, BigInteger[] arr) {
        validateLength(type.arrayLen, arr.length);
        int maxRawLen = 0;
        for (BigInteger e : arr) {
            byte[] bigIntBytes = serializeBigInteger(type.elementType, e);
            if (bigIntBytes.length > maxRawLen) {
                maxRawLen = bigIntBytes.length;
            }
        }
        Object[] varWidth = serializeObjectArray(type, arr, 1);
        varWidth[0] = new byte[] { (byte) 0x00 };
        int varWidthLen = RLPEncoder.sumEncodedLen(Arrays.asList(varWidth));
        byte[] fixedWidth = serializeLargeBigIntegerArray(type, arr, maxRawLen);
        return varWidthLen < fixedWidth.length
                ? varWidth
                : fixedWidth;
    }

    private static byte[] serializeLargeBigIntegerArray(V3Type type, BigInteger[] arr, int byteWidth) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + byteWidth * arr.length);
        buffer.put((byte) byteWidth);
        for (BigInteger e : arr) {
            byte[] bigIntBytes = serializeBigInteger(type.elementType, e);
            final int padLen = byteWidth - bigIntBytes.length;
            for (int i = 0; i < padLen; i++) {
                buffer.put((byte) 0);
            }
            buffer.put(bigIntBytes);
        }
        return buffer.array();
    }

    private static Object[] deserializeBigIntegerArray(V3Type type, RLPItem list) {
        byte[] data = list.data();
        if (data[0] != (byte) 0x00) {
            return deserializeLargeBigIntegerArray(list);
        }
        return deserializeObjectArray(type, list, true);
    }

    private static Object[] deserializeLargeBigIntegerArray(RLPItem list) {
        final int elementLen = list.buffer[list.dataIndex];
        BigInteger[] result = new BigInteger[(list.dataLength - 1) / elementLen];
        for (int i = 0, pos = list.dataIndex + 1; i < result.length; i++, pos += elementLen) {
            byte[] bytes = Arrays.copyOfRange(list.buffer, pos, pos + elementLen);
            result[i] = new BigInteger(bytes);
        }
        return result;
    }

    private static Object[] serializeObjectArray(V3Type type, Object[] objects, final int offset) {
        validateLength(type.arrayLen, objects.length);
        final Object[] out = new Object[offset + objects.length];
        for (int i = 0; i < objects.length; i++) {
            out[i + offset] = serialize(type.elementType, objects[i]);
        }
        return out;
    }

    private static Object[] deserializeObjectArray(V3Type type, RLPItem list, boolean skipFirst) {
        final List<RLPItem> elements = list.elements();
        final Iterator<RLPItem> listSeqIter = elements.iterator();
        int numElements = elements.size();
        if (skipFirst) {
            listSeqIter.next();
            numElements--;
        }
        final Object[] in = (Object[]) Array.newInstance(type.elementType.clazz, numElements); // reflection
        for (int i = 0; i < in.length; i++) {
            in[i] = deserialize(type.elementType, listSeqIter);
        }
        return in;
    }

    private static void validateLength(int expected, int actual) {
        if (expected != actual && expected != -1) throw new IllegalArgumentException();
    }
}
