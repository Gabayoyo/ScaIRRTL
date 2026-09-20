package chocola.parser

import chocola.parser.Decl
import chocola.parser.Decl.*
import chocola.parser.EnumCase
import chocola.parser.Expr
import chocola.parser.Literal
import chocola.parser.MatchCase
import chocola.parser.Program
import chocola.dialects.*
import scair.dialects.builtin.{ArrayAttribute, StringData, SymbolRefAttr}
import scair.ir.*

import chocola.parser.Type as AstType

import scala.collection.mutable

// ---------------------------------------------------------------------------
// AST type → SCAIR attribute
// ---------------------------------------------------------------------------

private def convertAstType(t: AstType): ChocolaType =
  t match
    case AstType.TInt       => IntType()
    case AstType.TDouble    => DoubleType()
    case AstType.TString    => StringType()
    case AstType.TBool      => BoolType()
    case AstType.TList(e)   => chocola.dialects.ListType(convertAstType(e))
    case AstType.TFun(f, r) => FunType(convertAstType(f), convertAstType(r))
    case AstType.TTuple(es) =>
      TupleType(ArrayAttribute(es.map(convertAstType)*))
    case AstType.TName("_")  => unknown
    case AstType.TName(name) => NamedType(symbolRef(name))

private def optionalAstTypeToAttr(tpe: Option[AstType]): ChocolaType =
  tpe.fold[ChocolaType](unknown)(convertAstType)

private def symbolRef(name: String): SymbolRefAttr =
  SymbolRefAttr(StringData(name))

// ---------------------------------------------------------------------------
// IR generation context
// ---------------------------------------------------------------------------

private class IrGenContext(startEnv: Map[String, Value[Attribute]] = Map.empty):
  var env: Map[String, Value[Attribute]] = startEnv
  val ops: mutable.ListBuffer[Operation] = mutable.ListBuffer.empty

  def emit(op: Operation): Unit = ops += op
  def bind(name: String, v: Value[Attribute]): Unit = env = env + (name -> v)
  def lookup(name: String): Value[Attribute] = env(name)

  def childContext(): IrGenContext = IrGenContext(env)

// ---------------------------------------------------------------------------
// Top-level entry point
// ---------------------------------------------------------------------------

def generateProgram(program: Program): Seq[Operation] =
  val ctx = IrGenContext()
  program.decls.foreach(generateDecl(_, ctx))
  program.expr.foreach(generateExpr(_, ctx))
  ctx.ops.toSeq

// ---------------------------------------------------------------------------
// Declaration generation
// ---------------------------------------------------------------------------

private def generateDecl(decl: Decl, ctx: IrGenContext): Unit =
  decl match
    case ValDecl(name, body) =>
      val v = generateExpr(body, ctx)
      ctx.bind(name, v)

    case DefDecl(name, typeParams, params, retTy, body) =>
      val funcResult = Result(convertAstType(retTy))
      val paramTypes: Seq[Attribute] = params.map((_, t) => convertAstType(t))
      val bodyBlock = Block(
        paramTypes,
        (args: Iterable[Value[Attribute]]) =>
          val childCtx = ctx.childContext()
          params.map(_._1).zip(args).foreach { case (n, v) =>
            childCtx.bind(n, v)
          }
          val retVal = generateExpr(body, childCtx)
          childCtx.emit(ReturnOp(retVal))
          childCtx.ops.toSeq,
      )
      ctx
        .emit(
          FuncDeclOp(
            symbolRef(name),
            ArrayAttribute(typeParams.map(symbolRef)*),
            funcResult,
            Region(bodyBlock),
          )
        )
      ctx.bind(name, funcResult)

    case TypeDecl(name, tpe) =>
      ctx.emit(TypeAliasDeclOp(symbolRef(name), convertAstType(tpe)))

    case EnumDecl(name, typeParams, cases) =>
      val caseOps: Seq[Operation] = cases.map {
        case EnumCase(caseName, fields) =>
          val fieldAttrs = fields.map { case (fieldName, fieldType) =>
            EnumFieldAttr(symbolRef(fieldName), convertAstType(fieldType))
          }
          EnumCaseDeclOp(symbolRef(caseName), ArrayAttribute(fieldAttrs*))
      }
      val body = Region(Block(caseOps))
      ctx
        .emit(
          EnumDeclOp(
            symbolRef(name),
            ArrayAttribute(typeParams.map(symbolRef)*),
            body,
          )
        )

// ---------------------------------------------------------------------------
// Expression generation
// ---------------------------------------------------------------------------

private def generateExpr(expr: Expr, ctx: IrGenContext): Value[Attribute] =
  expr match

    case Expr.Lit(lit, _) =>
      val s = literalToString(lit)
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      ctx.emit(ConstantOp(StringData(s), result))
      result

    case Expr.Var(name, _) =>
      ctx.lookup(name)

    case Expr.BinOp(op, l, r, _) =>
      val lv = generateExpr(l, ctx)
      val rv = generateExpr(r, ctx)
      val result = Result(optionalAstTypeToAttr(expr.tpe))
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

    case Expr.UnOp(op, e, _) =>
      val v = generateExpr(e, ctx)
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      val unOp = op match
        case "-" => NegOp(v, result)
        case "!" => NotOp(v, result)
        case _ => throw IllegalArgumentException(s"Unknown unary operator: $op")
      ctx.emit(unOp)
      result

    case Expr.Apply(f, args, _) =>
      val calleeVal = generateExpr(f, ctx)
      val argVals = args.map(generateExpr(_, ctx))
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      ctx.emit(CallOp(calleeVal, argVals, result))
      result

    case Expr.Construct(name, args, _) =>
      val argVals = args.map(generateExpr(_, ctx))
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      ctx.emit(ConstructOp(symbolRef(name), argVals, result))
      result

    case Expr.Tuple(elems, _) =>
      val elemVals = elems.map(generateExpr(_, ctx))
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      ctx.emit(TupleOp(elemVals, result))
      result

    case Expr.If(cond, thenBranch, elseBranch, _) =>
      val condVal = generateExpr(cond, ctx)
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      val thenBlock = buildYieldingBlock(thenBranch, ctx)
      val elseBlock = buildYieldingBlock(elseBranch, ctx)
      ctx.emit(IfOp(condVal, result, Region(thenBlock), Region(elseBlock)))
      result

    case Expr.Lambda(params, body, _) =>
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      val paramTypes: Seq[Attribute] = params.map((_, t) => convertAstType(t))
      val bodyBlock = Block(
        paramTypes,
        (args: Iterable[Value[Attribute]]) =>
          val childCtx = ctx.childContext()
          params.map(_._1).zip(args).foreach { case (n, v) =>
            childCtx.bind(n, v)
          }
          val bodyVal = generateExpr(body, childCtx)
          childCtx.emit(YieldOp(bodyVal))
          childCtx.ops.toSeq,
      )
      ctx.emit(LambdaOp(result, Region(bodyBlock)))
      result

    case Expr.Match(scrutinee, cases, _) =>
      val sv = generateExpr(scrutinee, ctx)
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      val caseOps: Seq[Operation] = cases.map {
        case MatchCase(ctor, bindings, body) =>
          val bindingTypes: Seq[Attribute] = bindings.map(_ => unknown)
          val bodyBlock = Block(
            bindingTypes,
            (bindingArgs: Iterable[Value[Attribute]]) =>
              val childCtx = ctx.childContext()
              bindings.zip(bindingArgs).foreach { case (n, v) =>
                childCtx.bind(n, v)
              }
              val bodyVal = generateExpr(body, childCtx)
              childCtx.emit(YieldOp(bodyVal))
              childCtx.ops.toSeq,
          )
          CaseOp(symbolRef(ctor), Region(bodyBlock))
      }
      ctx.emit(MatchOp(sv, result, Region(Block(caseOps))))
      result

private def buildYieldingBlock(expr: Expr, ctx: IrGenContext): Block =
  val childCtx = ctx.childContext()
  val v = generateExpr(expr, childCtx)
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
