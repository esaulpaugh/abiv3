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

    public static final byte VERSION_IDENTIFIER = (byte) 0x81;

//    private static final byte[] PREFIX = new byte[] { (byte) 0xca, (byte) 0xfe, (byte) 0xde, (byte) 0xf1 };

    static final int SELECTOR_LEN = 4;

    public static byte[] toRLP(String functionName, V3Type[] schema, Object[] vals, boolean withVersionId) {
        List<Object> tuple = Arrays.asList(serializeTuple(schema, vals));
        ByteBuffer encoding = ByteBuffer.allocate(SELECTOR_LEN + RLPEncoder.sumEncodedLen(tuple) + (withVersionId ? 1 : 0));
        encoding.put(generateSelector(functionName, schema));
        RLPEncoder.putSequence(tuple, encoding);
        if(withVersionId) {
            encoding.put(VERSION_IDENTIFIER); // optional version byte on the end
        }
        return encoding.array();
    }

    public static Object[] fromRLP(String functionName, V3Type[] schema, byte[] rlp) {
        checkSelector(generateSelector(functionName, schema), rlp);
        return deserializeTuple(schema, RLPItem.ABIv3Iterator.sequenceIterator(rlp, SELECTOR_LEN));
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

    private static void checkSelector(byte[] expectedSelector, byte[] rlp) {
        for (int i = 0; i < expectedSelector.length; i++) {
            if(rlp[i] != expectedSelector[i]) {
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
        if(sequenceIterator.hasNext()) {
            throw new IllegalArgumentException("trailing unconsumed items");
        }
        return elements;
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
        case V3Type.TYPE_CODE_TUPLE: return deserializeTuple(type.elementTypes, sequenceIterator.next().iterator());
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
        if(booleans.length == 0) {
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
        if(len == 0) return new boolean[0];
        final String binaryStr = sequenceIterator.next().asBigInt().toString(2);
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
        final List<RLPItem> elements = sequenceIterator.next().elements();
        final Iterator<RLPItem> listSeqIter = elements.iterator();
        final Object[] in = (Object[]) Array.newInstance(type.elementClass, elements.size()); // reflection
        for (int i = 0; i < in.length; i++) {
            in[i] = deserialize(type.elementType, listSeqIter);
        }
        return in;
    }

    private static void validateLength(int expected, int actual) {
        if(expected != actual && expected != -1) throw new IllegalArgumentException();
    }
}
