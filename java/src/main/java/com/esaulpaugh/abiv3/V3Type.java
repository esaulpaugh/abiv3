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

public final class V3Type {

    public static final int TYPE_CODE_BOOLEAN = 0;
    public static final int TYPE_CODE_BIG_INTEGER = 1;
    public static final int TYPE_CODE_ARRAY = 2;
    public static final int TYPE_CODE_TUPLE = 3;
    public static final int TYPE_CODE_BYTE = 9;

    final int typeCode;

    final String canonicalType;

    final Integer arrayLen; // if an array type
    final V3Type elementType;
    final Class<?> elementClass;
    final boolean isString;

    final Boolean unsigned; // if a number type
    final Integer bitLen;
    final Integer scale;

    final V3Type[] elementTypes; // if a tuple type

    V3Type(String canonicalType, Integer arrayLen, V3Type elementType, Class<?> elementClass, boolean isString) {
        this(canonicalType, TYPE_CODE_ARRAY, arrayLen, elementType, elementClass, isString, null, null, null, null);
    }

    V3Type(String canonicalType, Boolean unsigned, Integer bitLen) {
        this(canonicalType, TYPE_CODE_BIG_INTEGER, null, null, null, null, unsigned, bitLen, null, null);
    }

    V3Type(String canonicalType, Boolean unsigned, Integer bitLen, Integer scale) {
        this(canonicalType, TYPE_CODE_BIG_INTEGER, null, null, null, null, unsigned, bitLen, scale, null);
    }

    V3Type(String canonicalType, V3Type[] elementTypes) {
        this(canonicalType, V3Type.TYPE_CODE_TUPLE, null, null, null, null, null, null, null, elementTypes);
    }

    private V3Type(String canonicalType, int typeCode, Integer arrayLen, V3Type elementType, Class<?> elementClass,
                   Boolean isString, Boolean unsigned, Integer bitLen, Integer scale,
                   V3Type[] elementTypes) {
        this.canonicalType = canonicalType;
        this.typeCode = typeCode;
        this.arrayLen = arrayLen;
        this.elementClass = elementClass;
        this.elementType = elementType;
        this.isString = isString != null && isString;
        this.unsigned = unsigned;
        this.bitLen = bitLen;
        this.scale = scale;
        this.elementTypes = elementTypes;
    }

    static final V3Type BYTE = new V3Type("-BYTE-", TYPE_CODE_BYTE,
            null, null, null, null,
            false, 8, null, null);

    static final V3Type STRING = new V3Type("string", -1, BYTE, Byte.class, true);

    static final V3Type BOOL = new V3Type("bool", TYPE_CODE_BOOLEAN,
            null, null, null, null,
            true, 1, null, null);

    static final V3Type ADDRESS = new V3Type("address", true, 160);

    static final V3Type FUNCTION = new V3Type("function", 24, BYTE, Byte.class, false);
}
