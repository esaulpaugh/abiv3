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
from abiv3.RLPItem import RLPItem


def to_bytes(number):
    return to_bytes_unsigned(number) if number >= 0 else to_bytes_negative(number)


def to_bytes_unsigned(number):
    return number.to_bytes(unsigned_length(number), "big")


def unsigned_length(val):
    n = 0
    while val != 0:
        n = n + 1
        val = val >> 8
    return n


def to_bytes_negative(val):
    n = 0
    temp = val
    while temp != -1:
        n = n + 1
        temp = temp >> 8
    arr = bytearray(n)
    while val != -1:
        n = n - 1
        arr[n] = val & 0xff
        val = val >> 8
    return arr


def to_signed_byte(lead):
    if lead >= 128:
        return lead - 256
    return lead


def wrap(buffer, index, container_end):
    lead = buffer[index]
    rlp_type = RLPItem.rlp_type(lead)
    if rlp_type == 0:
        return RLPItem.single_byte(buffer, index, container_end)
    if rlp_type == 1:
        return RLPItem.short_string(buffer, index, lead, container_end)
    if rlp_type == 2:
        return RLPItem.long_item(lead, 0xb7, buffer, index, container_end)
    if rlp_type == 3:
        return RLPItem.short_list(buffer, index, lead, container_end)
    return RLPItem.long_item(lead, 0xf7, buffer, index, container_end)
