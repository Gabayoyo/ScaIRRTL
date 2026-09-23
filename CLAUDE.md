# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ChocoLaCompiler is a prototype compiler for an educational subset of Scala called "ChocoLa". It is written in Scala 3 using the [Mill](https://mill-build.org/) build tool. The goal is a readable, hackable codebase that students can use as a starting point to re-implement individual compiler passes.

## Build commands

The project uses a bundled `./mill` wrapper script (no global installation needed).

| Task | Command |
|---|---|
| Compile all modules | `./mill __.compile` |
| Run the compiler on a file | `./mill opt.run <file.cola>` |
| Dump raw AST instead of IR | `./mill opt.run --dump-ast <file.cola>` |
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
- **fastparse tuple flattening**: `P[(String, Type)]` is flattened when composed with `~`, so typed-parameter lists are collected via `lambdaParamList: P[List[(String, Type)]]` before being composed further.

## IR generation (parser module)

`IrGen.scala` walks the typed AST purely functionally: there is no mutable
op buffer. An immutable `Env` maps names to SSA values, function symbols and
constructor field types.

Key invariants:
- `generateExpr(expr)(using Env)` returns a `Generated(ops, value)`: the ops to emit, in order, and the SSA value holding the result.
- `generateDecl(decl)(using Env)` returns `(ops, newEnv)`; `generateProgram` folds over declarations threading the env.
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

## CI

Two GitHub Actions workflows run on every push to `main` and every pull request:

- **[format.yml](.github/workflows/format.yml)** — runs `./mill checkFormat`
- **[tests.yml](.github/workflows/tests.yml)** — installs `lit` + `filecheck` via pip, then runs `./mill filechecks.run`

Both workflows cache `out/` for incremental Mill builds and include a clean-retry fallback in case a stale cache causes a spurious failure.

## Language grammar

The formal grammar is in [parser/syntax.md](parser/syntax.md) (LaTeX BNF). A prose summary is in the [README](README.md).
