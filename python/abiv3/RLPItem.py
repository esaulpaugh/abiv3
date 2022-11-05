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
from abiv3.RLPIterator import RLPIterator


class RLPItem:

    def __init__(self, buffer, index, data_index, data_length, end_index):
        self.buffer = buffer
        self.index = index
        self.dataIndex = data_index
        self.dataLength = data_length
        self.endIndex = end_index

    def data(self):
        return self.buffer[self.dataIndex: self.endIndex: 1]

    def as_int(self):
        return int.from_bytes(self.data(), byteorder='big')

    def as_int_signed(self):
        return int.from_bytes(self.data(), byteorder='big', signed=True)

    def elements(self):
        the_list = []
        next_item = self.next_element(self.dataIndex)
        while next_item is not None:
            the_list.append(next_item)
            next_item = self.next_element(next_item.endIndex)
        return the_list

    def next_element(self, idx):
        if idx >= self.endIndex:
            return None
        return Utils.wrap(self.buffer, idx, self.endIndex)

    def iterator(self):
        return RLPIterator(self.buffer, self.dataIndex, self.endIndex)

    @staticmethod
    def rlp_type(lead):
        lead = Utils.to_signed_byte(lead)
        if lead < -72:  # 0xB8
            return 1  # short string
        if lead < -64:  # 0xC0
            return 2  # long string
        if lead < -8:  # 0xF8
            return 3  # short list
        if lead < 0:
            return 4  # long list
        return 0  # single byte

    @staticmethod
    def require_in_bounds(val, container_end, index):
        if val > container_end:
            msg = f'element @ index {index} exceeds its container: {val} > {container_end}'
            raise Exception(msg)
        return val

    @staticmethod
    def single_byte(buffer, index, container_end):
        end_index = RLPItem.require_in_bounds(index + 1, container_end, index)
        return RLPItem(buffer, index, index, 1, end_index)

    @staticmethod
    def short_string(buffer, index, lead, container_end):
        data_index = index + 1
        data_length = Utils.to_signed_byte(lead) - Utils.to_signed_byte(0x80)
        end_index = RLPItem.require_in_bounds(data_index + data_length, container_end, index)
        if data_length == 1 and RLPItem.rlp_type(buffer[data_index] == 0):
            raise Exception('invalid rlp for single byte @ ' + index)
        return RLPItem(buffer, index, data_index, data_length, end_index)

    @staticmethod
    def short_list(buffer, index, lead, container_end):
        data_index = index + 1
        data_length = Utils.to_signed_byte(lead) - Utils.to_signed_byte(0xC0)
        end_index = RLPItem.require_in_bounds(data_index + data_length, container_end, index)
        return RLPItem(buffer, index, data_index, data_length, end_index)

    @staticmethod
    def long_item(lead, offset, buffer, index, container_end):
        diff = Utils.to_signed_byte(lead) - Utils.to_signed_byte(offset)
        length_index = index + 1
        data_index = RLPItem.require_in_bounds(length_index + diff, container_end, index)
        data_length = int.from_bytes(buffer[length_index: length_index + diff: 1], "big")
        if data_length < 56:
            raise Exception(f'long element data length must be 56 or greater; found: {data_length} for element @ {index}')
        data_len = RLPItem.require_in_bounds(data_length, container_end, index)
        end_index = RLPItem.require_in_bounds(data_index + data_length, container_end, index)
        return RLPItem(buffer, index, data_index, data_len, end_index)
