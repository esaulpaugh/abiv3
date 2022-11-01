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
from abiv3 import Utils
from abiv3.RLPItem import RLPItem


class RLPEncoder:

    @staticmethod
    def length(val):
        length = 0
        while val != 0:
            length = length + 1
            val = val >> 8
        return length

    @staticmethod
    def item_length(data_len):
        return (1 if data_len < 56 else 1 + RLPEncoder.length(data_len)) + data_len

    @staticmethod
    def str_encoded_len(byte_string):
        data_len = len(byte_string)
        data_len_one = data_len == 1
        is_single_byte = data_len_one and RLPItem.rlp_type(byte_string[0]) == 0
        return RLPEncoder.item_length(
            0 if is_single_byte else data_len
        )

    @staticmethod
    def list_encoded_len(items):
        return RLPEncoder.item_length(RLPEncoder.sum_encoded_len(items))

    @staticmethod
    def encoded_len(e):
        if isinstance(e, bytes):
            return RLPEncoder.str_encoded_len(e)
        if isinstance(e, bytearray):
            return RLPEncoder.str_encoded_len(e)
        if isinstance(e, list):
            return RLPEncoder.list_encoded_len(e)
        if e is None:
            return 0
        raise Exception('?')

    @staticmethod
    def sum_encoded_len(items):
        len_sum = 0
        for e in items:
            len_sum = len_sum + RLPEncoder.encoded_len(e)
        return len_sum

    @staticmethod
    def encode_len1_string(first, dest):
        if first >= 0x80:  # same as (first & 0xFF) >= 0x80
            dest.put(0x80 + 1)
        dest.put(first)

    @staticmethod
    def put_string(byte_string, dest):
        data_len = len(byte_string)
        if data_len < 56:
            if data_len == 1:
                RLPEncoder.encode_len1_string(byte_string[0], dest)
                return
            dest.put(0x80 + data_len)  # data_len is 0 or 2-55
        else:  # long string
            dest.put(0xb7 + RLPEncoder.length(data_len))
            arr = Utils.unsigned_to_bytes(data_len)
            dest.put(arr)
        dest.put(byte_string)

    @staticmethod
    def insert_list_prefix(data_len, dest):
        if data_len < 56:
            dest.put(0xc0 + data_len)
        else:
            dest.put(0xf7 + RLPEncoder.length(data_len))
            arr = Utils.unsigned_to_bytes(data_len)
            dest.put(arr)

    @staticmethod
    def put_list(data_len, elements, dest):
        RLPEncoder.insert_list_prefix(data_len, dest)
        RLPEncoder.put_sequence(elements, dest)

    @staticmethod
    def encode_item(e, dest):
        if isinstance(e, bytes):
            RLPEncoder.put_string(e, dest)
        elif isinstance(e, bytearray):
            RLPEncoder.put_string(e, dest)
        elif isinstance(e, list):
            RLPEncoder.put_list(RLPEncoder.sum_encoded_len(e), e, dest)
        elif e is None:
            return  # skip
        else:
            raise Exception('illegal type')

    @staticmethod
    def put_sequence(objects, dest):
        for e in objects:
            RLPEncoder.encode_item(e, dest)
