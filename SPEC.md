
## Assumptions

### The following assumptions drive this iteration of the design:

1. Ethereum will scale and the overwhelming majority of contract execution will occur on Layer-2 in and among contracts which do not exist today.
2. De facto, there is no calldata standardization on Layer 2 currently. It is not possible to achieve compatibility with existing L2 contracts in general because L2 contracts generally do not conform to any standard.
3. Layer-2 computation costs will be inconsequential compared to costs that scale with calldata length (such as cryptographic verification).
4. If a proposed standard's calldata is significantly more expensive than custom hacked calldata, that standard will fail to achieve widespread adoption.

## Resources

ABIv2 spec: https://solidity.readthedocs.io/en/latest/abi-spec.html

RLP spec: https://github.com/ethereum/wiki/wiki/RLP

## Encoding

### Byte zero:

The first (leftmost) two bits of the first byte are the version number in unsigned big-endian two's complement format. This is version 0. Versions 1-3 are reserved to accommodate future encoding formats.

The last (rightmost) six bits of the first byte are the function identifier, an unsigned big-endian integer in two's complement which is used instead of a four-byte hash selector. If the function ID is 63 or larger, all six bits are set and the RLP encoding of function ID minus 63 is appended after byte zero.

### Arguments:

Immediately following byte zero and the function ID, arguments are encoded according to their type and are appended in sequence.

#### Tuple types:

Tuples are encoded as merely the concatenation of the encodings of the elements.

#### Base types:

Booleans are encoded as one byte: either `0x01` for true or `0x00` for false.

Integers are encoded in big-endian two's complement and are sign-extended to the width of the datatype. For example, `0xfffffe` and `0x000005` are the encodings of negative two and five respectively given an `int24` datatype.

#### Array types:

#### Byte array types (including `string` which is utf-8 bytes):

Static byte arrays are encoded as merely the byte string itself. Dynamic-length byte arrays are encoded as the RLP encoding of the byte string.

##### Boolean array types:

A static boolean array is encoded as if it were an integer, specifically the (big-endian two's complement) unsigned integer formed by the array elements interpreted left to right as bits where true values are `1` and false values are `0`. The integer's width in bytes is the smallest whole number greater than or equal to the number of elements divided by 8. Within these bytes, the bits representing the array elements are contiguous and right-aligned.

Dynamic-length boolean arrays are encoded as if a static boolean array appended to the RLP encoding of the array length.

For example, `[true, false, true, false, false, true, false, true, true]` would encode as `0x014B` if a static boolean array and as `0x09014B` if a dynamic boolean array.

##### All other array types:

Arrays are encoded as the concatenation of the encodings of the elements.

If the array type is dynamic (i.e. variable-length and not statically-sized), the array length, in elements, must encoded RLP-wise and prepended to the encodings of the elements.

### Versioning:

Any or all of the 3 possible future versions can define an arbitrary number of sub-versions each, depending on how the lead bytes are interpreted.

Version zero (this version) could also specify an arbitrarily large number of future sub-versions of itself. To define an incompatible subversion, specify byte zero as `00111111` (i.e. `0x3f`) and byte one as something other than the RLP encoding of an integer, such as `0xc0` (because this is a list item) or `0x00` (because leading zeroes are not allowed).

### Distinguishing from ABIv2:

While the current ABIv2 spec permits encodings not of length 4 modulo 32 (because dynamic element offsets may be any arbitrary value), in practice, encodings are almost invariably of length 4 modulo 32.

ABIv3 encodings therefore are forbidden to be of length 4 mod 32, and decoders MUST reject any such encodings. It is the responsibility of the encoder to append a final zero-byte if the encoding would otherwise have this property. Decoders SHOULD fail if unconsumed bytes remain after the last parameter has been decoded unless the length of the encoding is 5 mod 32 and only one unconsumed byte exists and that byte's value is zero.

## Footnotes

Algorithm and code based on https://github.com/esaulpaugh/headlong/blob/master/src/main/java/com/esaulpaugh/headlong/abi/SuperSerial.java