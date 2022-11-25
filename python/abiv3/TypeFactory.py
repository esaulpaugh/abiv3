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
from abiv3.V3Type import V3Type


def lead_digit_valid(c):
    return 0 < int(c) <= 9


def parse_len(len_str):
    if lead_digit_valid(len_str[0]) or "0" == len_str:
        return int(len_str)


def next_terminator(signature, i):
    while True:
        i = i + 1
        c = signature[i]
        if c == ',' or c == ')':
            return i


def last_index_of(string, char, start):
    i = start
    while i >= 0:
        if string[i] == char:
            return i
        i = i - 1
    return -1


def find_subtuple_end(parent_type_str, arg_start):
    depth = 1
    i = arg_start
    while True:
        i = i + 1
        x = parent_type_str[i]
        if int(x) <= int(')'):
            if int(x) == int(')'):
                if depth <= 1:
                    return i
                depth = depth - 1
            elif int(x) == int('('):
                depth = depth + 1


class TypeFactory:

    typeMap = type_map = {
        "address": V3Type(V3Type.TYPE_CODE_INTEGER, "address", None, None, False, True, 160, None),
        "function": V3Type(V3Type.TYPE_CODE_ARRAY, "function", 24, V3Type.BYTE, False, None, None, None),
        "bytes": V3Type(V3Type.TYPE_CODE_ARRAY, "bytes", -1, V3Type.BYTE, False, None, None, None),
        "string": V3Type(V3Type.TYPE_CODE_ARRAY, "string", -1, V3Type.BYTE, True, None, None, None),
        "bool": V3Type.BOOL
    }

    @staticmethod
    def create(raw_type):
        return TypeFactory.build(raw_type, None)

    @staticmethod
    def resolve_base_type(base_type_str):
        if base_type_str[0] == '(':
            return TypeFactory.parse_tuple_type(base_type_str)
        ret = TypeFactory.typeMap.get(base_type_str)
        return ret if ret is not None else TypeFactory.try_parse_fixed(base_type_str)

    @staticmethod
    def build(raw_type, base_type):
        last_char_idx = len(raw_type) - 1
        if raw_type[last_char_idx] == ']':  # array
            second_to_last_char_idx = last_char_idx - 1
            array_open_index = last_index_of(raw_type, '[', second_to_last_char_idx)
            element_type = TypeFactory.build(raw_type[:array_open_index], base_type)
            the_type = element_type.canonicalType + raw_type[array_open_index:]
            length = -1 if array_open_index == second_to_last_char_idx else parse_len(raw_type[array_open_index + 1: last_char_idx])
            return V3Type(V3Type.TYPE_CODE_ARRAY, the_type, length, element_type, False, None, None, None)
        if base_type is not None:
            return base_type
        base_type = TypeFactory.resolve_base_type(raw_type)
        if base_type is not None:
            return base_type
        raise Exception(f'unrecognized type: "{raw_type}"')

    @staticmethod
    def parse_tuple_type(raw_type_str):
        the_len = len(raw_type_str)
        if the_len == 2 and raw_type_str == "()": return V3Type(V3Type.TYPE_CODE_TUPLE, "()", None, None, False, None, None, [])
        elements = []
        arg_end = 1
        canonical_builder = "("
        while True:
            arg_start = arg_end
            if raw_type_str[arg_start] == ')' or raw_type_str[arg_start] == ',':
                return None
            if raw_type_str[arg_start] == '(':
                arg_end = next_terminator(raw_type_str, find_subtuple_end(raw_type_str, arg_start))
            else:
                arg_end = next_terminator(raw_type_str, arg_start)
            e = TypeFactory.build(raw_type_str[arg_start: arg_end], None)
            canonical_builder = canonical_builder + e.canonicalType + ','
            elements.append(e)
            if raw_type_str[arg_end] == ')':
                arg_end = arg_end + 1
                break
            arg_end = arg_end + 1
        canonical_builder = canonical_builder[0: len(canonical_builder) - 1] + ')'
        return V3Type(V3Type.TYPE_CODE_TUPLE, canonical_builder, None, None, False, None, None, elements) if arg_end == the_len else None

    @staticmethod
    def try_parse_fixed(base_type_str):
        idx = base_type_str.index("fixed")
        unsigned = idx == 1 and base_type_str[0] == 'u'
        if idx == 0 or unsigned:
            index_of_x = (len(base_type_str) - 1) - base_type_str[::-1].index('x')
            begin = idx + len("fixed")
            m_str = base_type_str[begin: index_of_x]
            n_str = base_type_str[index_of_x + 1:]
            if lead_digit_valid(m_str[0]) and lead_digit_valid(n_str[0]):
                M = int(m_str)
                N = int(n_str)
                if M % 8 == 0 and M <= 256 and N <= 80:
                    return V3Type(
                        V3Type.TYPE_CODE_INTEGER,
                        ("ufixed" if unsigned else "fixed") + str(M) + 'x' + str(N),
                        None,
                        None,
                        False,
                        unsigned,
                        M,
                        None
                    )
        return None


for i in range(8, 257, 8):
    TypeFactory.type_map["int" + str(i)] = V3Type(V3Type.TYPE_CODE_ARRAY, "int" + str(i), i, None, False, False, i, None)
    TypeFactory.type_map["uint" + str(i)] = V3Type(V3Type.TYPE_CODE_ARRAY, "uint" + str(i), i, None, False, True, i, None)

for i in range(1, 33):
    TypeFactory.type_map["bytes" + str(i)] = V3Type(V3Type.TYPE_CODE_ARRAY, "bytes" + str(i), i, V3Type.BYTE, False, None, None, None)