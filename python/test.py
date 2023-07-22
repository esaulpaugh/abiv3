# Copyright 2022 Evan Saulpaugh
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import binascii

from abiv3.TypeFactory import TypeFactory
from abiv3.V3 import V3

# arr = b'\x7f'
# arr = b'\xb8\x38\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00'
# arr = b'\xf8\x38\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00'
# item = RLPItem.wrap(arr, 0, len(arr))
# print(item)

# items = [b'\x80']  # , b'\x7f'
# encoding_len = RLPEncoder.sum_encoded_len(items)
# print(encoding_len)
# buf = ByteBuffer.allocate(encoding_len)
# RLPEncoder.put_sequence(items, buf)
#
# print(buf)
#
# buf.rewind()
# arr = buf.array(encoding_len)
#
# print(binascii.hexlify(arr))

# n = len(arr)
# for i in range(0, n):
#     print(RLPItem.to_signed_byte(arr[i]))

# schema = [V3Type.BOOL, V3Type.INT256, V3Type.BYTES, V3Type.INT256_ARRAY_3, V3Type.UFIXED_128_X_18]
# objects = [True, 5, b'\x03\x09', [10, -16777215, 0], -10.9]  # TODO decimals


int_val = 10

print(int_val.__class__)
print(type(int_val))

t = TypeFactory.create("()")
print(t.canonicalType)

t = TypeFactory.create("(fixed128x3)[]")
print(t.canonicalType)

bool_type = TypeFactory.create("bool")

enc = V3.encode_function(1, [bool_type], [True])
valz = V3.decode_function([bool_type], enc)
print(valz)

t = TypeFactory.create("((bool,bool),(bool,bool),(bool,bool),(bool,bool))")
# print(dec.canonicalType)
# print(dec.elementType.canonicalType)
# print(dec.elementType.elementType.canonicalType)

enc = V3.encode_function(1, [t], [[[True, False], [False, False], [False, True], [True, True]]])
print(binascii.hexlify(enc))
valz = V3.decode_function([t], enc)
print(valz)

addr = TypeFactory.create('int32')
print(addr.bitLen)

schema = [TypeFactory.create("uint72[]")]
ints = [
    2,
    0,
    16_777_216,
    16_777_217,
    16_777_218,
    16_777_219,
    16_777_220,
    16_777_221,
    16_777_222,
    16_777_223,
    16_777_224,
    16_777_225,
    65535
]
objects = [ints]

print(objects)

arr = V3.encode_function(31, schema, objects)

n = len(arr)
print('len = ' + str(n))
print(binascii.hexlify(arr))
# for i in range(0, n):
#     print(Utils.to_signed_byte(arr[i]))

decoded = V3.decode_function(schema, arr)
print(decoded)

# =================================
ints[len(ints) - 1] = 65536
print()
# =================================

print(objects)

arr = V3.encode_function(62, schema, objects)

n = len(arr)
print('len = ' + str(n))
print(binascii.hexlify(arr))
# for i in range(0, n):
#     print(Utils.to_signed_byte(arr[i]))

decoded = V3.decode_function(schema, arr)
print(decoded)

# # for i in range(0, 10):
# #     print(i)
#
# z = bytearray(32)
#
# z[31] = 0xff
#
# print(int.from_bytes(z[31: 32: 1], "big"))
#
# obj = V3Type(V3Type.TYPE_CODE_BOOLEAN)
#
# print(obj)
#
# zz = V3.header(32)
#
# print(zz)
