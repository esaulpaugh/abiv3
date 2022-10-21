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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

public final class Main {

    static final V3Type DYN_ARR_OF_BOOL = new V3Type("bool[]", -1, V3Type.BOOL, boolean.class, false);
    static final V3Type ARR_0_OF_BOOL = new V3Type("bool[0]", 0, V3Type.BOOL, boolean.class, false);
    static final V3Type ARR_8_OF_BOOL = new V3Type("bool[8]", 8, V3Type.BOOL, boolean.class, false);

    static final V3Type UINT72 = new V3Type("uint72", true, 72);
    static final V3Type INT16 = new V3Type("int16", false, 16);
    static final V3Type DYN_ARR_OF_UINT72 = new V3Type("uint72[]", -1, UINT72, BigInteger.class, false);

    static final V3Type FIXED_128x18 = new V3Type("fixed128x18", false, 128, 18);

    static final V3Type DYN_ARRAY_OF_TUPLE_OF_DYN_ARR_OF_BOOL = new V3Type("(bool[])[]", -1, new V3Type("bool[]", new V3Type[] { DYN_ARR_OF_BOOL }), Object[].class, false);

    static final V3Type DYN_ARR_OF_STRING = new V3Type("string[]", -1, V3Type.STRING, String.class, false);
    static final V3Type STRING_ARRAY_ARRAY = new V3Type("string[][]", -1, DYN_ARR_OF_STRING, String[].class, false);

    static final V3Type TUPLE_OF_STRING_BOOL_BOOL_UINT72 = new V3Type("(string,bool,bool,int72)", new V3Type[] { V3Type.STRING, V3Type.BOOL, V3Type.BOOL, UINT72 });
    static final V3Type ARR_2_OF_TUPLE_OF_STRING_BOOL_BOOL_UINT72 = new V3Type("(string,bool,bool,int72)[2]", 2, TUPLE_OF_STRING_BOOL_BOOL_UINT72, Object[].class, false);

    static final V3Type TUPLE_OF_FUNCTION_BYTES_AND_TUPLE_OF_INT_16_AND_BYTES = new V3Type(
            "(function,bytes,(int16,bytes))",
            new V3Type[] {
                V3Type.FUNCTION,
                new V3Type("bytes", -1, V3Type.BYTE, Byte.class, false),
                new V3Type("(int16,bytes)", new V3Type[] {
                        INT16,
                        new V3Type("bytes", -1, V3Type.BYTE, Byte.class, false)
            })
    });

    static final V3Type TUPLE_OF_ADDRESS = new V3Type("(address)", new V3Type[] { V3Type.ADDRESS });
    static final V3Type TUPLE_OF_ADDRESS_ADDRESS = new V3Type("(address,address)", new V3Type[] { V3Type.ADDRESS, V3Type.ADDRESS });

    static int caseNumber = 0;

    public static void main(String[] args) {

        System.out.println("#\t\t\tSelector\tSignature\t\t\tCalldata example");

        testSingle(new V3Type("bool[12]", 12, V3Type.BOOL, boolean.class, false),
                new boolean[] { false, false, false, false, false, true, true, true, true, true, true, true });

        testSingle(ARR_0_OF_BOOL, new boolean[] { });
        testSingle(ARR_8_OF_BOOL, new boolean[] { true, true, true, true, true, true, true, true });

        testSingle(DYN_ARR_OF_BOOL, new boolean[] { });
        testSingle(DYN_ARR_OF_BOOL, new boolean[] { false, false, true });

        testSingle(UINT72, BigInteger.TEN);
        testSingle(DYN_ARR_OF_UINT72, new BigInteger[] { BigInteger.valueOf(2L), BigInteger.ZERO });

        testSingle(V3Type.STRING, "abcd");
        test(new V3Type[] { V3Type.STRING, V3Type.STRING }, "abcd", "efg");
        testSingle(DYN_ARR_OF_STRING, new String[] { "abcd", "efg" });
        testSingle(STRING_ARRAY_ARRAY, new String[][] { new String[] { "abcd", "efg" } });
        testSingle(V3Type.FUNCTION, new byte[] { 0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23 });

        testSingle(
                DYN_ARRAY_OF_TUPLE_OF_DYN_ARR_OF_BOOL,
                new Object[] {
                    new Object[] { new boolean[] { true, false, false } },
                    new Object[] { new boolean[] { true, true, true } }
                }
        );

        test(
                new V3Type[] { FIXED_128x18, FIXED_128x18 },
                new BigDecimal(BigInteger.TEN, 18), new BigDecimal(BigInteger.valueOf(125_000L), 18)
        );

        test(
                new V3Type[] { ARR_2_OF_TUPLE_OF_STRING_BOOL_BOOL_UINT72, new V3Type("uint8", true, 8) },
                new Object[] { new Object[] { "A", false, true, BigInteger.TEN }, new Object[] { "B", true, false, BigInteger.ONE } },
                BigInteger.valueOf(255L)
        );

        test(
                new V3Type[] { TUPLE_OF_FUNCTION_BYTES_AND_TUPLE_OF_INT_16_AND_BYTES, V3Type.STRING },
                new Object[] {
                        new byte[24],
                        new byte[] { -5, 4 },
                        new Object[] {
                                BigInteger.valueOf(-10L),
                                new byte[] { 0, 1, 3, 5, 7, 10, -1 }
                        }
                },
                "Oi!"
        );

        final BigInteger addr0 = new BigInteger("e102030405060708090a0b0c0d0e0f0f0f0f0f0d", 16);
        final BigInteger addr1 = new BigInteger("b1b2b3b4b5b6b7b8b90a0b0c0d0e0c0c0c0c0c0c", 16);
        test(new V3Type[] { V3Type.ADDRESS, INT16 }, addr0, BigInteger.valueOf(-2L));
        test(
                new V3Type[] { TUPLE_OF_ADDRESS, TUPLE_OF_ADDRESS_ADDRESS },
                new Object[] { addr0 },
                new Object[] { addr1, addr0 }
        );
    }

    private static void testSingle(V3Type type, Object value) {
        test(new V3Type[] { type }, value);
    }

    private static void test(final V3Type[] schema, final Object... values) {
        final byte[] rlp = V3.toRLP("foo", schema, values, true);
        System.out.println("case" + caseNumber++ + ":\t\t"
                + new BigInteger(1, Arrays.copyOfRange(rlp, 0, 4)).toString(16) + "\t"
                + V3.createSignature("foo", schema) + " --> "
                + new BigInteger(1, rlp).toString(16) + "\t\t"
                + " (len " + rlp.length + ")");
        final Object[] decoded = V3.fromRLP("foo", schema, rlp);
        final boolean eq = Arrays.deepEquals(values, decoded);
        if(!eq) {
            throw new AssertionError(values + " != " + decoded);
        }
//        System.out.println(value + " == " + decoded[0]);
    }
}