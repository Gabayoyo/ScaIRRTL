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

The repository owns the `scairrtl.*` dialect — its types, operations, and
assembly — and the `scairrtl-opt` driver that parses, verifies, transforms, and
prints IR built from it.

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
    ScaIRRTL.scala       # scairrtl.* types and operations
opt/
  src/
    Opt.scala            # scairrtl.main.Main: scopt-based scairrtl-opt CLI
tests/
  filecheck/
    lit.cfg              # scairrtl-opt substitution, SCAIRRTL_OPT override, MLIR features
    opt/                 # Driver behavior, MLIR input handling, and execution
```

`opt` depends on `dialects`. `filechecks.run` builds the `opt` launcher before
invoking lit. Conversion passes get their own module when lowering work begins.

## Key dependencies

- **ScaIR** — SSA-based, MLIR-inspired IR framework, pulled from Sonatype
  snapshots. `dialects` depends on `scair-core` and the Arith, Func, and SCF
  dialect libraries; `opt` adds `scair-passes`. The repository defines
  `scairrtl.*` types and operations on top of it; see
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

- **Types** are case classes deriving `DerivedAttribute["scairrtl.<name>"]` and
  implementing `AttrDefs`. A sealed trait over a type family lets operations
  accept several related types, and a shared instance can be exposed as a value
  for convenience.
- **Operations** are case classes deriving `DerivedOperation["scairrtl.<name>"]`
  and implementing `OpDefs`, with operands, results, attributes, and regions
  declared as constructor fields. Stateless operations mix in ScaIR's effect
  traits so canonicalization and folding can reason about them.
- Every type and operation is listed in the `summonDialect` tuple, which produces
  the dialect value registered with the context. A definition missing from that
  tuple is not part of the dialect.
- Generic assembly is the default. Custom printing and parsing are opt-in through
  `customPrint(p: Printer)` plus a `given AttributeCustomParser[T]` or the
  operation equivalent; reach for them only where the default spelling cannot
  express the syntax.
- Operations carry source locations through `Operation.location` /
  `Operation.at`. Stamp every operation built from a source node, and keep
  synthesized operations at `UnknownLoc`.

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
