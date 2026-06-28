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
  case Lit(value: Literal)
  case Var(name: String)
  case UnOp(op: String, expr: Expr)
  case BinOp(op: String, left: Expr, right: Expr)
  case Apply(fun: Expr, args: List[Expr])
  case If(cond: Expr, thenBranch: Expr, elseBranch: Expr)
  case Construct(name: String, args: List[Expr])
  case Match(scrutinee: Expr, cases: List[MatchCase])
  case Lambda(params: List[(String, Type)], body: Expr)
  case Tuple(elems: List[Expr])

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
