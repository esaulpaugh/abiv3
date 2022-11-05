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
class V3Type:

    BOOL = None
    INT256 = None
    BYTE = None
    BYTES = None
    INT256_ARRAY_3 = None
    UFIXED_128_X_18 = None

    TYPE_CODE_BOOLEAN = 0
    TYPE_CODE_BIG_INTEGER = 1
    TYPE_CODE_BIG_DECIMAL = 2
    TYPE_CODE_ARRAY = 3
    TYPE_CODE_TUPLE = 4
    TYPE_CODE_BYTE = 9

    def __init__(
            self,
            type_code,
            canonical_type,
            array_len=None,
            element_type=None,
            element_class=None,
            is_string=None,
            unsigned=None,
            bit_len=None,
            scale=None,
            element_types=None):
        self.typeCode = type_code
        self.canonicalType = canonical_type
        self.arrayLen = array_len
        self.elementType = element_type
        self.elementClass = element_class
        self.isString = is_string
        self.unsigned = unsigned
        self.bitLen = bit_len
        self.scale = scale
        self.elementTypes = element_types


V3Type.BOOL = V3Type(0, 'bool', None, None, None, False, True, 1, None, None)
V3Type.INT256 = V3Type(1, 'int256', None, None, None, False, False, 256, None, None)
V3Type.BYTE = V3Type(9, '--byte--', None, None, None, False, True, 8, None, None)
V3Type.BYTES = V3Type(3, 'bytes', -1, V3Type.BYTE, int.__class__, False, None, None, None, None)
V3Type.INT256_ARRAY_3 = V3Type(3, 'int256[3]', 3, V3Type.INT256, int.__class__, False, None, None, None, None)
V3Type.UFIXED_128_X_18 = V3Type(2, 'ufixed128x18', None, None, None, False, True, 128, None, None)
