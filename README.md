# ScaIRRTL

An RTL (register-transfer level) dialect for [ScaIR](https://edin-dal.github.io/scair),
a type-safe SSA-based IR framework for Scala 3 that is compatible with MLIR.

## Status

The dialect is at an early stage and is spelled `firrtl.*`, mirroring CIRCT's
FIRRTL dialect. `dialects/src/firrtl.scala` defines:

- **Types** — `!firrtl.uint<N>` and `!firrtl.sint<N>`, each also spellable
  without a width while that width is still uninferred, `!firrtl.clock`,
  `!firrtl.reset`, `!firrtl.asyncreset`, and aggregate
  `!firrtl.bundle<...>` / `!firrtl.vector<...>` types.
- **Structure** — `firrtl.circuit`, `firrtl.module`, and `firrtl.layer`,
  each owning a body region and acting as a symbol table.
- **Declarations and statements** — `firrtl.wire`, `firrtl.matchingconnect`,
  and `firrtl.when`, whose body regions hold conditional operations.
- **Expressions** — `firrtl.add`, `firrtl.sub`, `firrtl.mul`, `firrtl.div`,
  `firrtl.rem`, the bitwise `firrtl.and`/`or`/`xor`/`not`, the reductions
  `firrtl.andr`/`orr`/`xorr`, `firrtl.cat`, `firrtl.dshl`/`dshr`,
  `firrtl.pad`, `firrtl.tail`, `firrtl.bits`, `firrtl.mux`, comparisons, and
  `firrtl.subfield`/`subindex` for aggregate field access.

`dialects/src/chirrtl.scala` defines the CHIRRTL memory dialect: the types
`!chirrtl.cmemory<element, depth>` for a behavioral memory and
`!chirrtl.cmemoryport` for a port declared on one; `chirrtl.combmem` and
`chirrtl.seqmem` for declaring those memories, optionally initialized from a
`#firrtl.meminit<filename, isBinary, isInline>` file; `chirrtl.memoryport` and
`chirrtl.memoryport.access` for declaring a port and enabling it on an index at
a clock edge; and `chirrtl.debugport`, whose result is the read-only
`!firrtl.probe<...>` reference type. The Chisel importer builds these from a
memory's declaration and each of its ports, and imports Chisel enum-typed
signals as unsigned integers of the enum's width.

`scairrtl.frontend.EmitMLIR` prints a translated `firrtl.circuit` — the
firrtl and chirrtl operations produced by the importer — as MLIR assembly, to a
`String`, a caller-supplied `Writer`, or a `.mlir` file:

```scala
import scairrtl.frontend.*

for
  circuit <- ChiselStage.elaborate(new MyModule).toCircuitOp
  path    <- EmitMLIR.writeToFile(circuit, Path.of("build/mlir/MyModule.mlir"))
yield path
```

`EmitMLIR.pathFor` derives the file path from the circuit's name, and
`EmitOptions` toggles generic assembly and source locations.

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

# Run Chisel frontend tests
./mill frontend.test

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
frontend/           -- Chisel frontend: circuit access, builder, importer
opt/                -- scairrtl.main: ScaIR-compatible optimizer CLI
tests/filecheck/    -- lit + FileCheck regression tests
```

## CI

Three workflows run on pushes to `main` and pull requests:

- **Format** — `./mill checkFormat` (scalafix + scalafmt)
- **Tests** — `./mill filechecks.run` (lit + FileCheck)
- **Documentation** — `./mill opt.unidocSite` (whole-project Scaladoc build and HTML site artifact)
