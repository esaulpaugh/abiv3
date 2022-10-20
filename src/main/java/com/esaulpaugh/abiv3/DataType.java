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

/** Enumeration of the five RLP data types. */
public enum DataType {

    SINGLE_BYTE(0),
    STRING_SHORT(0x80),
    STRING_LONG(0xb7),
    LIST_SHORT(0xc0),
    LIST_LONG(0xf7);

    public static final int MIN_LONG_DATA_LEN = 56;

    public final byte offset;

    DataType(int offset) {
        this.offset = (byte) offset;
    }

    /**
     * @param leadByte the first (zeroth) byte of an RLP encoding
     * @return one of the five enumerated RLP data types
     */
    public static DataType type(final byte leadByte) {
        if(leadByte < (byte) 0xB8) return STRING_SHORT;
        if(leadByte < (byte) 0xC0) return STRING_LONG;
        if(leadByte < (byte) 0xF8) return LIST_SHORT;
        if(leadByte < 0) return LIST_LONG;
        return SINGLE_BYTE;
    }
}
