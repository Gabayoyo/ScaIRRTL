# Development backlog

Durable context for future development sessions. These items record open
decisions and implementation gaps, not settled behavior.
[AGENTS.md](AGENTS.md) describes current implementation practices.

Keep entries concise and grounded in the repository. Record relevant discoveries
under the existing item rather than adding session logs. Remove completed items
once their decisions and behavior are captured in the dialect, documentation,
and tests as appropriate.

## Dialect definition

**Problem:** The dialect has no types or operations yet.
`[ScaIRRTL.scala](dialects/src/ScaIRRTL.scala)` is a stub, and `scairrtl-opt`
registers only ScaIR's dialects.

**Known constraints:** ScaIRRTL follows ScaIR's derivation style — case classes
deriving `DerivedAttribute`/`DerivedOperation`, collected in a `summonDialect`
tuple — rather than a tablegen-style DSL. A dialect joins the context by being
appended to `scair.dialects.allDialects` in [Opt.scala](opt/src/Opt.scala).
Related prior art is FIRRTL and CIRCT's `rtl` dialect, which separate structural
wiring from clocked behavior.

**Decisions needed:** Choose the type set (widths, clocks, resets, wires,
registers, aggregates) and the operation set (module definition and
instantiation, connections, combinational logic, sequential elements). Decide
how widths are represented — parameterized types versus explicit attributes —
and whether port direction and flip are structural or attribute-based.

**Completion:** Define the types and operations, register them, and cover
assembly, verification, and round-tripping with complete IR fixtures.

## Lowering path

**Problem:** There are no conversion passes for the dialect, and no lower-level
representation to convert to.

**Known constraints:** ScaIR's `ConversionPass` framework, type converters, and
`builtin.unrealized_conversion_cast` materialization are available through
`scair-passes`, which `opt` already depends on. ScaIR registers
`convert-arith-to-llvm` and `convert-func-to-llvm` among its passes.

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
