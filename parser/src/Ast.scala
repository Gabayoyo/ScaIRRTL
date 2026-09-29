package chocola.parser

import scair.ir.FileLineColLoc
import scair.ir.FileLineColRange
import scair.ir.Location
import scair.ir.UnknownLoc

// ─── Source locations ────────────────────────────────────────────────────────

/** An AST node the parser can annotate with where it came from, mirroring
  * ScaIR's `Operation.location` / `Operation.at`.
  *
  * The location lives outside the constructor, so it does not take part in
  * pattern matching, equality or `toString`.
  */
trait Located:
  private var sourceLocation: Location = UnknownLoc

  final def location: Location = sourceLocation

  final def at(location: Location): this.type =
    sourceLocation = location
    this

/** Prefix `msg` with its location, in a format VS Code's terminal turns into a
  * link selecting the range: `file:line:col-endCol` on a single line,
  * `file:line:col-endLine.endCol` across lines (end column exclusive).
  */
def diagnostic(location: Location, msg: String): String =
  location match
    case UnknownLoc                      => msg
    case FileLineColLoc(file, line, col) => s"$file:$line:$col: $msg"
    case FileLineColRange(file, line, col, endLine, endCol) =>
      val end = if endLine == line then s"$endCol" else s"$endLine.$endCol"
      s"$file:$line:$col-$end: $msg"

enum Literal:
  case IntLit(n: Int)
  case DoubleLit(d: Double)
  case StrLit(s: String)
  case BoolLit(b: Boolean)
  case NilLit
  case MapPrim
  case FilterPrim
  case FoldLeftPrim
  case FoldRightPrim

enum Type:
  case TInt
  case TDouble
  case TString
  case TBool
  case TList(elem: Type)
  case TFun(from: Type, to: Type)
  case TTuple(elems: List[Type])
  case TName(name: String)

enum Expr extends Located:
  // Inferred type, set by TypeInferencer after parsing.
  def tpe: Option[Type]
  def tpe_=(value: Option[Type]): Unit

  case Lit(value: Literal, var tpe: Option[Type] = None)
  case Var(name: String, var tpe: Option[Type] = None)
  case UnOp(op: String, expr: Expr, var tpe: Option[Type] = None)
  case BinOp(op: String, left: Expr, right: Expr, var tpe: Option[Type] = None)
  case Apply(fun: Expr, args: List[Expr], var tpe: Option[Type] = None)

  case If(
      cond: Expr,
      thenBranch: Expr,
      elseBranch: Expr,
      var tpe: Option[Type] = None,
  )

  case Construct(name: String, args: List[Expr], var tpe: Option[Type] = None)

  case Match(
      scrutinee: Expr,
      cases: List[MatchCase],
      var tpe: Option[Type] = None,
  )

  case Lambda(
      params: List[(String, Type)],
      body: Expr,
      var tpe: Option[Type] = None,
  )

  case Tuple(elems: List[Expr], var tpe: Option[Type] = None)

case class MatchCase(ctor: String, bindings: List[String], body: Expr)
    extends Located

enum Decl extends Located:
  case ValDecl(name: String, body: Expr)
  case TypeDecl(name: String, tpe: Type)

  case DefDecl(
      name: String,
      typeParams: List[String],
      params: List[(String, Type)],
      returnType: Type,
      body: Expr,
  )

  case EnumDecl(name: String, typeParams: List[String], cases: List[EnumCase])

case class EnumCase(name: String, fields: List[(String, Type)]) extends Located

case class Program(decls: List[Decl], expr: Option[Expr])
