# Beginner-friendly diagnostics: contributor draft

This contributor draft explores how ChocoLa could help students learn
functional programming in a Scala-like language through approachable error
messages. Its audience is students using the compiler, complementing the
project's existing focus on students implementing compiler passes.

The suggested messages are design ideas, not descriptions of current compiler
output or committed implementation work. Scala examples and diagnostics below
are comparison material supplied by the contributor; they have not been
revalidated against a particular Scala version or compilation context.
The [language specification](docs/_docs/spec.md) remains authoritative for
ChocoLa syntax and typing. This draft does not add language features.

## Current diagnostic behavior

The parser currently reports `Parse error: expected ...`, with a source
location, source line, and caret. Type inference reports located
`Type error: ...` messages, often generic type mismatches using internal type
names. Some checks already report specific problems such as constructor or
function argument counts. Source-generation errors use a located `error:`
message.

Normal compilation fails on frontend errors and emits no IR for invalid input.
`--parsing-diagnostics` reports those errors on stdout and exits successfully
for diagnostic tests. `--dump-ast` runs type inference and fails on parsing or
typing errors unless diagnostic mode is selected.

The contextual explanations below remain proposals. Some would require
recognizing malformed syntax or likely mistakes before a specific explanation
can be produced, rather than simply changing an existing message.
Current output is defined by the implementation and its regression tests in
`tests/filecheck/`; these examples are not exact-output expectations.

## Reading the examples

Examples retain the contributor's Scala source, including deliberate mistakes.
`Boolean`, `import Position.*`, and `List.fill` are Scala comparison material
outside the ChocoLa syntax and built-ins described by the specification.
The constructor examples also illustrate Scala forms that ChocoLa's parser
does not currently accept. Proposed messages describe the intended teaching
experience, without promising support for those forms.

## Operators

### Type-incorrect uses of operators

```
"Hello" / 2

```

Contributor-supplied Scala diagnostic:

```
value / is not a member of String,
but could be made available as an extension method.

```

Proposed ChocoLa diagnostic:

```
The operator `/` requires two arguments of the same numeric type (Int or Double),
but it is passed "Hello" of type String as the first argument.

```

### Equality vs. assignment

Scala comparison input (`Boolean` is retained from the contributor draft):

```
def is0(x: Int): Boolean =
  x = 0

```

Contributor-supplied Scala diagnostic:

```
Reassignment to val x.
Maybe you meant to write an equality test using `==`?

```

Proposed ChocoLa diagnostic:

```
Equality test is written using the `==` operator.

```

## Conditionals

### Conditionals with branches of different types

```
if true then 42 else "hi"

```

Contributor-supplied Scala comparison: no error reported.

Proposed ChocoLa diagnostic:

```
The `then` branch has type Int, whereas the `else` branch has type `String`.
These branches must have the same type.

```

### Conditionals missing an else branch

```
def abs(x: Int): Int =
  if x < 0 then -x

```

Contributor-supplied Scala diagnostic:

```
Found:    Unit
Required: Int

Maybe you are missing an else part for the conditional?

```

Proposed ChocoLa diagnostic:

```
An `if` expression must have an `else` branch.

```

## Data type constructors

Scala comparison setup, including the contributor's preference about imports:

```
enum Position:
  case Pos(x: Double, y: Double)

import Position.*  // <- do not want to explicitly write this

```

### Data type value missing arguments

```
val p = Pos

```

Contributor-supplied Scala comparison: no error reported.

Proposed ChocoLa diagnostic:

```
Constructor Pos requires two arguments,
but it is passed zero arguments.

```

### Data type pattern missing arguments

```
def moveR(p: Position): Position =
  p match
    case Pos => Pos(x, y)

```

Contributor-supplied Scala diagnostic:

```
Not found: x
Not found: y

```

Proposed ChocoLa diagnostic:

```
Constructor Pos requires two arguments, but it is supplied zero arguments.

```

## Function definition

### Missing input type

```
def is0(x): Int =
  x == 0

```

Contributor-supplied Scala diagnostic:

```
`:` expected but `)` found
':' expected, but ')' found

```

Proposed ChocoLa diagnostic:

```
The type of argument `x` is missing.

```

### Missing equal

Scala comparison input (`Boolean` is retained from the contributor draft):

```
def is0(x: Int): Boolean
  x = 0

```

Contributor-supplied Scala diagnostic:

```
Declaration of method is0 not allowed here: only classes can have declared but undefined members

Not found: x

```

Proposed ChocoLa diagnostic:

```
A function signature must be followed by a `=` symbol.

```

## Type alias

Scala comparison input (`List.fill` is not a ChocoLa built-in):

```
type Row = List[Int]
type Board = List[Row]

def initBoard(n: Int): Board =
  List.fill(n)(0)

```

Contributor-supplied Scala diagnostic:

```
Found:    (0 : Int)
Required: List[Int]

```

Proposed ChocoLa diagnostic:

```
Found:    (0 : Int)
Required: Row (which is defined as List[Int])

```

Alternative proposed ChocoLa diagnostic:

```
Found:    Row (which is defined as List[Int])
Required: Board (which is defined as List[List[Int]])

```
