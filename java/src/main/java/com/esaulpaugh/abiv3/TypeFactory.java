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

import java.util.*;

import static com.esaulpaugh.abiv3.V3Type.*;

/** Creates the appropriate {@link V3Type} object for a given type string. */
public final class TypeFactory {

    private TypeFactory() {}

    static final int ADDRESS_BIT_LEN = 160;

    private static final int DECIMAL_BIT_LEN = 168;
    private static final int DECIMAL_SCALE = 10;

    private static final int FIXED_BIT_LEN = 128;
    private static final int FIXED_SCALE = 18;

    private static final int FUNCTION_BYTE_LEN = 24;

    private static final int MAX_LENGTH_CHARS = 2_000;

    private static final Map<String, V3Type> BASE_TYPE_MAP;

    static {
        BASE_TYPE_MAP = new HashMap<>(256);

        for(int n = 8; n <= 256; n += 8) {
            mapBigInteger("int" + n, false, n);
            mapBigInteger("uint" + n, true, n);
        }

        for (int n = 1; n <= 32; n++) {
            mapByteArray("bytes" + n, n);
        }

        mapBigInteger("address", true, 160);
        mapByteArray("function", FUNCTION_BYTE_LEN);
        mapByteArray("bytes", -1);
        BASE_TYPE_MAP.put("string", new V3Type("string", -1, String.class, String[].class, BYTE, true));
        BASE_TYPE_MAP.put("bool", BOOL);
    }

    private static void mapBigInteger(String type, boolean unsigned, int bitLen) {
        BASE_TYPE_MAP.put(type, new V3Type(type, unsigned, bitLen));
    }

    private static void mapByteArray(String type, int arrayLen) {
        BASE_TYPE_MAP.put(type, new V3Type(type, arrayLen, Byte.class, Byte[].class, BYTE, false));
    }

    public static V3Type create(String rawType) {
        return build(rawType, null);
    }

    private static V3Type build(final String rawType, V3Type baseType) {
        try {
            final int lastCharIdx = rawType.length() - 1;
            if (rawType.charAt(lastCharIdx) == ']') { // array

                final int secondToLastCharIdx = lastCharIdx - 1;
                final int arrayOpenIndex = rawType.lastIndexOf('[', secondToLastCharIdx);

                final V3Type elementType = build(rawType.substring(0, arrayOpenIndex), baseType);
                final String type = elementType.canonicalType + rawType.substring(arrayOpenIndex);
                final int length = arrayOpenIndex == secondToLastCharIdx ? -1 : parseLen(rawType.substring(arrayOpenIndex + 1, lastCharIdx));

                return new V3Type(type, length, elementType.arrayClass(), null, elementType, false);
            }
            if(baseType != null || (baseType = resolveBaseType(rawType)) != null) {
                return baseType;
            }
        } catch (StringIndexOutOfBoundsException ignored) { // e.g. type equals "" or "82]" or "[]" or "[1]"
            /* fall through */
        }
        throw new IllegalArgumentException("unrecognized type: \"" + rawType + '"');
    }

    private static int parseLen(String lenStr) {
        try {
            if(leadDigitValid(lenStr.charAt(0)) || "0".equals(lenStr)) {
                return Integer.parseInt(lenStr);
            }
        } catch (NumberFormatException ignored) {
            /* fall through */
        }
        throw new IllegalArgumentException("bad array length");
    }

    private static V3Type resolveBaseType(final String baseTypeStr) {
        if (baseTypeStr.charAt(0) == '(') {
            return parseTupleType(baseTypeStr);
        }
        final V3Type ret = BASE_TYPE_MAP.get(baseTypeStr);
        return ret != null ? ret : tryParseFixed(baseTypeStr);
    }

    private static V3Type tryParseFixed(final String type) {
        final int idx = type.indexOf("fixed");
        boolean unsigned = false;
        if (idx == 0 || (unsigned = (idx == 1 && type.charAt(0) == 'u'))) {
            final int indexOfX = type.lastIndexOf('x');
            try {
                final String mStr = type.substring(idx + "fixed".length(), indexOfX);
                final String nStr = type.substring(indexOfX + 1); // everything after x
                if (leadDigitValid(mStr.charAt(0)) && leadDigitValid(nStr.charAt(0))) { // starts with a digit 1-9
                    final int M = Integer.parseInt(mStr); // no parseUnsignedInt on older Android versions?
                    final int N = Integer.parseInt(nStr);
                    if (M % 8 == 0 && M <= 256 && N <= 80) { // no multiples of 8 less than 8 except 0
                        return new V3Type((unsigned ? "ufixed" : "fixed") + M + 'x' + N, unsigned, M);
                    }
                }
            } catch (IndexOutOfBoundsException | NumberFormatException ignored) {
                /* fall through */
            }
        }
        return null;
    }

    private static boolean leadDigitValid(char c) {
        return c > '0' && c <= '9';
    }

    private static V3Type parseTupleType(final String rawTypeStr) { /* assumes that rawTypeStr.charAt(0) == '(' */
        final int len = rawTypeStr.length();
        if (len == 2 && rawTypeStr.equals("()")) return new V3Type(new V3Type[0]);
        final List<V3Type> elements = new ArrayList<>();
        int argEnd = 1;
        final StringBuilder canonicalBuilder = new StringBuilder("(");
        try {
            do {
                final int argStart = argEnd;
                switch (rawTypeStr.charAt(argStart)) {
                case ')':
                case ',': return null;
                case '(': argEnd = nextTerminator(rawTypeStr, findSubtupleEnd(rawTypeStr, argStart)); break;
                default: argEnd = nextTerminator(rawTypeStr, argStart);
                }
                final V3Type e = build(rawTypeStr.substring(argStart, argEnd), null);
                canonicalBuilder.append(e.canonicalType).append(',');
                elements.add(e);
            } while (rawTypeStr.charAt(argEnd++) != ')');
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("@ index " + elements.size() + ", " + iae.getMessage(), iae);
        }
        return argEnd == len
                ? new V3Type(elements.toArray(new V3Type[0]))
                : null;
    }

    private static int nextTerminator(String signature, int i) {
        char c;
        do {
            c = signature.charAt(++i);
        } while (c != ',' && c != ')');
        return i;
    }

    private static int findSubtupleEnd(String parentTypeString, int i) {
        int depth = 1;
        do {
            char x = parentTypeString.charAt(++i);
            if(x <= ')') {
                if(x == ')') {
                    if(depth <= 1) {
                        return i;
                    }
                    depth--;
                } else if(x == '(') {
                    depth++;
                }
            }
        } while(true);
    }
}
