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

/** A source-level error, tied to the location of the offending node. */
final case class SourceError(msg: String, location: Location)
    extends Exception(msg)

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

/** A single op for `expr`, located at `expr`. */
private def singleAt(
    expr: Expr,
    op: Operation,
    result: Value[ChocolaType],
): Generated =
  single(op.at(expr.location), result)

// ---------------------------------------------------------------------------
// Top-level entry point
// ---------------------------------------------------------------------------

def generateProgram(program: Program): ModuleOp =
  val sourceMains = program.decls.collect {
    case decl @ DefDecl("main", _, _, _, _) => decl
  }

  if sourceMains.size > 1 then
    throw SourceError(
      "Program may define at most one source main function",
      sourceMains(1).location,
    )

  sourceMains.headOption.foreach {
    case decl @ DefDecl(_, typeParams, params, returnType, _)
        if typeParams.nonEmpty || params.nonEmpty ||
          returnType != AstType.TInt =>
      throw SourceError(
        "Source main function must have signature def main(): Int",
        decl.location,
      )
    case _ =>
  }

  program.expr.filter(_ => sourceMains.nonEmpty).foreach { expr =>
    throw SourceError(
      "Program cannot contain both a source main function and a final expression",
      expr.location,
    )
  }
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
      if expr.tpe.contains(AstType.TInt) then
        g.ops :+ ReturnOp(g.value).at(expr.location)
      else g.ops ++ zeroReturn
    case None => zeroReturn

  val mainOp = FuncDeclOp(
    symbolRef("main"),
    ArrayAttribute[SymbolRefAttr](),
    FunctionType(
      ArrayAttribute[Attribute](),
      ArrayAttribute[Attribute](IntType()),
    ),
    Region(Block(bodyOps)),
  )
  expr.fold(mainOp)(expr => mainOp.at(expr.location))

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
          g.ops :+ ReturnOp(g.value).at(body.location),
      )
      val funcOp = FuncDeclOp(
        sym,
        ArrayAttribute(typeParams.map(symbolRef)*),
        functionType,
        Region(bodyBlock),
      ).at(decl.location)
      (Seq(funcOp), outerEnv)

    case TypeDecl(name, tpe) =>
      val aliasOp =
        TypeAliasDeclOp(symbolRef(name), convertAstType(tpe)).at(decl.location)
      (Seq(aliasOp), env)

    case EnumDecl(name, typeParams, cases) =>
      val ctorTypes = cases.map { case EnumCase(caseName, fields) =>
        val bindingTypes =
          if typeParams.isEmpty then fields.map((_, t) => convertAstType(t))
          else fields.map(_ => unknown)
        caseName -> bindingTypes
      }
      val caseOps: Seq[Operation] = cases.map {
        case enumCase @ EnumCase(caseName, fields) =>
          val fieldAttrs = fields.map { case (fieldName, fieldType) =>
            EnumFieldAttr(symbolRef(fieldName), convertAstType(fieldType))
          }
          EnumCaseDeclOp(symbolRef(caseName), ArrayAttribute(fieldAttrs*))
            .at(enumCase.location)
      }
      val enumOp = EnumDeclOp(
        symbolRef(name),
        ArrayAttribute(typeParams.map(symbolRef)*),
        Region(Block(caseOps)),
      ).at(decl.location)
      (Seq(enumOp), env.bindConstructors(ctorTypes))

// ---------------------------------------------------------------------------
// Expression generation
// ---------------------------------------------------------------------------

private def generateExpr(expr: Expr)(using env: Env): Generated =
  expr match

    case Expr.Lit(lit, _) =>
      val result = resultOf(expr)
      singleAt(
        expr,
        ConstantOp(StringData(literalToString(lit)), result),
        result,
      )

    case Expr.Var(name, _) =>
      env.locals.get(name) match
        case Some(value) => Generated(Seq.empty, value)
        case None        =>
          val result = resultOf(expr)
          singleAt(
            expr,
            FunctionRefOp(env.functions(name), asResult[FunType](result)),
            result,
          )

    case Expr.BinOp(op, l, r, _) =>
      val lg = generateExpr(l)
      val rg = generateExpr(r)
      val result = resultOf(expr)
      Generated(
        lg.ops ++ rg.ops :+ binaryOp(op, lg.value, rg.value, result)
          .at(expr.location),
        result,
      )

    case Expr.UnOp(op, e, _) =>
      val g = generateExpr(e)
      val result = resultOf(expr)
      Generated(g.ops :+ unaryOp(op, g.value, result).at(expr.location), result)

    case Expr.Apply(Expr.Var(name, _), args, _)
        if !env.locals.contains(name) && env.functions.contains(name) =>
      val (argOps, argVals) = generateAll(args)
      val result = resultOf(expr)
      Generated(
        argOps :+ DirectCallOp(env.functions(name), argVals, result)
          .at(expr.location),
        result,
      )

    case Expr.Apply(f, args, _) =>
      val fg = generateExpr(f)
      val (argOps, argVals) = generateAll(args)
      val result = resultOf(expr)
      Generated(
        fg.ops ++ argOps :+
          CallOp(asOperand[FunType](fg.value), argVals, result)
            .at(expr.location),
        result,
      )

    case Expr.Construct(name, args, _) =>
      val (argOps, argVals) = generateAll(args)
      val result = resultOf(expr)
      Generated(
        argOps :+
          ConstructOp(symbolRef(name), argVals, asResult[NamedType](result))
            .at(expr.location),
        result,
      )

    case Expr.Tuple(elems, _) =>
      val (elemOps, elemVals) = generateAll(elems)
      val result = resultOf(expr)
      Generated(
        elemOps :+ TupleOp(elemVals, asResult[TupleType](result))
          .at(expr.location),
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
      ).at(expr.location)
      Generated(cg.ops :+ ifOp, result)

    case Expr.Lambda(params, body, _) =>
      val result = resultOf(expr)
      val paramTypes: Seq[ChocolaType] = params.map((_, t) => convertAstType(t))
      val bodyBlock = Block(
        paramTypes,
        (args: Iterable[Value[Attribute]]) =>
          yielding(body)(using env.bindAll(params.map(_._1), args)),
      )
      singleAt(
        expr,
        LambdaOp(asResult[FunType](result), Region(bodyBlock)),
        result,
      )

    case Expr.Match(scrutinee, cases, _) =>
      val sg = generateExpr(scrutinee)
      val result = resultOf(expr)
      val caseOps: Seq[Operation] = cases.map {
        case matchCase @ MatchCase(ctor, bindings, body) =>
          val bindingTypes = env.constructors
            .getOrElse(ctor, bindings.map(_ => unknown))
          val bodyBlock = Block(
            bindingTypes,
            (bindingArgs: Iterable[Value[Attribute]]) =>
              yielding(body)(using env.bindAll(bindings, bindingArgs)),
          )
          CaseOp(symbolRef(ctor), Region(bodyBlock)).at(matchCase.location)
      }
      Generated(
        sg.ops :+ MatchOp(sg.value, result, Region(Block(caseOps)))
          .at(expr.location),
        result,
      )

/** The ops of `expr` followed by a `YieldOp` of its value. */
private def yielding(expr: Expr)(using Env): Seq[Operation] =
  val g = generateExpr(expr)
  g.ops :+ YieldOp(g.value).at(expr.location)

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
