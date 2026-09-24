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
// IR generation environment and results
// ---------------------------------------------------------------------------

/** Immutable scope: what names mean at a given point of the program. */
private final case class Env(
    locals: Map[String, Value[ChocolaType]] = Map.empty,
    functions: Map[String, SymbolRefAttr] = Map.empty,
    constructors: Map[String, Seq[ChocolaType]] = Map.empty,
):

  def bind(name: String, v: Value[ChocolaType]): Env =
    copy(locals = locals + (name -> v))

  def bindAll(names: Seq[String], vs: Iterable[Value[Attribute]]): Env =
    copy(locals = locals ++ names.zip(vs.map(asChocola)))

  def bindFunction(name: String, sym: SymbolRefAttr): Env =
    copy(functions = functions + (name -> sym))

  def bindConstructors(ctors: Seq[(String, Seq[ChocolaType])]): Env =
    copy(constructors = constructors ++ ctors)

/** The ops generated for an expression, and the SSA value holding its result.
  */
private final case class Generated(
    ops: Seq[Operation],
    value: Value[ChocolaType],
)

/** A single op producing `result`. */
private def single(op: Operation, result: Value[ChocolaType]): Generated =
  Generated(Seq(op), result)

/** Generate `exprs` left to right, concatenating their ops. */
private def generateAll(
    exprs: Seq[Expr]
)(using Env): (Seq[Operation], Seq[Value[ChocolaType]]) =
  val gens = exprs.map(generateExpr)
  (gens.flatMap(_.ops), gens.map(_.value))

private def resultOf(expr: Expr): Result[ChocolaType] =
  Result(optionalAstTypeToAttr(expr.tpe))

// ---------------------------------------------------------------------------
// Top-level entry point
// ---------------------------------------------------------------------------

def generateProgram(program: Program): ModuleOp =
  val sourceMains = program.decls.collect {
    case decl @ DefDecl("main", _, _, _, _) => decl
  }

  if sourceMains.size > 1 then
    throw IllegalArgumentException(
      "Program may define at most one source main function"
    )

  sourceMains.headOption.foreach {
    case DefDecl(_, typeParams, params, returnType, _)
        if typeParams.nonEmpty || params.nonEmpty ||
          returnType != AstType.TInt =>
      throw IllegalArgumentException(
        "Source main function must have signature def main(): Int"
      )
    case _ =>
  }

  if sourceMains.nonEmpty && program.expr.nonEmpty then
    throw IllegalArgumentException(
      "Program cannot contain both a source main function and a final expression"
    )
  val (declOps, env) =
    program.decls
      .foldLeft((Seq.empty[Operation], Env())) { case ((ops, env), decl) =>
        val (newOps, newEnv) = generateDecl(decl)(using env)
        (ops ++ newOps, newEnv)
      }
  val mainOps =
    if sourceMains.nonEmpty then Seq.empty
    else Seq(generateMain(program.expr)(using env))
  ModuleOp(Region(Block(declOps ++ mainOps)))

/** Generate the synthetic entry point for a program without a source main. */
private def generateMain(expr: Option[Expr])(using Env): FuncDeclOp =
  val bodyOps = expr match
    case Some(expr) =>
      val g = generateExpr(expr)
      if expr.tpe.contains(AstType.TInt) then g.ops :+ ReturnOp(g.value)
      else g.ops ++ zeroReturn
    case None => zeroReturn

  FuncDeclOp(
    symbolRef("main"),
    ArrayAttribute[SymbolRefAttr](),
    FunctionType(
      ArrayAttribute[Attribute](),
      ArrayAttribute[Attribute](IntType()),
    ),
    Region(Block(bodyOps)),
  )

private def zeroReturn: Seq[Operation] =
  val zero = Result[ChocolaType](IntType())
  Seq(
    ConstantOp(StringData("0"), zero),
    ReturnOp(zero),
  )

// ---------------------------------------------------------------------------
// Declaration generation
// ---------------------------------------------------------------------------

private def generateDecl(decl: Decl)(using env: Env): (Seq[Operation], Env) =
  decl match
    case ValDecl(name, body) =>
      val g = generateExpr(body)
      (g.ops, env.bind(name, g.value))

    case DefDecl(name, typeParams, params, retTy, body) =>
      val sym = symbolRef(name)
      // Make the symbol visible while constructing its own body so recursive
      // calls are represented symbolically rather than as an SSA cycle.
      val outerEnv = env.bindFunction(name, sym)
      val paramTypes: Seq[ChocolaType] = params.map((_, t) => convertAstType(t))
      val resultType = convertAstType(retTy)
      val functionType = FunctionType(
        ArrayAttribute(paramTypes*),
        ArrayAttribute(resultType),
      )
      val bodyBlock = Block(
        paramTypes,
        (args: Iterable[Value[Attribute]]) =>
          val g =
            generateExpr(body)(using outerEnv.bindAll(params.map(_._1), args))
          g.ops :+ ReturnOp(g.value),
      )
      val funcOp = FuncDeclOp(
        sym,
        ArrayAttribute(typeParams.map(symbolRef)*),
        functionType,
        Region(bodyBlock),
      )
      (Seq(funcOp), outerEnv)

    case TypeDecl(name, tpe) =>
      (Seq(TypeAliasDeclOp(symbolRef(name), convertAstType(tpe))), env)

    case EnumDecl(name, typeParams, cases) =>
      val ctorTypes = cases.map { case EnumCase(caseName, fields) =>
        val bindingTypes =
          if typeParams.isEmpty then fields.map((_, t) => convertAstType(t))
          else fields.map(_ => unknown)
        caseName -> bindingTypes
      }
      val caseOps: Seq[Operation] = cases.map {
        case EnumCase(caseName, fields) =>
          val fieldAttrs = fields.map { case (fieldName, fieldType) =>
            EnumFieldAttr(symbolRef(fieldName), convertAstType(fieldType))
          }
          EnumCaseDeclOp(symbolRef(caseName), ArrayAttribute(fieldAttrs*))
      }
      val enumOp = EnumDeclOp(
        symbolRef(name),
        ArrayAttribute(typeParams.map(symbolRef)*),
        Region(Block(caseOps)),
      )
      (Seq(enumOp), env.bindConstructors(ctorTypes))

// ---------------------------------------------------------------------------
// Expression generation
// ---------------------------------------------------------------------------

private def generateExpr(expr: Expr)(using env: Env): Generated =
  expr match

    case Expr.Lit(lit, _) =>
      val result = resultOf(expr)
      single(ConstantOp(StringData(literalToString(lit)), result), result)

    case Expr.Var(name, _) =>
      env.locals.get(name) match
        case Some(value) => Generated(Seq.empty, value)
        case None        =>
          val result = resultOf(expr)
          single(
            FunctionRefOp(env.functions(name), asResult[FunType](result)),
            result,
          )

    case Expr.BinOp(op, l, r, _) =>
      val lg = generateExpr(l)
      val rg = generateExpr(r)
      val result = resultOf(expr)
      Generated(
        lg.ops ++ rg.ops :+ binaryOp(op, lg.value, rg.value, result),
        result,
      )

    case Expr.UnOp(op, e, _) =>
      val g = generateExpr(e)
      val result = resultOf(expr)
      Generated(g.ops :+ unaryOp(op, g.value, result), result)

    case Expr.Apply(Expr.Var(name, _), args, _)
        if !env.locals.contains(name) && env.functions.contains(name) =>
      val (argOps, argVals) = generateAll(args)
      val result = resultOf(expr)
      Generated(
        argOps :+ DirectCallOp(env.functions(name), argVals, result),
        result,
      )

    case Expr.Apply(f, args, _) =>
      val fg = generateExpr(f)
      val (argOps, argVals) = generateAll(args)
      val result = resultOf(expr)
      Generated(
        fg.ops ++ argOps :+
          CallOp(asOperand[FunType](fg.value), argVals, result),
        result,
      )

    case Expr.Construct(name, args, _) =>
      val (argOps, argVals) = generateAll(args)
      val result = resultOf(expr)
      Generated(
        argOps :+
          ConstructOp(symbolRef(name), argVals, asResult[NamedType](result)),
        result,
      )

    case Expr.Tuple(elems, _) =>
      val (elemOps, elemVals) = generateAll(elems)
      val result = resultOf(expr)
      Generated(
        elemOps :+ TupleOp(elemVals, asResult[TupleType](result)),
        result,
      )

    case Expr.If(cond, thenBranch, elseBranch, _) =>
      val cg = generateExpr(cond)
      val result = resultOf(expr)
      val ifOp = IfOp(
        asOperand[BoolType](cg.value),
        result,
        Region(Block(yielding(thenBranch))),
        Region(Block(yielding(elseBranch))),
      )
      Generated(cg.ops :+ ifOp, result)

    case Expr.Lambda(params, body, _) =>
      val result = resultOf(expr)
      val paramTypes: Seq[ChocolaType] = params.map((_, t) => convertAstType(t))
      val bodyBlock = Block(
        paramTypes,
        (args: Iterable[Value[Attribute]]) =>
          yielding(body)(using env.bindAll(params.map(_._1), args)),
      )
      single(LambdaOp(asResult[FunType](result), Region(bodyBlock)), result)

    case Expr.Match(scrutinee, cases, _) =>
      val sg = generateExpr(scrutinee)
      val result = resultOf(expr)
      val caseOps: Seq[Operation] = cases.map {
        case MatchCase(ctor, bindings, body) =>
          val bindingTypes = env.constructors
            .getOrElse(ctor, bindings.map(_ => unknown))
          val bodyBlock = Block(
            bindingTypes,
            (bindingArgs: Iterable[Value[Attribute]]) =>
              yielding(body)(using env.bindAll(bindings, bindingArgs)),
          )
          CaseOp(symbolRef(ctor), Region(bodyBlock))
      }
      Generated(
        sg.ops :+ MatchOp(sg.value, result, Region(Block(caseOps))),
        result,
      )

/** The ops of `expr` followed by a `YieldOp` of its value. */
private def yielding(expr: Expr)(using Env): Seq[Operation] =
  val g = generateExpr(expr)
  g.ops :+ YieldOp(g.value)

private def binaryOp(
    op: String,
    lv: Value[ChocolaType],
    rv: Value[ChocolaType],
    result: Result[ChocolaType],
): Operation =
  op match
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

private def unaryOp(
    op: String,
    v: Value[ChocolaType],
    result: Result[ChocolaType],
): Operation =
  op match
    case "-" => NegOp(asOperand[NumericType](v), asResult[NumericType](result))
    case "!" => NotOp(asOperand[BoolType](v), asResult[BoolType](result))
    case _   => throw IllegalArgumentException(s"Unknown unary operator: $op")

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
