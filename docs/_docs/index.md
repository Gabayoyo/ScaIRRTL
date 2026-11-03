# ScaIRRTL

Dialect and API documentation for the ScaIRRTL RTL dialect, built on
[ScaIR](https://edin-dal.github.io/scair).

## CHIRRTL memory dialect

CHIRRTL declares behavioral memories before they are lowered to FIRRTL memory.

| Spelling | Meaning |
|---|---|
| `!chirrtl.cmemory<element, depth>` | a `depth`-element memory holding `element` values |
| `!chirrtl.cmemoryport` | a port declared on a memory |
| `!firrtl.probe<type>` | a read-only reference to a passive value |
| `#firrtl.meminit<filename, isBinary, isInline>` | the file a memory is initialized from |

The operations are `chirrtl.combmem` and `chirrtl.seqmem`, which declare a
memory and produce the `cmemory` value; `chirrtl.memoryport` and
`chirrtl.memoryport.access`, which declare a port and enable it on an index at a
clock edge; and `chirrtl.debugport`, which reads the memory as a `probe` of a
vector. `chirrtl.seqmem` also records its read-under-write behavior.
