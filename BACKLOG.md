# Development backlog

Durable context for future development sessions. These items record open
decisions and implementation gaps, not settled behavior.
[AGENTS.md](AGENTS.md) describes current implementation practices.

Keep entries concise and grounded in the repository. Record relevant discoveries
under the existing item rather than adding session logs. Remove completed items
once their decisions and behavior are captured in the dialect, documentation,
and tests as appropriate.

## Width inference

**Problem:** `scairrtl.add` requires its result type to be written by hand.
FIRRTL sizes the result at `max(w1, w2) + 1`, propagates an uninferred width when
either operand's width is unknown, and takes the left operand's signedness.
Nothing derives or checks any of that today.

**Known constraints:** CIRCT expresses this through `InferTypeOpInterface` and an
`inferReturnType` hook; ScaIR has no equivalent, and `OpDefs` exposes no
inference point. `unknownWidth` already marks an uninferred width. See
[ScaIRRTL.scala](dialects/src/ScaIRRTL.scala).

**Decisions needed:** Choose where inference runs — as a trait on the operation,
as a pass, or as a verifier that only checks a written type — and how an
uninferred width propagates through a chain of operations.

**Completion:** Derive the result width for the implemented primitives, keep an
unknown input width from producing a wrong answer, and cover both known- and
unknown-width operands with IR fixtures.

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

**Known constraints:** `SingleBlock`, `NoRegionArguments`, `HasParent`, and
`SameTypeOperands` are implemented locally at the top of
[ScaIRRTL.scala](dialects/src/ScaIRRTL.scala) so operations can enforce them.
Still absent: a `ParentOneOf` variant for a layer or wire, `ForceableOpInterface`
whose verifier gives `forceable` meaning, `FNamableOpInterface`,
`InnerSymbolOpInterface` with the `inner_sym` attribute it needs,
`SymbolUserOpInterface`, `OpAsmOpInterface`, and `SymbolName`/`SymbolVisibility`.

**Decisions needed:** Decide which of these are general enough to upstream into
`scair.ir` and which belong to the dialect, then keep them out of the operation
bodies either way.

**Completion:** Implement the ones an operation cannot be used correctly without,
upstream the structural ones, and remove them from the corresponding comment
blocks.

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

**Problem:** `scairrtl.layer` defines a layer, and the circuit can name layers it
enables or disables, but nothing puts code inside a layer.

**Known constraints:** CIRCT pairs `firrtl.layer` with `firrtl.layerblock`,
which carries a `SymbolRefAttr` to its layer and may nest in a module, another
layer block, a `when`, or a `match`. It also has a `LayerSpecialization` value
that selects a layer's default enablement. The parent constraint is a set rather
than a single operation, which the local `HasParent` cannot express.

**Decisions needed:** Choose whether to implement the parent-set constraint
before adding the layer block, and how far the layer subsystem needs to go for
the dialect's purpose.

**Completion:** Implement the layer block and its parent constraint, and cover
nesting, an undefined layer reference, and a layer whose specialization is
disabled.

## Sibling primitives

**Problem:** `scairrtl.add` is the only arithmetic operation, and there is no
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
