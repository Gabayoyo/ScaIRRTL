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

## Language grammar

ChocoLa is a strict subset of Scala 3 with first-class functions, algebraic data types, and pattern matching.

### Programs

A program is a sequence of declarations followed by an optional expression:

```
program ::= declaration* expr?
```

### Expressions

```
expr ::= literal
       | unop expr
       | expr binop expr
       | x                                          -- variable
       | expr(expr, ..., expr)                      -- function call
       | if expr then expr else expr
       | C(expr, ..., expr)                         -- enum constructor
       | expr match { (case C(x, ..., x) => expr)+ }
       | (x: type, ..., x: type) => expr            -- lambda
       | (expr, ..., expr)                          -- tuple
```

### Literals

```
literal ::= INTEGER | DOUBLE | STRING | true | false | Nil
          | map | filter | foldLeft | foldRight      -- built-in primitives
```

### Operators

```
unop  ::= -  | !
binop ::= +  | -  | *  | /
        | <  | >  | <= | >= | == | !=
        | && | ||
        | ::                                         -- list cons (right-associative)
```

Operator precedence (low → high): `||`, `&&`, `== !=`, `< > <= >=`, `::`, `+ -`, `* /`, unary.

### Declarations

```
declaration ::= val x = expr
              | type T = type
              | def f(x: type, ..., x: type): type = expr
              | def f[a, ..., a](x: type, ..., x: type): type = expr
              | enum T[a, ..., a] { (case C(x: type, ..., x: type))+ }
```

### Types

```
type ::= Int | Double | String | Bool
       | List[type]
       | type => type                                -- function type (right-associative)
       | (type, ..., type)                          -- tuple type
       | T                                          -- named type
       | a                                          -- type variable
```

### Example program

```scala
enum Tree[a] {
  case Leaf(value: a)
  case Node(left: Tree, right: Tree)
}

def depth(t: Tree): Int =
  t match {
    case Leaf(v) => 0
    case Node(l, r) => 1 + depth(l)
  }

def mapList(f: Int => Int, xs: List[Int]): List[Int] =
  xs match {
    case Nil() => Nil
    case Cons(h, t) => f(h) :: mapList(f, t)
  }

depth(Node(Leaf(1), Leaf(2)))
```

> **Parser note:** `match` and `enum` bodies require braces in the current implementation.
> Line comments (`//`) are supported and ignored by the parser.

## Repository layout

```
parser/             -- chocola.parser: AST + fastparse 3 parser
dialects/           -- chocola.* types and operations
lowering/           -- ChocoLa-to-Builtin/Func/SCF/Arith passes
opt/                -- chocola.main: ScaIR-compatible optimizer CLI
tests/filecheck/    -- lit + FileCheck regression tests
```

## CI

Two workflows run on every push and pull request:
- **Format** — `./mill checkFormat` (scalafix + scalafmt)
- **Tests** — `./mill filechecks.run` (lit + FileCheck)
