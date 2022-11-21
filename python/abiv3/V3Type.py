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
    UINT72 = None
    BYTE = None
    BYTES = None
    DYN_ARR_OF_UINT72 = None
    INT256_ARRAY_3 = None
    UFIXED_128_X_18 = None
    TUPLE_BOOL_INT256 = None

    TYPE_CODE_BOOLEAN = 0
    TYPE_CODE_INTEGER = 1
    TYPE_CODE_ARRAY = 2
    TYPE_CODE_TUPLE = 3
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


V3Type.BOOL = V3Type(V3Type.TYPE_CODE_BOOLEAN, 'bool', None, None, None, False, True, 1, None, None)
V3Type.UINT72 = V3Type(V3Type.TYPE_CODE_INTEGER, 'uint72', None, None, None, False, True, 72, None, None)
V3Type.BYTE = V3Type(V3Type.TYPE_CODE_BYTE, '--byte--', None, None, None, False, True, 8, None, None)
V3Type.BYTES = V3Type(V3Type.TYPE_CODE_ARRAY, 'bytes', -1, V3Type.BYTE, int.__class__, False, None, None, None, None)
V3Type.DYN_ARR_OF_UINT72 = V3Type(V3Type.TYPE_CODE_ARRAY, 'uint72[]', -1, V3Type.UINT72, int.__class__, False, None, None, None, None)
V3Type.UINT72_ARRAY_3 = V3Type(V3Type.TYPE_CODE_ARRAY, 'uint72[3]', 3, V3Type.UINT72, int.__class__, False, None, None, None, None)
V3Type.UFIXED_128_X_18 = V3Type(V3Type.TYPE_CODE_INTEGER, 'ufixed128x18', None, None, None, False, True, 128, None, None)
V3Type.TUPLE_BOOL_UINT72 = V3Type(V3Type.TYPE_CODE_TUPLE, '(bool,int256)', None, None, None, False, None, None, None, [V3Type.BOOL, V3Type.UINT72])
