package chocola.parser

import chocola.parser.Decl.*
import chocola.parser.Expr.*
import chocola.parser.Literal.*
import chocola.parser.Type.*

import scala.collection.mutable

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

case class TypeError(msg: String)

class TypeInferencer:
  val errors: mutable.ListBuffer[TypeError] = mutable.ListBuffer.empty

  // Maps constructor name → (enum name, type params, field types)
  private val ctorRegistry
      : mutable.Map[String, (String, List[String], List[(String, Type)])] =
    mutable.Map.empty

  def inferProgram(p: Program): List[TypeError] =
    buildRegistry(p.decls)
    val env = p.decls.foldLeft(Map.empty[String, Type])(inferDecl)
    p.expr.foreach(synthExpr(_, env))
    errors.toList

  // ---------------------------------------------------------------------------
  // Registry
  // ---------------------------------------------------------------------------

  private def buildRegistry(decls: List[Decl]): Unit =
    decls.foreach:
      case EnumDecl(name, typeParams, cases) =>
        cases.foreach(c => ctorRegistry(c.name) = (name, typeParams, c.fields))
      case _ => ()

  // ---------------------------------------------------------------------------
  // Declaration inference
  // ---------------------------------------------------------------------------

  private def inferDecl(env: Map[String, Type], decl: Decl): Map[String, Type] =
    decl match
      case ValDecl(name, body) =>
        val t = synthExpr(body, env)
        env + (name -> t)

      case DefDecl(name, _, params, retTy, body) =>
        val paramEnv = env ++ params.toMap
        checkExpr(body, retTy, paramEnv)
        val fnTy = params.foldRight(retTy)((p, acc) => TFun(p._2, acc))
        env + (name -> fnTy)

      case TypeDecl(_, _) | EnumDecl(_, _, _) => env

  // ---------------------------------------------------------------------------
  // Synthesis (bottom-up)
  // ---------------------------------------------------------------------------

  def synthExpr(e: Expr, env: Map[String, Type]): Type =
    val t = e match
      case Lit(IntLit(_), _)    => TInt
      case Lit(DoubleLit(_), _) => TDouble
      case Lit(StrLit(_), _)    => TString
      case Lit(BoolLit(_), _)   => TBool
      case Lit(NilLit, _)       => TList(TName("_"))
      case Lit(MapPrim, _)      =>
        TFun(
          TFun(TName("A"), TName("B")),
          TFun(TList(TName("A")), TList(TName("B"))),
        )
      case Lit(FilterPrim, _) =>
        TFun(
          TFun(TName("A"), TBool),
          TFun(TList(TName("A")), TList(TName("A"))),
        )
      case Lit(FoldLeftPrim, _)  => foldType
      case Lit(FoldRightPrim, _) => foldType

      case Var(name, _) =>
        env.get(name) match
          case Some(t) => t
          case None    =>
            errors += TypeError(s"Unbound variable: $name")
            TName("_")

      case UnOp("-", inner, _) => synthExpr(inner, env)
      case UnOp("!", inner, _) =>
        checkExpr(inner, TBool, env)
        TBool
      case UnOp(op, _, _) =>
        errors += TypeError(s"Unknown unary operator: $op")
        TName("_")

      case BinOp("||" | "&&", l, r, _) =>
        checkExpr(l, TBool, env)
        checkExpr(r, TBool, env)
        TBool

      case BinOp("==" | "!=" | "<" | ">" | "<=" | ">=", l, r, _) =>
        val lt = synthExpr(l, env)
        checkExpr(r, lt, env)
        TBool

      case BinOp("::", head, tail, _) =>
        val ht = synthExpr(head, env)
        checkExpr(tail, TList(ht), env)
        TList(ht)

      case BinOp("+" | "-" | "*" | "/", l, r, _) =>
        val lt = synthExpr(l, env)
        checkExpr(r, lt, env)
        lt

      case BinOp(op, _, _, _) =>
        errors += TypeError(s"Unknown binary operator: $op")
        TName("_")

      case Apply(fun, args, _) =>
        val ft = synthExpr(fun, env)
        peelArgs(ft, args, env)

      case Lambda(params, body, _) =>
        val paramEnv = env ++ params.toMap
        val bodyTy = synthExpr(body, paramEnv)
        params.foldRight(bodyTy)((p, acc) => TFun(p._2, acc))

      case If(cond, thenBranch, elseBranch, _) =>
        checkExpr(cond, TBool, env)
        val tt = synthExpr(thenBranch, env)
        checkExpr(elseBranch, tt, env)
        tt

      case Construct(name, args, _) =>
        ctorRegistry.get(name) match
          case None =>
            errors += TypeError(s"Unknown constructor: $name")
            args.foreach(synthExpr(_, env))
            TName("_")
          case Some((enumName, _, fields)) =>
            args.zip(fields).foreach { case (arg, (_, fieldTy)) =>
              checkExpr(arg, fieldTy, env)
            }
            if args.length != fields.length then
              errors += TypeError(
                s"Constructor $name expects ${fields.length} args, got ${args
                    .length}"
              )
            TName(enumName)

      case Match(scrutinee, cases, _) =>
        synthExpr(scrutinee, env)
        cases.foldLeft(Option.empty[Type]) { (acc, mc) =>
          val caseEnv = env ++ mc.bindings.map(_ -> TName("_"))
          val bt = synthExpr(mc.body, caseEnv)
          acc.orElse(Some(bt))
        }.getOrElse(TName("_"))

      case Tuple(elems, _) =>
        TTuple(elems.map(synthExpr(_, env)))

    e.tpe = Some(t)
    t

  // ---------------------------------------------------------------------------
  // Checking (top-down)
  // ---------------------------------------------------------------------------

  def checkExpr(e: Expr, expected: Type, env: Map[String, Type]): Unit =
    val actual = synthExpr(e, env)
    if !compatible(actual, expected) then
      errors += TypeError(
        s"Type mismatch: expected $expected but got $actual"
      )

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def peelArgs(
      ft: Type,
      args: List[Expr],
      env: Map[String, Type],
  ): Type =
    args match
      case Nil         => ft
      case arg :: rest =>
        ft match
          case TFun(from, to) =>
            checkExpr(arg, from, env)
            peelArgs(to, rest, env)
          case _ =>
            errors += TypeError(s"Applying non-function type: $ft")
            synthExpr(arg, env)
            peelArgs(TName("_"), rest, env)

  private def foldType: Type =
    // (B => A => B) => B => List[A] => B
    val a = TName("A")
    val b = TName("B")
    TFun(TFun(b, TFun(a, b)), TFun(b, TFun(TList(a), b)))

  def compatible(a: Type, b: Type): Boolean =
    (a, b) match
      case (TName("_"), _) | (_, TName("_")) => true
      case (TInt, TInt) | (TDouble, TDouble) | (TString, TString) |
          (TBool, TBool) =>
        true
      case (TList(ea), TList(eb))       => compatible(ea, eb)
      case (TFun(fa, ta), TFun(fb, tb)) =>
        compatible(fa, fb) && compatible(ta, tb)
      case (TTuple(as), TTuple(bs)) =>
        as.length == bs.length && as.zip(bs).forall(compatible(_, _))
      case (TName(na), TName(nb)) => na == nb
      case _                      => false
