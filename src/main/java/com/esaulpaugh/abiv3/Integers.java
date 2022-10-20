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
import java.nio.ByteBuffer;

public final class Integers {

    private Integers() {}

    /**
     * Retrieves an integer up to four bytes in length. Big-endian two's complement format.
     *
     * @param buffer  the array containing the integer's representation
     * @param offset  the array index locating the integer
     * @param len     the length in bytes of the integer's representation
     * @return the integer
     * @throws IllegalArgumentException if {@code lenient} is false and the integer's representation is found to have leading zeroes
     * @see #toBytes(int)
     * @see #putInt(int, byte[], int)
     */
    public static int getInt(final byte[] buffer, int offset, final int len) {
        if (len < 0 || len > Integer.BYTES || buffer[offset] == 0) {
            throw new IllegalArgumentException();
        }
        int shift = Byte.SIZE * (len - 1);
        int val = 0;
        for (int i = 0; i < len; i++) {
            val |= (buffer[offset + i] & 0xFF) << shift;
            shift -= Byte.SIZE;
        }
        return val;
    }

    /**
     * Retrieves an integer up to eight bytes in length. Big-endian two's complement format.
     *
     * @param buffer  the array containing the integer's representation
     * @param offset  the array index locating the integer
     * @param len     the length in bytes of the integer's representation
     * @return the integer
     * @throws IllegalArgumentException if the integer's representation is found to have leading zeroes
     */
    public static long getLong(final byte[] buffer, final int offset, final int len) {
        if (len < 0 || len > Long.BYTES || buffer[offset] == 0) {
            throw new IllegalArgumentException();
        }
        int shift = Byte.SIZE * (len - 1);
        long val = 0L;
        for (int i = 0; i < len; i++) {
            val |= (buffer[offset + i] & 0xFFL) << shift;
            shift -= Byte.SIZE;
        }
        return val;
    }

    public static BigInteger getBigInt(byte[] buffer, int offset, int len) {
        if(len != 0) {
            if(buffer[offset] == 0) {
                throw new IllegalArgumentException();
            }
            byte[] arr = new byte[len];
            System.arraycopy(buffer, offset, arr, 0, len);
            return new BigInteger(1, arr);
        }
        return BigInteger.ZERO;
    }

    /**
     * Returns the byte length of an integer's minimal (without leading zeroes) two's complement representation. The
     * integer zero always has zero length.
     *
     * @param val the integer
     * @return the byte length
     */
    public static int len(int val) {
        int len = 0;
        while (val != 0) {
            len++;
            val >>>= Byte.SIZE;
        }
        return len;
    }

    /**
     * Returns an integer's minimal big-endian two's complement representation. The integer zero is represented by the
     * empty byte array.
     *
     * @param val the integer
     * @return the minimal representation
     */
    public static byte[] toBytes(int val) {
        byte[] bytes = new byte[len(val)];
        putInt(val, bytes, 0);
        return bytes;
    }

    /**
     * Inserts into a byte array an integer's minimal (without leading zeroes), big-endian two's complement representation,
     * up to four bytes in length. The integer zero always has length zero.
     *
     * @param val the integer to be inserted
     * @param o   the destination array
     * @param i   the index into the destination for the output
     * @see #toBytes(int)
     */
    public static void putInt(int val, byte[] o, int i) {
        final byte[] temp = new byte[Integer.BYTES];
        int j = Integer.BYTES;
        for ( ; val != 0; val >>>= Byte.SIZE) {
            temp[--j] = (byte) val;
        }
        System.arraycopy(temp, j, o, i, Integer.BYTES - j);
    }

    public static void putLong(long val, ByteBuffer o) {
        final byte[] temp = new byte[Long.BYTES];
        int j = Long.BYTES;
        for ( ; val != 0; val >>>= Byte.SIZE) {
            temp[--j] = (byte) val;
        }
        o.put(temp, j, Long.BYTES - j);
    }
}
