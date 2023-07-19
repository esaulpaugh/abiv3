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
import java.util.List;

/** Serializes and deserializes tuples through the use of RLP encoding. */
public final class V3 {

    private V3() {}

    private static final byte[] TRUE = new byte[] { 0x1 };
    private static final byte[] FALSE = new byte[] { 0x00 };

    static final byte VERSION_ID = 0;
    static final byte VERSION_MASK = (byte) 0b1100_0000;
    static final byte ID_MASK = (byte) ~VERSION_MASK; // 0x3f (decimal 63), the complement of VERSION_MASK

    public static byte[] encodeFunction(int functionNumber, V3Type tupleType, Object[] vals) {
        List<byte[]> results = new ArrayList<>();
        byte[][] header = header(functionNumber);
        if (header.length == 2) {
            results.add(header[1]);
        }
        encodeTuple(tupleType, vals, results);
        int len = 0;
        for (byte[] result : results) {
            len += result.length;
        }
        ByteBuffer encoding = ByteBuffer.allocate(len);
        for (byte[] result : results) {
            encoding.put(result);
        }
        return encoding.array();
    }

    public static Object[] decodeFunction(V3Type tupleType, byte[] buffer) {
        final ByteBuffer bb = ByteBuffer.wrap(buffer);
        final byte zeroth = bb.get();
        final int version = zeroth & VERSION_MASK;
        if (version != VERSION_ID) {
            throw new IllegalArgumentException();
        }
        int sequenceStart = 1;
        long fnNumber = zeroth & ID_MASK;
        if (fnNumber == ID_MASK) {
            final byte first = bb.get();
            final DataType type = DataType.type(first);
            if (first == 0x00 || type == DataType.STRING_LONG || type == DataType.LIST_SHORT || type == DataType.LIST_LONG) {
                throw new IllegalArgumentException("invalid function ID format");
            }
            int len = first - DataType.STRING_SHORT.offset;
            byte[] fnNumBytes = new byte[len];
            bb.get(fnNumBytes);
            fnNumber = ID_MASK + Integers.getLong(fnNumBytes, 0, len);
            if (fnNumber < 0) throw new AssertionError();
            sequenceStart = 2 + len;
        }
        return decodeTuple(tupleType, bb);
    }

    private static byte[][] header(int functionNumber) {
        if (functionNumber < 0) throw new IllegalArgumentException();
        if (functionNumber < ID_MASK) {
            if (functionNumber == 0) {
                return new byte[][] { new byte[] { 0 } };
            }
            return new byte[][] { Integers.toBytes(functionNumber) };
        }
        return new byte[][] { new byte[] { ID_MASK }, Integers.toBytes(functionNumber - ID_MASK) };
    }

    private static void encode(V3Type t, Object val, List<byte[]> results) {
        switch (t.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: encodeBoolean((boolean) val, results); return;
        case V3Type.TYPE_CODE_BIG_INTEGER: encodeInteger(t, (BigInteger) val, results); return;
        case V3Type.TYPE_CODE_ARRAY: encodeArray(t, val, results); return;
        case V3Type.TYPE_CODE_TUPLE: encodeTuple(t, (Object[]) val, results); return;
        default: throw new Error();
        }
    }

    private static Object decode(V3Type type, ByteBuffer bb) {
        switch (type.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return decodeBoolean(bb);
        case V3Type.TYPE_CODE_BIG_INTEGER: return decodeInteger(type, bb);
        case V3Type.TYPE_CODE_ARRAY: return decodeArray(type, bb);
        case V3Type.TYPE_CODE_TUPLE: return decodeTuple(type, bb);
        default: throw new AssertionError();
        }
    }

    private static void encodeBoolean(boolean val, List<byte[]> results) {
        results.add(new byte[] { val ? (byte) 0x01 : (byte) 0x00 });
    }

    public static boolean decodeBoolean(ByteBuffer bb) {
        return bb.get() != 0;
    }

    private static void encodeInteger(V3Type ut, BigInteger val, List<byte[]> results) {
        final byte[] valBytes = new byte[ut.bitLen / Byte.SIZE];
        if (val.signum() != 0) {
            final byte[] bytes = val.toByteArray();
            if (val.signum() < 0) {
                byte[] extended = signExtendNegative(bytes, ut.bitLen / Byte.SIZE);
                results.add(extended);
                return;
            }
            if (bytes[0] != 0) {
                System.arraycopy(bytes, 0, valBytes, valBytes.length - bytes.length, bytes.length);
            } else {
                System.arraycopy(bytes, 1, valBytes, 1 + valBytes.length - bytes.length, bytes.length - 1);
            }
        }
        results.add(valBytes);
    }

    private static BigInteger decodeInteger(V3Type type, ByteBuffer bb) {
        final byte[] bytes = new byte[type.bitLen / Byte.SIZE];
        bb.get(bytes);
        return type.unsigned
                ? new BigInteger(1, bytes)
                : new BigInteger(bytes);
    }

    private static byte[] signExtendNegative(final byte[] negative, final int newWidth) {
        final byte[] extended = new byte[newWidth];
        Arrays.fill(extended, (byte) 0xff);
        System.arraycopy(negative, 0, extended, newWidth - negative.length, negative.length);
        return extended;
    }

    private static void encodeTuple(V3Type tupleType, Object[] tuple, List<byte[]> results) {
        validateLength(tupleType.elementTypes.length, tuple.length);
        final Object[] out = new Object[tupleType.elementTypes.length];
        for(int i = 0; i < out.length; i++) {
            encode(tupleType.elementTypes[i], tuple[i], results);
        }
    }

    private static Object[] decodeTuple(V3Type tupleType, ByteBuffer bb) {
        final Object[] out = new Object[tupleType.elementTypes.length];
        for(int i = 0; i < out.length; i++) {
            out[i] = decode(tupleType.elementTypes[i], bb);
        }
        return out;
    }

    private static void validateLength(int expected, int actual) {
        if (expected != actual && expected != -1) throw new IllegalArgumentException();
    }

    private static void encodeArray(V3Type type, Object arr, List<byte[]> results) {
        final V3Type et = type.elementType;
        switch (et.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: serializeBooleanArray(type, (boolean[]) arr, results); return;
        case V3Type.TYPE_CODE_BYTE: encodeByteArray(type, arr, results); return;
        case V3Type.TYPE_CODE_BIG_INTEGER: encodeIntegerArray(type, (BigInteger[]) arr, results); return;
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: serializeObjectArray(type, (Object[]) arr, results); return;
        default: throw new AssertionError();
        }
    }

    private static Object decodeArray(V3Type type, ByteBuffer bb) {
        final V3Type et = type.elementType;
        switch (et.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return decodeBooleanArray(type, bb);
        case V3Type.TYPE_CODE_BYTE: return decodeByteArray(type, bb);
        case V3Type.TYPE_CODE_BIG_INTEGER: return decodeIntegerArray(type, bb);
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: return decodeTuple(type, bb);
        default: throw new AssertionError();
        }
    }

    private static void serializeBooleanArray(V3Type type, boolean[] booleans, List<byte[]> results) {
        validateLength(type.arrayLen, booleans.length);
        if (type.arrayLen == -1) {
            results.add(rlp(Integers.toBytes(booleans.length)));
        }
        if (booleans.length > 0) {
            StringBuilder binary = new StringBuilder("+");
            for (boolean b : booleans) {
                binary.append(b ? '1' : '0');
            }
            encodeInteger(type, new BigInteger(binary.toString(), 2), results);
        }
    }

    private static boolean[] decodeBooleanArray(final V3Type type, ByteBuffer bb) {
        final byte[] lenBytes = unrlp(bb);
        final int len = type.arrayLen == -1 ? Integers.getInt(lenBytes, 0, lenBytes.length) : type.arrayLen;
        if (len == 0) return new boolean[0];
        final String binaryStr = new BigInteger(unrlp(bb)).toString(2);
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

    private static void encodeByteArray(V3Type type, Object arr, List<byte[]> results) {
        byte[] bytes = type.isString ? ((String) arr).getBytes(StandardCharsets.UTF_8) : (byte[]) arr;
        validateLength(type.arrayLen, bytes.length);
        results.add(bytes);
    }

    private static Object decodeByteArray(V3Type type, ByteBuffer bb) {
        return type.isString
                ? new String(unrlp(bb), StandardCharsets.UTF_8)
                : unrlp(bb);
    }

    private static void encodeIntegerArray(V3Type type, BigInteger[] arr, List<byte[]> results) {
        validateLength(type.arrayLen, arr.length);
        if (type.arrayLen == -1) {
            results.add(rlp(Integers.toBytes(arr.length)));
        }
        for (BigInteger bigInteger : arr) {
            encodeInteger(type.elementType, bigInteger, results);
        }
    }

    private static BigInteger[] decodeIntegerArray(V3Type type, ByteBuffer bb) {
        if (type.arrayLen == -1) {
            throw new RuntimeException("todo");
        }
        BigInteger[] bigInts = new BigInteger[type.arrayLen];
        for (int i = 0; i < bigInts.length; i++) {
            bigInts[i] = decodeInteger(type.elementType, bb);
        }
        return bigInts;
    }

    /**
     * Returns the RLP encoding of the given byte string.
     *
     * @param byteString the byte string to be encoded
     */
    public static byte[] rlp(byte[] byteString) {
        final int dataLen = byteString.length;
        if (dataLen < DataType.MIN_LONG_DATA_LEN) {
            if (dataLen == 1) {
                return encodeLen1String(byteString[0]);
            }
            byte[] result = new byte[1 + byteString.length];
            result[0] = (byte) (DataType.STRING_SHORT.offset + dataLen);
            System.arraycopy(byteString, 0, result, 1, byteString.length);
            return result;
        }
        throw new Error(); // long string
    }

    private static byte[] unrlp(ByteBuffer bb) {
        final byte lead = bb.get();
        if (DataType.type(lead) == DataType.SINGLE_BYTE) {
            return new byte[] { lead };
        }
        if (DataType.type(lead) == DataType.STRING_SHORT) {
            byte[] bytes = new byte[lead - DataType.STRING_SHORT.offset];
            bb.get(bytes);
            return bytes;
        }
        throw new Error(); // long string
    }

    private static byte[] encodeLen1String(byte first) {
        if (first < 0x00) { // same as (first & 0xFF) >= 0x80
            return new byte[] {
                    (byte) (DataType.STRING_SHORT.offset + 1),
                    first
            };
        }
        return new byte[] { first };
    }

    private static void serializeObjectArray(V3Type type, Object[] objects, List<byte[]> results) {
        validateLength(type.arrayLen, objects.length);
        for (Object object : objects) {
            encode(type.elementType, object, results);
        }
    }
}
