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
from PyByteBuffer import ByteBuffer

from abiv3 import Utils
from abiv3.RLPEncoder import RLPEncoder
from abiv3.RLPItem import RLPItem
from abiv3.RLPIterator import RLPIterator

## next tuple len list type str sum bytes int
class V3:

    V3_VERSION_ID = 0

    @staticmethod
    def to_rlp(function_number, schema, vals):
        objects = []
        header = V3.header(function_number)
        if len(header) == 2:
            objects.append(header[1])
        for e in V3.serialize_tuple(schema, vals):
            objects.append(e)
        buf_len = 1 + RLPEncoder.sum_encoded_len(objects)
        buf = ByteBuffer.allocate(buf_len)
        buf.put(header[0])
        RLPEncoder.put_sequence(objects, buf)
        buf.rewind()
        return buf.array(buf_len)

    @staticmethod
    def from_rlp(schema, rlp):
        zeroth = rlp[0]
        version = zeroth & 0b1110_0000
        if version != V3.V3_VERSION_ID:
            raise Exception()
        sequence_start = 1
        fn_number = zeroth & 0b0001_1111
        if fn_number >= 31:
            fn_number_item = RLPItem.wrap(rlp, 1, rlp.length)
            fn_number = fn_number_item.asInt()
            if fn_number < 31:
                raise Exception()
            sequence_start = fn_number_item.endIndex
        return V3.deserialize_tuple(schema, RLPIterator.sequence_iterator(rlp, sequence_start))

    @staticmethod
    def header(fn_num):
        arr = Utils.unsigned_to_bytes(fn_num)
        if fn_num < 31:
            if fn_num == 0:
                return [b'\x00']
            return [arr]
        return [b'31', arr]

    @staticmethod
    def validate_length(expected_len, actual_len):
        if expected_len != -1 and (expected_len != actual_len):
            raise Exception(str(expected_len) + ' != ' + str(actual_len))

    @staticmethod
    def serialize_tuple(schema, vals):
        V3.validate_length(len(schema), len(vals))
        out = []
        for i in range(0, len(schema)):
            out.append(V3.serialize(schema[i], vals[i]))
        return out

    @staticmethod
    def deserialize_tuple(schema, sequence_iterator):
        elements = []
        for i in range(0, len(schema)):
            elements.append(V3.deserialize(schema[i], sequence_iterator))
        if sequence_iterator.has_next():
            raise Exception('trailing unconsumed items')
        return elements

    @staticmethod
    def serialize(v3_type, obj):
        code = v3_type.typeCode
        if code == 0:
            return V3.serialize_boolean(obj)
        if code == 1:
            return V3.serialize_integer(v3_type, obj)
        if code == 2:
            return V3.serialize_decimal(v3_type, obj)
        if code == 3:
            return V3.serialize_array(v3_type, obj)
        if code == 4:
            return V3.serialize_tuple(v3_type.elementTypes, obj)
        raise Exception('??')

    @staticmethod
    def deserialize(v3_type, sequence_iterator):
        code = v3_type.typeCode
        if code == 0:
            return V3.deserialize_boolean(sequence_iterator)
        if code == 1:
            return V3.deserialize_integer(v3_type, sequence_iterator)
        if code == 2:
            return V3.deserialize_decimal(v3_type, sequence_iterator)
        if code == 3:
            return V3.deserialize_array(v3_type, sequence_iterator)
        if code == 4:
            return V3.deserialize_tuple(v3_type.elementTypes, sequence_iterator)
        raise Exception('??')

    @staticmethod
    def serialize_boolean(val):
        return b'\x01' if val else b''

    @staticmethod
    def deserialize_boolean(sequence_iterator):
        enc = sequence_iterator.next().data()
        if enc == b'\x01':
            return True
        if enc == b'\x00':
            return False
        raise Exception('illegal boolean RLP: 0x" + enc + ". Expected 0x1 or 0x0')

    @staticmethod
    def serialize_integer(v3_type, val):
        if val != 0:
            the_bytes = Utils.unsigned_to_bytes(val)
            if val < 0:
                return V3.sign_extend_negative(the_bytes, v3_type.bitLen / 8)
            if the_bytes[0] != 0:
                return the_bytes
            return the_bytes[1: len(the_bytes): 1]
        return b''

    @staticmethod
    def sign_extend_negative(negative, new_width):
        extended = bytearray(new_width)
        for i in range(0, new_width):
            extended[i] = 0xff
        negative_len = len(negative)
        j = new_width - negative.length
        for i in range(0, negative_len):
            extended[j] = negative[i]
            j = j + 1
        return extended

    @staticmethod
    def deserialize_integer(v3_type, sequence_iterator):
        item = sequence_iterator.next()
        if v3_type.unsigned or ((item.dataLength * 8) < v3_type.bitLen):
            return item.as_int()
        return item.as_int_signed()

    @staticmethod
    def serialize_decimal(v3_type, val):
        return None

    @staticmethod
    def deserialize_decimal(v3_type, val):
        return None

    @staticmethod
    def serialize_array(v3_type, arr):
        if v3_type.elementType.typeCode == 9:
            the_bytes = bytes(arr, 'utf-8') if v3_type.isString else arr
            V3.validate_length(v3_type.arrayLen, len(the_bytes))
            return the_bytes
        V3.validate_length(v3_type.arrayLen, len(arr))
        out = []
        for i in range(0, len(arr)):
            out.append(V3.serialize(v3_type.elementType, arr[i]))
        return out

    @staticmethod
    def deserialize_array(v3_type, sequence_iterator):
        if v3_type.elementType.typeCode == 9:
            the_bytes = sequence_iterator.next().data()
            if v3_type.isString:
                return the_bytes.decode('utf-8')
            return the_bytes
        list_item = sequence_iterator.next()
        elements = list_item.elements()
        list_iter = list_item.iterator()
        found = []
        for i in range(0, len(elements)):
            found.append(V3.deserialize(v3_type.elementType, list_iter))
        return found
