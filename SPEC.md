
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

The first (leftmost) three bits of the first byte are the version number in unsigned big-endian two's complement format. This is version 0. Versions 1-7 are reserved to accommodate future encoding formats.

The last (rightmost) five bits of the first byte are the function identifier, an unsigned big-endian integer which is used instead of a four-byte hash selector. If the function ID is 31 or larger, all five bits are set and the RLP encoding of the ID is appended after byte zero.

### Arguments:

Immediately following byte zero and the function ID, arguments are encoded according to their type and are appended in sequence.

#### Tuple types:

Tuples are encoded as an RLP list surrounding the concatenation of the encodings of its elements. The arguments, collectively, are not considered a tuple for purposes of encoding.

#### Base types:

Booleans are encoded as one byte: either `0x01` for true or `0x00` for false.

Non-negative integers are encoded as usual according to the RLP specification. Negative integers are sign-extended to the width of the datatype. For example, `0xffffff` for negative one as an `int24`.

#### Array types:

#### Byte array types (including `string` which is utf-8 bytes):

Byte arrays are encoded as an RLP string as per the RLP specification.

##### Boolean array types:

Static boolean arrays are encoded as the RLP encoding of the (big-endian two's complement) unsigned integer formed by the array elements interpreted left to right as bits where true values are `1` and false values are `0`.

Dynamic-length boolean arrays are encoded as if a static boolean array appended to the RLP encoding of the array length.

For example, `[false, false, true, false]` would encode as `0x02` if a static boolean array and as `0x0402` if a dynamic boolean array.

##### Integer array types:

Integer arrays may be encoded in two ways:

Variable-width, as if a byte array where the first byte is `0x00` and the remaining bytes are the concatenations of the encodings of the elements (as if they were base types).

Fixed-width, as if a byte array where the first byte is the byte width, `w`, of the elements which follow, raw, in order, and `w` bytes each. Encoders SHOULD determine the byte width by the width of the widest element.

##### All other array types (tuple arrays, multidimensional arrays):

Arrays of objects are encoded as if a tuple containing the array elements.

### Versioning:

Any or all of the 7 possible future versions can define an arbitrary number of sub-versions each, depending on how the lead bytes are interpreted.

Version zero (this version) could also specify an arbitrarily large number of future sub-versions of itself. To define an incompatible subversion, specify byte zero as `00011111` (i.e. `0x1f`) and byte one as something other than the RLP encoding of an integer, such as `0xc0` (because this is a list item) or `0x00` (because leading zeroes are not allowed).

### Distinguishing from ABIv2:

While the current ABIv2 spec permits encodings not of length 4 modulo 32 (because dynamic element offsets may be any arbitrary value), in practice, encodings are almost invariably of length 4 modulo 32.

ABIv3 encodings therefore are forbidden to be of length 4 mod 32, and decoders MUST reject any such encodings. It is the responsibility of the encoder to avoid this by appending a final zero-byte if the encoding would otherwise have this property. Decoders SHOULD fail if unconsumed bytes remain after the last parameter has been decoded unless the length of the encoding is 5 mod 32 and only one unconsumed byte exists and that byte's value is zero.

## Footnotes

Algorithm and code based on https://github.com/esaulpaugh/headlong/blob/master/src/main/java/com/esaulpaugh/headlong/abi/SuperSerial.java