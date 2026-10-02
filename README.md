# ChocoLaCompiler

Prototype compiler for an educational subset of Scala, written in Scala 3.
Intended as a starting point for students to re-implement individual compiler passes.

## Building and running

The repository ships a self-contained `./mill` wrapper — no global installation needed.

```sh
# Compile everything
./mill opt.compile

# Parse ChocoLa source and emit IR
./mill opt.run path/to/file.cola

# Run the currently supported ChocoLa-to-MLIR conversion
./mill opt.run -p convert-choco-to-mlir path/to/file.cola

# Lower and execute main through upstream MLIR tools
./mill opt.run -p convert-choco-to-mlir --run path/to/file.cola

# Run lit + FileCheck tests
./mill filechecks.run

# Format all sources (scalafix + scalafmt)
./mill format

# Check formatting (also enforced in CI)
./mill checkFormat
```

`pip install "lit<23" filecheck` is required for `filechecks.run`.

`choco-opt` accepts the same input, diagnostics, verification, generic-printing,
and pass-selection flags as `scair-opt`. Files ending in `.cola` use the
ChocoLa frontend; all other files and standard input are parsed as MLIR. The
individual ChocoLa passes are `convert-choco-to-func`,
`convert-choco-to-scf`, and `convert-choco-to-arith`;
`convert-choco-to-mlir` runs all three followed by
`reconcile-unrealized-casts`. Use `--dump-ast` to inspect a `.cola` file before
IR generation. Use `--run` after selecting the necessary lowering passes to
execute `main` as a `() -> i32` function; this requires `mlir-opt` and
`mlir-runner` to be available on `PATH`.

## Reading and building the specification

The [language specification](docs/_docs/spec.md) is the source of truth for the
syntax and typing rules, with an example program. Read it directly on GitHub,
or build the whole-project Scaladoc site with the specification and API
documentation for the parser, dialects, lowering passes, and optimizer:

```sh
./mill opt.unidocSite
```

Open `out/opt/unidocSite.dest/index.html` in your browser and follow the
specification link. To build the site and print its local `file:` URL, use:

```sh
./mill opt.unidocUrl
```

Pages can be opened directly from disk; browser restrictions on local files may
limit Scaladoc's dynamic navigation and search.
The site uses MathJax 3 from a CDN to render the mathematical notation;
JavaScript and an internet connection are needed for math rendering.
No TeX installation is required. PDF export is not maintained.
API source links point to the repository's `main` branch on GitHub.

The specification workflow builds the HTML site on pushes to `main` and pull
requests and uploads it as the `chocola-specification` artifact.

## Conversion direction

ScaIR is used to build and print the IR; upstream MLIR is the eventual
conversion backend. The `-p convert-choco-to-mlir` pipeline currently converts
primitive types, scalar arithmetic, direct non-generic functions, calls,
returns, and if-expressions to MLIR-compatible Builtin, Func, SCF, and Arith operations.
Unsupported language constructs intentionally remain as `chocola.*` IR.
Each conversion pass is a `WalkerPass` made from small, operation-local rewrite
patterns. `builtin.unrealized_conversion_cast` bridges source and target types
while the patterns run; ScaIR's reconciliation pass removes redundant cast
chains afterward. Casts remain only at the boundary of residual ChocoLa IR.

Once a program contains no residual `chocola.*` operations, its output is
intended to be passed to upstream tools such as `mlir-opt` for downstream
conversion and code generation. When `mlir-opt` is available, the FileCheck
suite enables an additional handoff test for this boundary.

## Repository layout

```
parser/             -- chocola.parser: AST + fastparse 3 parser
dialects/           -- chocola.* types and operations
lowering/           -- ChocoLa-to-Builtin/Func/SCF/Arith passes
opt/                -- chocola.main: ScaIR-compatible optimizer CLI
tests/filecheck/    -- lit + FileCheck regression tests
```

## CI

Three workflows run on pushes to `main` and pull requests:
- **Format** — `./mill checkFormat` (scalafix + scalafmt)
- **Tests** — `./mill filechecks.run` (lit + FileCheck)
- **Specification** — `./mill opt.unidocSite` (whole-project Scaladoc build and HTML site artifact)
