# Development backlog

Durable context for future development sessions. These items record open
decisions and implementation gaps, not settled behavior.
[AGENTS.md](AGENTS.md) describes current implementation practices.

Keep entries concise and grounded in the repository. Record relevant discoveries
under the existing item rather than adding session logs. Remove completed items
once their decisions and behavior are captured in the dialect, documentation,
and tests as appropriate.

## Width inference

**Problem:** `InfersResultTypes` derives and checks a result width for each
integer operation, but the signedness of `sub`, `mul`, `div`, `rem`, and the
shifts is taken from the declared result rather than the operands. Chisel's
internal IR can pair `UInt` operands with an `SInt` result, so a stricter rule
would reject valid imports.

**Known constraints:** ScaIR has no inference interface and `OpDefs` exposes no
inference point, so `InfersResultTypes` is a dialect-local convention whose only
consumer is its own verifier. Nothing outside the dialect can ask an operation
for its result types. `unknownWidth` marks an uninferred width, and every
operation's rule propagates it rather than guessing. See
[firrtl.scala](dialects/src/firrtl.scala).

**Decisions needed:** Whether to upstream the inference hook so passes can use it,
and how an uninferred width should resolve once a pass is responsible for it
rather than the author.

**Completion:** Add the width rule for each new primitive as it lands, covered by
a fixture for known and uninferred widths — `arithmetic.mlir` and
`unsized_add.mlir` are the pattern.

## Module port wiring

**Problem:** The builder creates one entry-block argument per port, but the
module verifier does not relate arguments to `port_types`/`port_locations`, and
symbol lookup for ports is absent.

**Known constraints:** CIRCT's `FModuleOp` verifier requires exactly one argument
per port, each argument's type to match `portTypes[i]`, and each argument's
location to match `portLocations[i]`. ScaIR's `NoTerminator` fixes the region at
one block, which is what makes an entry argument list well-defined. Chisel's
`DefModule.ports` keeps the top `io` bundle as one aggregate port; the importer
preserves it as a `BundleType` and resolves `io.y` through `firrtl.subfield`.

**Decisions needed:** Choose whether ports are entry-block arguments, symbol
lookups, or both, and how direction is represented — a numeric array, an enum
per port, or a type-level flip.

**Completion:** Give a module body a way to reference its ports and enforce the
correspondence with the port attributes, with fixtures covering matching and
mismatched arities.

## Traits and interfaces ScaIR does not have

**Problem:** Several traits the dialect's operations need do not exist in ScaIR,
so operations either define them locally or leave the behaviour unenforced. The
trailing comment blocks on each operation name them.

**Known constraints:** ScaIR has no op-interface system: an interface becomes a
Scala trait, and its abstract members are the interface methods. Implemented in
[firrtl.scala](dialects/src/firrtl.scala): `SingleBlock`,
`NoRegionArguments`, `HasParent`, `SameTypeOperands`, `SymbolName`,
`SymbolVisibility`, `SymbolUserOpInterface`, `InfersResultTypes`,
`SameOperandsIntTypeKind`, `OneBitOperands`, `ResetOperands`, and
`BitsSelectionRange`.

Still absent, all CIRCT- or FIRRTL-specific: `ForceableOpInterface`, whose
verifier is what gives `forceable` meaning; `FNamableOpInterface`;
`InnerSymbolOpInterface` and `InnerSymbolTable` with the `inner_sym` attribute
they need; `InnerRefNamespace`; `FConnectLike`, `FModuleLike`, `PortList`, and
`InstanceGraphModuleOpInterface`.

**Not expressible at all:** `OpAsmOpInterface` and `HasCustomSSAName`.
`AssemblyPrinter` assigns SSA names itself and consults no hook, so no trait can
influence them. The module's entry-block arguments depend on this.

**Gaps these traits work around:** `Operand[T]`/`Result[T]` bounds are never
verified, which is why `OperandAndResultTypes` and `InfersResultTypes` check by
hand; and an `IntData` typed property prints in a form that does not parse back,
so numeric properties have to be `IntegerAttr`; and `SymbolTable.lookupSymbol` throws once it runs out of symbol tables
instead of reporting an undefined symbol, which is why `SymbolUserOpInterface`
resolves references itself.

**Decisions needed:** Whether to report the printing hook, symbol lookup, and
type-constraint verification upstream, carry them as a patch, or keep working
around them.

**Completion:** Implement the CIRCT-specific ones as the operations that need
them land, and remove each from the corresponding comment block.

## Remaining types

**Problem:** `uint`, `sint`, `clock`, `reset`, `asyncreset`, `bundle`, and
`vector` exist, but `analog`, `enum`, and the reference type do not.

**Known constraints:** CIRCT parameterizes `uint`, `sint`, and `analog` with a
width sentinel of `-1` for an uninferred width, and gives `clock`, `reset`, and
`asyncreset` no width at all. Every base type also carries an `isConst`
parameter, spelled with a `const.` prefix. A forceable wire exposes a second
`ref` result, which needs a reference type.

**Decisions needed:** Choose the order to add `analog`, the reference type, and
`enum`, and whether the `isConst` parameter is carried on every type or modelled
separately.

**Completion:** Add each type with the same print/parse pairing the current types
use, plus fixtures that round-trip a sized and an unsized spelling. Aggregate
wires/registers and dynamic `subaccess` remain importer gaps.

## Layers

**Problem:** `firrtl.layer` defines a layer, and the circuit can name layers it
enables or disables, but nothing puts code inside a layer.

**Known constraints:** CIRCT pairs `firrtl.layer` with `firrtl.layerblock`,
which carries a `SymbolRefAttr` to its layer and may nest in a module, another
layer block, a `when`, or a `match`. It also has a `LayerSpecialization` value
that selects a layer's default enablement. The parent constraint is a set, which
`HasParent` already expresses, so the layer block only needs its set widened and
`WireOp`'s extended to the same kinds.

**Decisions needed:** How far the layer subsystem needs to go for the dialect's
purpose, and whether a layer block needs its own parent set distinct from
`WireOp`'s.

**Completion:** Implement the layer block and widen both parent sets, and cover
nesting, an undefined layer reference, and a layer whose specialization is
disabled.

## Unsupported commands and primitives

**Problem:** The importer still rejects several Chisel commands and FIRRTL
primitives that ordinary designs use.

**Known constraints:** Chisel lowers `printf`, assertions, and other verification
statements into layer blocks, which the dialect cannot represent. Module
instantiation (`DefInstance`) and its reset-valued connects need
`firrtl.instance` and a permissive `firrtl.connect`; the dialect has neither.
`DefInvalid`, named-port `FirrtlMemory`, aggregate wires/registers, and dynamic
vector indexing also remain unimplemented. Among primitives, `shl`, `shr`,
`head`, `asUInt`, `asSInt`, `asClock`, `asReset`, `asAsyncReset`, `neg`, and
`cvt` are absent, so any design using them fails on import.

**Decisions needed:** Choose whether to add layer blocks before printf and
verification, and whether `connect` lands with instance support or on its own.

**Completion:** Implement each command or primitive with its verifier and IR
fixture, then remove it from this list and from the per-operation TODO blocks.

## Lowering path

**Problem:** There are no conversion passes for the dialect, and no lower-level
representation to convert to.

**Known constraints:** ScaIR's `ConversionPass` framework, type converters, and
`builtin.unrealized_conversion_cast` materialization are available through
`scair-passes`, which `opt` already depends on. ScaIR registers
`convert-arith-to-llvm` and `convert-func-to-llvm` among its passes. Widths are
part of the type, so a converter has to decide what an uninferred width becomes.

**Decisions needed:** Choose the lowering target and its granularity, whether
intermediate structural and behavioral dialects are introduced, and how
unsupported constructs remain represented.

**Completion:** Implement the conversions, keep residual IR well-formed, and add
execution coverage where a target exists.

## CHIRRTL memory dialect

**Problem:** The CHIRRTL memory types and five memory operations round-trip, but
several of their properties are recorded without a consumer, and two spellings
deviate from CIRCT.

**Known constraints:** ScaIR registers attribute companions by a single name, so
CIRCT's `RefType` — one type whose printed spelling switches between `probe` and
`rwprobe` on a `forceable` parameter — cannot be one Scala type here. Only the
non-forceable `!firrtl.probe<...>` exists, without the optional layer symbol
reference. `inner_sym` is a plain optional string where CIRCT carries
`hw.inner_name`, and `prefix` is inert without an `FNamableOp`. `combmem` and
`seqmem` record `init`, `prefix`, and (for `seqmem`) `ruw`, and `memoryport`
records `MemDirEnum.Infer`, but no pass reads any of them.
The importer maps `DefMemory`, `DefSeqMemory`, and `DefMemPort`, and `when`
regions now import, but `DefInvalid` is unsupported, so
`WireDefault(..., DontCare)` and masked writes still fail to import, as does
`FirrtlMemory`.

**Decisions needed:** Whether a forceable probe type earns its own Scala class
and the layer parameter, whether `inner_sym` stays an untyped attribute, whether
a zero-length `cmemory` is legal (CIRCT accepts it, the FIRRTL spec requires a
positive count), and where direction inference for `Infer` belongs once a
lowering path exists.

**Completion:** Add the reference operations that consume a probe, implement
direction inference and memory initialization, and add fixtures for `rwprobe` and
the optional properties once their consumers exist.

## CIRCT schema conformance

**Problem:** The importer emits `firrtl.*` operations, but the module metadata is
not yet CIRCT-compatible: `port_directions` holds enum ordinals rather than
dense booleans, `port_locations` is empty because ScaIR has no location
attribute, and `port_annotations` and `port_symbols` are empty arrays.

**Completion:** Type the port metadata, add or drop location encoding, and
verify output with `firtool --format=mlir` against a pinned CIRCT version.

## Documented dialect reference

**Problem:** `docs/_docs/` describes the project but not the dialect's types,
operations, and assembly.

**Decisions needed:** Choose how much of the reference belongs in prose versus
Scaladoc, and whether type and operation spellings are stated normatively.

**Completion:** Document the types, operations, and assembly in `docs/_docs/`,
and keep the reference current as definitions land.
