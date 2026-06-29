package chocola.parser

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

enum Expr:
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

enum Decl:
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

case class EnumCase(name: String, fields: List[(String, Type)])

case class Program(decls: List[Decl], expr: Option[Expr])
