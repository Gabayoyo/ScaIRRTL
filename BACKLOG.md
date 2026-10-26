# Development backlog

Durable context for future development sessions. These items record open
decisions and implementation gaps, not settled behavior.
[AGENTS.md](AGENTS.md) describes current implementation practices.

Keep entries concise and grounded in the repository. Record relevant discoveries
under the existing item rather than adding session logs. Remove completed items
once their decisions and behavior are captured in the dialect, documentation,
and tests as appropriate.

## Width inference

**Problem:** `InfersResultTypes` derives and checks result types for the one
arithmetic operation that exists. The sibling primitives will each need their own
rule: `mul` widens by summing the operand widths, `sub` and `add` share theirs,
and comparisons produce a one-bit unsigned result.

**Known constraints:** ScaIR has no inference interface and `OpDefs` exposes no
inference point, so `InfersResultTypes` is a dialect-local convention whose only
consumer is its own verifier. Nothing outside the dialect can ask an operation
for its result types. `unknownWidth` marks an uninferred width, and
`AddOp.inferResultTypes` propagates it rather than guessing. See
[ScaIRRTL.scala](dialects/src/ScaIRRTL.scala).

**Decisions needed:** Whether to upstream the inference hook so passes can use it,
and how an uninferred width should resolve once a pass is responsible for it
rather than the author.

**Completion:** Add the width rule for each primitive as it lands, covered by a
fixture for known and uninferred widths — `arithmetic.mlir` and
`unsized_add.mlir` are the pattern.

## Module port wiring

**Problem:** A module declares its interface in `port_types`, `port_directions`,
and `port_names`, but the body cannot reach any of it: there are no entry-block
arguments, and no verifier relates the two.

**Known constraints:** CIRCT's `FModuleOp` verifier requires exactly one argument
per port, each argument's type to match `portTypes[i]`, and each argument's
location to match `portLocations[i]`. ScaIR's `NoTerminator` fixes the region at
one block, which is what makes an entry argument list well-defined.

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
[ScaIRRTL.scala](dialects/src/ScaIRRTL.scala): `SingleBlock`,
`NoRegionArguments`, `HasParent`, `SameTypeOperands`, `SymbolName`,
`SymbolVisibility`, `SymbolUserOpInterface`, `InfersResultTypes`,
`SameOperandsIntTypeKind`, `OneBitOperands`, and `BitsSelectionRange`.

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

**Problem:** Only `uint`, `sint`, and `clock` exist, so several operations cannot
be expressed.

**Known constraints:** CIRCT parameterizes `uint`, `sint`, and `analog` with a
width sentinel of `-1` for an uninferred width, and gives `clock`, `reset`, and
`asyncreset` no width at all. Every base type also carries an `isConst`
parameter, spelled with a `const.` prefix. A forceable wire exposes a second
`ref` result, which needs a reference type.

**Decisions needed:** Choose the order to add `analog`, `reset`, `asyncreset`,
the reference type, and the aggregate types (`bundle`, `vector`, `enum`), and
whether the `isConst` parameter is carried on every type or modelled separately.

**Completion:** Add each type with the same print/parse pairing the current types
use, plus fixtures that round-trip a sized and an unsized spelling.

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

## Sibling primitives

**Problem:** `firrtl.add` is the only arithmetic operation, and there is no
permissive `connect` for it to be distinguished from.

**Known constraints:** CIRCT's FIRRTL primitives share the same operand shape:
`sub`, `mul`, `div`, and `rem` take two integer operands and produce an integer
result, the bitwise operations take unsigned operands, and comparisons produce a
one-bit unsigned result. `connect` differs from `matchingconnect` by permitting a
wider destination but never a narrower one.

**Decisions needed:** Choose the arithmetic subset to cover and whether to
generalize the primitives behind a shared base rather than repeating the
operand and result fields.

**Completion:** Add the primitives with `SameOperandsIntTypeKind` and the
commutative trait where they apply, add the permissive connect with its width
rule, and cover each with IR fixtures.

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

## Documented dialect reference

**Problem:** `docs/_docs/` describes the project but not the dialect's types,
operations, and assembly.

**Decisions needed:** Choose how much of the reference belongs in prose versus
Scaladoc, and whether type and operation spellings are stated normatively.

**Completion:** Document the types, operations, and assembly in `docs/_docs/`,
and keep the reference current as definitions land.
