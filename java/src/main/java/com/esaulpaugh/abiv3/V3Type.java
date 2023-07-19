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

public final class V3Type {

    private static final ClassLoader CLASS_LOADER = Thread.currentThread().getContextClassLoader();

    public static final int TYPE_CODE_BOOLEAN = 0;
    public static final int TYPE_CODE_BIG_INTEGER = 1;
    public static final int TYPE_CODE_ARRAY = 2;
    public static final int TYPE_CODE_TUPLE = 3;
    public static final int TYPE_CODE_BYTE = 9;

    final int typeCode;

    final String canonicalType;

    final Integer arrayLen; // if an array type

    final Class<?> clazz;
    final V3Type elementType;
    final Class<?> arrayClass;
    final boolean isString;

    final Boolean unsigned; // if a number type
    final Integer bitLen;

    final V3Type[] elementTypes; // if a tuple type

    public Class<?> arrayClass() {
        if (arrayClass != null) {
            return arrayClass;
        }
        try {
            return Class.forName('[' + clazz.getName(), false, CLASS_LOADER);
        } catch (ClassNotFoundException cnfe) {
            throw new AssertionError(cnfe);
        }
    }

    V3Type(String canonicalType, Integer arrayLen, Class<?> clazz, Class<?> arrayClass, V3Type elementType, boolean isString) {
        this(canonicalType, TYPE_CODE_ARRAY, arrayLen, clazz, arrayClass, elementType, isString, null, null, null);
    }

    V3Type(String canonicalType, Boolean unsigned, Integer bitLen) {
        this(canonicalType, TYPE_CODE_BIG_INTEGER, null, BigInteger.class, BigInteger[].class, null, null, unsigned, bitLen, null);
    }

    V3Type(V3Type[] elementTypes) {
        this(createSignature(elementTypes), V3Type.TYPE_CODE_TUPLE, null, Object[].class, Object[][].class, null, null, null, null, elementTypes);
    }

    private V3Type(String canonicalType, int typeCode, Integer arrayLen, Class<?> clazz, Class<?> arrayClass, V3Type elementType,
                   Boolean isString, Boolean unsigned, Integer bitLen, V3Type[] elementTypes) {
        this.canonicalType = canonicalType;
        this.typeCode = typeCode;
        this.arrayLen = arrayLen;
        this.clazz = clazz;
        this.arrayClass = arrayClass;
        this.elementType = elementType;
        this.isString = isString != null && isString;
        this.unsigned = unsigned;
        this.bitLen = bitLen;
        this.elementTypes = elementTypes;
    }

    static final V3Type BYTE = new V3Type("-BYTE-", TYPE_CODE_BYTE,
            null, Byte.class, Byte[].class, null, null,
            false, 8, null);

    static final V3Type BOOL = new V3Type("bool", TYPE_CODE_BOOLEAN,
            null, Boolean.class, Boolean[].class, null, null,
            true, 1, null);

    private static String createSignature(V3Type[] elementTypes) {
        if (elementTypes.length == 0) {
            return "()";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (V3Type t : elementTypes) {
            sb.append(t.canonicalType).append(',');
        }
        return sb.deleteCharAt(sb.length() - 1).append(')').toString(); // replace trailing comma
    }
}
