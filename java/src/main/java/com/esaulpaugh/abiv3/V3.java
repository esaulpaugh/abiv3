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

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Serializes and deserializes tuples of values. */
public final class V3 {

    private V3() {}

    static final byte VERSION_ID_INTERNAL = 0b0000_0000;
    static final byte VERSION_ID_EXTERNAL = 0b0100_0000;
    static final byte VERSION_MASK = (byte) 0b1100_0000;
    static final byte ID_MASK = (byte) ~VERSION_MASK; // 0x3f (decimal 63), the complement of VERSION_MASK

    public static byte[] encodeFunction(int functionNumber, V3Type tupleType, Object[] vals, boolean external) {
        if (functionNumber < 0) throw new IllegalArgumentException();
        final List<byte[]> results = external ? headerExternal(functionNumber) : headerInternal(functionNumber);
        encodeTuple(tupleType, vals, external, results);
        int len = 0;
        for (byte[] result : results) {
            len += result.length;
        }
        final ByteBuffer encoding = ByteBuffer.allocate(len);
        for (byte[] result : results) {
            encoding.put(result);
        }
        return encoding.array();
    }

    public static Object[] decodeFunction(V3Type tupleType, byte[] buffer) {
        final ByteBuffer bb = ByteBuffer.wrap(buffer);
        final byte zeroth = bb.get();
        final int versionBits = zeroth & VERSION_MASK;
        if (versionBits == VERSION_ID_INTERNAL) {
            long fnNumber = decodeInteger(4, true, bb, false).longValueExact();
            if (fnNumber < 0) throw new AssertionError();
        } else if (versionBits == VERSION_ID_EXTERNAL) {
            long fnNumber = zeroth & ID_MASK;
            if (fnNumber == ID_MASK) {
                final int first = bb.get() & 0xff;
                if (first > 0xb7) throw new IllegalArgumentException("invalid function ID format");
                if (first < 0x80) {
                    fnNumber = first;
                } else {
                    int len = first - 0x80;
                    fnNumber = ID_MASK + Integers.getLong(readBytes(len, bb), 0, len);
                }
                if (fnNumber < 0) throw new AssertionError();
            }
        } else {
            throw new IllegalArgumentException();
        }
        return decodeTuple(tupleType, bb, versionBits == VERSION_ID_EXTERNAL);
    }

    private static void encode(V3Type t, Object val, boolean external, List<byte[]> results) {
        switch (t.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: encodeBoolean((boolean) val, results); return;
        case V3Type.TYPE_CODE_BIG_INTEGER: encodeInteger(t.bitLen / Byte.SIZE, (BigInteger) val, external, results); return;
        case V3Type.TYPE_CODE_ARRAY: encodeArray(t, val, external, results); return;
        case V3Type.TYPE_CODE_TUPLE: encodeTuple(t, (Object[]) val, external, results); return;
        default: throw new Error();
        }
    }

    private static Object decode(V3Type type, ByteBuffer bb, boolean external) {
        switch (type.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return decodeBoolean(bb);
        case V3Type.TYPE_CODE_BIG_INTEGER: return decodeInteger(type.bitLen / Byte.SIZE, type.unsigned, bb, external);
        case V3Type.TYPE_CODE_ARRAY: return decodeArray(type, bb, external);
        case V3Type.TYPE_CODE_TUPLE: return decodeTuple(type, bb, external);
        default: throw new AssertionError();
        }
    }

    private static void encodeBoolean(boolean val, List<byte[]> results) {
        results.add(single(val ? (byte) 0x01 : (byte) 0x00));
    }

    public static boolean decodeBoolean(ByteBuffer bb) {
        return bb.get() != 0;
    }

    private static void encodeInteger(int byteLen, BigInteger val, boolean external, List<byte[]> results) {
        if (external) {
            encodeIntegerExternal(byteLen, val, results);
            return;
        }
        final byte[] destBytes = new byte[byteLen];
        if (val.signum() != 0) {
            final byte[] sourceBytes = val.toByteArray();
            if (val.signum() < 0) {
                results.add(signExtendNegative(sourceBytes, byteLen));
                return;
            }
            final int padLength = destBytes.length - sourceBytes.length;
            if (sourceBytes[0] != 0) {
                System.arraycopy(sourceBytes, 0, destBytes, padLength, sourceBytes.length);
            } else {
                System.arraycopy(sourceBytes, 1, destBytes, padLength + 1, sourceBytes.length - 1);
            }
        }
        results.add(destBytes);
    }

    private static void encodeIntegerExternal(int byteLen, BigInteger val, List<byte[]> results) {
        if (val.signum() != 0) {
            final byte[] bytes = val.toByteArray();
            results.add(
                    rlp(
                        val.signum() < 0
                            ? signExtendNegative(bytes, byteLen)
                            : bytes[0] != 0
                                ? bytes
                                : Arrays.copyOfRange(bytes, 1, bytes.length)
                    )
            );
        } else {
            results.add(rlp(new byte[0]));
        }
    }

    private static BigInteger decodeInteger(int byteLen, boolean unsigned, ByteBuffer bb, boolean external) {
        final byte[] bytes = external ? unrlp(bb) : readBytes(byteLen, bb);
        return unsigned
                ? new BigInteger(1, bytes)
                : new BigInteger(bytes);
    }

    private static void encodeTuple(V3Type tupleType, Object[] tuple, boolean external, List<byte[]> results) {
        validateLength(tupleType.elementTypes.length, tuple.length);
        final Object[] out = new Object[tupleType.elementTypes.length];
        for(int i = 0; i < out.length; i++) {
            encode(tupleType.elementTypes[i], tuple[i], external, results);
        }
    }

    private static Object[] decodeTuple(V3Type tupleType, ByteBuffer bb, boolean external) {
        final Object[] out = new Object[tupleType.elementTypes.length];
        for(int i = 0; i < out.length; i++) {
            out[i] = decode(tupleType.elementTypes[i], bb, external);
        }
        return out;
    }

    private static void encodeArray(V3Type type, Object arr, boolean external, List<byte[]> results) {
        final V3Type et = type.elementType;
        switch (et.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: encodeBooleanArray(type, (boolean[]) arr, results); return;
        case V3Type.TYPE_CODE_BYTE: encodeByteArray(type, arr, results); return;
        case V3Type.TYPE_CODE_BIG_INTEGER: encodeIntegerArray(type, (BigInteger[]) arr, external, results); return;
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: encodeObjectArray(type, (Object[]) arr, external, results); return;
        default: throw new AssertionError();
        }
    }

    private static Object decodeArray(V3Type type, ByteBuffer bb, boolean external) {
        final V3Type et = type.elementType;
        switch (et.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return decodeBooleanArray(type, bb);
        case V3Type.TYPE_CODE_BYTE: return decodeByteArray(type, bb);
        case V3Type.TYPE_CODE_BIG_INTEGER: return decodeIntegerArray(type, bb, external);
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: return decodeObjectArray(type, bb, external);
        default: throw new AssertionError();
        }
    }

    private static void encodeBooleanArray(V3Type type, boolean[] booleans, List<byte[]> results) {
        validateLength(type.arrayLen, booleans.length);
        if (type.arrayLen == -1) {
            results.add(rlp(booleans.length));
        }
        if (booleans.length > 0) {
            final byte[] bits = new byte[Integers.roundLengthUp(booleans.length, Byte.SIZE) / Byte.SIZE];
            for (int k = 0; k < booleans.length; k++) {
                if (booleans[booleans.length - 1 - k]) {
                    final int idx = bits.length - 1 - (k / Byte.SIZE);
                    bits[idx] |= 0b0000_0001 << (k % 8);
                }
            }
            results.add(bits);
        }
    }

    private static boolean[] decodeBooleanArray(final V3Type type, ByteBuffer bb) {
        final int len;
        if (type.arrayLen == 0 || (len = getLength(type, bb)) == 0) return new boolean[0];
        final int byteLen = Integers.roundLengthUp(len, Byte.SIZE) / Byte.SIZE;
        final String binaryStr = new BigInteger(1, readBytes(byteLen, bb)).toString(2);
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
        final byte[] bytes = type.isString ? ((String) arr).getBytes(StandardCharsets.UTF_8) : (byte[]) arr;
        validateLength(type.arrayLen, bytes.length);
        if (type.arrayLen == -1) {
            results.add(rlp(bytes));
        } else {
            results.add(bytes);
        }
    }

    private static Object decodeByteArray(V3Type type, ByteBuffer bb) {
        final byte[] raw = type.arrayLen == -1 ? unrlp(bb) : readBytes(type.arrayLen, bb);
        return type.isString
                ? new String(raw, StandardCharsets.UTF_8)
                : raw;
    }

    private static void encodeIntegerArray(V3Type type, BigInteger[] arr, boolean external, List<byte[]> results) {
        validateLength(type.arrayLen, arr.length);
        if (type.arrayLen == -1) {
            results.add(rlp(arr.length));
        }
        for (BigInteger bigInteger : arr) {
            encodeInteger(type.elementType.bitLen / Byte.SIZE, bigInteger, external, results);
        }
    }

    private static BigInteger[] decodeIntegerArray(V3Type type, ByteBuffer bb, boolean external) {
        final BigInteger[] bigInts = new BigInteger[getLength(type, bb)];
        for (int i = 0; i < bigInts.length; i++) {
            bigInts[i] = decodeInteger(type.elementType.bitLen / Byte.SIZE, type.elementType.unsigned, bb, external);
        }
        return bigInts;
    }

    private static void encodeObjectArray(V3Type type, Object[] objects, boolean external, List<byte[]> results) {
        validateLength(type.arrayLen, objects.length);
        if (type.arrayLen == -1) {
            results.add(rlp(objects.length));
        }
        for (Object object : objects) {
            encode(type.elementType, object, external, results);
        }
    }

    private static Object decodeObjectArray(V3Type type, ByteBuffer bb, boolean external) {
        final int len = getLength(type, bb);
        final Object[] in = (Object[]) Array.newInstance(type.elementType.clazz, len); // reflection
        for (int i = 0; i < in.length; i++) {
            in[i] = decode(type.elementType, bb, external);
        }
        return in;
    }

    public static byte[] rlp(int value) {
        return rlp(Integers.toBytes(value));
    }

    /**
     * Returns the RLP encoding of the given byte string.
     *
     * @param byteString the byte string to be encoded
     */
    public static byte[] rlp(byte[] byteString) {
        final int dataLen = byteString.length;
        final ByteBuffer bb;
        if (dataLen < 56) {
            if (dataLen == 1) {
                final byte first = byteString[0];
                return first < 0x00
                        ? new byte[] { (byte) 0x81, first }
                        : single(first);
            }
            bb = ByteBuffer.allocate(1 + byteString.length);
            bb.put((byte) (0x80 + dataLen));
            bb.put(byteString);
        } else {
            final int lenOfLen = Integers.len(dataLen);
            bb = ByteBuffer.allocate(1 + lenOfLen + byteString.length);
            bb.put((byte) (0xb7 + lenOfLen));
            Integers.putLong(dataLen, bb);
            bb.put(byteString);
        }
        return bb.array();
    }

    private static byte[] unrlp(ByteBuffer bb) {
        final int lead = bb.get() & 0xFF;
        if (lead < 0x80) {
            return single((byte) lead);
        }
        if (lead < 0xB8) {
            return readBytes(lead - 0x80, bb);
        }
        if (lead < 0xC0) {
            final int lengthOfLength = lead - 0xB7;
            final int dataLength = Integers.getInt(readBytes(lengthOfLength, bb), 0, lengthOfLength);
            if (dataLength >= 56) {
                return readBytes(dataLength, bb);
            }
        }
        throw new Error();
    }

    private static List<byte[]> headerExternal(int functionNumber) {
        final List<byte[]> results = new ArrayList<>(2);
        if (functionNumber < ID_MASK) {
            results.add(
                    functionNumber == 0
                            ? single(VERSION_ID_EXTERNAL)
                            : single((byte) (VERSION_ID_EXTERNAL | Integers.toBytes(functionNumber)[0]))
            );
        } else {
            results.add(single((byte) (VERSION_ID_EXTERNAL | ID_MASK)));
            results.add(rlp(functionNumber - ID_MASK));
        }
        return results;
    }

    private static List<byte[]> headerInternal(int functionNumber) {
        final List<byte[]> results = new ArrayList<>(2);
        results.add(single(VERSION_ID_INTERNAL));
        encodeInteger(4, BigInteger.valueOf(functionNumber), false, results);
        return results;
    }

    private static byte[] single(byte val) {
        return new byte[] { val };
    }

    private static byte[] signExtendNegative(final byte[] negative, final int newWidth) {
        final byte[] extended = new byte[newWidth];
        Arrays.fill(extended, (byte) 0xff);
        System.arraycopy(negative, 0, extended, newWidth - negative.length, negative.length);
        return extended;
    }

    private static void validateLength(int expected, int actual) {
        if (expected != actual && expected != -1) throw new IllegalArgumentException();
    }

    private static int getLength(V3Type type, ByteBuffer bb) {
        if (type.arrayLen == -1) {
            final byte[] prefix = unrlp(bb);
            return Integers.getInt(prefix, 0, prefix.length);
        }
        return type.arrayLen;
    }

    private static byte[] readBytes(int n, ByteBuffer bb) {
        final byte[] bytes = new byte[n];
        bb.get(bytes);
        return bytes;
    }
}
