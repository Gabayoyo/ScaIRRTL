# AGENTS.md

This file provides guidance to coding agents working in this repository.

## What this is

ChocoLaCompiler is a prototype compiler for an educational subset of Scala called "ChocoLa". It is written in Scala 3 using the [Mill](https://mill-build.org/) build tool. The goal is a readable, hackable codebase that students can use as a starting point to re-implement individual compiler passes.

## Build commands

The project uses a bundled `./mill` wrapper script (no global installation needed).

| Task | Command |
|---|---|
| Compile all modules | `./mill __.compile` |
| Run the compiler on a file | `./mill opt.run <file.cola>` |
| Dump raw AST instead of IR | `./mill opt.run --dump-ast <file.cola>` |
| Print IR with source locations | `./mill opt.run --print-locations <file.cola>` |
| Run lit + FileCheck tests | `./mill filechecks.run` |
| Format all sources | `./mill format` |
| Check formatting (CI) | `./mill checkFormat` |

`pip install lit filecheck` is required before running `filechecks.run`.

The `format` command runs both scalafix (lint/rewrite) and scalafmt on all modules.

## Module structure

```
build.mill               # Mill build definition
parser/
  src/
    Ast.scala            # AST: Program, Decl, Expr, Type, Literal as Scala 3 enums
    Parser.scala         # fastparse 3 grammar; exports parseFile(path: String): Option[Program]
    IrGen.scala          # AST → SCAIR IR generation; entry point: generateProgram(Program)
dialects/
  src/
    ChocolaDialect.scala # SCAIR IR op definitions for the chocola.* dialect
main/
  src/
    Main.scala           # CaseApp CLI; parses, generates, and prints SCAIR assembly
tests/
  filecheck/
    lit.cfg              # lit configuration; defines the CHOCO_OPT substitution
    parser/              # One .cola test file per language feature
```

Module dependency chain: `dialects` ← `parser` ← `main`.

## Key dependencies

- **fastparse 3.1.1** — parser combinator library. Both `Ast.scala` and `Parser.scala` live in `parser/`. The parser uses `NoWhitespace` throughout (whitespace is consumed explicitly via the `ws` rule, which also skips `//` line comments).
- **scair-core 0.0.0-SNAPSHOT** — SCAIR IR framework (SSA-based, MLIR-inspired). Pulled from Sonatype snapshots. The `dialects` module defines the `chocola.*` dialect on top of it; see [docs](https://edin-dal.github.io/scair).
- **case-app 2.1.0** — command-line argument parsing in `main/`.
- **mill-scalafix** — scalafix is wired into the `ChocoLaModule` trait and runs via `./mill format`.

## Parser design notes

`Parser.scala` is organized into labeled sections: lexical helpers → literals → types → expression precedence layers → atoms → match cases → declarations → program. Key decisions:

- **Operator precedence** is implemented as seven explicit layered parser rules (`orExpr` down to `mulExpr`), making it easy to read and modify.
- **`::` (list cons)** is right-associative, implemented via direct recursion in `consExpr`.
- **Lambda vs tuple disambiguation**: `lambdaExpr` (tried first) requires typed parameters `(x: T, ...) => body`; plain `parenExpr` handles everything else and backtracks on failure.
- **Constructors vs variables**: uppercase-starting names followed by `(...)` are parsed as `Construct` at the atom level; lowercase names are `Var`.
- **`match` and `enum` bodies use braces** (`{ case ... }`) to avoid indentation sensitivity in fastparse.
- **`Program.expr` is `Option[Expr]`** so files containing only declarations (like `zero.cola`) parse correctly.
- **Source locations** reuse ScaIR's `scair.ir.Location`. `Expr`, `Decl`, `MatchCase` and `EnumCase` extend `Located`, which mirrors `Operation.location`/`Operation.at` and keeps the location outside the constructor (so `toString`, equality and patterns are unaffected). `parseSource` parses a `SourceInput` (an `IndexedParserInput` that knows its file name and `--split-input-file` line offset), so the parser builds `FileLineColRange`s directly: every node spans its full source text (end column one past the last character, as in MLIR). Nodes are located with `located(...)`, or, for infix/postfix nodes (`BinOp`, `Apply`, `Match`), with `spanning` from the start of their left operand/callee. `diagnostic(location, msg)` prefixes messages with the range in VS Code's terminal-link format (`file:line:col-endCol`, or `file:line:col-endLine.endCol` across lines).
- **fastparse tuple flattening**: `P[(String, Type)]` is flattened when composed with `~`, so typed-parameter lists are collected via `lambdaParamList: P[List[(String, Type)]]` before being composed further.

## IR generation (parser module)

`IrGen.scala` walks the typed AST purely functionally: there is no mutable
op buffer. An immutable `Env` maps names to SSA values and function symbols.

Type inference annotates the AST before generation: expressions have `tpe`,
declarations have `resolvedType`, enum cases have `fieldTypes`, and match cases
have `bindingTypes`. These annotations contain resolved aliases and substituted
generic arguments. `generateProgram(Program)` consumes that annotated AST;
there is no separate inference metadata environment passed into generation.

Key invariants:
- `generateExpr(expr)(using Env)` returns a `Generated(ops, value)`: the ops to emit, in order, and the SSA value holding the result.
- `generateDecl(decl)(using Env)` returns `(ops, newEnv)`; `generateProgram` folds over declarations threading the env.
- `generateProgram` accepts either a source `def main(): Int` or a final expression, but not both. Without a source main, it synthesizes `chocola.func @main`; an absent or non-`Int` expression returns zero, after evaluating the expression when present.
- Top-level `val` declarations remain ordered, immutable module-level SSA bindings. Functions may capture them until a later pass eliminates captures.
- Every op generated from a source node is stamped with its location via `.at(node.location)`; `YieldOp`/`ReturnOp` take the location of the returned expression. Synthesized ops (zero-return, module) keep `UnknownLoc`. Lowering passes do not forward locations.
- Source-level errors raised during generation throw `SourceError(msg, location)`, which `choco-opt` renders as `file:line:col: error: msg`. Type errors carry a location too.
- Regions with a non-terminator result use `YieldOp` (see `yielding`); function bodies use `ReturnOp`.
- Nested regions (lambdas, functions, match cases) pass an extended env explicitly via `(using env.bindAll(...))`, so inner bindings can't escape.
- When adding a new `Expr` variant in `Ast.scala`, add a matching op in `ChocolaDialect.scala`, a generation case in `generateExpr`, and register the op in the `summonDialect` tuple at the bottom of `ChocolaDialect.scala`.

## Testing

Tests live in `tests/filecheck/parser/`. Each `.cola` file contains a small program, a `// RUN:` directive that pipes the compiler output through `filecheck`, and `// CHECK:` patterns that assert the expected AST fragment.

```
// RUN: chocola-parse %s | filecheck %s

val x = 42

// CHECK: Parsing succeeded.
// CHECK: ValDecl(x,Lit(IntLit(42)))
```

`chocola-parse` is resolved by `lit` to `out/main/launcher.dest/run` (built by Mill). Run all tests with `./mill filechecks.run`.

IR-based tests use complete, exact FileCheck expectations generated with
[`filecheckize`](https://pypi.org/project/filecheckize/). It helped generate and
refresh these expectations directly from compiler output, avoiding manual
transcription of full lines, SSA names, and operation order. Prefer it when
adding or updating IR checks, then review the output for correctness.
Install it in a new
environment with `pip install filecheckize`, then regenerate a check block by
piping the relevant compiler invocation into it, for example:

```sh
choco-opt input.cola | filecheckize
choco-opt -p convert-choco-to-func input.cola | filecheckize --check-prefix FUNCTIONS
```

Do not anonymize SSA values or block labels: concrete names make the expected
IR easier to follow and verify operand wiring. Keep the generated `CHECK-NEXT`
lines so tests cover the complete operation order and module structure.

For round-trip tests, pipe directly through the compiler again:

```sh
// RUN: choco-opt %s | choco-opt | filecheck %s
```

Use temporary files only when their contents are reused or compared. New IR
tests must check the complete output with full lines and chained `CHECK-NEXT`
expectations, including tests with separate prefixes for lowering or round trips.
Do not replace these with selected operation names or type fragments.

ChocoLa inputs are parsed as one whole file; `--split-input-file` is for MLIR
inputs. Keep independent invalid ChocoLa examples in separate `.cola` files
and use `--parsing-diagnostics` to check their diagnostics with FileCheck.

## CI

Two GitHub Actions workflows run on every push to `main` and every pull request:

- **[format.yml](.github/workflows/format.yml)** — runs `./mill checkFormat`
- **[tests.yml](.github/workflows/tests.yml)** — installs `lit` + `filecheck` via pip, then runs `./mill filechecks.run`

Both workflows cache `out/` for incremental Mill builds and include a clean-retry fallback in case a stale cache causes a spurious failure.

## Language grammar

The specification is maintained in [docs/_docs/spec.md](docs/_docs/spec.md).
Build it alongside the whole project's API documentation with `./mill opt.unidocSite`.
