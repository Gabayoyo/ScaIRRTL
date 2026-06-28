# ChocoLaCompiler

Prototype compiler for an educational subset of Scala, written in Scala 3.
Intended as a starting point for students to re-implement individual compiler passes.

## Building and running

The repository ships a self-contained `./mill` wrapper — no global installation needed.

```sh
# Compile everything
./mill main.compile

# Parse a source file
./mill main.run -- path/to/file.cola

# Run lit + FileCheck tests
./mill filechecks.run

# Format all sources (scalafix + scalafmt)
./mill format

# Check formatting (also enforced in CI)
./mill checkFormat
```

`pip install lit filecheck` is required for `filechecks.run`.

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
main/               -- chocola.main: CLI entry point (CaseApp)
tests/filecheck/    -- lit + FileCheck regression tests
```

## CI

Two workflows run on every push and pull request:
- **Format** — `./mill checkFormat` (scalafix + scalafmt)
- **Tests** — `./mill filechecks.run` (lit + FileCheck)
