# AGENTS.md

This file provides guidance to coding agents working in this repository.

## Keeping this guidance current

Every agent working in this repository must read this file at the start of
each session. Before finishing a session, review whether the work or verified
discoveries changed any guidance here, and update the affected sections as
part of the same change. This includes commands, dependencies, module structure,
dialect invariants, testing practices, and CI behavior.

Correct stale guidance encountered during the task and add concise, durable
information that future agents need. Check claims against source and
configuration. Keep this file focused on current repository guidance; put
session logs, progress reports, and outstanding work elsewhere. If no guidance
changed, no edit is needed. Agents doing delegated work must report relevant
documentation changes to the coordinating agent, who ensures they are included
before the session ends.

Consult [BACKLOG.md](BACKLOG.md) when choosing development work. Record durable
discoveries and unresolved decisions under the relevant item, and add concise
entries for newly discovered implementation gaps. Remove completed items once
their decisions and behavior are captured in the dialect, documentation, and
tests as appropriate. Keep session logs and completed-work histories out of it.

Keep the dialect's types, operations, and assembly authoritative in
`dialects/src/ScaIRRTL.scala` and in `docs/_docs/`. The README should link to
that material rather than duplicate it; use it for project setup, tooling,
implementation status, and navigation.

## What this is

ScaIRRTL is an RTL (register-transfer level) dialect for
[ScaIR](https://edin-dal.github.io/scair), a type-safe SSA-based IR framework for
Scala 3 that is compatible with MLIR. It is written in Scala 3 using the
[Mill](https://mill-build.org/) build tool.

The repository owns the Scala implementation of the `firrtl.*` dialect — its
types, operations, and assembly — and the `scairrtl-opt` driver that parses,
verifies, transforms, and prints IR built from it. The namespace mirrors CIRCT's
FIRRTL dialect while the Scala package remains `scairrtl`; attribute spellings
and module port block arguments still differ from CIRCT's schema.

## Build commands

The project uses a bundled `./mill` wrapper script (no global installation needed).

| Task | Command |
|---|---|
| Compile all modules | `./mill __.compile` |
| Run the optimizer on a file | `./mill opt.run <file.mlir>` |
| Print IR with source locations | `./mill opt.run --print-locations <file.mlir>` |
| Apply passes | `./mill opt.run -p <pass>[,<pass>...] <file.mlir>` |
| Execute `main` through MLIR tools | `./mill opt.run --run <file.mlir>` |
| Run lit + FileCheck tests | `./mill filechecks.run` |
| Format all sources | `./mill format` |
| Check formatting (CI) | `./mill checkFormat` |
| Build documentation and API reference | `./mill opt.unidocSite` |
| Build documentation and print its local URL | `./mill opt.unidocUrl` |

`pip install "lit<23" filecheck` is required before running `filechecks.run`.
Execution with `--run` requires `mlir-opt` and `mlir-runner` on `PATH` and an
`i32`-returning `main`.

The `format` command runs both scalafix (lint/rewrite) and scalafmt on all modules.

## Module structure

```
build.mill               # Mill build definition
dialects/
  src/
    ScaIRRTL.scala       # firrtl.* types and operations
opt/
  src/
    Opt.scala            # scairrtl.main.Main: scopt-based scairrtl-opt CLI
tests/
  filecheck/
    lit.cfg              # scairrtl-opt substitution, SCAIRRTL_OPT override, MLIR features
    dialects/            # Dialect IR: types, structure, declarations, statements
    opt/                 # Driver behavior, MLIR input handling, and execution
```

`opt` depends on `dialects`. `filechecks.run` builds the `opt` launcher before
invoking lit. Conversion passes get their own module when lowering work begins.

## Key dependencies

- **ScaIR** — SSA-based, MLIR-inspired IR framework, pulled from Sonatype
  snapshots. `dialects` depends on `scair-core` and the Arith, Func, and SCF
  dialect libraries; `opt` adds `scair-passes`. The repository defines
  `firrtl.*` types and operations on top of it; see
  [docs](https://edin-dal.github.io/scair).
- **scopt** — command-line argument parsing in `opt/`.
- **mill-scalafix** — scalafix is wired into the `ScaIRRTLModule` trait and runs
  via `./mill format`.

Use `build.mill` as the authority for dependency versions, Scala version,
repositories, and module dependencies. Its header also declares the Mill version
used by the wrapper and the scalafix plugin dependency. `ScaIRRTLModule` adds the
Sonatype snapshots repository, so every module that depends on ScaIR resolves
from it.

## Driver and CLI

`opt/src/Opt.scala` implements `scairrtl-opt`. Every input is parsed as MLIR,
verified, run through the selected passes, then printed or executed.

- Input is parsed with ScaIR's MLIR parser. `--split-input-file` splits the input
  at `// -----` lines and treats each chunk as a separate module.
- Parse failures stop the run with a failing status. `--parsing-diagnostics`
  reports them on stdout instead and exits successfully; invalid inputs produce
  no IR.
- IR is verified before passes and after each selected pass unless
  `--skip-verify` is set. `--verify-diagnostics` controls verification errors
  separately from parse errors.
- `-p` accepts comma-separated registered pass names in execution order. ScaIR's
  passes are registered through `scair.passes.allPasses` — `canonicalize`, `cse`,
  `licm`, `convert-arith-to-llvm`, `convert-func-to-llvm`, and
  `reconcile-unrealized-casts` among them — with the dialect's own passes
  appended. `-g` prints generic assembly, and `--print-locations` includes
  operation locations.
- `--run` pipes the processed module through `mlir-opt --convert-scf-to-cf
  --convert-to-llvm` and `mlir-runner -e main --entry-point-result=i32`. Select
  the required lowering passes explicitly before execution.
- Dialects are registered by appending them to `scair.dialects.allDialects`,
  which `Main` registers with a single `MLContext` at startup. That context is
  shared for the whole run, so dialect and pass registration must be complete
  before the first parse.

## Defining dialect types and operations

ScaIRRTL follows ScaIR's derivation style rather than a tablegen-style DSL.

- **Types** are case classes deriving `DerivedAttribute["firrtl.<name>"]` and
  implementing `AttrDefs`, mixed with a family trait (such as `RTLType`) so
  operations can accept several related types. Family traits nest where one
  narrows another: `IntType` is the two integer types out of `RTLType`.
  `TypeAttribute` makes them print with a leading `!`.
- **Operations** are case classes deriving `DerivedOperation["firrtl.<name>"]`
  and implementing `OpDefs`, with operands, results, attributes, and regions
  declared as constructor fields. Stateless operations mix in ScaIR's effect
  traits so canonicalization and folding can reason about them.
- **Enums** are integer-backed: `enum X(name: String) extends I32Enum(name)` with
  one `case` per variant, used directly as an operation property. They print as
  ordinals in generic assembly, which only a custom op syntax can turn back into
  keywords.
- **Constructor field types decide the construct.** `Operand[T]` and `Result[T]`
  are operands and results, `Region` is a region, `Seq[Operand[T]]` is variadic,
  and every remaining field is a property. A derived attribute parameter must
  itself be an attribute: an `Option` field does not compile there, so an
  uninferred value gets a sentinel (`unknownWidth`) as ScaIR does for dynamic
  dimensions.
- **`Operand[T]` and `Result[T]` bounds are never verified.** ScaIR checks a
  declared operand or result type against the actual type nowhere, for a
  concrete class, a trait, or a union alike, so a wrong type verifies happily.
  The bound documents intent and types the Scala API; only a trait's
  `traitVerify` enforces anything at the IR level, so a constraint that must
  hold needs a companion trait. `OperandAndResultTypes` is that companion: it
  takes the required class of every operand and result, which is also why
  `IntType` is a trait rather than a union alias — no runtime check can test a
  union.
- **`name` is reserved by `Operation`**, which uses it for the dialect-qualified
  operation name, so no operation may declare a field called `name`; the field
  is a compile error, not a shadowing warning. CIRCT's fields with that name,
  the circuit's and a wire's, are spelled `sym_name` here.
- Every type and operation is listed in the `summonDialect` tuple, which produces
  the dialect value registered with the context. A definition missing from that
  tuple is not part of the dialect.
- Generic assembly is the default. Custom printing and parsing are opt-in
  and must be provided together: `customPrint(p: Printer)` plus a
  `given AttributeCustomParser[T]`, or `given OperationCustomParser[T]` for
  operations. Custom printing alone breaks round-tripping.
- Operations carry source locations through `Operation.location` /
  `Operation.at`. Stamp every operation built from a source node, and keep
  synthesized operations at `UnknownLoc`.

### Property shape and absence

How a property is declared decides what happens when the input omits it. A
non-`Option` field falls back to its Scala default; with no default, omission
fails verification with a message that names the type as a macro object rather
than a type. An `Option[T]` field is always `None` when absent — its
`defaultValue` is never consulted, so an optional property cannot carry a
non-`None` default — and an enum property cannot have a default at all, which is
why optional enums are declared `Option[E]`.

Choose the shape from the meaning:

- **Bare, required** — omitting it means malformed IR, either because the field
  is part of the operation's identity or because the CIRCT declaration it mirrors
  says so. ScaIR's loop bounds, switch cases, and IRDL names are the first kind;
  the module's six port arrays are the second, and four of those are metadata
  that CIRCT nonetheless declares required.
- **`Option[T] = None`** — absence is meaningful and must stay distinguishable
  from empty. ScaIR's `scf.forall` mapping is the example, and the layer arrays
  on `CircuitOp` follow it. It is also the only way to make an enum property
  omittable, since enums cannot be defaulted.
- **`IntData` does not work as a property.** It prints as `0 : i64` and that
  form does not parse back, so a round-trip fails. Use `IntegerAttr` for a
  numeric property and read the number with `.value.value`.
- **Default value** — the value is neutral metadata. ScaIR's own dialects do not
  default array properties, so a defaulted array such as `annotations` mirrors
  CIRCT's `DefaultValuedAttr<..., "{}">` rather than local convention.

### Recording what an operation still lacks

Every operation carries a trailing comment block naming what is missing,
ordered by how badly it is needed. Keep the tags meaningful: `[BLOCKING]` marks
something the operation cannot verify or be used without, `[HIGH]` marks
semantics that are wrong or unenforced until it exists, and `[TODO]` marks
absent conveniences and interfaces.

These blocks list only what is *still* missing, so delete an entry in the same
change that resolves it. An entry describing a field or trait that now exists
reads as a live defect and is worse than no entry at all: it makes a working
operation look unusable. An operation whose `[BLOCKING]` entries are unresolved
is genuinely not usable, so resolve those before building on it.

### Traits are ScaIR's interfaces

ScaIR has no op-interface system. An MLIR op interface and an MLIR structural
trait become the same Scala construct — a `trait ... extends Operation` — because
a trait supplies both the verifier (`traitVerify`) and the `isInstanceOf`
dispatch that generic code uses. ScaIR's own source says so: `LoopLike` is
documented as "`LoopLikeOpInterface`, reduced to what code motion needs", and
`ConditionallySpeculatable` is a method-carrying trait standing in for the MLIR
interface of the same name. Abstract members (`def sym_name: StringData`) are how
an interface declares its methods; a companion `unapply`, as on `ConstantLike`,
is how a trait is queried.

Two MLIR interface capabilities do **not** survive the translation:

- **Traits cannot influence printing or parsing.** ScaIR's printer assigns SSA
  names itself and consults no hook, so `OpAsmOpInterface` and
  `HasCustomSSAName` have no trait form. They need a change in ScaIR.
- **An interface cannot be retrofitted** onto an operation defined elsewhere, and
  there is no runtime registry to ask which operations implement one. A trait
  must be mixed in at the operation's definition.

`HasParent` is set-valued, mirroring upstream rather than the two-trait shape it
first appears to have: MLIR declares `HasParent<string op>` and
`ParentOneOf<list<string> ops>` as separate TableGen classes, but both emit the
same variadic native `HasParent<...>` trait, and the diagnostic branches on arity
to say "parent op 'X'" for one parent and "parent op to be one of 'X, Y'" for
several. This trait is that one trait.

Two traits exist because ScaIR does not verify what a definition declares:
`Operand[T]`/`Result[T]` bounds are never checked, so `InfersResultTypes` checks
declared results against inferred ones, and `SymbolUserOpInterface` resolves
references itself because `SymbolTable.lookupSymbol` throws rather than reporting
an undefined symbol.

## Comments

A comment is about the code it sits next to. If it is not about that code, it
does not belong in the file.

- **No history, no rationale, no comparison.** Never record what the code used to
  do, why a choice was made, or how it differs from MLIR, CIRCT, or another
  source — no "was", "used to", "no longer", "now that", "unlike", "matching
  CIRCT's". Design rationale and upstream comparison belong in this file or
  `BACKLOG.md`.
- **Self-contained.** Nothing may depend on the conversation, a request, a
  review, a person, or a ticket to make sense.
- **No commented-out code.** Delete it. A `[TODO]` entry is how an absence gets
  recorded, and a commented-out line beside it says the same thing twice.
- **Short, and about what rather than how.** A doc comment above a function or
  method is one or two lines saying what it is or does. Anything about how the
  code works belongs in single-line comments inside the body.
- One sanctioned exception: the per-operation `[TODO]`/`[HIGH]`/`[BLOCKING]`
  blocks, which stay multi-line and still describe only the present state.

## Testing

Tests under `tests/filecheck/` use lit's external-shell test format with `.mlir`
fixtures. They exercise the driver: parsing, verification, diagnostics, generic
printing, pass selection, and execution. Fixtures use `// RUN:` directives and
FileCheck expectations.

`scairrtl-opt` is resolved by `lit` to `out/opt/launcher.dest/run` (built by
Mill). Run all tests with `./mill filechecks.run`.

The build sets `SCAIRRTL_OPT` to its launcher path; `lit.cfg` uses that
environment variable when present and otherwise uses the path above. For focused
tests, build with `./mill opt.launcher`, then run
`lit -v tests/filecheck/<directory-or-file>`. Outside lit, use
`out/opt/launcher.dest/run` wherever examples below say `scairrtl-opt`.

IR-based tests use complete, exact FileCheck expectations generated with
[`filecheckize`](https://pypi.org/project/filecheckize/). It helped generate and
refresh these expectations directly from driver output, avoiding manual
transcription of full lines, SSA names, and operation order. Prefer it when
adding or updating IR checks, then review the output for correctness.
Install it in a new environment with `pip install filecheckize`, then regenerate
a check block by piping the relevant invocation into it, for example:

```sh
scairrtl-opt input.mlir | filecheckize
scairrtl-opt -p canonicalize input.mlir | filecheckize --check-prefix CANONICALIZED
```

Do not anonymize SSA values or block labels: concrete names make the expected
IR easier to follow and verify operand wiring. Keep the generated `CHECK-NEXT`
lines so tests cover the complete operation order and module structure.

Use temporary files only when their contents are reused or compared. New IR
tests must check the complete output with full lines and chained `CHECK-NEXT`
expectations, including tests with separate prefixes for lowerings.
Do not replace these with selected operation names or type fragments.

Keep independent invalid inputs in separate fixtures and use
`--parsing-diagnostics` where the diagnostic text is the subject of the test.
Do not add a duplicate `not scairrtl-opt` or `not bash scairrtl-opt` RUN to every
invalid fixture. Reserve CLI exit-status checks for focused driver tests.

`lit.cfg` detects `mlir-opt` and `mlir-runner` on `PATH`. Execution fixtures
declare these tools with `REQUIRES:` and are unavailable locally when the tools
are absent; report those omissions separately from passing tests. Run the
affected test directory for a focused change and the full suite for changes
spanning the dialect or the driver.

## CI

Three GitHub Actions workflows run on every push to `main` and every pull request:

- **[format.yml](.github/workflows/format.yml)** — runs `./mill checkFormat`
- **[tests.yml](.github/workflows/tests.yml)** — installs `"lit<23"` + `filecheck`, ensures/restores the MLIR build cache, adds its tools to `PATH`, then runs `./mill filechecks.run`.
- **[specification.yml](.github/workflows/specification.yml)** — runs `./mill opt.unidocSite` and uploads the HTML site as the `scairrtl-documentation` artifact.

The format and tests workflows cache `out/` for incremental Mill builds and
include a clean-retry fallback in case a stale cache causes a spurious failure.
A separate **[keep-mlir-cache-warm.yml](.github/workflows/keep-mlir-cache-warm.yml)**
workflow runs on a schedule or manual dispatch to keep the MLIR cache available.

## Dialect reference

The dialect's types, operations, and assembly are documented in
[docs/_docs](docs/_docs). Build it alongside the whole project's API
documentation with `./mill opt.unidocSite`.
