# Error Messages

## Operators

### Type-incorrect uses of operators
```
"Hello" / 2
```

Scala:
```
value / is not a member of String, 
but could be made available as an extension method.
```

Educational Scala:
```
The operator `/` requires two arguments of type Int,
but it is passed "Hello" of type String as the first argument.
```

### Equality vs. assignment
```
def is0(x: Int): Boolean =
  x = 0
```

Scala:
```
Reassignment to val x.
Maybe you meant to write an equality test using `==`?
```

Educational Scala:
```
Equality test is written using the `==` operator.
```

## Conditionals

### Conditionals with branches of different types
```
if true then 42 else "hi"
```

Scala: No error

Educational Scala:
```
The `then` branch has type Int, whereas the `else` branch has type `String`.
These branches must have the same type.
```

### Conditionals missing an else branch
```
def abs(x: Int): Int =
  if x < 0 then -x
```

Scala:
```
Found:    Unit
Required: Int

Maybe you are missing an else part for the conditional?
```

Educational Scala:
```
An `if` expression must have an `else` branch.
```

## Data type constructors

```
enum Position:
  case Pos(x: Double, y: Double)

import Position.*  // <- do not want to explicitly write this
```

### Data type value missing arguments
```
val p = Pos
```

Scala: No error

Educational Scala:
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

Scala:
```
Not found: x
Not found: y
```

Educational Scala:
```
Constructor Pos requires two arguments, but it is supplied zero arguments.
```

## Function definition

### Missing input type
```
def is0(x): Int =
  x == 0
```

Scala:
```
`:` expected but `)` found
':' expected, but ')' found
```

Educational Scala:
```
The type of argument `x` is missing.
```

### Missing equal
```
def is0(x: Int): Boolean
  x = 0
```

Scala:
```
Declaration of method is0 not allowed here: only classes can have declared but undefined members

Not found: x
```

Educational Scala:
```
A function signature must be followed by a `=` symbol.
```

## Type alias

```
type Row = List[Int]
type Board = List[Row]

def initBoard(n: Int): Board =
  List.fill(n)(0)
```

Scala: 
```
Found:    (0 : Int)
Required: List[Int]
```

Educational Scala:
```
Found:    (0 : Int)
Required: Row (which is defined as List[Int])
```

or

```
Found:    Row (which is defined as List[Int])
Required: Board (which is defined as List[List[Int]])
```