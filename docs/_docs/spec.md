---
title: ChocoLa specification
extraJS:
  - js/math.js
---

# ChocoLa specification

This document defines the syntax and typing rules of ChocoLa.

## Syntax

```math
\begin{aligned}
e ::= \
&\ lit \\
&\mid uop \ e \\
&\mid e \ bop \ e \\
&\mid x \\
&\mid e (e_1, \cdots, e_n) \\
&\mid \mathrm{if} \ e \ \mathrm{then} \ e \ \mathrm{else} \ e \\
&\mid C_i(e_1, \dots ,e_{n_i}) \\
&\mid e \ \mathrm{match} \\
&\quad (\mathrm{case} \ C_i(x_1, \dots, x_{n_i}) \Rightarrow e)^+ \\
&\mid (x_1 \colon \, \tau_1, \cdots, x_n \colon \, \tau_n) \Rightarrow e \\
&\mid (e_1, \cdots, e_n) \\

lit ::= \
&\ \mathrm{INTEGER} \mid \mathrm{DOUBLE} \mid \mathrm{STRING} \mid \mathrm{true} \mid \mathrm{false} \mid \mathrm{Nil} \mid prim \\

prim ::= \
&\ \mathrm{map} \mid \mathrm{filter} \mid \mathrm{foldLeft} \mid \mathrm{foldRight} \\

uop ::= \
&\ - \mid \ ! \\

bop ::= \
&\ + \mid - \mid * \mid / \\
&\mid \mathrm{<} \mid \mathrm{>} \mid \mathrm{<=} \mid \mathrm{>=} \mid \mathrm{==} \mid \mathrm{!=} \\
&\mid \mathrm{\&\&} \mid \mathrm{||} \\
&\mid \mathrm{::} \\

declaration ::= \
&\ \mathrm{val} \ x = e \\
&\mid \mathrm{val} \ x \colon \, \tau = e \\
&\mid \mathrm{type} \ T = \tau \\
&\mid \mathrm{def} \ f(x_1 \colon \, \tau_1, \cdots, x_n \colon \, \tau_n) \colon \, \tau = e \\
&\mid \mathrm{def} \ f[\alpha_1, \cdots, \alpha_m](x_1 \colon \, \tau_1, \cdots, x_n \colon \, \tau_n) \colon \, \tau = e\\
&\mid \mathrm{enum} \ T[\alpha_1, \cdots, \alpha_m] \colon   \\
&\quad (\mathrm{case} \ C_i(x_1 \colon \, \tau_1, \dots , x_{n_i} \colon \, \tau_{n_i}))^+\\

\tau ::= \
&\ \mathrm{Int} \mid \mathrm{Double} \mid \mathrm{String} \mid \mathrm{Bool} \mid \mathrm{List} [\tau] \mid (\tau_1, \cdots, \tau_n) \to \tau \mid (\tau_1, \cdots, \tau_k) \mid T \mid T[\tau_1, \cdots, \tau_m] \mid \alpha
\end{aligned}
```

A function type `(τ1, ..., τn) => τ` has one value-parameter list, with
`n ≥ 0`; `=>` is written as `\to` in the typing rules. A tuple type
`(τ1, ..., τk)` has `k ≥ 2`. In a function type, the parentheses immediately
before the arrow denote the parameter list: `(Int, Int) => Int` takes two
arguments, whereas `((Int, Int)) => Int` takes one tuple argument. A unary
function may also be written `Int => Int`, meaning `(Int) => Int`.
`()` is an empty parameter or argument list, not an `Int` value or a tuple.
`T[τ1, ..., τm]` applies a named enum type to exactly its declared number of
type arguments; a non-generic named type is written `T`.

Functions and lambdas have exactly one value-parameter list. A generic `def`
has at most one type-parameter list. Function results may themselves be
functions: `makeAdder(1)(2)` first calls `makeAdder` with one argument, then
calls its returned function with one argument. Multiple lists in a declaration
and partial application are excluded. The type `() => Int` denotes a function
that must be called with `()` and is distinct from `Int`.

Enum declarations use `enum T[a]:` followed by indented `case` declarations;
non-generic enums omit `[a]`. Match cases likewise follow `expression match`
on indented lines. Brace-delimited enum and match bodies are excluded.

## Type Checking

### Value Reference

All expression judgments use the global type environment `Δ` implicitly.
T-ValueRef applies to monomorphic bindings. For a binding with scheme
`∀α1, ..., αm. τ`, a reference may have type `τ[α1 := σ1, ..., αm := σm]`
for well-formed types `σ1, ..., σm`. Each reference may choose different type
arguments. A generic function body must be valid for every choice of its
own declared type parameters.

```math
\frac{
  x : \tau \in \Gamma
}{
  \Gamma \vdash x : \tau
} \quad \text{(T-ValueRef)}
```

### Arithmetic

```math
\begin{gathered}
\frac{

}{
  \Gamma \vdash n : Int
} \quad \text{(T-Int)} \qquad \frac{

}{
  \Gamma \vdash n : Double
} \quad \text{(T-Double)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e : \tau \\
  \tau \in \{\mathrm{Int}, \mathrm{Double}\}\end{gathered}
}{
  \Gamma \vdash - e : \tau
} \quad \text{(T-ArithNeg)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{Int}, \mathrm{Double}\}\end{gathered}
}{
  \Gamma \vdash e_1 + e_2 : \tau
} \quad \text{(T-Add)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{Int}, \mathrm{Double}\}\end{gathered}
}{
  \Gamma \vdash e_1 - e_2 : \tau
} \quad \text{(T-Sub)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{Int}, \mathrm{Double}\}\end{gathered}
}{
  \Gamma \vdash e_1 * e_2 : \tau
} \quad \text{(T-Mul)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{Int}, \mathrm{Double}\}\end{gathered}
}{
  \Gamma \vdash e_1 / e_2 : \tau
} \quad \text{(T-Div)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{Int}, \mathrm{Double}\}\end{gathered}
}{
  \Gamma \vdash e_1 < e_2 : \mathrm{Bool}
} \quad \text{(T-LT)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{Int}, \mathrm{Double}\}\end{gathered}
}{
  \Gamma \vdash e_1 > e_2 : \mathrm{Bool}
} \quad \text{(T-GT)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{Int}, \mathrm{Double}\}\end{gathered}
}{
  \Gamma \vdash e_1 <= e_2 : \mathrm{Bool}
} \quad \text{(T-LTEQ)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{Int}, \mathrm{Double}\}\end{gathered}
}{
  \Gamma \vdash e_1 >= e_2 : \mathrm{Bool}
} \quad \text{(T-GTEQ)}
\end{gathered}
```

### Strings

```math
\frac{

}{
  \Gamma \vdash s : String
} \quad \text{(T-String)}
```

### Boolean Logic

```math
\begin{gathered}
\frac{

}{
  \Gamma \vdash true : Bool
} \quad \text{(T-True)} \qquad \frac{

}{
  \Gamma \vdash false : Bool
} \quad \text{(T-False)} \\[1.5em]
\frac{
  \Gamma \vdash e : Bool
}{
  \Gamma \vdash ! e : Bool
} \quad \text{(T-BoolNeg)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : Bool \\
  \Gamma \vdash e_2: Bool\end{gathered}
}{
  \Gamma \vdash e_1  \&\&  e_2 : Bool
} \quad \text{(T-And)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e_1 : Bool \\
  \Gamma \vdash e_2: Bool\end{gathered}
}{
  \Gamma \vdash e_1  ||  e_2 : Bool
} \quad \text{(T-Or)}
\end{gathered}
```

### Equality Comparison

```math
\frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau\end{gathered}
}{
  \Gamma \vdash e_1 == e_2 : Bool
} \quad \text{(T-EQ)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau\end{gathered}
}{
  \Gamma \vdash e_1  !=  e_2 : Bool
} \quad \text{(T-NEQ)}
```

### Tuples

```math
\frac{
  \Gamma \vdash e_i : \tau_i \quad (1 \le i \le n)
}{
  \Gamma \vdash (e_1,\dots,e_n) : (\tau_1,\dots,\tau_n)
} \quad \text{(T-Tuple)}
```

### Lists

```math
\frac{

}{
  \Gamma \vdash Nil : List[\tau]
} \quad \text{(T-Nil)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: List[\tau]\end{gathered}
}{
  \Gamma \vdash e_1 :: e_2 : List[\tau]
} \quad \text{(T-Cons)}
```

### Control Flow

```math
\frac{
  \begin{gathered}\Gamma \vdash e : Bool \\
  \Gamma \vdash e_1: \tau \\
  \Gamma \vdash e_2: \tau\end{gathered}
}{
  \Gamma \vdash if \hspace{0.3em} e \hspace{0.3em} then \hspace{0.3em} e_1 \hspace{0.3em} else \hspace{0.3em} e_2 : \tau
} \quad \text{(T-If)}
```

### Functions

```math
\begin{gathered}
\frac{
  \Gamma, x_1:\tau_1, \dots, x_n:\tau_n \vdash e : \tau
}{
  \Gamma \vdash (x_1:\tau_1,\dots,x_n:\tau_n) \Rightarrow e \;:\; (\tau_1,\dots,\tau_n) \to \tau
} \quad \text{(T-Lambda)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e : (\tau_1,\dots,\tau_n) \to \tau \\
    \Gamma \vdash e_i : \tau_i \quad (1 \le i \le n)\end{gathered}
}{
  \Gamma \vdash e(e_1,\dots,e_n) : \tau
} \quad \text{(T-Apply)}
\end{gathered}
```

Calls must supply exactly `n` arguments in T-Apply, including when `n = 0`.
This applies equally to named functions, lambdas, and function-valued variables.
Thus `add(1)` is invalid for a two-parameter `add`, and `1()` is invalid because
its callee has no function type.

### Primitives

The call forms are `map(f, xs)`, `filter(xs, predicate)`,
`foldLeft(xs, initial, step)`, and `foldRight(xs, initial, step)`.
A fold step has two parameters in one list; its element and accumulator types
need not be equal. Different primitive references may use different types
for their quantified parameters.

```math
\begin{gathered}
\frac{

}{
  \Gamma \vdash \mathrm{map} : \forall A, B.\ ((A) \to B, \mathrm{List}[A]) \to \mathrm{List}[B]
} \quad \text{(T-Map)} \\[1.5em]
\frac{

}{
  \Gamma \vdash \mathrm{filter} : \forall A.\ (\mathrm{List}[A], (A) \to \mathrm{Bool}) \to \mathrm{List}[A]
} \quad \text{(T-Filter)} \\[1.5em]
\frac{

}{
  \Gamma \vdash \mathrm{foldLeft} : \forall A, B.\ (\mathrm{List}[A], B, (B, A) \to B) \to B
} \quad \text{(T-FoldLeft)} \\[1.5em]
\frac{

}{
  \Gamma \vdash \mathrm{foldRight} : \forall A, B.\ (\mathrm{List}[A], B, (A, B) \to B) \to B
} \quad \text{(T-FoldRight)}
\end{gathered}
```

### Constructor

```math
\frac{
  \begin{gathered}\Gamma \vdash C_i : (\tau_1,\dots,\tau_{n_i}) \to T[\alpha_1,\dots,\alpha_m] \\
  \Gamma \vdash e_j : \tau_j[\bar\alpha := \bar\sigma] \quad (1 \le j \le n_i)\end{gathered}
}{
  \Gamma \vdash C_i(e_1,\dots,e_{n_i}) : T[\sigma_1,\dots,\sigma_m]
} \quad \text{(T-ConstrApply)}
```

### Match

```math
\frac{
  \begin{gathered}\Gamma \vdash e : T[\sigma_1,\dots,\sigma_m] \\
      \big( \Gamma \vdash C_i : (\tau_{i,1},\dots,\tau_{i,n_i}) \to T[\alpha_1,\dots,\alpha_m] \big)^+ \\
      \big( \Gamma, x_1 : \tau_{i,1}[\bar\alpha := \bar\sigma], \dots, x_{n_i} : \tau_{i,n_i}[\bar\alpha := \bar\sigma] \vdash e_i : \tau \big)^+ \\
      \{C_1,\dots\} = \mathrm{constructors}(T)\end{gathered}
}{
  \Gamma \vdash e \ \mathrm{match} \ (\mathrm{case} \ C_i(x_1,\dots,x_{n_i}) \Rightarrow e_i)^+ : \tau
} \quad \text{(T-Match)}
```

Every constructor of the scrutinee's enum must occur exactly once. Constructors
from other enums, duplicate cases, and patterns with the wrong number of
bindings are type errors. Constructor applications also require exact field
arity. Type arguments remain part of the enum type: constructing `MkBox(7)`
for an enum declared as follows produces `Box[Int]`, and matching it binds
its field as `Int`:

```scala
enum Box[a]:
  case MkBox(value: a)
```

No erasure or implicit widening is performed.

### Declaration scope and well-formedness

Let `Δ` contain all top-level alias and enum names and their arities, and let
`G` contain the primitives, all top-level function signatures, and all enum
constructor schemes. These global declarations are visible regardless of
source order. A generic function signature is a scheme
`∀α1, ..., αm. (τ1, ..., τn) → τ`; constructor schemes quantify the owning enum's
parameters. An ordinary function signature is monomorphic.

Every declaration must be well formed, including unused declarations. The
judgment `Δ; A ⊢ τ wf` means that a type is well formed with the type parameters
in `A` in scope. Built-in types are well formed; a list, tuple, or function
type is well formed when all its component types are well formed. A named
type must belong to `Δ` and have exactly its declared number of arguments.
A type parameter must belong to `A`. Aliases have no type parameters in this
grammar; their right-hand sides are checked with `A = ∅`. Function annotations
use that function's declared type parameters; enum fields use the owning enum's
parameters.

Aliases are transparent: an alias and the type it denotes are equivalent.
The dependency relation between alias definitions must be acyclic, including
references within list elements, tuple components, function types, and enum
type arguments. Enum types are nominal; their definitions are not expanded
for type equivalence. Self-recursive and mutually recursive enum fields are
permitted and do not themselves constitute alias cycles.

For declaration `d_i`, define `V_<i` as the monomorphic types of the preceding
top-level `val` bindings, and `Γ_i = G, V_<i`. A function body is checked in
`Γ_i` extended with its own rigid type parameters and value parameters. Its
own signature and every other function signature are present in `G`;
self-recursion, mutual recursion, and forward function references are allowed.
A function may capture preceding top-level values, but no later value. A
`val` initializer is checked in `Γ_i`, without binding the value being defined.
Its inferred type is added only for subsequent declarations. There are no
recursive value bindings.

Top-level value initializers are evaluated in source order. The visibility
of a function signature does not initialize any values captured by that function.

### Declarations

In the following judgments `Δ` and `G` are the global environments defined
above. Function, alias, and enum declarations do not change ordered value
visibility; T-ValDecl and T-ValDeclAnn extend `V_<i`.

```math
\begin{gathered}
\frac{
  \Gamma_i \vdash e : \tau
}{
  \Gamma_i \vdash \mathrm{val} \ x = e \dashv \Gamma_i, x : \tau
} \quad \text{(T-ValDecl)} \\[1.5em]
\frac{
  \begin{gathered}
    \Delta; \varnothing \vdash \tau\ \mathrm{wf} \\
    \Gamma_i \vdash e : \tau
  \end{gathered}
}{
  \Gamma_i \vdash \mathrm{val} \ x \colon \tau = e \dashv \Gamma_i, x : \tau
} \quad \text{(T-ValDeclAnn)} \\[1.5em]
\frac{
  \Delta; \varnothing \vdash \tau\ \mathrm{wf}
}{
  \Gamma_i \vdash \mathrm{type} \ T = \tau \dashv \Gamma_i
} \quad \text{(T-TypeDecl)} \\[1.5em]
\frac{
  \begin{gathered}
    \Delta; \varnothing \vdash \tau_1,\dots,\tau_n,\tau\ \mathrm{wf} \\
    \Gamma_i, x_1 : \tau_1, \dots, x_n : \tau_n \vdash e : \tau
  \end{gathered}
}{
  \Gamma_i \vdash \mathrm{def} \ f(x_1\colon \tau_1, \dots, x_n \colon \tau_n) \colon \tau = e \dashv \Gamma_i
} \quad \text{(T-DefDecl)} \\[1.5em]
\frac{
  \begin{gathered}
    \Delta; \{\alpha_1,\dots,\alpha_m\} \vdash \tau_1,\dots,\tau_n,\tau\ \mathrm{wf} \\
    \Gamma_i, \alpha_1, \dots, \alpha_m, x_1 : \tau_1, \dots, x_n : \tau_n \vdash e : \tau
  \end{gathered}
}{
  \Gamma_i \vdash \mathrm{def} \ f[\alpha_1,\dots,\alpha_m](x_1\colon \tau_1, \dots, x_n \colon \tau_n) \colon \tau = e \dashv \Gamma_i
} \quad \text{(T-DefDeclPoly)} \\[1.5em]
\frac{
  \Delta; \{\alpha_1,\dots,\alpha_m\} \vdash \tau_{i,j}\ \mathrm{wf}
  \quad (\text{every constructor } i \text{ and field } j)
}{
  \begin{gathered}
    \Gamma_i \vdash \mathrm{enum} \ T[\alpha_1,\dots,\alpha_m] \colon\
    (\mathrm{case}\ C_i(x_1\colon\tau_{i,1},\dots,x_{n_i}\colon\tau_{i,n_i}))^+ \\
    \dashv \Gamma_i
  \end{gathered}
} \quad \text{(T-EnumDecl)}
\end{gathered}
```

### Inferred types and polymorphism

An ordinary `val` has one monomorphic type shared by all its uses. Its type
may be inferred from its initializer, an explicit annotation, and the
requirements imposed by its uses. It is not implicitly generalized into a
polymorphic type scheme. Lambdas are also monomorphic; polymorphism is
provided by generic `def` declarations and the primitive and constructor
schemes.

An annotated binding `val x: τ = e` requires a well-formed annotation and an
initializer of that type. An unannotated binding requires an inferred type
consistent with its initializer and all uses. Only finite types are permitted.
Types must agree structurally, up to alias equivalence, including function
parameter counts and named type arguments. There is no implicit numeric or
enum widening, bottom type, or list covariance. Conditional and match branches
must agree on a result type.

A program is ill typed if any inferred type remains undetermined by these
requirements, including types of unused bindings and inferred type arguments.
Declared type parameters within generic definitions are valid symbolic types;
they are not ambiguous merely because they are not concrete types.

For example, both list element types are determined as `Int` in:

```scala
val xs = Nil
val ys = 1 :: xs
```

In contrast, an isolated `val xs = Nil` is ambiguous and is rejected.
`val xs: List[Int] = Nil` determines the element type even when the binding is
unused. One binding cannot be used as both `List[Int]` and `List[String]`.

A program with a type or declaration well-formedness error must be rejected
by the compiler.

## Example program

```scala
enum Tree[a]:
  case Leaf(value: a)
  case Node(left: Tree[a], right: Tree[a])

def depth[a](t: Tree[a]): Int =
  t match
    case Leaf(v) => 0
    case Node(l, r) => 1 + depth(l)

def makeAdder(x: Int): (Int) => Int =
  (y: Int) => x + y

def answer(): Int = 42

val tree = Node(Leaf(1), Leaf(2))
val treeDepth = depth(tree)
val incremented = makeAdder(1)(2)
val result = answer()
```
