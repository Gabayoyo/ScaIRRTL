package chocola.parser

import scair.ir.FileLineColLoc
import scair.ir.FileLineColRange
import scair.ir.Location
import scair.ir.UnknownLoc

// The parser and type inferencer share one AST: parsing builds the syntax tree,
// then inference fills and resolves its mutable type annotations in place.
// This keeps IR generation self-contained without a separate typed tree or
// type lookup table. Generation requires successful inference and only reads
// the annotations; a parsed or partially inferred tree is not ready for IR.

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
  case TFun(params: List[Type], result: Type)
  case TTuple(elems: List[Type])
  // Surface names become nominal enum types after annotation validation.
  case TName(name: String, args: List[Type] = Nil)
  // Owner identities keep parameters of separate declarations rigid and distinct.
  case TParam(name: String, owner: Int)
  case TMeta(id: Int)
  // Internal recovery marker; never an accepted source or IR type.
  case TError

enum Expr extends Located:
  // Instantiated arguments are finalized with tpe, including phantom arguments.
  var typeArguments: List[Type] = Nil

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
    extends Located:
  // Types of the pattern bindings after substituting the scrutinee arguments.
  var bindingTypes: List[Type] = Nil

enum Decl extends Located:
  // Inference resolves aliases and records the value or function signature here.
  var resolvedType: Option[Type] = None

  case ValDecl(name: String, body: Expr, annotation: Option[Type] = None)
  case TypeDecl(name: String, tpe: Type)

  case DefDecl(
      name: String,
      typeParams: List[String],
      params: List[(String, Type)],
      returnType: Type,
      body: Expr,
  )

  case EnumDecl(name: String, typeParams: List[String], cases: List[EnumCase])

case class EnumCase(name: String, fields: List[(String, Type)]) extends Located:
  var fieldTypes: List[Type] = Nil

case class Program(decls: List[Decl], expr: Option[Expr])
