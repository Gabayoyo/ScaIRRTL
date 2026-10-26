# ScaIRRTL

An RTL (register-transfer level) dialect for [ScaIR](https://edin-dal.github.io/scair),
a type-safe SSA-based IR framework for Scala 3 that is compatible with MLIR.

## Status

The dialect is at an early stage and is spelled `firrtl.*`, mirroring CIRCT's
FIRRTL dialect. `dialects/src/ScaIRRTL.scala` defines:

- **Types** — `!firrtl.uint<N>` and `!firrtl.sint<N>`, each also spellable
  without a width while that width is still uninferred, and `!firrtl.clock`.
- **Structure** — `firrtl.circuit`, `firrtl.module`, and `firrtl.layer`,
  each owning a body region and acting as a symbol table.
- **Declarations and statements** — `firrtl.wire` and
  `firrtl.matchingconnect`.
- **Expressions** — `firrtl.add`.

Generic assembly is the only syntax for now: every operation parses, verifies,
prints, and round-trips through `scairrtl-opt`, but none has custom assembly.
Definitions carry trailing comment blocks recording what each operation still
lacks, tagged `[BLOCKING]`, `[HIGH]`, or `[TODO]`; see
[AGENTS.md](AGENTS.md#recording-what-an-operation-still-lacks).

## Building and running

The repository ships a self-contained `./mill` wrapper — no global installation needed.

```sh
# Compile everything
./mill opt.compile

# Parse, verify, and print IR
./mill opt.run path/to/file.mlir

# Print IR with source locations
./mill opt.run --print-locations path/to/file.mlir

# Run lit + FileCheck tests
./mill filechecks.run

# Format all sources (scalafix + scalafmt)
./mill format

# Check formatting (also enforced in CI)
./mill checkFormat
```

`pip install "lit<23" filecheck` is required for `filechecks.run`.

`scairrtl-opt` accepts the same input, diagnostics, verification,
generic-printing, and pass-selection flags as `scair-opt`. Input is parsed as
MLIR; `--split-input-file` splits it at `// -----` lines. Use `--run` to execute
`main` as a `() -> i32` function, which requires `mlir-opt` and `mlir-runner` to
be available on `PATH`.

## Reading and building the documentation

The dialect reference lives in [docs/_docs](docs/_docs), alongside the
whole-project API documentation for the dialects and the optimizer:

```sh
./mill opt.unidocSite
```

Open `out/opt/unidocSite.dest/index.html` in your browser. To build the site and
print its local `file:` URL, use:

```sh
./mill opt.unidocUrl
```

Pages can be opened directly from disk; browser restrictions on local files may
limit Scaladoc's dynamic navigation and search.
The site uses MathJax 3 from a CDN to render the mathematical notation;
JavaScript and an internet connection are needed for math rendering.
No TeX installation is required. PDF export is not maintained.
API source links point to the repository's `main` branch on GitHub.

The documentation workflow builds the HTML site on pushes to `main` and pull
requests and uploads it as the `scairrtl-documentation` artifact.

## Repository layout

```
dialects/           -- firrtl.* types and operations
opt/                -- scairrtl.main: ScaIR-compatible optimizer CLI
tests/filecheck/    -- lit + FileCheck regression tests
```

## CI

Three workflows run on pushes to `main` and pull requests:

- **Format** — `./mill checkFormat` (scalafix + scalafmt)
- **Tests** — `./mill filechecks.run` (lit + FileCheck)
- **Documentation** — `./mill opt.unidocSite` (whole-project Scaladoc build and HTML site artifact)
