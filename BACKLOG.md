# Development backlog

Durable context for future development sessions. These items record open
decisions and implementation gaps, not changes to the language rules.
The [specification](docs/_docs/spec.md) remains the source of truth for syntax
and typing; [AGENTS.md](AGENTS.md) describes current implementation practices.

Keep entries concise and grounded in the repository. Record relevant discoveries
under the existing item rather than adding session logs. Remove completed items
once their decisions and behavior are captured in the specification, code,
and tests as appropriate.

## Initialization semantics for forward calls and captured values

**Problem:** A value initializer can call a forward function whose body captures
a top-level value that has not yet been initialized. For example:

```scala
val first = readLater()
val later = 42
def readLater(): Int = later
first
```

**Known constraints:** All function signatures are visible regardless of source
order. Values are initialized in source order, and a function can capture only
values preceding its own declaration. Those visibility rules permit the example
but do not define what happens when `readLater()` runs before `later` is ready.
See declaration scope in the specification, `checkBodies` in
[TypeInfer.scala](parser/src/TypeInfer.scala), and declaration generation in
[IrGen.scala](parser/src/IrGen.scala).

**Decisions needed:** Choose the runtime policy and where to enforce it,
including calls through other functions and function-valued bindings. Preserve
the distinction between signature visibility and value initialization.

**Completion:** Document the chosen semantics and implement the necessary checks
or runtime support. Cover direct and transitive calls before initialization,
safe calls after initialization, and recursion with regression tests; include
execution coverage for supported lowering.

## Generic specialization and execution

**Problem:** The frontend infers generic arguments and emits symbolic generic
IR, but generic functions do not currently lower to executable MLIR.

**Known constraints:** Resolved types and inferred type arguments live on AST
nodes; enum type arguments are preserved. Function conversion currently requires
no declared type parameters and a convertible scalar signature. See
[IrGen.scala](parser/src/IrGen.scala),
[ChocolaDialect.scala](dialects/src/ChocolaDialect.scala), and
[Conversion.scala](lowering/src/Conversion.scala).

**Decisions needed:** Choose an execution strategy, such as specialization,
where it runs, and an initial supported subset. Define handling of recursive
generic calls and repeated instantiations, and how unsupported cases remain
represented. Coordinate aggregate types and closures with their own lowering
support rather than assuming they already execute.

**Completion:** Preserve current typing rules and produce verifiable, executable
IR for the chosen subset. Test multiple concrete instantiations, repeated uses,
recursion, and unsupported cases, with complete IR and upstream execution checks.

## Closure conversion and lowering

**Problem:** Lambdas, returned functions, indirect calls, and captured top-level
values are supported by the frontend, but there is no closure conversion or
lowering. Function conversion currently rejects captures.

**Known constraints:** IR generation uses immutable environments and nested
regions; captures remain references to outer SSA values. Direct and indirect
calls have distinct IR operations. See [IrGen.scala](parser/src/IrGen.scala),
[ChocolaDialect.scala](dialects/src/ChocolaDialect.scala), and
[ConvertChocoToFunc.scala](lowering/src/ConvertChocoToFunc.scala).

**Decisions needed:** Choose a closure representation, capture passing and
lifetime strategy, and an indirect-call convention. Decide an initial supported
subset and how top-level captures interact with initialization semantics.

**Completion:** Convert captures into explicit IR operands or environments and
lower the supported function values and calls. Test capture-free and capturing
lambdas, returned functions, nested captures, and top-level captures, including
execution and complete IR checks. Preserve exact call arity and monomorphic
value typing.

## Related diagnostic proposal

[errors.md](errors.md) is the contributor draft for beginner-friendly diagnostics
aimed at students using ChocoLa. It contains the teaching goals, Scala comparison
material, and proposed messages. Keep further diagnostic design there rather
than duplicating it here; implementation scope and wording still need decisions.
