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
        return RLPEncoder.sequenceWithPrefix(
                generateSelector(functionName, schema),
                serializeTuple(schema, vals)
        );
    }

    public static Object[] fromRLP(String functionName, V3Type[] schema, byte[] rlp) {
        checkSelector(generateSelector(functionName, schema), rlp);
        return deserializeTuple(schema, rlp, SELECTOR_LEN);
    }

    private static byte[] generateSelector(String functionName, V3Type[] schema) {
        String signature = createSignature(functionName, schema);
        byte[] ascii = signature.getBytes(StandardCharsets.US_ASCII);
        byte[] selector = new byte[V3.SELECTOR_LEN];
        ByteBuffer bb = ByteBuffer.wrap(selector);
        Keccak k = new Keccak(256);
        k.update(ascii);
        k.update((byte) 0); // alternatively just append '\0' to the signature beforehand
        k.digest(bb, V3.SELECTOR_LEN);
        return selector;
    }

    public static String createSignature(String functionName, V3Type[] schema) {
        StringBuilder sb = new StringBuilder(functionName);
        sb.append('(');
        for (V3Type t : schema) {
            sb.append(t.canonicalType).append(',');
        }
        return completeTupleTypeString(sb);
    }

    private static String completeTupleTypeString(StringBuilder sb) {
        final int len = sb.length();
        return len != 1
                ? sb.deleteCharAt(len - 1).append(')').toString() // replace trailing comma
                : "()";
    }

    private static Object[] serializeTuple(V3Type[] tupleType, Object[] tuple) {
        final Object[] out = new Object[tupleType.length];
        for(int i = 0; i < out.length; i++) {
            out[i] = serialize(tupleType[i], tuple[i]);
        }
        return out;
    }

    private static Object[] deserializeTuple(V3Type[] tupleType, byte[] buffer, final int idx) {
        final Iterator<RLPItem> sequenceIterator = new RLPItem.ABIv3Iterator(buffer, idx);
        final Object[] elements = new Object[tupleType.length];
        for(int i = 0; i < elements.length; i++) {
            elements[i] = deserialize(tupleType[i], sequenceIterator.next());
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

    private static Object deserialize(V3Type type, RLPItem item) {
        switch (type.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return deserializeBoolean(item);
        case V3Type.TYPE_CODE_BIG_INTEGER: return deserializeBigInteger(type, item);
        case V3Type.TYPE_CODE_BIG_DECIMAL: return new BigDecimal(deserializeBigInteger(type, item), type.scale);
        case V3Type.TYPE_CODE_ARRAY: return deserializeArray(type, item);
        case V3Type.TYPE_CODE_TUPLE: return deserializeTuple(type.elementTypes, item.data(), 0);
        default: throw new AssertionError();
        }
    }

    private static byte[] serializeBoolean(boolean val) {
        return val ? TRUE : FALSE;
    }

    private static Boolean deserializeBoolean(RLPItem item) {
        final String enc = item.asBigInt().toString(16);
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

    private static BigInteger deserializeBigInteger(V3Type ut, RLPItem item) {
        return ut.unsigned || item.dataLength * Byte.SIZE < ut.bitLen
                ? item.asBigInt()
                : item.asBigIntSigned();
    }

    private static Object serializeArray(V3Type type, Object arr) {
        final V3Type et = type.elementType;
        switch (et.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return serializeBooleanArray(type, (boolean[]) arr);
        case V3Type.TYPE_CODE_BYTE: return serializeByteArray(arr, type.isString);
        case V3Type.TYPE_CODE_BIG_INTEGER:
        case V3Type.TYPE_CODE_BIG_DECIMAL:
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: return serializeObjectArray(et, (Object[]) arr);
        default: throw new AssertionError();
        }
    }

    private static Object deserializeArray(V3Type type, RLPItem item) {
        final V3Type et = type.elementType;
        switch (et.typeCode) {
        case V3Type.TYPE_CODE_BOOLEAN: return deserializeBooleanArray(type, item);
        case V3Type.TYPE_CODE_BYTE: return deserializeByteArray(type, item);
        case V3Type.TYPE_CODE_BIG_INTEGER:
        case V3Type.TYPE_CODE_BIG_DECIMAL:
        case V3Type.TYPE_CODE_ARRAY:
        case V3Type.TYPE_CODE_TUPLE: return deserializeObjectArray(type, item);
        default: throw new AssertionError();
        }
    }

    private static Object serializeBooleanArray(V3Type type, boolean[] booleans) {
        final byte[] blob = new byte[Integers.roundLengthUp(booleans.length, Byte.SIZE) / Byte.SIZE];
        int offset = 0;
        final int fullBytes = booleans.length / Byte.SIZE;
        for (int i = 0; i < fullBytes; i++, offset += Byte.SIZE) {
            byte b = 0;
            for (final int end = offset + Byte.SIZE; offset < end; offset++) {
                b = writeBit(booleans, offset, b);
            }
            blob[i] = b;
        }
        if(fullBytes != blob.length) {
            byte b = 0;
            while (offset < booleans.length) {
                b = writeBit(booleans, offset++, b);
            }
            blob[fullBytes] = b;
        }
        return type.arrayLen == -1
                ? new Object[] { Integers.toBytes(booleans.length), blob }
                : blob;
    }

    private static byte writeBit(boolean[] booleans, int offset, byte b) {
        if (booleans[offset]) {
            b |= 0b1000_0000 >>> (offset & 0x7);
        }
        return b;
    }

    public static boolean[] deserializeBooleanArray(V3Type type, RLPItem item) {
        if(type.arrayLen == -1) {
            final RLPItem lenItem = RLPItem.wrap(item.buffer, item.dataIndex, item.buffer.length);
            final RLPItem blob = RLPItem.wrap(item.buffer, lenItem.endIndex, item.buffer.length);
            return deserializeBooleanArray(lenItem.asInt(), blob.data());
        }
        return deserializeBooleanArray(type.arrayLen, item.data());
    }

    private static boolean[] deserializeBooleanArray(final int len, final byte[] blob) {
        final boolean[] booleans = new boolean[len];
        int offset = 0;
        final int fullBytes = len / Byte.SIZE;
        for (int i = 0, end = Byte.SIZE; i < fullBytes; i++, end += Byte.SIZE) {
            final byte b = blob[i];
            while (offset < end) {
                readBit(b, offset++, booleans);
            }
        }
        if(offset != len) {
            final byte b = blob[fullBytes];
            while (offset < len) {
                readBit(b, offset++, booleans);
            }
        }
        return booleans;
    }

    private static void readBit(byte b, int offset, boolean[] booleans) {
        final int mask = 0b1000_0000 >>> (offset & 0x7);
        booleans[offset] = (b & mask) != 0;
    }

    private static byte[] serializeByteArray(Object arr, boolean isString) {
        return isString ? ((String) arr).getBytes(StandardCharsets.UTF_8) : (byte[]) arr;
    }

    private static Object deserializeByteArray(V3Type type, RLPItem item) {
        return type.isString ? new String(item.data(), StandardCharsets.UTF_8) : item.data();
    }

    private static Object[] serializeObjectArray(V3Type elementType, Object[] objects) {
        final Object[] out = new Object[objects.length];
        for (int i = 0; i < objects.length; i++) {
            out[i] = serialize(elementType, objects[i]);
        }
        return out;
    }

    private static Object[] deserializeObjectArray(V3Type type, RLPItem list) {
        final List<RLPItem> elements = list.elements();
        final Object[] in = (Object[]) Array.newInstance(type.elementClass, elements.size()); // reflection
        for (int i = 0; i < in.length; i++) {
            in[i] = deserialize(type.elementType, elements.get(i));
        }
        return in;
    }
}
