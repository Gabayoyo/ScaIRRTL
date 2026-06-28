# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ChocoLaCompiler is a prototype compiler for an educational subset of Scala (called "ChocoLa"). It is written in Scala 3 using the [Mill](https://mill-build.org/) build tool.

## Build commands

The project uses a bundled `./mill` wrapper script (no global installation needed).

| Task | Command |
|---|---|
| Compile all modules | `./mill main.compile` |
| Run the compiler | `./mill main.run -- <file.cola>` |
| Format all sources | `./mill format` |
| Check formatting (CI) | `./mill checkFormat` |
| Run tests | `./mill main.test` |

The `format` command runs both scalafix (lint/rewrite) and scalafmt on all modules.

## Module structure

```
build.mill          # Mill build definition
parser/             # chocola.parser package — lexer + parser
  src/
    Lexer.scala     # Token-level parsers (keywords, identifiers, operators)
    Parser.scala    # Grammar rules; exports parseFile(path: String): Option[Any]
main/               # chocola.main package — CLI entry point
  src/
    Main.scala      # CaseApp CLI; reads one .cola file and calls parseFile
  test/
    resources/      # Sample .cola source files used as test inputs
```

`main` depends on `parser`. There is no third module yet.

## Key dependencies

- **fastparse 3.1.1** — parser combinator library used in `parser/`. The lexer uses `NoWhitespace`, the parser uses `ScalaWhitespace`.
- **case-app 2.1.0** — command-line argument parsing in `main/`.
- **mill-scalafix** — scalafix is wired into the `ChocoLaModule` trait and runs via `./mill format`.

## Language grammar

The target language grammar is documented in [parser/syntax.md](parser/syntax.md) (LaTeX math notation). It covers:

- Literals: `Int`, `Double`, `String`, `Bool`, `Nil`, and built-in primitives (`map`, `filter`, `foldLeft`, `foldRight`)
- Expressions: unary/binary ops, variables, function calls, `if/then/else`, enum constructors, `match`, lambdas, tuples
- Declarations: `val`, `type alias`, `def` (with optional type parameters), `enum`
- Types: `Int`, `Double`, `String`, `Bool`, `List[τ]`, function `τ → τ`, tuples, type variables

## Formatting conventions

Scalafmt is configured in [.scalafmt.conf](.scalafmt.conf) for Scala 3 syntax:
- New-style Scala 3 syntax (`convertToNewSyntax`, `removeOptionalBraces`)
- Trailing commas on multi-line argument lists
- `redundantBraces` rewrite rule enabled

CI enforces formatting via `./mill checkFormat` (see [.github/workflows/format.yml](.github/workflows/format.yml)).
