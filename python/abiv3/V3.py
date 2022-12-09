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
from abiv3.RLPEncoder import RLPEncoder, DynamicBoolArray
from abiv3.RLPIterator import RLPIterator
from abiv3.V3Type import V3Type


# next tuple len list type str sum bytes int
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
            fn_number_item = Utils.wrap(rlp, 1, rlp.length)
            fn_number = fn_number_item.asInt()
            if fn_number < 31:
                raise Exception()
            sequence_start = fn_number_item.endIndex
        return V3.deserialize_tuple(schema, RLPIterator.sequence_iterator(rlp, sequence_start))

    @staticmethod
    def header(fn_num):
        arr = Utils.to_bytes_unsigned(fn_num)
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
            element = V3.serialize(schema[i], vals[i])
            out.append(element)
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
        if code == V3Type.TYPE_CODE_BOOLEAN:
            return V3.serialize_boolean(obj)
        if code == V3Type.TYPE_CODE_INTEGER:
            return V3.serialize_integer(v3_type, obj)
        if code == V3Type.TYPE_CODE_ARRAY:
            return V3.serialize_array(v3_type, obj)
        if code == V3Type.TYPE_CODE_TUPLE:
            return V3.serialize_tuple(v3_type.elementTypes, obj)
        raise Exception('??')

    @staticmethod
    def deserialize(v3_type, sequence_iterator):
        code = v3_type.typeCode
        if code == V3Type.TYPE_CODE_BOOLEAN:
            return V3.deserialize_boolean(sequence_iterator)
        if code == V3Type.TYPE_CODE_INTEGER:
            return V3.deserialize_integer(v3_type, sequence_iterator)
        if code == V3Type.TYPE_CODE_ARRAY:
            return V3.deserialize_array(v3_type, sequence_iterator)
        if code == V3Type.TYPE_CODE_TUPLE:
            return V3.deserialize_tuple(v3_type.elementTypes, sequence_iterator.next().iterator())
        raise Exception('??')

    @staticmethod
    def serialize_boolean(val):
        return b'\x01' if val else b'\x00'

    @staticmethod
    def deserialize_boolean(sequence_iterator):
        enc = sequence_iterator.next().data()
        if enc == b'\x01':
            return True
        if enc == b'\x00':
            return False
        raise Exception('illegal boolean RLP: expected 0x1 or 0x0')

    @staticmethod
    def serialize_integer(v3_type, val):
        if val != 0:
            the_bytes = Utils.to_bytes(val)
            if val < 0:
                return V3.sign_extend_negative(the_bytes, int(v3_type.bitLen / 8))
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
        j = new_width - negative_len
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
    def serialize_array(v3_type, arr):
        if v3_type.elementType.typeCode == V3Type.TYPE_CODE_BYTE:
            the_bytes = bytes(arr, 'utf-8') if v3_type.isString else arr
            V3.validate_length(v3_type.arrayLen, len(the_bytes))
            return the_bytes
        elif v3_type.elementType.typeCode == V3Type.TYPE_CODE_BOOLEAN:
            return V3.serialize_boolean_array(v3_type, arr)
        elif v3_type.elementType.typeCode == V3Type.TYPE_CODE_INTEGER:
            return V3.serialize_integer_array(v3_type, arr)
        return V3.serialize_object_array(v3_type, arr, b'')

    @staticmethod
    def deserialize_array(v3_type, sequence_iterator):
        if v3_type.elementType.typeCode == V3Type.TYPE_CODE_BYTE:
            the_bytes = sequence_iterator.next().data()
            if v3_type.isString:
                return the_bytes.decode('utf-8')
            return the_bytes
        elif v3_type.elementType.typeCode == V3Type.TYPE_CODE_BOOLEAN:
            return V3.deserialize_boolean_array(v3_type, sequence_iterator)
        elif v3_type.elementType.typeCode == V3Type.TYPE_CODE_INTEGER:
            return V3.deserialize_integer_array(v3_type, sequence_iterator.next())
        return V3.deserialize_object_array(v3_type, sequence_iterator.next(), False)

    @staticmethod
    def serialize_boolean_array(v3_type, booleans):
        V3.validate_length(v3_type.arrayLen, len(booleans))
        the_bytes = None
        if len(booleans) > 0:
            binary = '+'
            for b in booleans:
                binary = binary + ('1' if b else '0')
            the_bytes = V3.serialize_integer(type, int(binary, 2))
        return DynamicBoolArray(Utils.to_bytes_unsigned(len(booleans)), the_bytes) if v3_type.arrayLen == -1 else the_bytes

    @staticmethod
    def deserialize_boolean_array(v3_type, sequence_iterator):
        the_len = sequence_iterator.next().as_int() if v3_type.arrayLen == -1 else v3_type.arrayLen
        if the_len == 0:
            return []
        binary = '{0:b}'.format(sequence_iterator.next().as_int())
        num_chars = len(binary)
        implied_zeros = the_len - num_chars
        booleans = []
        for c in range(0, implied_zeros):
            booleans.append(False)
        for c in range(0, num_chars):
            booleans.append(True if binary[c] == '1' else False)
        return booleans

    @staticmethod
    def serialize_integer_array(v3_type, arr):
        V3.validate_length(v3_type.arrayLen, len(arr))
        max_raw_len = 0
        for e in arr:
            int_bytes = V3.serialize_integer(v3_type.elementType, e)
            if len(int_bytes) > max_raw_len:
                max_raw_len = len(int_bytes)
        var_width = V3.serialize_object_array(v3_type, arr, b'0x00')
        var_width_len = RLPEncoder.sum_encoded_len(var_width) + 1
        fixed_width = V3.serialize_large_integer_array(v3_type, arr, max_raw_len)
        return var_width if var_width_len < len(fixed_width) else fixed_width

    @staticmethod
    def deserialize_integer_array(v3_type, arr):
        data = arr.data()
        if data[0] != 0x00:
            return V3.deserialize_large_integer_array(arr)
        return V3.deserialize_object_array(v3_type, arr, True)

    @staticmethod
    def serialize_large_integer_array(v3_type, arr, byte_width):
        buf_len = 1 + byte_width * len(arr)
        buf = ByteBuffer.allocate(buf_len)
        buf.put(byte_width)
        for e in arr:
            int_bytes = V3.serialize_integer(v3_type.elementType, e)
            pad_len = byte_width - len(int_bytes)
            for i in range(0, pad_len):
                buf.put(0)
            buf.put(int_bytes)
        buf.rewind()
        return buf.array(buf_len)

    @staticmethod
    def deserialize_large_integer_array(arr):
        result = []
        pos = arr.dataIndex + 1
        element_len = arr.buffer[arr.dataIndex]
        result_len = (arr.dataLength - 1) // element_len
        for i in range(0, result_len):
            chunk = arr.buffer[pos: pos + element_len: 1]
            result.append(int.from_bytes(chunk, byteorder="big"))
            pos = pos + element_len
        return result

    @staticmethod
    def serialize_object_array(v3_type, arr, first):
        V3.validate_length(v3_type.arrayLen, len(arr))
        out = []
        if first is not None:
            out.append(first)
        for i in range(0, len(arr)):
            out.append(V3.serialize(v3_type.elementType, arr[i]))
        return out

    @staticmethod
    def deserialize_object_array(v3_type, list_item, skip_first):
        elements = list_item.elements()
        list_iter = list_item.iterator()
        num_elements = len(elements)
        if skip_first:
            list_iter.next()
            num_elements = num_elements - 1
        found = []
        for i in range(0, num_elements):
            found.append(V3.deserialize(v3_type.elementType, list_iter))
        return found
