# AGENTS.md

This file provides guidance to coding agents working in this repository.

## Keeping this guidance current

Every agent working in this repository must read this file at the start of
each session. Before finishing a session, review whether the work or verified
discoveries changed any guidance here, and update the affected sections as
part of the same change. This includes commands, dependencies, module structure,
compiler invariants, language restrictions, testing practices, and CI behavior.

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
their decisions and behavior are captured in the specification, code, and tests
as appropriate. Keep session transcripts and completed-work histories out of it.

Keep language syntax and typing rules authoritative in `docs/_docs/spec.md`.
The README should link to the specification rather than duplicate those rules;
use it for project setup, tooling, implementation status, and navigation.
`errors.md` is a contributor draft proposing beginner-friendly diagnostics
for students using ChocoLa. Keep its proposals and Scala comparison material
distinct from implemented behavior; exact current diagnostics belong in tests.

## What this is

ChocoLaCompiler is a prototype compiler for an educational subset of Scala called "ChocoLa". It is written in Scala 3 using the [Mill](https://mill-build.org/) build tool. The goal is a readable, hackable codebase that students can use as a starting point to re-implement individual compiler passes.

## Build commands

The project uses a bundled `./mill` wrapper script (no global installation needed).

| Task | Command |
|---|---|
| Compile all modules | `./mill __.compile` |
| Run the compiler on a file | `./mill opt.run <file.cola>` |
| Dump inferred AST instead of IR | `./mill opt.run --dump-ast <file.cola>` |
| Print IR with source locations | `./mill opt.run --print-locations <file.cola>` |
| Lower supported constructs to MLIR | `./mill opt.run -p convert-choco-to-mlir <file.cola>` |
| Lower and execute main | `./mill opt.run -p convert-choco-to-mlir --run <file.cola>` |
| Run lit + FileCheck tests | `./mill filechecks.run` |
| Format all sources | `./mill format` |
| Check formatting (CI) | `./mill checkFormat` |
| Build specification and API documentation | `./mill opt.unidocSite` |
| Build documentation and print its local URL | `./mill opt.unidocUrl` |

`pip install "lit<23" filecheck` is required before running `filechecks.run`.
Execution with `--run` requires `mlir-opt` and `mlir-runner` on `PATH` and
a fully lowered `main` of type `() -> i32`.

The `format` command runs both scalafix (lint/rewrite) and scalafmt on all modules.

## Module structure

```
build.mill               # Mill build definition
parser/
  src/
    Ast.scala            # AST: Program, Decl, Expr, Type, Literal as Scala 3 enums
    Parser.scala         # fastparse 3 grammar; parseSource returns Either[String, Program]
    TypeInfer.scala      # TypeInferencer.inferProgram: annotate AST, return diagnostics
    IrGen.scala          # AST → SCAIR IR generation; entry point: generateProgram(Program)
dialects/
  src/
    ChocolaDialect.scala # SCAIR IR op definitions for the chocola.* dialect
lowering/
  src/
    Conversion.scala     # Shared scalar type conversion and function eligibility
    ConvertChocoTo*.scala # Func, SCF, Arith, and combined MLIR conversion passes
opt/
  src/
    Opt.scala            # chocola.main.Main: scopt-based choco-opt CLI
tests/
  filecheck/
    lit.cfg              # choco-opt substitution, CHOCO_OPT override, MLIR features
    parser/              # Syntax, layout, and AST regressions
    typeinfer/           # Typing diagnostics and inferred AST/IR regressions
    ir/                  # Frontend IR and source-generation errors
    opt/                 # Driver behavior and MLIR input handling
    lowering/            # Conversion, upstream compatibility, and execution
```

`parser` and `lowering` each depend on `dialects`; `opt` depends on both
`parser` and `lowering`. `filechecks.run` builds the `opt` launcher before
invoking lit.

## Key dependencies

- **fastparse** — parser combinator library in `parser/`. The parser uses `NoWhitespace` throughout; named spacing and layout helpers explicitly control newlines, indentation, and `//` comments.
- **ScaIR** — SSA-based, MLIR-inspired IR framework, pulled from Sonatype snapshots. `dialects` depends on `scair-core` and the Arith, Func, and SCF dialect libraries; `lowering` adds `scair-passes`. The repository defines `chocola.*` types and operations on top of it; see [docs](https://edin-dal.github.io/scair).
- **scopt** — command-line argument parsing in `opt/`.
- **mill-scalafix** — scalafix is wired into the `ChocoLaModule` trait and runs via `./mill format`.

Use `build.mill` as the authority for dependency versions, Scala version,
repositories, and module dependencies. Its header also declares the Mill
version used by the wrapper and the scalafix plugin dependency.

## Frontend and CLI

`opt/src/Opt.scala` implements `choco-opt`. Normal ChocoLa compilation runs
`parseSource` → a fresh `TypeInferencer().inferProgram` → `generateProgram` →
IR verification → selected passes → printing or execution. `parseFile` is a
convenience wrapper that prints parse errors and returns `Option[Program]`;
the CLI uses `parseSource` directly.

- Only file names ending in `.cola` select the ChocoLa frontend. Other files
  and standard input use the MLIR parser. `--dump-ast` requires a `.cola` file
  and runs inference before printing the AST; it bypasses IR generation.
- ChocoLa files are parsed whole. `--split-input-file` splits only MLIR inputs
  at `// -----` lines.
- Parsing, typing, and source-generation errors stop normal compilation with
  a failing status. `--parsing-diagnostics` reports frontend errors on stdout
  and exits successfully; invalid inputs produce no IR. AST dumping also fails
  on parse/type errors unless this mode is selected.
- IR is verified before passes and after each selected pass unless
  `--skip-verify` is set. `--verify-diagnostics` controls verification errors
  separately from frontend diagnostics.
- `-p` accepts comma-separated registered pass names in execution order;
  ScaIR passes and ChocoLa passes are registered together. `-g` prints generic
  assembly, and `--print-locations` includes operation locations.

## Parser design notes

`Parser.scala` is organized into labeled sections: lexical helpers → literals → types → expression precedence layers → atoms → match cases → declarations → program. Key decisions:

- **Operator precedence** is implemented as seven explicit layered parser rules (`orExpr` down to `mulExpr`), making it easy to read and modify.
- **`::` (list cons)** is right-associative, implemented via direct recursion in `consExpr`.
- **Lambda vs tuple disambiguation**: `lambdaExpr` (tried first) requires typed parameters `(x: T, ...) => body`; plain `parenExpr` handles everything else and backtracks on failure.
- **Constructors vs variables**: uppercase-starting names followed by `(...)` are parsed as `Construct` at the atom level; lowercase names are `Var`.
- **Educational priorities**: accepted ChocoLa layouts must be valid Scala layouts with the same grouping. After compatibility, prioritize a clear implementation students can read and modify; performance and formatting flexibility come later. Keep the explicit precedence layers and small, named layout helpers rather than hiding behavior in clever abstractions.
- **Indentation-only cases**: `enum T[a]:` and `expression match` introduce newline-delimited, more-indented case lists. Siblings align exactly; braces are rejected. `caseBlock` and its separator leave dedented outer cases untouched, and check the block boundary without consuming trailing trivia.
- **Expression bodies**: `body` handles inline expressions or a more-indented single expression after `=`, `=>`, `then`, or `else`. Binary precedence rules inherit a contextual `minIndent: Int`. Explicit `using` arguments establish it at body, delimiter, and program boundaries (for example, `expr(using 0)` inside parentheses); owner and case indentation remain explicit arguments. There is no mutable indentation stack. Multiline `else` aligns with its owning `if`; grammar delimiters close nested expressions.
- **Whitespace**: `inlineSpace` never crosses a newline; `ws` skips blank/comment-only lines and supports LF/CRLF. Arguments and types inside delimiters wrap freely. A call's `(` and a binary operator stay on the line of their preceding expression's last token; `operandSpace` permits trailing-operator continuation without blank lines. Top-level items start on separate lines at column 1. Tabs in code indentation, local statement blocks, and explicit `end` markers are excluded.
- **Unary operators**: `-` and `!` touch their operands everywhere, without whitespace or comments. `unaryExpr` needs no layout context; this uniform restriction avoids special handling of final unary expressions after declarations.
- **`Program.expr` is `Option[Expr]`** so files containing only declarations (like `zero.cola`) parse correctly.
- **Source locations** reuse ScaIR's `scair.ir.Location`. `Expr`, `Decl`, `MatchCase` and `EnumCase` extend `Located`, which mirrors `Operation.location`/`Operation.at` and keeps the location outside the constructor (so `toString`, equality and patterns are unaffected). `parseSource` parses a `SourceInput` (an `IndexedParserInput` that knows its file name and optional line offset), so the parser builds `FileLineColRange`s directly: every node spans its full source text (end column one past the last character, as in MLIR). Nodes are located with `located(...)`, or, for infix/postfix nodes (`BinOp`, `Apply`, `Match`), with `spanning` from the start of their left operand/callee. The CLI parses ChocoLa whole with no offset. `diagnostic(location, msg)` prefixes messages with the range in VS Code's terminal-link format (`file:line:col-endCol`, or `file:line:col-endLine.endCol` across lines).
- **fastparse tuple flattening**: `P[(String, Type)]` is flattened when composed with `~`, so typed-parameter lists are collected via `lambdaParamList: P[List[(String, Type)]]` before being composed further.

## Type inference (parser module)

`TypeInfer.scala` owns constraint-based inference. Use a fresh
`TypeInferencer` per program: it keeps mutable registries and substitutions.
`inferProgram` returns `List[TypeError]` and annotates the existing AST.

- Register type/enum names before validating annotations, aliases, enum fields,
  and function signatures, including unused declarations. Recursive enums are
  legal; alias expansion stops at nominal enums and rejects alias cycles.
- All top-level function signatures are visible in function bodies and value
  initializers. Values enter scope in source order, after their initializers;
  functions may capture only preceding values. Self/mutual recursion and
  forward function references are supported. The runtime policy for an
  initializer calling a forward function that captures an uninitialized value
  remains unresolved; signature visibility alone does not resolve it.
- Ordinary values and lambdas are monomorphic and share inference variables
  across uses. Generic declarations and primitives instantiate fresh variables
  per reference. `TMeta` variables are solvable; declaration-owned `TParam`
  parameters remain rigid. Do not introduce implicit let-generalization.
- Function types carry a parameter-type list and result type. Enforce exact
  call arity, including zero arguments; retain functions returning functions.
  Preserve enum type arguments and substitute them into match bindings.
- Resolve AST annotations only after all program constraints and numeric
  requirements have been checked. Remaining inference variables are ambiguous;
  unresolved types must never reach IR generation. Earlier typing errors
  suppress secondary final ambiguity reports.

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
- Source-level errors raised during generation throw `SourceError(msg, location)`, which `choco-opt` renders with `diagnostic(location, s"error: $msg")`. Type errors carry a location too.
- Regions with a non-terminator result use `YieldOp` (see `yielding`); function bodies use `ReturnOp`.
- Nested regions (lambdas, functions, match cases) pass an extended env explicitly via `(using env.bindAll(...))`, so inner bindings can't escape.
- When adding a new `Expr` variant in `Ast.scala`, update parsing and inference, add a matching op in `ChocolaDialect.scala` and a generation case in `generateExpr`, and register the op in the `summonDialect` tuple at the bottom of `ChocolaDialect.scala`. Add syntax, typing, and complete IR regressions; update the specification when language rules change. Add lowering coverage if the construct is intended to convert.

## Lowering

`lowering/` uses ScaIR `ConversionPass` with small operation-local conversion
patterns and a shared type converter. Scalars map as `Int` → `i32`, `Double`
→ `f64`, and `Bool` → `i1`; other types remain unchanged.

- `convert-choco-to-func` converts eligible functions, direct calls, and
  returns. Function signatures must contain only convertible scalar types;
  functions must have no type parameters or captures.
- `convert-choco-to-scf` converts scalar-result if-expressions and their yields.
  `convert-choco-to-arith` converts supported scalar constants, arithmetic,
  comparisons, and Boolean operations.
- `convert-choco-to-mlir` combines the Func, SCF, and Arith patterns in one
  conversion sweep. ScaIR's conversion driver reconciles materialized casts
  after each conversion pass by default. The separately registered
  `reconcile-unrealized-casts` pass can also be selected explicitly.
- `builtin.unrealized_conversion_cast` bridges converted and residual types.
  Unsupported constructs remain as `chocola.*` IR; generic specialization,
  closure conversion, and lowering of lists, tuples, strings, enums, and matches
  are not implemented. Successful partial conversion does not imply executability.
- `--run` pipes the processed module through `mlir-opt --convert-scf-to-cf
  --convert-to-llvm` and `mlir-runner -e main --entry-point-result=i32`.
  Select the required lowering passes explicitly before execution.

## Testing

Tests under `tests/filecheck/` use lit's external-shell test format with `.cola`
and `.mlir` fixtures. `parser/` checks syntax/layout and ASTs, `typeinfer/`
checks typing and annotations, `ir/` checks frontend IR and generation errors,
`opt/` checks the driver and MLIR inputs, and `lowering/` checks conversion and
upstream execution. Fixtures use `// RUN:` directives and FileCheck expectations
for ASTs, diagnostics, or IR as appropriate. A valid AST fixture looks like:

```
// RUN: choco-opt --dump-ast %s | filecheck %s

val x = 42

// CHECK: Parsing succeeded.
// CHECK: ValDecl(x,Lit(IntLit(42),Some(TInt)),None)
```

`choco-opt` is resolved by `lit` to `out/opt/launcher.dest/run` (built by Mill). AST dumping also runs type inference, so valid parser fixtures must be well typed. Run all tests with `./mill filechecks.run`.

The build sets `CHOCO_OPT` to its launcher path; `lit.cfg` uses that environment
variable when present and otherwise uses the path above. For focused tests,
build with `./mill opt.launcher`, then run `lit -v tests/filecheck/<directory-or-file>`.
Outside lit, use `out/opt/launcher.dest/run` wherever examples below say `choco-opt`.

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

Frontend tests check ASTs, diagnostics, or IR generated directly from ChocoLa.
Do not pipe frontend IR through the compiler again: ScaIR's general assembly
round-trip robustness is outside ChocoLa's responsibility.

Use temporary files only when their contents are reused or compared. New IR
tests must check the complete output with full lines and chained `CHECK-NEXT`
expectations, including tests with separate prefixes for lowering.
Do not replace these with selected operation names or type fragments.

ChocoLa inputs are parsed as one whole file; `--split-input-file` is for MLIR
inputs. Keep independent invalid ChocoLa examples in separate `.cola` files
and use `--parsing-diagnostics` to check their diagnostics with FileCheck.
Do not add a duplicate `not choco-opt` or `not bash choco-opt` RUN to every
invalid fixture. Reserve CLI exit-status checks for focused driver tests.

`lit.cfg` detects `mlir-opt` and `mlir-runner` on `PATH`. Upstream compatibility
and execution fixtures declare these tools with `REQUIRES:` and are unavailable
locally when the tools are absent; report those omissions separately from
passing tests. Run the affected test directory for a focused change and the
full suite for changes spanning frontend or lowering behavior.

## CI

Three GitHub Actions workflows run on every push to `main` and every pull request:

- **[format.yml](.github/workflows/format.yml)** — runs `./mill checkFormat`
- **[tests.yml](.github/workflows/tests.yml)** — installs `"lit<23"` + `filecheck`, ensures/restores the MLIR build cache, adds its tools to `PATH`, then runs `./mill filechecks.run` including upstream checks.
- **[specification.yml](.github/workflows/specification.yml)** — runs `./mill opt.unidocSite` and uploads the HTML site as the `chocola-specification` artifact.

The format and tests workflows cache `out/` for incremental Mill builds and
include a clean-retry fallback in case a stale cache causes a spurious failure.
A separate **[keep-mlir-cache-warm.yml](.github/workflows/keep-mlir-cache-warm.yml)**
workflow runs on a schedule or manual dispatch to keep the MLIR cache available.

## Language grammar

The specification is maintained in [docs/_docs/spec.md](docs/_docs/spec.md).
Build it alongside the whole project's API documentation with `./mill opt.unidocSite`.
