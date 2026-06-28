package chocola.dialects

import chocola.parser.Decl
import chocola.parser.Decl.*
import chocola.parser.EnumCase
import chocola.parser.Expr
import chocola.parser.Literal
import chocola.parser.MatchCase
import chocola.parser.Program
import chocola.parser.Type
import scair.dialects.builtin.StringData
import scair.ir.*

import scala.collection.mutable

// ---------------------------------------------------------------------------
// Lowering context
// ---------------------------------------------------------------------------

class LoweringContext(startEnv: Map[String, Value[Attribute]] = Map.empty):
  var env: Map[String, Value[Attribute]] = startEnv
  val ops: mutable.ListBuffer[Operation] = mutable.ListBuffer.empty

  def emit(op: Operation): Unit = ops += op
  def bind(name: String, v: Value[Attribute]): Unit = env = env + (name -> v)
  def lookup(name: String): Value[Attribute] = env(name)

  def childCtx(): LoweringContext = LoweringContext(env)

// ---------------------------------------------------------------------------
// Top-level entry point
// ---------------------------------------------------------------------------

def lowerProgram(program: Program): Seq[Operation] =
  val ctx = LoweringContext()
  program.decls.foreach(lowerDecl(_, ctx))
  program.expr.foreach(lowerExpr(_, ctx))
  ctx.ops.toSeq

// ---------------------------------------------------------------------------
// Declaration lowering
// ---------------------------------------------------------------------------

def lowerDecl(decl: Decl, ctx: LoweringContext): Unit =
  decl match
    case ValDecl(name, body) =>
      val v = lowerExpr(body, ctx)
      ctx.bind(name, v)

    case DefDecl(name, _, params, _, body) =>
      val funcResult = Result(unknown)
      val paramTypes: Seq[Attribute] = params.map(_ => unknown)
      val bodyBlock = Block(
        paramTypes,
        (args: Iterable[Value[Attribute]]) =>
          val childCtx = ctx.childCtx()
          params.map(_._1).zip(args).foreach { case (n, v) =>
            childCtx.bind(n, v)
          }
          val retVal = lowerExpr(body, childCtx)
          childCtx.emit(ReturnOp(retVal))
          childCtx.ops.toSeq,
      )
      ctx.emit(FuncDeclOp(StringData(name), funcResult, Region(bodyBlock)))
      ctx.bind(name, funcResult)

    case TypeDecl(name, tpe) =>
      ctx.emit(TypeAliasDeclOp(StringData(name), StringData(tpe.toString)))

    case EnumDecl(name, typeParams, cases) =>
      val caseOps: Seq[Operation] = cases.map {
        case EnumCase(caseName, fields) =>
          val fieldsStr = fields.map { case (n, t) => s"$n: $t" }.mkString(", ")
          EnumCaseDeclOp(StringData(caseName), StringData(fieldsStr))
      }
      val body = Region(Block(caseOps))
      ctx
        .emit(
          EnumDeclOp(
            StringData(name),
            StringData(typeParams.mkString(", ")),
            body,
          )
        )

// ---------------------------------------------------------------------------
// Expression lowering
// ---------------------------------------------------------------------------

def lowerExpr(expr: Expr, ctx: LoweringContext): Value[Attribute] =
  expr match

    case Expr.Lit(lit) =>
      val s = literalToString(lit)
      val result = Result(unknown)
      ctx.emit(ConstantOp(StringData(s), result))
      result

    case Expr.Var(name) =>
      ctx.lookup(name)

    case Expr.BinOp(op, l, r) =>
      val lv = lowerExpr(l, ctx)
      val rv = lowerExpr(r, ctx)
      val result = Result(unknown)
      val binOp = op match
        case "+"  => AddOp(lv, rv, result)
        case "-"  => SubOp(lv, rv, result)
        case "*"  => MulOp(lv, rv, result)
        case "/"  => DivOp(lv, rv, result)
        case "==" => EqOp(lv, rv, result)
        case "!=" => NeqOp(lv, rv, result)
        case "<"  => LtOp(lv, rv, result)
        case ">"  => GtOp(lv, rv, result)
        case "<=" => LeqOp(lv, rv, result)
        case ">=" => GeqOp(lv, rv, result)
        case "||" => OrOp(lv, rv, result)
        case "&&" => AndOp(lv, rv, result)
        case "::" => ConsOp(lv, rv, result)
        case _    =>
          throw IllegalArgumentException(s"Unknown binary operator: $op")
      ctx.emit(binOp)
      result

    case Expr.UnOp(op, e) =>
      val v = lowerExpr(e, ctx)
      val result = Result(unknown)
      val unOp = op match
        case "-" => NegOp(v, result)
        case "!" => NotOp(v, result)
        case _ => throw IllegalArgumentException(s"Unknown unary operator: $op")
      ctx.emit(unOp)
      result

    case Expr.Apply(f, args) =>
      val calleeVal = lowerExpr(f, ctx)
      val argVals = args.map(lowerExpr(_, ctx))
      val result = Result(unknown)
      ctx.emit(CallOp(calleeVal, argVals, result))
      result

    case Expr.Construct(name, args) =>
      val argVals = args.map(lowerExpr(_, ctx))
      val result = Result(unknown)
      ctx.emit(ConstructOp(StringData(name), argVals, result))
      result

    case Expr.Tuple(elems) =>
      val elemVals = elems.map(lowerExpr(_, ctx))
      val result = Result(unknown)
      ctx.emit(TupleOp(elemVals, result))
      result

    case Expr.If(cond, thenBranch, elseBranch) =>
      val condVal = lowerExpr(cond, ctx)
      val result = Result(unknown)
      val thenBlock = buildYieldingBlock(thenBranch, ctx)
      val elseBlock = buildYieldingBlock(elseBranch, ctx)
      ctx.emit(IfOp(condVal, result, Region(thenBlock), Region(elseBlock)))
      result

    case Expr.Lambda(params, body) =>
      val result = Result(unknown)
      val paramTypes: Seq[Attribute] = params.map(_ => unknown)
      val bodyBlock = Block(
        paramTypes,
        (args: Iterable[Value[Attribute]]) =>
          val childCtx = ctx.childCtx()
          params.map(_._1).zip(args).foreach { case (n, v) =>
            childCtx.bind(n, v)
          }
          val bodyVal = lowerExpr(body, childCtx)
          childCtx.emit(YieldOp(bodyVal))
          childCtx.ops.toSeq,
      )
      ctx.emit(LambdaOp(result, Region(bodyBlock)))
      result

    case Expr.Match(scrutinee, cases) =>
      val sv = lowerExpr(scrutinee, ctx)
      val result = Result(unknown)
      val caseOps: Seq[Operation] = cases.map {
        case MatchCase(ctor, bindings, body) =>
          val bindingTypes: Seq[Attribute] = bindings.map(_ => unknown)
          val bodyBlock = Block(
            bindingTypes,
            (bindingArgs: Iterable[Value[Attribute]]) =>
              val childCtx = ctx.childCtx()
              bindings.zip(bindingArgs).foreach { case (n, v) =>
                childCtx.bind(n, v)
              }
              val bodyVal = lowerExpr(body, childCtx)
              childCtx.emit(YieldOp(bodyVal))
              childCtx.ops.toSeq,
          )
          CaseOp(StringData(ctor), Region(bodyBlock))
      }
      ctx.emit(MatchOp(sv, result, Region(Block(caseOps))))
      result

private def buildYieldingBlock(expr: Expr, ctx: LoweringContext): Block =
  val childCtx = ctx.childCtx()
  val v = lowerExpr(expr, childCtx)
  childCtx.emit(YieldOp(v))
  Block(childCtx.ops.toSeq)

// ---------------------------------------------------------------------------
// Literal serialisation
// ---------------------------------------------------------------------------

private def literalToString(lit: Literal): String =
  lit match
    case Literal.IntLit(n)     => n.toString
    case Literal.DoubleLit(d)  => d.toString
    case Literal.StrLit(s)     => s
    case Literal.BoolLit(b)    => b.toString
    case Literal.NilLit        => "Nil"
    case Literal.MapPrim       => "map"
    case Literal.FilterPrim    => "filter"
    case Literal.FoldLeftPrim  => "foldLeft"
    case Literal.FoldRightPrim => "foldRight"
