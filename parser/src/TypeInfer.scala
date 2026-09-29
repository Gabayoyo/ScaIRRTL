package chocola.parser

import chocola.parser.Decl.*
import chocola.parser.Expr.*
import chocola.parser.Literal.*
import chocola.parser.Type.*

import scair.ir.Location
import scair.ir.UnknownLoc

import scala.collection.mutable

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

case class TypeError(msg: String, location: Location = UnknownLoc)

class TypeInferencer:
  val errors: mutable.ListBuffer[TypeError] = mutable.ListBuffer.empty

  // ---------------------------------------------------------------------------
  // Unification metavariables
  // ---------------------------------------------------------------------------
  // Reuses chocola.parser.Type directly (no parallel type hierarchy) via a
  // naming convention: a fresh unification metavariable is TName("?n") -- a
  // prefix no source identifier can produce -- distinct from rigid
  // type-parameter names (e.g. a def's own TName("A")) and resolved
  // user/enum type names (e.g. TName("Option")).

  private var counter = 0

  private def freshVar(): Type =
    counter += 1
    TName(s"?$counter")

  private def isMeta(name: String): Boolean = name.startsWith("?")

  private val subst: mutable.Map[String, Type] = mutable.Map.empty

  private def resolve(t: Type): Type =
    t match
      case TName(n) if isMeta(n) =>
        subst.get(n) match
          case Some(inner) => resolve(inner)
          case None        => t
      case _ => t

  private def occurs(v: String, t: Type): Boolean =
    resolve(t) match
      case TName(n) if isMeta(n) => n == v
      case TList(elem)           => occurs(v, elem)
      case TFun(from, to)        => occurs(v, from) || occurs(v, to)
      case TTuple(elems)         => elems.exists(occurs(v, _))
      case _                     => false

  // actual `unify` expected -- binds unresolved metavariables on either side,
  // otherwise recurses structurally. Reports (and returns false) on mismatch,
  // at `location`.
  def unify(actual: Type, expected: Type, location: Location): Boolean =
    val ra = resolve(actual)
    val re = resolve(expected)
    (ra, re) match
      case (TName(na), TName(ne)) if isMeta(na) && isMeta(ne) && na == ne =>
        true
      case (TName(na), _) if isMeta(na) =>
        if occurs(na, re) then
          errors += TypeError(s"Occurs check failed: infinite type", location)
          false
        else
          subst(na) = re
          true
      case (_, TName(ne)) if isMeta(ne) =>
        if occurs(ne, ra) then
          errors += TypeError(s"Occurs check failed: infinite type", location)
          false
        else
          subst(ne) = ra
          true
      case (TInt, TInt) | (TDouble, TDouble) | (TString, TString) |
          (TBool, TBool) =>
        true
      case (TList(ea), TList(ee))       => unify(ea, ee, location)
      case (TFun(fa, ta), TFun(fe, te)) =>
        unify(fa, fe, location) && unify(ta, te, location)
      case (TTuple(as), TTuple(es)) if as.length == es.length =>
        as.zip(es).forall(unify(_, _, location))
      case (TName(na), TName(ne)) if na == ne => true
      case _                                  =>
        errors += TypeError(
          s"Type mismatch: expected ${zonk(re)} but got ${zonk(ra)}",
          location,
        )
        false

  // fully resolves metavariables; any still-unbound metavariable (an
  // ambiguous/unused polymorphic value) becomes TName("_"), matching the
  // existing permissive IR fallback (!chocola.unknown).
  private def zonk(t: Type): Type =
    resolve(t) match
      case TName(n) if isMeta(n) => TName("_")
      case TList(elem)           => TList(zonk(elem))
      case TFun(from, to)        => TFun(zonk(from), zonk(to))
      case TTuple(elems)         => TTuple(elems.map(zonk))
      case other                 => other

  // ---------------------------------------------------------------------------
  // Type schemes (let-free parametric polymorphism for defs/enums/primitives)
  // ---------------------------------------------------------------------------

  case class Scheme(vars: List[String], body: Type)

  private def substituteNames(t: Type, mapping: Map[String, Type]): Type =
    t match
      case TName(n)       => mapping.getOrElse(n, t)
      case TList(elem)    => TList(substituteNames(elem, mapping))
      case TFun(from, to) =>
        TFun(substituteNames(from, mapping), substituteNames(to, mapping))
      case TTuple(elems) => TTuple(elems.map(substituteNames(_, mapping)))
      case _             => t

  private def instantiate(s: Scheme): Type =
    if s.vars.isEmpty then s.body
    else substituteNames(s.body, s.vars.map(_ -> freshVar()).toMap)

  private def mapScheme: Scheme =
    val a = TName("A"); val b = TName("B")
    Scheme(List("A", "B"), TFun(TFun(a, b), TFun(TList(a), TList(b))))

  private def filterScheme: Scheme =
    val a = TName("A")
    Scheme(List("A"), TFun(TList(a), TFun(TFun(a, TBool), TList(a))))

  private def foldLeftScheme: Scheme =
    val a = TName("A"); val b = TName("B")
    Scheme(
      List("A", "B"),
      TFun(TList(a), TFun(b, TFun(TFun(b, TFun(a, b)), b))),
    )

  private def foldRightScheme: Scheme =
    val a = TName("A"); val b = TName("B")
    Scheme(
      List("A", "B"),
      TFun(TList(a), TFun(b, TFun(TFun(a, TFun(b, b)), b))),
    )

  // ---------------------------------------------------------------------------
  // Registries (one prepass over all decls, allowing mutual/forward reference
  // between type/enum declarations -- a pragmatic, deliberate deviation from
  // the PDF's strictly left-to-right Gamma threading)
  // ---------------------------------------------------------------------------

  private case class CtorInfo(
      enumName: String,
      typeParams: List[String],
      fields: List[(String, Type)],
  )

  private val ctorRegistry: mutable.Map[String, CtorInfo] = mutable.Map.empty
  private val enumCtors: mutable.Map[String, List[String]] = mutable.Map.empty
  private val enumArity: mutable.Map[String, Int] = mutable.Map.empty
  private val aliasRegistry: mutable.Map[String, Type] = mutable.Map.empty

  private def buildRegistry(decls: List[Decl]): Unit =
    decls.foreach:
      case EnumDecl(name, typeParams, cases) =>
        enumArity(name) = typeParams.length
        enumCtors(name) = cases.map(_.name)
        cases.foreach(c =>
          ctorRegistry(c.name) = CtorInfo(name, typeParams, c.fields)
        )
      case TypeDecl(name, tpe) =>
        aliasRegistry(name) = tpe
      case _ => ()

  // Resolves a surface-syntax type annotation: validates/expands names
  // against in-scope type params (rigid), known enums (left bare -- the
  // surface grammar has no T[...] application syntax for user types, only
  // for List[_]) and type aliases (expanded, with cycle detection).
  private def resolveAnnotation(
      t: Type,
      rigid: Set[String],
      location: Location,
      seen: Set[String] = Set.empty,
  ): Type =
    t match
      case TName(name) =>
        if rigid.contains(name) then TName(name)
        else if enumArity.contains(name) then TName(name)
        else
          aliasRegistry.get(name) match
            case Some(aliased) =>
              if seen.contains(name) then
                errors += TypeError(s"Cyclic type alias: $name", location)
                TName("_")
              else resolveAnnotation(aliased, rigid, location, seen + name)
            case None =>
              errors += TypeError(s"Unknown type: $name", location)
              TName("_")
      case TList(elem) => TList(resolveAnnotation(elem, rigid, location, seen))
      case TFun(from, to) =>
        TFun(
          resolveAnnotation(from, rigid, location, seen),
          resolveAnnotation(to, rigid, location, seen),
        )
      case TTuple(elems) =>
        TTuple(elems.map(resolveAnnotation(_, rigid, location, seen)))
      case other => other

  // ---------------------------------------------------------------------------
  // Deferred AST annotation
  // ---------------------------------------------------------------------------
  // synthExpr records every node's (possibly still metavariable-containing)
  // type instead of writing e.tpe immediately -- writing immediately would
  // freeze a node's displayed type before a later sibling/unification call
  // has a chance to solve its metavariables (e.g. Nil's element type, only
  // pinned down once it's consed with something concrete). e.tpe is written
  // once, after the whole program has been processed, via zonk.

  private val recorded: mutable.ArrayBuffer[(Expr, Type)] = mutable.ArrayBuffer
    .empty

  def inferProgram(p: Program): List[TypeError] =
    buildRegistry(p.decls)
    val env = p.decls.foldLeft(Map.empty[String, Scheme])(inferDecl)
    p.expr.foreach(synthExpr(_, env, Set.empty))
    recorded.foreach { case (e, t) => e.tpe = Some(zonk(t)) }
    errors.toList

  // ---------------------------------------------------------------------------
  // Declaration inference
  // ---------------------------------------------------------------------------

  private def inferDecl(
      env: Map[String, Scheme],
      decl: Decl,
  ): Map[String, Scheme] =
    decl match
      case ValDecl(name, body) =>
        val t = synthExpr(body, env, Set.empty)
        env + (name -> Scheme(Nil, t))

      case DefDecl(name, typeParams, params, retTyAnn, body) =>
        val rigid = typeParams.toSet
        val paramTypes =
          params.map((n, t) => n -> resolveAnnotation(t, rigid, decl.location))
        val retTy = resolveAnnotation(retTyAnn, rigid, decl.location)
        val fnTy = paramTypes.foldRight(retTy)((p, acc) => TFun(p._2, acc))
        // bind f in its own body's env to support (self-)recursion -- not
        // literal in T-DefDecl/T-DefDeclPoly, but necessary for `def` to be
        // usable at all for recursive functions.
        val selfScheme = Scheme(typeParams, fnTy)
        val bodyEnv =
          env + (name -> selfScheme) ++
            paramTypes.map((n, t) => n -> Scheme(Nil, t))
        checkExpr(body, retTy, bodyEnv, rigid)
        env + (name -> selfScheme)

      case TypeDecl(_, _)    => env // resolved via aliasRegistry prepass
      case EnumDecl(_, _, _) => env // resolved via ctor/enum registry prepass

  // ---------------------------------------------------------------------------
  // Synthesis (bottom-up)
  // ---------------------------------------------------------------------------

  def synthExpr(e: Expr, env: Map[String, Scheme], rigid: Set[String]): Type =
    val t = e match
      case Lit(IntLit(_), _)     => TInt
      case Lit(DoubleLit(_), _)  => TDouble
      case Lit(StrLit(_), _)     => TString
      case Lit(BoolLit(_), _)    => TBool
      case Lit(NilLit, _)        => TList(freshVar())
      case Lit(MapPrim, _)       => instantiate(mapScheme)
      case Lit(FilterPrim, _)    => instantiate(filterScheme)
      case Lit(FoldLeftPrim, _)  => instantiate(foldLeftScheme)
      case Lit(FoldRightPrim, _) => instantiate(foldRightScheme)

      case Var(name, _) =>
        env.get(name) match
          case Some(s) => instantiate(s)
          case None    =>
            errors += TypeError(s"Unbound variable: $name", e.location)
            freshVar()

      case UnOp("-", inner, _) =>
        val it = synthExpr(inner, env, rigid)
        requireNumeric(it, " in unary -", e.location)
        it
      case UnOp("!", inner, _) =>
        checkExpr(inner, TBool, env, rigid)
        TBool
      case UnOp(op, inner, _) =>
        errors += TypeError(s"Unknown unary operator: $op", e.location)
        synthExpr(inner, env, rigid)
        freshVar()

      case BinOp("||" | "&&", l, r, _) =>
        checkExpr(l, TBool, env, rigid)
        checkExpr(r, TBool, env, rigid)
        TBool

      case BinOp("<" | ">" | "<=" | ">=", l, r, _) =>
        val lt = synthExpr(l, env, rigid)
        checkExpr(r, lt, env, rigid)
        requireNumeric(lt, " in comparison", e.location)
        TBool

      case BinOp("==" | "!=", l, r, _) =>
        val lt = synthExpr(l, env, rigid)
        checkExpr(r, lt, env, rigid)
        TBool

      case BinOp("::", head, tail, _) =>
        val ht = synthExpr(head, env, rigid)
        checkExpr(tail, TList(ht), env, rigid)
        TList(ht)

      case BinOp("+" | "-" | "*" | "/", l, r, _) =>
        val lt = synthExpr(l, env, rigid)
        checkExpr(r, lt, env, rigid)
        requireNumeric(lt, " in arithmetic", e.location)
        lt

      case BinOp(op, l, r, _) =>
        errors += TypeError(s"Unknown binary operator: $op", e.location)
        synthExpr(l, env, rigid)
        synthExpr(r, env, rigid)
        freshVar()

      case Apply(fun, args, _) =>
        val ft = synthExpr(fun, env, rigid)
        peelArgs(ft, args, env, rigid, e.location)

      case Lambda(params, body, _) =>
        val paramTypes =
          params.map((n, t) => n -> resolveAnnotation(t, rigid, e.location))
        val paramEnv = env ++ paramTypes.map((n, t) => n -> Scheme(Nil, t))
        val bodyTy = synthExpr(body, paramEnv, rigid)
        paramTypes.foldRight(bodyTy)((p, acc) => TFun(p._2, acc))

      case If(cond, thenBranch, elseBranch, _) =>
        checkExpr(cond, TBool, env, rigid)
        val tt = synthExpr(thenBranch, env, rigid)
        checkExpr(elseBranch, tt, env, rigid)
        tt

      case Construct(name, args, _) =>
        ctorRegistry.get(name) match
          case None =>
            errors += TypeError(s"Unknown constructor: $name", e.location)
            args.foreach(synthExpr(_, env, rigid))
            freshVar()
          case Some(CtorInfo(enumName, typeParams, fields)) =>
            if args.length != fields.length then
              errors += TypeError(
                s"Constructor $name expects ${fields.length} args, got ${args
                    .length}",
                e.location,
              )
            val mapping: Map[String, Type] = typeParams.map(_ -> freshVar())
              .toMap
            args.zip(fields).foreach { case (arg, (_, fieldTyAnn)) =>
              val fieldTy =
                substituteNames(
                  resolveAnnotation(fieldTyAnn, typeParams.toSet, e.location),
                  mapping,
                )
              checkExpr(arg, fieldTy, env, rigid)
            }
            if args.length > fields.length then
              args.drop(fields.length).foreach(synthExpr(_, env, rigid))
            TName(enumName)

      case Match(scrutinee, cases, _) =>
        val scrutTy = resolve(synthExpr(scrutinee, env, rigid))
        val enumNameOpt: Option[String] = scrutTy match
          case TName(n) if !isMeta(n) && enumArity.contains(n) => Some(n)
          case TName(n) if isMeta(n)                           =>
            // ambiguous scrutinee (e.g. matching directly on Nil/an unbound
            // metavariable): recover the enum from the first case's ctor.
            cases.headOption.flatMap(mc => ctorRegistry.get(mc.ctor))
              .map(_.enumName) match
              case Some(en) =>
                unify(scrutTy, TName(en), e.location)
                Some(en)
              case None => None
          case _ => None

        enumNameOpt match
          case None =>
            errors += TypeError(
              s"Cannot match on non-enum type: ${zonk(scrutTy)}",
              e.location,
            )
            cases.foreach(mc => synthExpr(mc.body, env, rigid))
            freshVar()
          case Some(enumName) =>
            val covered = mutable.Set.empty[String]
            var resultTy: Option[Type] = None
            cases.foreach { mc =>
              ctorRegistry.get(mc.ctor) match
                case None =>
                  errors += TypeError(
                    s"Unknown constructor in match: ${mc.ctor}",
                    mc.location,
                  )
                  synthExpr(mc.body, env, rigid)
                case Some(CtorInfo(ctorEnum, _, _)) if ctorEnum != enumName =>
                  errors += TypeError(
                    s"Constructor ${mc.ctor} does not belong to enum $enumName",
                    mc.location,
                  )
                  synthExpr(mc.body, env, rigid)
                case Some(CtorInfo(_, typeParams, fields)) =>
                  if !covered.add(mc.ctor) then
                    errors += TypeError(
                      s"Duplicate case for constructor ${mc.ctor}",
                      mc.location,
                    )
                  if mc.bindings.length != fields.length then
                    errors += TypeError(
                      s"Pattern ${mc.ctor} expects ${fields
                          .length} bindings, got ${mc.bindings.length}",
                      mc.location,
                    )
                  val caseEnv = env ++ mc.bindings.zip(fields).map {
                    case (bindingName, (_, fieldTyAnn)) =>
                      bindingName -> Scheme(
                        Nil,
                        resolveAnnotation(
                          fieldTyAnn,
                          typeParams.toSet,
                          mc.location,
                        ),
                      )
                  }
                  val bt = synthExpr(mc.body, caseEnv, rigid)
                  resultTy match
                    case None     => resultTy = Some(bt)
                    case Some(rt) => unify(bt, rt, mc.body.location)
            }
            val missing = enumCtors.getOrElse(enumName, Nil)
              .filterNot(covered.contains)
            if missing.nonEmpty then
              errors += TypeError(
                s"Non-exhaustive match on $enumName: missing ${missing.mkString(", ")}",
                e.location,
              )
            resultTy.getOrElse(freshVar())

      case Tuple(elems, _) =>
        TTuple(elems.map(synthExpr(_, env, rigid)))

    recorded += (e -> t)
    t

  // ---------------------------------------------------------------------------
  // Checking (top-down)
  // ---------------------------------------------------------------------------

  def checkExpr(
      e: Expr,
      expected: Type,
      env: Map[String, Scheme],
      rigid: Set[String],
  ): Unit =
    val actual = synthExpr(e, env, rigid)
    unify(actual, expected, e.location)

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def peelArgs(
      ft: Type,
      args: List[Expr],
      env: Map[String, Scheme],
      rigid: Set[String],
      location: Location,
  ): Type =
    args match
      case Nil         => ft
      case arg :: rest =>
        resolve(ft) match
          case TFun(from, to) =>
            checkExpr(arg, from, env, rigid)
            peelArgs(to, rest, env, rigid, location)
          case TName(n) if isMeta(n) =>
            val from = freshVar()
            val to = freshVar()
            unify(ft, TFun(from, to), location)
            checkExpr(arg, from, env, rigid)
            peelArgs(to, rest, env, rigid, location)
          case other =>
            errors +=
              TypeError(s"Applying non-function type: ${zonk(other)}", location)
            synthExpr(arg, env, rigid)
            peelArgs(freshVar(), rest, env, rigid, location)

  private def requireNumeric(
      t: Type,
      where: String,
      location: Location,
  ): Unit =
    resolve(t) match
      case TInt | TDouble        => ()
      case TName(n) if isMeta(n) =>
        errors += TypeError(s"Cannot infer a numeric type$where", location)
      case other =>
        errors += TypeError(
          s"Expected int or double$where, got ${zonk(other)}",
          location,
        )
