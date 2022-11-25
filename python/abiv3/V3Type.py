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

    BYTE = None
    BOOL = None

    TYPE_CODE_BOOLEAN = 0
    TYPE_CODE_INTEGER = 1
    TYPE_CODE_ARRAY = 2
    TYPE_CODE_TUPLE = 3
    TYPE_CODE_BYTE = 9

    def __init__(
            self,
            type_code,
            canonical_type,
            array_len,
            element_type,
            is_string,
            unsigned,
            bit_len,
            element_types):
        self.typeCode = type_code
        self.canonicalType = canonical_type
        self.arrayLen = array_len
        self.elementType = element_type
        self.isString = is_string
        self.unsigned = unsigned
        self.bitLen = bit_len
        self.elementTypes = element_types


V3Type.BYTE = V3Type(V3Type.TYPE_CODE_BYTE, '--byte--', None, None, False, True, 8, None)
V3Type.BOOL = V3Type(V3Type.TYPE_CODE_BOOLEAN, 'bool', None, None, False, True, 1, None)
