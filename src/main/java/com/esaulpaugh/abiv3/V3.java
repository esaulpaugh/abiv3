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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/** Serializes and deserializes tuples through the use of RLP encoding. */
public final class V3 {

    private V3() {} // TODO keyed hashing? heuristics? compressibility?

    private static final byte[] TRUE = new byte[] { 0x1 };
    private static final byte[] FALSE = new byte[0];

//    private static final byte[] PREFIX = new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xde, (byte) 0xf1 };

    static final int SELECTOR_LEN = 4;

    public static byte[] toRLP(String functionName, V3Type[] schema, Object[] vals) {
        List<Object> tuple = Arrays.asList(serializeTuple(schema, vals));
        ByteBuffer encoding = ByteBuffer.allocate(SELECTOR_LEN + RLPEncoder.sumEncodedLen(tuple));
        encoding.put(generateSelector(functionName, schema));
        RLPEncoder.putSequence(tuple, encoding);
        return encoding.array();
    }

    public static Object[] fromRLP(String functionName, V3Type[] schema, byte[] rlp) {
        checkSelector(generateSelector(functionName, schema), rlp);
        return deserializeTuple(schema, new RLPItem.ABIv3Iterator(rlp, SELECTOR_LEN, rlp.length));
    }

    private static byte[] generateSelector(String functionName, V3Type[] schema) {
        String signature = createSignature(functionName, schema);
        byte[] ascii = signature.getBytes(StandardCharsets.US_ASCII);
        ascii[ascii.length - 1] = (byte) 0;
        Keccak k = new Keccak(256);
        k.update(ascii);
        ByteBuffer selector = ByteBuffer.allocate(V3.SELECTOR_LEN);
        k.digest(selector, V3.SELECTOR_LEN);
        return selector.array();
    }

    public static String createSignature(String functionName, V3Type[] schema) {
        if(schema.length == 0) {
            return functionName + "()";
        }
        StringBuilder sb = new StringBuilder(functionName);
        sb.append('(');
        for (V3Type t : schema) {
            sb.append(t.canonicalType).append(',');
        }
        return sb.deleteCharAt(sb.length() - 1).append(')').toString(); // replace trailing comma
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
        if(sequenceIterator.hasNext()) {
            throw new IllegalArgumentException("trailing unconsumed items");
        }
        return elements;
    }

    private static void checkSelector(byte[] expectedSelector, byte[] rlp) {
        for (int i = 0; i < expectedSelector.length; i++) {
            if(rlp[i] != expectedSelector[i]) {
                throw new IllegalArgumentException("bad selector");
            }
        }
    }

    private static Object serialize(V3Type type, Object obj) {
        switch (type.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return serializeBoolean((boolean) obj);
        case V3Type.TYPE_CODE_BIG_INTEGER: return serializeBigInteger(type, (BigInteger) obj);
        case V3Type.TYPE_CODE_BIG_DECIMAL: return serializeBigInteger(type, ((BigDecimal) obj).unscaledValue());
        case V3Type.TYPE_CODE_ARRAY: return serializeArray(type, obj);
        case V3Type.TYPE_CODE_TUPLE: return serializeTuple(type.elementTypes, (Object[]) obj);
        default: throw new AssertionError();
        }
    }

    private static Object deserialize(V3Type type, Iterator<RLPItem> sequenceIterator) {
        switch (type.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return deserializeBoolean(sequenceIterator);
        case V3Type.TYPE_CODE_BIG_INTEGER: return deserializeBigInteger(type, sequenceIterator);
        case V3Type.TYPE_CODE_BIG_DECIMAL: return new BigDecimal(deserializeBigInteger(type, sequenceIterator), type.scale);
        case V3Type.TYPE_CODE_ARRAY: return deserializeArray(type, sequenceIterator);
        case V3Type.TYPE_CODE_TUPLE:
            RLPItem list = sequenceIterator.next();
            return deserializeTuple(type.elementTypes, new RLPItem.ABIv3Iterator(list.buffer, list.dataIndex, list.endIndex));
        default: throw new AssertionError();
        }
    }

    private static byte[] serializeBoolean(boolean val) {
        return val ? TRUE : FALSE;
    }

    private static Boolean deserializeBoolean(Iterator<RLPItem> sequenceIterator) {
        final String enc = sequenceIterator.next().asBigInt().toString(16);
        if("1".equals(enc)) return Boolean.TRUE;
        if("0".equals(enc)) return Boolean.FALSE;
        throw new IllegalArgumentException("illegal boolean RLP: 0x" + enc + ". Expected 0x1 or 0x0");
    }

    private static byte[] serializeBigInteger(V3Type ut, BigInteger val) {
        if(val.signum() != 0) {
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
        case V3Type.TYPE_CODE_BIG_INTEGER:
        case V3Type.TYPE_CODE_BIG_DECIMAL:
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: return serializeObjectArray(type, (Object[]) arr);
        default: throw new AssertionError();
        }
    }

    private static Object deserializeArray(V3Type type, Iterator<RLPItem> sequenceIterator) {
        final V3Type et = type.elementType;
        switch (et.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return deserializeBooleanArray(type, sequenceIterator);
        case V3Type.TYPE_CODE_BYTE: return deserializeByteArray(type, sequenceIterator);
        case V3Type.TYPE_CODE_BIG_INTEGER:
        case V3Type.TYPE_CODE_BIG_DECIMAL:
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: return deserializeObjectArray(type, sequenceIterator);
        default: throw new AssertionError();
        }
    }

    static class BoolArrayHolder {
        final byte[] arrayLen;
        final byte[] bits;

        BoolArrayHolder(byte[] arrayLen, byte[] bits) {
            this.arrayLen = arrayLen;
            this.bits = bits;
        }
    }

    private static Object serializeBooleanArray(V3Type type, boolean[] booleans) {
        validateLength(type.arrayLen, booleans.length);
        final byte[] bytes;
        if(booleans.length == 0) {
            bytes = new byte[0];
        } else {
            StringBuilder binary = new StringBuilder("+");
            for (boolean b : booleans) {
                binary.append(b ? '1' : '0');
            }
            bytes = serializeBigInteger(type, new BigInteger(binary.toString(), 2));
        }
        return type.arrayLen == -1
                ? new BoolArrayHolder(Integers.toBytes(booleans.length), bytes)
                : bytes;
    }

    public static boolean[] deserializeBooleanArray(V3Type type, Iterator<RLPItem> sequenceIterator) {
        return type.arrayLen == -1
            ? deserializeBooleanArray(sequenceIterator.next().asInt(), sequenceIterator.next().asBigInt())
            : deserializeBooleanArray(type.arrayLen, sequenceIterator.next().asBigInt());
    }

    private static boolean[] deserializeBooleanArray(final int len, final BigInteger bigInt) {
        final String binaryStr = bigInt.toString(2);
        final int numChars = binaryStr.length();
        final int impliedZeros = len - numChars;
        final boolean[] booleans = new boolean[len];
        for (int c = 0; c < numChars; c++) {
            if(binaryStr.charAt(c) == '1') {
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

    private static Object[] serializeObjectArray(V3Type type, Object[] objects) {
        validateLength(type.arrayLen, objects.length);
        final Object[] out = new Object[objects.length];
        for (int i = 0; i < objects.length; i++) {
            out[i] = serialize(type.elementType, objects[i]);
        }
        return out;
    }

    private static Object[] deserializeObjectArray(V3Type type, Iterator<RLPItem> sequenceIterator) {
        final RLPItem list = sequenceIterator.next();
        final Iterator<RLPItem> listSeqIter = new RLPItem.ABIv3Iterator(list.buffer, list.dataIndex, list.endIndex);
        final Object[] in = (Object[]) Array.newInstance(type.elementClass, list.elements().size()); // reflection
        for (int i = 0; i < in.length; i++) {
            in[i] = deserialize(type.elementType, listSeqIter);
        }
        return in;
    }

    private static void validateLength(int expected, int actual) {
        if(expected != actual && expected != -1) throw new IllegalArgumentException();
    }
}
