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
import java.util.Arrays;
import java.util.Random;

public final class Main {

    private static final Random RANDOM = new Random();

    static int caseNumber = 0;

    public static void main(String[] args) {

        System.out.println("#\t\t\tfn#\t\tSignature\t\tCalldata example");

        test(64, TypeFactory.create("(uint8)"), BigInteger.valueOf(0xfeL));
        test(60, TypeFactory.create("(uint8,())"), BigInteger.valueOf(0xfeL), new Object[0]);
        test(100, TypeFactory.create("(bytes,bytes1)"), new byte[3], new byte[1]);

//        testSingle("(address,uint256,uint256,address,address,address,uint256,uint256,uint8,uint256,uint256,bytes32,uint256,bytes32,bytes32,uint256,(uint256,address)[],bytes)",
//                new Object[] {
//                    BigInteger.ZERO,
//                    BigInteger.ZERO,
//                    new BigInteger("54600000000000000"),
//                    new BigInteger("19375beb75fb14ce2ed58b684dc286f402721c69", 16),
//                    new BigInteger("003C00500000aD104D7DBd00e3ae0A5C00560b00", 16),
//                    new BigInteger("68e5d4ff0274dd95760e300ef16b81c5eed09833", 16),
//                    BigInteger.valueOf(3833L),
//                    BigInteger.valueOf(1L),
//                    BigInteger.valueOf(2L),
//                    BigInteger.valueOf(1669168117L),
//                    BigInteger.valueOf(1671760117L),
//                    new byte[32],
//                    new BigInteger("24446860302761739304752683030156737591518664810215442929806760874570849832304"),
//                    new byte[] { 0, 0, 0, 123, 2, 35, 0, -111, -89, -19, 1, 35, 0, 114, -9, 0, 106, 0, 77, 96, -88, -44, -25, 29, 89, -101, -127, 4, 37, 15, 0, 0 },
//                    new byte[] { 0, 0, 0, 123, 2, 35, 0, -111, -89, -19, 1, 35, 0, 114, -9, 0, 106, 0, 77, 96, -88, -44, -25, 29, 89, -101, -127, 4, 37, 15, 0, 0 },
//                    BigInteger.valueOf(1L),
//                    new Object[] {
//                            new Object[] { new BigInteger("1400000000000000"), new BigInteger("0000b26b00c1f0df003000390027140000faa610", 16) }
//                    },
//                    new byte[] { 54, -22, -107, 33, 104, 23, -44, 109, -110, 91, -21, -93, 40, 62, 4, -102, -46, -69, -7, 21, 17, -87, 124, -45, -114, -92, -74, -8, -32, -70, 15, 113, 80, 64, 94, -100, -119, -53, -93, 125, -89, -34, 17, -126, -123, 119, -39, -97, 84, -101, 81, 0, -77, 92, 68, -1, 93, 120, -39, -23, 53, 30, 53, 69, 28 }
//                }
//        );
//
//        final BigInteger[] bigInts = new BigInteger[] {
//                BigInteger.valueOf(2L),
//                BigInteger.ZERO,
//                new BigInteger("16777216"),
//                new BigInteger("16777217"),
//                new BigInteger("16777218"),
//                new BigInteger("16777219"),
//                new BigInteger("16777220"),
//                new BigInteger("16777221"),
//                new BigInteger("16777222"),
//                new BigInteger("16777223"),
//                new BigInteger("16777224"),
//                new BigInteger("16777225"),
//                new BigInteger("65535")
//        };
//
//        testSingle("uint72[]", bigInts);
//
//        bigInts[bigInts.length - 1] = new BigInteger("65536");
//        testSingle("uint72[]", bigInts);
//
//        testSingle("bool[12]", new boolean[] { false, false, false, false, false, true, true, true, true, true, true, true });
//
//        testSingle("bool[0]", new boolean[] { });
//        testSingle("bool[8]", new boolean[] { true, true, true, true, true, true, true, true });
//
//        testSingle("bool[]", new boolean[] { });
//        testSingle("bool[]", new boolean[] { false, false, true });
//
//        testSingle("uint72", BigInteger.TEN);
//        testSingle("uint72[]", new BigInteger[] { BigInteger.valueOf(2L), BigInteger.ZERO });
//
//        testSingle("string", "abcd");
//        testSingle("(string,string)", new Object[] { "abcd", "efg" });
//        testSingle("string[]", new String[] { "abcd", "efg" });
//        testSingle("string[][]", new String[][] { new String[] { "abcd", "efg" } });
//        testSingle("function", new byte[] { 0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23 });
//
//        testSingle(
//                "(bool[])[]",
//                new Object[] {
//                    new Object[] { new boolean[] { true, false, false } },
//                    new Object[] { new boolean[] { true, true, true } }
//                }
//        );
//
//        testSingle(
//                "(fixed128x18,fixed128x18)",
//                new Object[] { BigInteger.TEN, BigInteger.valueOf(125_000L) }
//        );
//
//        test(
//                new V3Type[] { TypeFactory.create("(string,bool,bool,int72)[2]"), new V3Type("uint8", true, 8) },
//                new Object[] { new Object[] { "A", false, true, BigInteger.TEN }, new Object[] { "B", true, false, BigInteger.ONE } },
//                BigInteger.valueOf(255L)
//        );
//
//        testSingle(
//                "((function,bytes,(int16,bytes)),string)",
//                new Object[] {
//                        new Object[] {
//                                new byte[24],
//                                new byte[] { -5, 4 },
//                                new Object[] {
//                                        BigInteger.valueOf(-10L),
//                                        new byte[] { 0, 1, 3, 5, 7, 10, -1 }
//                                }
//                        },
//                        "Oi!"
//                }
//        );
//
//        final BigInteger addr0 = new BigInteger("e102030405060708090a0b0c0d0e0f0f0f0f0f0d", 16);
//        final BigInteger addr1 = new BigInteger("b1b2b3b4b5b6b7b8b90a0b0c0d0e0c0c0c0c0c0c", 16);
//        test(new V3Type[] { TypeFactory.create("address"), TypeFactory.create("int16") }, addr0, BigInteger.valueOf(-2L));
//        test(
//                new V3Type[] { TypeFactory.create("(address)"), TypeFactory.create("(address,address)") },
//                new Object[] { addr0 },
//                new Object[] { addr1, addr0 }
//        );
    }

//    private static void test(final V3Type tupleType, final Object... values) {
//        test(RANDOM.nextInt(260), tupleType, values);
//    }

    private static void test(final int fnNumber, final V3Type tupleType, final Object... values) {
        final byte[] encoding = V3.encodeFunction(fnNumber, tupleType, values);
        final String calldataStr = slowHex(encoding); // new BigInteger(1, rlp).toString(16);
        System.out.println("case" + caseNumber++ + ":\t\t"
                + fnNumber + "\t\t"
                + "foo" + tupleType.canonicalType + " --> "
                + calldataStr + "\t\t"
                + " (len " + encoding.length + ")");
        final Object[] decoded = V3.decodeFunction(tupleType, encoding);
        final boolean eq = Arrays.deepEquals(values, decoded);
        if (!eq) {
            throw new AssertionError(values + " != " + decoded);
        }
//        System.out.println(value + " == " + decoded[0]);
    }

    private static String slowHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}