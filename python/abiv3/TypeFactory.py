from abiv3.V3Type import V3Type


class TypeFactory:

    typeMap = type_map = {
        "address": V3Type(V3Type.TYPE_CODE_INTEGER, "address", unsigned=True, bit_len=160),
        "function": V3Type(V3Type.TYPE_CODE_ARRAY, "function", 24, V3Type.BYTE, int.__class__, False, None, None, None),
        "bytes": V3Type(V3Type.TYPE_CODE_ARRAY, "bytes", -1, V3Type.BYTE, int.__class__, False, None, None, None),
        "string": V3Type(V3Type.TYPE_CODE_ARRAY, "string", -1, V3Type.BYTE, int.__class__, True, None, None, None),
        "bool": V3Type.BOOL
    }


for i in range(8, 257, 8):
    TypeFactory.type_map["int" + str(i)] = V3Type(V3Type.TYPE_CODE_ARRAY, "int" + str(i), i, None, None, False, False, i, None)
    TypeFactory.type_map["uint" + str(i)] = V3Type(V3Type.TYPE_CODE_ARRAY, "uint" + str(i), i, None, None, False, True, i, None)

for i in range(1, 33):
    TypeFactory.type_map["bytes" + str(i)] = V3Type(V3Type.TYPE_CODE_ARRAY, "bytes" + str(i), i, V3Type.BYTE, int.__class__, False, None, None, None)