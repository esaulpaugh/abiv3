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
from abiv3.V3Type import V3Type


# next tuple len list type str sum bytes int
class V3:

    VERSION_ID = 0
    VERSION_MASK = 0b1100_0000
    ID_MASK = 0b0011_1111  # 0x3f (decimal 63), the complement of VERSION_MASK

    @staticmethod
    def encode_function(function_number, schema, vals):
        results = []
        V3.header(function_number, results)
        V3.encode_tuple(schema, vals, results)
        buf_len = 0
        for e in results:
            buf_len = buf_len + len(e)
        buf = ByteBuffer.allocate(buf_len)
        for e in results:
            buf.put(e)
        buf.rewind()
        return buf.array(buf_len)

    @staticmethod
    def decode_function(schema, data):
        bb = ByteBuffer.wrap(data)
        zeroth = bb.get()
        version = zeroth & V3.VERSION_MASK
        if version != V3.VERSION_ID:
            raise Exception()
        sequence_start = 1
        fn_number = zeroth & V3.ID_MASK

        return V3.decode_tuple(schema, bb)

    @staticmethod
    def header(fn_num, results):
        if fn_num < V3.ID_MASK:
            if fn_num == 0:
                results.append(bytes(b'\x00'))
                return
            results.append(bytes(Utils.to_bytes_unsigned(fn_num)))
            return
        results.append(bytes(b'\x3f', Utils.to_bytes_unsigned(fn_num - V3.ID_MASK)))

    @staticmethod
    def validate_length(expected_len, actual_len):
        if expected_len != -1 and (expected_len != actual_len):
            raise Exception(str(expected_len) + ' != ' + str(actual_len))

    @staticmethod
    def encode_tuple(schema, vals, results):
        V3.validate_length(len(schema), len(vals))
        for i in range(0, len(schema)):
            V3.encode(schema[i], vals[i], results)

    @staticmethod
    def decode_tuple(schema, bb):
        elements = []
        for i in range(0, len(schema)):
            elements.append(V3.decode(schema[i], bb))
        return elements

    @staticmethod
    def encode(v3_type, obj, results):
        code = v3_type.typeCode
        if code == V3Type.TYPE_CODE_BOOLEAN:
            V3.encode_boolean(obj, results)
        elif code == V3Type.TYPE_CODE_INTEGER:
            V3.encode_integer(v3_type, obj, results)
        elif code == V3Type.TYPE_CODE_ARRAY:
            V3.encode_array(v3_type, obj, results)
        elif code == V3Type.TYPE_CODE_TUPLE:
            V3.encode_tuple(v3_type.elementTypes, obj, results)
        else:
            raise Exception('??')

    @staticmethod
    def decode(v3_type, bb):
        code = v3_type.typeCode
        if code == V3Type.TYPE_CODE_BOOLEAN:
            return V3.decode_boolean(bb)
        if code == V3Type.TYPE_CODE_INTEGER:
            return V3.decode_integer(v3_type, bb)
        if code == V3Type.TYPE_CODE_ARRAY:
            return V3.decode_array(v3_type, bb)
        if code == V3Type.TYPE_CODE_TUPLE:
            return V3.decode_tuple(v3_type.elementTypes, bb)
        raise Exception('??')

    @staticmethod
    def encode_boolean(val, results):
        results.append(b'\x01' if val else b'\x00')

    @staticmethod
    def decode_boolean(bb):
        enc = bb.get()
        if enc == 1:
            return True
        if enc == 0:
            return False
        raise Exception('illegal boolean RLP: expected 0x1 or 0x0')

    @staticmethod
    def encode_integer(v3_type, val, results):
        byte_width = int(v3_type.bitLen / 8)
        extended = bytearray(byte_width)
        minimal_bytes = Utils.to_bytes(val)
        minimal_width = len(minimal_bytes)
        padding_byte = 0xff if val < 0 else 0x00
        for i in range(0, len(minimal_bytes)):
            extended[i] = padding_byte
        j = byte_width - minimal_width
        for i in range(0, minimal_width):
            extended[j] = minimal_bytes[i]
            j = j + 1
        results.append(extended)

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
    def decode_integer(v3_type, bb):
        item = bb.get()
        if v3_type.unsigned or ((item.dataLength * 8) < v3_type.bitLen):
            return item.as_int()
        return item.as_int_signed()

    @staticmethod
    def encode_array(v3_type, arr, results):
        if v3_type.elementType.typeCode == V3Type.TYPE_CODE_BYTE:
            the_bytes = bytes(arr, 'utf-8') if v3_type.isString else arr
            V3.validate_length(v3_type.arrayLen, len(the_bytes))
            results.append(the_bytes)
        elif v3_type.elementType.typeCode == V3Type.TYPE_CODE_BOOLEAN:
            V3.encode_boolean_array(v3_type, arr, results)
        elif v3_type.elementType.typeCode == V3Type.TYPE_CODE_INTEGER:
            V3.encode_integer_array(v3_type, arr, results)
        else:
            V3.encode_object_array(v3_type, arr, b'')

    @staticmethod
    def decode_array(v3_type, bb):
        if v3_type.elementType.typeCode == V3Type.TYPE_CODE_BYTE:
            the_bytes = bb.get()
            if v3_type.isString:
                return the_bytes.decode('utf-8')
            return the_bytes
        elif v3_type.elementType.typeCode == V3Type.TYPE_CODE_BOOLEAN:
            return V3.decode_boolean_array(v3_type, bb)
        elif v3_type.elementType.typeCode == V3Type.TYPE_CODE_INTEGER:
            return V3.decode_integer_array(v3_type, bb)
        return V3.decode_object_array(v3_type, bb, False)

    @staticmethod
    def encode_boolean_array(v3_type, booleans, results):
        V3.validate_length(v3_type.arrayLen, len(booleans))
        if v3_type.arrayLen == -1:
            results.add(V3.rlp(len(booleans)))
        if len(booleans) > 0:
            bits = bytearray(int(V3.round_length_up(len(booleans), 8) / 8))
            for k in range(0, len(booleans)):
                if booleans[len(booleans) - 1 - k]:
                    idx = len(bits) - 1 - int(k / 8)
                    bits[idx] |= 0b0000_0001 << (k % 8)
            results.append(bits)

    @staticmethod
    def decode_boolean_array(v3_type, bb):
        the_len = bb.get() if v3_type.arrayLen == -1 else v3_type.arrayLen
        if the_len == 0:
            return []
        byte_len = int(V3.round_length_up(the_len, 8) / 8)
        binary = '{0:b}'.format(bb.get(byte_len))
        num_chars = len(binary)
        implied_zeros = the_len - num_chars
        booleans = []
        for c in range(0, implied_zeros):
            booleans.append(False)
        for c in range(0, num_chars):
            booleans.append(True if binary[c] == '1' else False)
        return booleans

    @staticmethod
    def encode_integer_array(v3_type, arr, results):
        V3.validate_length(v3_type.arrayLen, len(arr))
        for e in arr:
            V3.encode_integer(v3_type.elementType, e, results)

    @staticmethod
    def decode_integer_array(v3_type, arr):
        return None

    @staticmethod
    def encode_object_array(v3_type, arr, first, results):
        V3.validate_length(v3_type.arrayLen, len(arr))
        out = []
        if first is not None:
            out.append(first)
        for i in range(0, len(arr)):
            out.append(V3.encode(v3_type.elementType, arr[i], results))
        results.append(out)

    @staticmethod
    def decode_object_array(v3_type, list_item, skip_first):
        elements = list_item.elements()
        list_iter = list_item.iterator()
        num_elements = len(elements)
        if skip_first:
            list_iter.next()
            num_elements = num_elements - 1
        found = []
        for i in range(0, num_elements):
            found.append(V3.decode(v3_type.elementType, list_iter))
        return found

    @staticmethod
    def rlp(value):
        return V3.rlp(Utils.to_bytes(value))

    @staticmethod
    def rlp(byte_string):
        data_len = len(byte_string)
        if data_len < 56:
            if data_len == 1:
                first = byte_string[0]
                return bytes([0x81, first]) if first < 0x00 else V3.single(first)
            bb = ByteBuffer.allocate(1 + len(byte_string))
            bb.put(0x80 + data_len)
            bb.put(byte_string)
        else:
            len_of_len = V3.len(data_len)
            bb = ByteBuffer.allocate(1 + len_of_len + len(byte_string))
            bb.put(0xb7 + len_of_len)
            V3.put_long(data_len, bb)
            bb.put(byte_string)
        return bb.array()

    @staticmethod
    def len(val):
        leng = 0
        while val != 0:
            leng = leng + 1
            val >>= 8
        return leng

    @staticmethod
    def put_long(v, bb):
        val = v
        temp = bytearray(8)
        j = 8
        while val != 0:
            temp[--j] = val
            val >>= 8
        bb.put(temp, j, 8 - j)


    @staticmethod
    def single(val):
        return bytes([ val ])

    @staticmethod
    def mod(val, powerOfTwo):
        return val & (powerOfTwo - 1)


    @staticmethod
    def round_length_up(len, powerOfTwo):
        mod = V3.mod(len, powerOfTwo)
        return len + (powerOfTwo - mod) if mod != 0 else len