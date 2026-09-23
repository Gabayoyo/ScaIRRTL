package chocola.parser

import chocola.parser.Decl
import chocola.parser.Decl.*
import chocola.parser.EnumCase
import chocola.parser.Expr
import chocola.parser.Literal
import chocola.parser.MatchCase
import chocola.parser.Program
import chocola.dialects.*
import scair.dialects.builtin.{
  ArrayAttribute,
  FunctionType,
  ModuleOp,
  StringData,
  SymbolRefAttr,
}
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

private def asChocola(value: Value[Attribute]): Value[ChocolaType] =
  value.asInstanceOf[Value[ChocolaType]]

private def asOperand[T <: ChocolaType](
    value: Value[ChocolaType]
): Operand[T] = value.asInstanceOf[Operand[T]]

private def asResult[T <: ChocolaType](
    result: Result[ChocolaType]
): Result[T] = result.asInstanceOf[Result[T]]

// ---------------------------------------------------------------------------
// IR generation context
// ---------------------------------------------------------------------------

private class IrGenContext(
    startEnv: Map[String, Value[ChocolaType]] = Map.empty,
    startFunctions: Map[String, SymbolRefAttr] = Map.empty,
    startConstructors: Map[String, Seq[ChocolaType]] = Map.empty,
):
  var env: Map[String, Value[ChocolaType]] = startEnv
  var functions: Map[String, SymbolRefAttr] = startFunctions
  var constructors: Map[String, Seq[ChocolaType]] = startConstructors
  val ops: mutable.ListBuffer[Operation] = mutable.ListBuffer.empty

  def emit(op: Operation): Unit = ops += op
  def bind(name: String, v: Value[ChocolaType]): Unit = env = env + (name -> v)

  def bindFunction(name: String, sym: SymbolRefAttr): Unit =
    functions = functions + (name -> sym)

  def bindConstructor(name: String, fields: Seq[ChocolaType]): Unit =
    constructors = constructors + (name -> fields)

  def lookupLocal(name: String): Option[Value[ChocolaType]] = env.get(name)
  def lookupFunction(name: String): Option[SymbolRefAttr] = functions.get(name)

  def lookupConstructor(name: String): Option[Seq[ChocolaType]] =
    constructors.get(name)

  def childContext(): IrGenContext =
    IrGenContext(env, functions, constructors)

// ---------------------------------------------------------------------------
// Top-level entry point
// ---------------------------------------------------------------------------

def generateProgram(program: Program): ModuleOp =
  val ctx = IrGenContext()
  program.decls.foreach(generateDecl(_, ctx))
  program.expr.foreach(generateExpr(_, ctx))
  ModuleOp(Region(Block(ctx.ops.toSeq)))

// ---------------------------------------------------------------------------
// Declaration generation
// ---------------------------------------------------------------------------

private def generateDecl(decl: Decl, ctx: IrGenContext): Unit =
  decl match
    case ValDecl(name, body) =>
      val v = generateExpr(body, ctx)
      ctx.bind(name, v)

    case DefDecl(name, typeParams, params, retTy, body) =>
      val sym = symbolRef(name)
      // Make the symbol visible while constructing its own body so recursive
      // calls are represented symbolically rather than as an SSA cycle.
      ctx.bindFunction(name, sym)
      val paramTypes: Seq[ChocolaType] = params.map((_, t) => convertAstType(t))
      val resultType = convertAstType(retTy)
      val functionType = FunctionType(
        ArrayAttribute(paramTypes*),
        ArrayAttribute(resultType),
      )
      val bodyBlock = Block(
        paramTypes,
        (args: Iterable[Value[Attribute]]) =>
          val childCtx = ctx.childContext()
          params.map(_._1).zip(args).foreach { case (n, v) =>
            childCtx.bind(n, asChocola(v))
          }
          val retVal = generateExpr(body, childCtx)
          childCtx.emit(ReturnOp(retVal))
          childCtx.ops.toSeq,
      )
      ctx
        .emit(
          FuncDeclOp(
            sym,
            ArrayAttribute(typeParams.map(symbolRef)*),
            functionType,
            Region(bodyBlock),
          )
        )

    case TypeDecl(name, tpe) =>
      ctx.emit(TypeAliasDeclOp(symbolRef(name), convertAstType(tpe)))

    case EnumDecl(name, typeParams, cases) =>
      val caseOps: Seq[Operation] = cases.map {
        case EnumCase(caseName, fields) =>
          val bindingTypes =
            if typeParams.isEmpty then fields.map((_, t) => convertAstType(t))
            else fields.map(_ => unknown)
          ctx.bindConstructor(caseName, bindingTypes)
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

private def generateExpr(expr: Expr, ctx: IrGenContext): Value[ChocolaType] =
  expr match

    case Expr.Lit(lit, _) =>
      val s = literalToString(lit)
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      ctx.emit(ConstantOp(StringData(s), result))
      result

    case Expr.Var(name, _) =>
      ctx.lookupLocal(name) match
        case Some(value) => value
        case None        =>
          val result = Result(optionalAstTypeToAttr(expr.tpe))
          ctx
            .emit(
              FunctionRefOp(
                ctx.lookupFunction(name).get,
                asResult[FunType](result),
              )
            )
          result

    case Expr.BinOp(op, l, r, _) =>
      val lv = generateExpr(l, ctx)
      val rv = generateExpr(r, ctx)
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      val binOp = op match
        case "+" =>
          AddOp(
            asOperand[NumericType](lv),
            asOperand[NumericType](rv),
            asResult[NumericType](result),
          )
        case "-" =>
          SubOp(
            asOperand[NumericType](lv),
            asOperand[NumericType](rv),
            asResult[NumericType](result),
          )
        case "*" =>
          MulOp(
            asOperand[NumericType](lv),
            asOperand[NumericType](rv),
            asResult[NumericType](result),
          )
        case "/" =>
          DivOp(
            asOperand[NumericType](lv),
            asOperand[NumericType](rv),
            asResult[NumericType](result),
          )
        case "==" => EqOp(lv, rv, asResult[BoolType](result))
        case "!=" => NeqOp(lv, rv, asResult[BoolType](result))
        case "<"  =>
          LtOp(
            asOperand[NumericType](lv),
            asOperand[NumericType](rv),
            asResult[BoolType](result),
          )
        case ">" =>
          GtOp(
            asOperand[NumericType](lv),
            asOperand[NumericType](rv),
            asResult[BoolType](result),
          )
        case "<=" =>
          LeqOp(
            asOperand[NumericType](lv),
            asOperand[NumericType](rv),
            asResult[BoolType](result),
          )
        case ">=" =>
          GeqOp(
            asOperand[NumericType](lv),
            asOperand[NumericType](rv),
            asResult[BoolType](result),
          )
        case "||" =>
          OrOp(
            asOperand[BoolType](lv),
            asOperand[BoolType](rv),
            asResult[BoolType](result),
          )
        case "&&" =>
          AndOp(
            asOperand[BoolType](lv),
            asOperand[BoolType](rv),
            asResult[BoolType](result),
          )
        case "::" =>
          ConsOp(
            lv,
            asOperand[chocola.dialects.ListType](rv),
            asResult[chocola.dialects.ListType](result),
          )
        case _ =>
          throw IllegalArgumentException(s"Unknown binary operator: $op")
      ctx.emit(binOp)
      result

    case Expr.UnOp(op, e, _) =>
      val v = generateExpr(e, ctx)
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      val unOp = op match
        case "-" =>
          NegOp(
            asOperand[NumericType](v),
            asResult[NumericType](result),
          )
        case "!" =>
          NotOp(
            asOperand[BoolType](v),
            asResult[BoolType](result),
          )
        case _ => throw IllegalArgumentException(s"Unknown unary operator: $op")
      ctx.emit(unOp)
      result

    case Expr.Apply(f, args, _) =>
      f match
        case Expr.Var(name, _)
            if ctx.lookupLocal(name).isEmpty &&
              ctx.lookupFunction(name).isDefined =>
          val argVals = args.map(generateExpr(_, ctx))
          val result = Result(optionalAstTypeToAttr(expr.tpe))
          ctx.emit(DirectCallOp(ctx.lookupFunction(name).get, argVals, result))
          result
        case _ =>
          val calleeVal = generateExpr(f, ctx)
          val argVals = args.map(generateExpr(_, ctx))
          val result = Result(optionalAstTypeToAttr(expr.tpe))
          ctx
            .emit(
              CallOp(asOperand[FunType](calleeVal), argVals, result)
            )
          result

    case Expr.Construct(name, args, _) =>
      val argVals = args.map(generateExpr(_, ctx))
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      ctx
        .emit(
          ConstructOp(symbolRef(name), argVals, asResult[NamedType](result))
        )
      result

    case Expr.Tuple(elems, _) =>
      val elemVals = elems.map(generateExpr(_, ctx))
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      ctx.emit(TupleOp(elemVals, asResult[TupleType](result)))
      result

    case Expr.If(cond, thenBranch, elseBranch, _) =>
      val condVal = generateExpr(cond, ctx)
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      val thenBlock = buildYieldingBlock(thenBranch, ctx)
      val elseBlock = buildYieldingBlock(elseBranch, ctx)
      ctx
        .emit(
          IfOp(
            asOperand[BoolType](condVal),
            result,
            Region(thenBlock),
            Region(elseBlock),
          )
        )
      result

    case Expr.Lambda(params, body, _) =>
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      val paramTypes: Seq[ChocolaType] = params.map((_, t) => convertAstType(t))
      val bodyBlock = Block(
        paramTypes,
        (args: Iterable[Value[Attribute]]) =>
          val childCtx = ctx.childContext()
          params.map(_._1).zip(args).foreach { case (n, v) =>
            childCtx.bind(n, asChocola(v))
          }
          val bodyVal = generateExpr(body, childCtx)
          childCtx.emit(YieldOp(bodyVal))
          childCtx.ops.toSeq,
      )
      ctx.emit(LambdaOp(asResult[FunType](result), Region(bodyBlock)))
      result

    case Expr.Match(scrutinee, cases, _) =>
      val sv = generateExpr(scrutinee, ctx)
      val result = Result(optionalAstTypeToAttr(expr.tpe))
      val caseOps: Seq[Operation] = cases.map {
        case MatchCase(ctor, bindings, body) =>
          val bindingTypes = ctx.lookupConstructor(ctor)
            .getOrElse(bindings.map(_ => unknown))
          val bodyBlock = Block(
            bindingTypes,
            (bindingArgs: Iterable[Value[Attribute]]) =>
              val childCtx = ctx.childContext()
              bindings.zip(bindingArgs).foreach { case (n, v) =>
                childCtx.bind(n, asChocola(v))
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
