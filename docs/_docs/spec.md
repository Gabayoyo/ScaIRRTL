---
title: ChocoLa specification
extraJS:
  - js/math.js
---

# ChocoLa specification

<!-- Required for inserting images -->

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
&\mid \mathrm{type} \ T = \tau \\
&\mid \mathrm{def} \ f(x_1 \colon \, \tau_1, \cdots, x_n \colon \, \tau_n) \colon \, \tau = e \\
&\mid \mathrm{def} \ f[\alpha_1, \cdots, \alpha_m](x_1 \colon \, \tau_1, \cdots, x_n \colon \, \tau_n) \colon \, \tau = e\\
&\mid \mathrm{enum} \ T[\alpha_1, \cdots, \alpha_m] \colon   \\
&\quad (\mathrm{case} \ C_i(x_1 \colon \, \tau_1, \dots , x_{n_i} \colon \, \tau_{n_i}))^+\\

\tau ::= \
&\ \mathrm{int} \mid \mathrm{double} \mid \mathrm{String} \mid \mathrm{Bool} \mid \mathrm{List} [\tau] \mid \tau \to \tau \mid (\tau_1, \cdots, \tau_n) \mid T \mid \alpha
\end{aligned}
```

<!-- -->

<!-- -->

<!-- -->

<!-- -->

<!-- -->

<!-- -->

## Type Checking

### Value Reference

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
  \Gamma \vdash n : int
} \quad \text{(T-Int)} \qquad \frac{

}{
  \Gamma \vdash n : double
} \quad \text{(T-Double)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e : \tau \\
  \tau \in \{\mathrm{int}, \mathrm{double}\}\end{gathered}
}{
  \Gamma \vdash - e : \tau
} \quad \text{(T-ArithNeg)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{int}, \mathrm{double}\}\end{gathered}
}{
  \Gamma \vdash e_1 + e_2 : \tau
} \quad \text{(T-Add)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{int}, \mathrm{double}\}\end{gathered}
}{
  \Gamma \vdash e_1 - e_2 : \tau
} \quad \text{(T-Sub)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{int}, \mathrm{double}\}\end{gathered}
}{
  \Gamma \vdash e_1 * e_2 : \tau
} \quad \text{(T-Mul)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{int}, \mathrm{double}\}\end{gathered}
}{
  \Gamma \vdash e_1 / e_2 : \tau
} \quad \text{(T-Div)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{int}, \mathrm{double}\}\end{gathered}
}{
  \Gamma \vdash e_1 < e_2 : \tau
} \quad \text{(T-LT)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{int}, \mathrm{double}\}\end{gathered}
}{
  \Gamma \vdash e_1 > e_2 : \tau
} \quad \text{(T-GT)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{int}, \mathrm{double}\}\end{gathered}
}{
  \Gamma \vdash e_1 <= e_2 : \tau
} \quad \text{(T-LTEQ)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau \\
  \tau \in \{\mathrm{int}, \mathrm{double}\}\end{gathered}
}{
  \Gamma \vdash e_1 >= e_2 : \tau
} \quad \text{(T-GTEQ)}
\end{gathered}
```

### Strings

```math
\frac{

}{
  \Gamma \vdash s : string
} \quad \text{(T-String)}
```

### Boolean Logic

```math
\begin{gathered}
\frac{

}{
  \Gamma \vdash true : bool
} \quad \text{(T-True)} \qquad \frac{

}{
  \Gamma \vdash false : bool
} \quad \text{(T-False)} \\[1.5em]
\frac{
  \Gamma \vdash e : bool
}{
  \Gamma \vdash ! e : bool
} \quad \text{(T-BoolNeg)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : bool \\
  \Gamma \vdash e_2: bool\end{gathered}
}{
  \Gamma \vdash e_1  \&\&  e_2 : bool
} \quad \text{(T-And)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma \vdash e_1 : bool \\
  \Gamma \vdash e_2: bool\end{gathered}
}{
  \Gamma \vdash e_1  ||  e_2 : bool
} \quad \text{(T-Or)}
\end{gathered}
```

### Equality Comparison

```math
\frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau\end{gathered}
}{
  \Gamma \vdash e_1 == e_2 : bool
} \quad \text{(T-EQ)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: \tau\end{gathered}
}{
  \Gamma \vdash e_1  !=  e_2 : bool
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
  \Gamma \vdash Nil : \tau : List[\tau]
} \quad \text{(T-Nil)} \qquad \frac{
  \begin{gathered}\Gamma \vdash e_1 : \tau \\
  \Gamma \vdash e_2: list[\tau]\end{gathered}
}{
  \Gamma \vdash e_1 :: e_2 : list[\tau]
} \quad \text{(T-Cons)}
```

<!-- questionable - could add a bottom type to the type system -->

### Control Flow

```math
\frac{
  \begin{gathered}\Gamma \vdash e : bool \\
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

### Primitives

```math
\begin{gathered}
\frac{

}{
  \Gamma \vdash \mathrm{map} : \forall A, B.\ (A \to B) \to \mathrm{List}[A] \to \mathrm{List}[B]
} \quad \text{(T-Map)} \\[1.5em]
\frac{

}{
  \Gamma \vdash \mathrm{filter} : \forall A, B.\ \mathrm{List}[A] \to (A \to \mathrm{Bool}) \to \mathrm{List}[A]
} \quad \text{(T-Filter)} \\[1.5em]
\frac{

}{
  \Gamma \vdash \mathrm{foldLeft} : \forall A, B.\ \mathrm{List}[A] \to B \to ((B, A) \to B) \to B
} \quad \text{(T-FoldLeft)} \\[1.5em]
\frac{

}{
  \Gamma \vdash \mathrm{foldRight} : \forall A, B.\ \mathrm{List}[A] \to B \to ((A, B) \to B) \to B
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

<!-- this part can also be a warning rather than a full blow type error -->

### Declarations

```math
\begin{gathered}
\frac{
  \Gamma \vdash e : \tau
}{
  \Gamma \vdash \mathrm{val} \ x = e \dashv \Gamma, x : \tau
} \quad \text{(T-ValDecl)} \\[1.5em]
\frac{

}{
  \Gamma \vdash \mathrm{type} \ T = \tau \dashv \Gamma, T = \tau
} \quad \text{(T-TypeDecl)} \\[1.5em]
\frac{
  \Gamma, x_1 : \tau_1, \dots, x_n : \tau_n \vdash e : \tau
}{
  \Gamma \vdash \mathrm{def} \ f(x_1\colon \tau_1, \dots, x_n \colon \tau_n) \colon \tau = e \dashv \Gamma, f : (\tau_1,\dots,\tau_n) \to \tau
} \quad \text{(T-DefDecl)} \\[1.5em]
\frac{
  \Gamma, \alpha_1, \dots, \alpha_m, x_1 : \tau_1, \dots, x_n : \tau_n \vdash e : \tau
}{
  \Gamma \vdash \mathrm{def} \ f[\alpha_1,\dots,\alpha_m](x_1\colon \tau_1, \dots, x_n \colon \tau_n) \colon \tau = e \dashv \Gamma, f : \forall \alpha_1,\dots,\alpha_m.\ (\tau_1,\dots,\tau_n) \to \tau
} \quad \text{(T-DefDeclPoly)} \\[1.5em]
\frac{
  \begin{gathered}\Gamma' = \Gamma, \alpha_1, \dots, \alpha_m \\
    \Gamma'' = \Gamma', T[\alpha_1,\dots,\alpha_m] \\
    \big( \Gamma'' \vdash C_i : (\tau_{i,1}, \dots, \tau_{i,n_i}) \to T[\alpha_1,\dots,\alpha_m] \big)^{+}\end{gathered}
}{
  \begin{gathered}\Gamma \vdash \mathrm{enum} \ T[\alpha_1,\dots,\alpha_m] \colon \ (\mathrm{case} \ C_i(x_1\colon\tau_1,\dots,x_{n_i}\colon\tau_{n_i}))^+ \\ \dashv \Gamma, T[\alpha_1,\dots,\alpha_m], \big(C_i : (\tau_{i,1},\dots,\tau_{i,n_i}) \to T[\alpha_1,\dots,\alpha_m]\big)^+\end{gathered}
} \quad \text{(T-EnumDecl)}
\end{gathered}
```

### Existing Approaches for Defining Declarations

- The Rocq Reference Manual distinguishes between local contexts and global environments.
  [The Rocq Reference Manual](https://rocq-prover.org/doc/V9.2.0/refman/language/cic.html#id4)

- The Definition of Standard ML uses judgments of the form $`C \vdash decl \Rightarrow E`$, which reads “declaration `decl` produces environment `E` under context `C`”.
  [The Definition of Standard ML](https://smlfamily.github.io/sml97-defn.pdf) (page 26)

## Example program

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
