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


def to_bytes(number) -> bytes:
    return to_bytes_unsigned(number) if number >= 0 else to_bytes_negative(number)


def to_bytes_unsigned(number) -> bytes:
    return number.to_bytes(unsigned_length(number), "big")


def unsigned_length(val) -> int:
    n = 0
    while val != 0:
        n = n + 1
        val = val >> 8
    return n


def to_bytes_negative(val) -> bytearray:
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


def to_signed_byte(lead) -> int:
    if lead >= 128:
        return lead - 256
    return lead


def rlp_type(lead):
    lead = to_signed_byte(lead)
    if lead < -72:  # 0xB8
        return 1  # short string
    if lead < -64:  # 0xC0
        return 2  # long string
    if lead < -8:  # 0xF8
        return 3  # short list
    if lead < 0:
        return 4  # long list
    return 0  # single byte