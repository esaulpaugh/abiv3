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

    public static byte[] encodeFunction(int functionNumber, V3Type[] schema, Object[] vals) {
        List<byte[]> results = new ArrayList<>();
        byte[][] header = header(functionNumber);
        if (header.length == 2) {
            results.add(header[1]);
        }
        serializeTuple(schema, vals, results);
        ByteBuffer encoding = ByteBuffer.allocate(350);
        for (byte[] result : results) {
            encoding.put(result);
        }
        return encoding.array();
    }

    public static Object[] decodeFunction(V3Type[] schema, byte[] buffer) {
        final byte zeroth = buffer[0];
        final int version = zeroth & VERSION_MASK;
        if (version != VERSION_ID) {
            throw new IllegalArgumentException();
        }
        int sequenceStart = 1;
        long fnNumber = zeroth & ID_MASK;
        if (fnNumber == ID_MASK) {
            final DataType type = DataType.type(buffer[1]);
            if (buffer[1] == 0x00 || type == DataType.STRING_LONG || type == DataType.LIST_SHORT || type == DataType.LIST_LONG) {
                throw new IllegalArgumentException("invalid function ID format");
            }
            int len = buffer[1] - DataType.STRING_SHORT.offset;
            fnNumber = ID_MASK + Integers.getLong(buffer, 2, len);
            if (fnNumber < 0) throw new AssertionError();
            sequenceStart = 2 + len;
        }
        return null;
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
        case V3Type.TYPE_CODE_BOOLEAN: serializeBoolean((boolean) val, results); return;
        case V3Type.TYPE_CODE_BIG_INTEGER: serializeInteger(t, (BigInteger) val, results); return;
        case V3Type.TYPE_CODE_ARRAY: serializeArray(t, val, results); return;
        case V3Type.TYPE_CODE_TUPLE: serializeTuple(t.elementTypes, (Object[]) val, results); return;
        }
    }

    private static void serializeBoolean(boolean val, List<byte[]> results) {
        results.add(new byte[] { val ? (byte) 0x01 : (byte) 0x00 });
    }

    private static void serializeInteger(V3Type ut, BigInteger val, List<byte[]> results) {
        final byte[] valBytes;
        if (val.signum() != 0) {
            final byte[] bytes = val.toByteArray();
            valBytes = val.signum() < 0
                    ? signExtendNegative(bytes, ut.bitLen / Byte.SIZE)
                    : bytes[0] != 0
                        ? bytes
                        : Arrays.copyOfRange(bytes, 1, bytes.length);
        } else {
            valBytes = new byte[ut.bitLen / Byte.SIZE];
        }
        results.add(valBytes);
    }

    private static byte[] signExtendNegative(final byte[] negative, final int newWidth) {
        final byte[] extended = new byte[newWidth];
        Arrays.fill(extended, (byte) 0xff);
        System.arraycopy(negative, 0, extended, newWidth - negative.length, negative.length);
        return extended;
    }

    private static void serializeTuple(V3Type[] tupleType, Object[] tuple, List<byte[]> results) {
        validateLength(tupleType.length, tuple.length);
        final Object[] out = new Object[tupleType.length];
        for(int i = 0; i < out.length; i++) {
            encode(tupleType[i], tuple[i], results);
        }
    }

    private static void validateLength(int expected, int actual) {
        if (expected != actual && expected != -1) throw new IllegalArgumentException();
    }

    private static void serializeArray(V3Type type, Object arr, List<byte[]> results) {
        final V3Type et = type.elementType;
        switch (et.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: serializeBooleanArray(type, (boolean[]) arr, results); return;
        case V3Type.TYPE_CODE_BYTE: serializeByteArray(type, arr, results); return;
        case V3Type.TYPE_CODE_BIG_INTEGER: serializeBigIntegerArray(type, (BigInteger[]) arr, results); return;
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: serializeObjectArray(type, (Object[]) arr, results); return;
        default: throw new AssertionError();
        }
    }

    private static void serializeBooleanArray(V3Type type, boolean[] booleans, List<byte[]> results) {
        validateLength(type.arrayLen, booleans.length);
        if (type.arrayLen == -1) {
            results.add(Integers.toBytes(booleans.length));
        }
        if (booleans.length > 0) {
            StringBuilder binary = new StringBuilder("+");
            for (boolean b : booleans) {
                binary.append(b ? '1' : '0');
            }
            serializeInteger(type, new BigInteger(binary.toString(), 2), results);
        }
    }

    private static void serializeByteArray(V3Type type, Object arr, List<byte[]> results) {
        byte[] bytes = type.isString ? ((String) arr).getBytes(StandardCharsets.UTF_8) : (byte[]) arr;
        validateLength(type.arrayLen, bytes.length);
        results.add(bytes);
    }

    private static void serializeBigIntegerArray(V3Type type, BigInteger[] arr, List<byte[]> results) {
        validateLength(type.arrayLen, arr.length);
        results.add(null);
    }

    private static void serializeObjectArray(V3Type type, Object[] objects, List<byte[]> results) {
        validateLength(type.arrayLen, objects.length);
        for (Object object : objects) {
            encode(type.elementType, object, results);
        }
    }
}
