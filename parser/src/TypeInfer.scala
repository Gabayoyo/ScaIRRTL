package chocola.parser

import chocola.parser.Decl.*
import chocola.parser.Expr.*
import chocola.parser.Literal.*
import chocola.parser.Type.*
import scair.ir.Location
import scair.ir.UnknownLoc
import scala.collection.mutable
import java.util.IdentityHashMap

case class TypeError(msg: String, location: Location = UnknownLoc)

/** Infers types by unifying the constraints contributed by each expression.
  * Parsing and inference share one AST; successful inference fills its type
  * annotations for IR generation. Use a fresh inferencer for each program.
  */
class TypeInferencer:
  val errors: mutable.ListBuffer[TypeError] = mutable.ListBuffer.empty
  private var nextTypeVariableId = 0
  // TMeta variables can be solved; TParam parameters remain rigid in their scope.
  private val substitutions = mutable.Map.empty[Int, Type]

  private case class InferredExpression(
      expression: Expr,
      inferredType: Type,
      allowedTypeParameters: Set[Type],
  )

  private case class NumericRequirement(
      inferredType: Type,
      diagnosticContext: String,
      location: Location,
  )

  private val inferredExpressions = mutable.ArrayBuffer
    .empty[InferredExpression]

  // Identity matters: even structurally equal declarations own distinct parameters.
  private val declarationTypeParameters =
    IdentityHashMap[Decl, Map[String, Type]]()

  private val numericRequirements = mutable.ArrayBuffer
    .empty[NumericRequirement]

  private case class ConstructorInfo(
      enumName: String,
      typeParameters: List[Type],
      fieldTypes: List[Type],
  )

  private val constructorRegistry = mutable.Map.empty[String, ConstructorInfo]
  private val enumConstructors = mutable.Map.empty[String, List[String]]
  private val enumArity = mutable.Map.empty[String, Int]
  private val aliasRegistry = mutable.Map.empty[String, Type]

  /** A generic signature whose declared parameters are freshened at each use.
    * With no parameters (`vars = Nil`), a binding is monomorphic: its uses
    * share the same inference variables in `body` rather than receiving fresh
    * ones.
    */
  case class Scheme(vars: List[Type], body: Type)

  /** Prepare declarations, infer bodies, then resolve the AST annotations. For
    * `val xs = Nil; 1 :: xs`, Nil starts with a fresh element variable. The
    * binding shares that variable with its uses; cons constrains it to Int.
    * Only after all uses are checked can finalization annotate xs as List[Int].
    */
  def inferProgram(p: Program): List[TypeError] =
    // 1. Make type names and declaration-owned parameters available.
    registerDeclarations(p.decls)
    // 2. Resolve annotations and prepare generic function/constructor signatures.
    val functions = validateDeclarations(p.decls)
    // 3. Infer bodies with all functions visible and values entering scope in order.
    checkBodies(p, functions)
    // 4. Check numeric requirements after later uses have constrained variables.
    checkNumericRequirements()
    // 5. Resolve annotations; only now are remaining variables truly ambiguous.
    finalizeTypes(p)
    errors.toList

  // ---------------------------------------------------------------------------
  // Declaration preparation
  // ---------------------------------------------------------------------------

  private def registerDeclarations(declarations: List[Decl]): Unit =
    // Register nominal names before expanding any annotation. Enum fields are
    // validated independently, so nominal recursion never expands indefinitely.
    declarations.foreach {
      case EnumDecl(name, typeParameterNames, constructors) =>
        enumArity(name) = typeParameterNames.length
        enumConstructors(name) = constructors.map(_.name)
      case TypeDecl(name, annotation) => aliasRegistry(name) = annotation
      case _                          => ()
    }
    var owner = 0
    declarations.foreach { declaration =>
      val names = declaration match
        case DefDecl(_, typeParameterNames, _, _, _) => typeParameterNames
        case EnumDecl(_, typeParameterNames, _)      => typeParameterNames
        case _                                       => Nil
      owner += 1
      declarationTypeParameters
        .put(declaration, names.map(name => name -> TParam(name, owner)).toMap)
    }

  private def validateDeclarations(
      declarations: List[Decl]
  ): Map[String, Scheme] =
    val functions = mutable.Map.empty[String, Scheme]
    declarations.foreach { declaration =>
      val typeParameters = declarationTypeParameters.get(declaration)
      declaration match
        case DefDecl(name, typeParameterNames, parameters, returnType, _) =>
          val signature = TFun(
            parameters.map((_, annotation) =>
              resolveAnnotation(
                annotation,
                typeParameters,
                declaration.location,
              )
            ),
            resolveAnnotation(
              returnType,
              typeParameters,
              declaration.location,
            ),
          )
          declaration.resolvedType = Some(signature)
          functions(name) = Scheme(
            typeParameterNames.map(typeParameters),
            signature,
          )
        case TypeDecl(_, annotation) =>
          declaration.resolvedType =
            Some(resolveAnnotation(annotation, Map.empty, declaration.location))
        case EnumDecl(name, typeParameterNames, constructors) =>
          constructors.foreach { constructor =>
            val fieldTypes = constructor.fields.map((_, annotation) =>
              resolveAnnotation(
                annotation,
                typeParameters,
                constructor.location,
              )
            )
            constructor.fieldTypes = fieldTypes
            constructorRegistry(constructor.name) = ConstructorInfo(
              name,
              typeParameterNames.map(typeParameters),
              fieldTypes,
            )
          }
        case _ => ()
    }
    functions.toMap

  private def checkBodies(
      program: Program,
      functions: Map[String, Scheme],
  ): Unit =
    // All function signatures are visible; value bindings enter scope in order.
    var valueEnvironment = functions
    program.decls.foreach { declaration =>
      declaration match
        case ValDecl(name, body, annotation) =>
          val resolvedAnnotation = annotation
            .map(resolveAnnotation(_, Map.empty, declaration.location))
          val inferredType = synthExpr(body, valueEnvironment, Map.empty)
          resolvedAnnotation.foreach(unify(inferredType, _, body.location))
          declaration.resolvedType = Some(inferredType)
          valueEnvironment = valueEnvironment +
            (name -> Scheme(Nil, inferredType))
        case DefDecl(_, _, parameters, _, body) =>
          val TFun(parameterTypes, returnType) = declaration.resolvedType
            .get: @unchecked
          val bodyEnvironment = valueEnvironment ++ parameters.map(_._1)
            .zip(parameterTypes)
            .map((name, inferredType) => name -> Scheme(Nil, inferredType))
          checkExpr(
            body,
            returnType,
            bodyEnvironment,
            declarationTypeParameters.get(declaration),
          )
        case _ => ()
    }
    program.expr.foreach(synthExpr(_, valueEnvironment, Map.empty))

  private def resolveAnnotation(
      annotation: Type,
      typeParameters: Map[String, Type],
      location: Location,
      visitedAliases: Set[String] = Set.empty,
  ): Type = annotation match
    case TName(name, arguments) =>
      val resolvedArguments = arguments
        .map(resolveAnnotation(_, typeParameters, location, visitedAliases))
      val arity =
        if typeParameters.contains(name) || aliasRegistry.contains(name) then
          Some(0)
        else if name == "List" then Some(1)
        else if Set("Int", "Double", "String", "Bool").contains(name) then
          Some(0)
        else enumArity.get(name)
      arity match
        case None =>
          errors += TypeError(s"Unknown type: $name", location)
          TError
        case Some(expectedArity) if arguments.length != expectedArity =>
          errors += TypeError(
            s"Type $name expects $expectedArity type arguments, got ${arguments
                .length}",
            location,
          )
          TError
        case _ if typeParameters.contains(name) => typeParameters(name)
        case _ if aliasRegistry.contains(name)  =>
          // Aliases expand structurally, so revisiting one would loop. Enum names
          // stay nominal: their fields are validated separately, allowing recursion.
          if visitedAliases.contains(name) then
            errors += TypeError(s"Cyclic type alias: $name", location)
            TError
          else
            // Aliases are closed declarations: use their declaration scope.
            resolveAnnotation(
              aliasRegistry(name),
              Map.empty,
              location,
              visitedAliases + name,
            )
        case _ => TName(name, resolvedArguments)
    case TList(elementType) =>
      TList(
        resolveAnnotation(elementType, typeParameters, location, visitedAliases)
      )
    case TFun(parameterTypes, resultType) =>
      TFun(
        parameterTypes
          .map(resolveAnnotation(_, typeParameters, location, visitedAliases)),
        resolveAnnotation(resultType, typeParameters, location, visitedAliases),
      )
    case TTuple(elementTypes) =>
      TTuple(
        elementTypes
          .map(resolveAnnotation(_, typeParameters, location, visitedAliases))
      )
    case other => other

  // ---------------------------------------------------------------------------
  // Expression inference
  // ---------------------------------------------------------------------------

  /** Synthesis infers an expression's type from its syntax and children. `env`
    * maps value names to signatures; `rigid` maps declared type parameters to
    * their scope-owned TParam identities. Pending types are finalized later.
    */
  def synthExpr(
      e: Expr,
      env: Map[String, Scheme],
      rigid: Map[String, Type],
  ): Type =
    val inferredType = e match
      case Lit(IntLit(_), _)     => TInt
      case Lit(DoubleLit(_), _)  => TDouble
      case Lit(StrLit(_), _)     => TString
      case Lit(BoolLit(_), _)    => TBool
      case Lit(NilLit, _)        => TList(freshVar())
      case Lit(MapPrim, _)       => instantiate(mapScheme, e)
      case Lit(FilterPrim, _)    => instantiate(filterScheme, e)
      case Lit(FoldLeftPrim, _)  => instantiate(foldLeftScheme, e)
      case Lit(FoldRightPrim, _) => instantiate(foldRightScheme, e)

      case Var(name, _) =>
        env.get(name) match
          case Some(scheme) => instantiate(scheme, e)
          case None         =>
            errors += TypeError(s"Unbound variable: $name", e.location)
            freshVar()

      case UnOp("-", inner, _) =>
        val innerType = synthExpr(inner, env, rigid)
        requireNumeric(innerType, " in unary -", e.location)
        innerType
      case UnOp("!", inner, _) =>
        checkExpr(inner, TBool, env, rigid)
        TBool
      case UnOp(op, inner, _) =>
        errors += TypeError(s"Unknown unary operator: $op", e.location)
        synthExpr(inner, env, rigid)
        freshVar()

      case BinOp("||" | "&&", left, right, _) =>
        checkExpr(left, TBool, env, rigid)
        checkExpr(right, TBool, env, rigid)
        TBool

      case BinOp("<" | ">" | "<=" | ">=", left, right, _) =>
        val leftType = synthExpr(left, env, rigid)
        checkExpr(right, leftType, env, rigid)
        requireNumeric(leftType, " in comparison", e.location)
        TBool

      case BinOp("==" | "!=", left, right, _) =>
        val leftType = synthExpr(left, env, rigid)
        checkExpr(right, leftType, env, rigid)
        TBool

      case BinOp("::", head, tail, _) =>
        val headType = synthExpr(head, env, rigid)
        checkExpr(tail, TList(headType), env, rigid)
        TList(headType)

      case BinOp("+" | "-" | "*" | "/", left, right, _) =>
        val leftType = synthExpr(left, env, rigid)
        checkExpr(right, leftType, env, rigid)
        requireNumeric(leftType, " in arithmetic", e.location)
        leftType

      case BinOp(op, left, right, _) =>
        errors += TypeError(s"Unknown binary operator: $op", e.location)
        synthExpr(left, env, rigid)
        synthExpr(right, env, rigid)
        freshVar()

      case Apply(fun, args, _) =>
        val functionType = synthExpr(fun, env, rigid)
        applyArgs(functionType, args, env, rigid, e.location)

      case Lambda(params, body, _) =>
        val parameterTypes =
          params.map((name, inferredType) =>
            name -> resolveAnnotation(inferredType, rigid, e.location)
          )
        val parameterEnvironment = env ++
          parameterTypes
            .map((name, inferredType) => name -> Scheme(Nil, inferredType))
        val bodyType = synthExpr(body, parameterEnvironment, rigid)
        TFun(parameterTypes.map(_._2), bodyType)

      case If(cond, thenBranch, elseBranch, _) =>
        checkExpr(cond, TBool, env, rigid)
        val branchType = synthExpr(thenBranch, env, rigid)
        checkExpr(elseBranch, branchType, env, rigid)
        branchType

      case constructor @ Construct(_, _, _) =>
        inferConstructor(constructor, env, rigid)

      case matching @ Match(_, _, _) =>
        inferMatch(matching, env, rigid)

      case Tuple(elems, _) =>
        TTuple(elems.map(synthExpr(_, env, rigid)))

    inferredExpressions +=
      InferredExpression(e, inferredType, rigid.values.toSet)
    inferredType

  /** Checking infers the expression's type, then unifies it with the expected
    * type. It uses the same value and declared-parameter environments as
    * synthesis.
    */
  def checkExpr(
      e: Expr,
      expected: Type,
      env: Map[String, Scheme],
      rigid: Map[String, Type],
  ): Unit =
    val actual = synthExpr(e, env, rigid)
    unify(actual, expected, e.location)

  private def inferConstructor(
      expression: Construct,
      valueEnvironment: Map[String, Scheme],
      typeParameters: Map[String, Type],
  ): Type =
    val name = expression.name
    val arguments = expression.args
    constructorRegistry.get(name) match
      case None =>
        errors += TypeError(s"Unknown constructor: $name", expression.location)
        arguments.foreach(synthExpr(_, valueEnvironment, typeParameters))
        freshVar()
      case Some(
            ConstructorInfo(enumName, constructorTypeParameters, fieldTypes)
          ) =>
        if arguments.length != fieldTypes.length then
          errors += TypeError(
            s"Constructor $name expects ${fieldTypes.length} args, got ${arguments
                .length}",
            expression.location,
          )
        val typeArgumentsByParameter: Map[Type, Type] =
          constructorTypeParameters.map(_ -> freshVar()).toMap
        val argumentTypes = arguments
          .map(synthExpr(_, valueEnvironment, typeParameters))
        argumentTypes.zip(fieldTypes).zip(arguments).foreach {
          case ((argumentType, fieldType), argument) =>
            unify(
              argumentType,
              substituteTypeParameters(fieldType, typeArgumentsByParameter),
              argument.location,
            )
        }
        val instantiatedTypeArguments = constructorTypeParameters
          .map(typeArgumentsByParameter)
        expression.typeArguments = instantiatedTypeArguments
        TName(enumName, instantiatedTypeArguments)

  private def inferMatch(
      expression: Match,
      valueEnvironment: Map[String, Scheme],
      typeParameters: Map[String, Type],
  ): Type =
    val scrutinee = expression.scrutinee
    val cases = expression.cases
    val scrutineeType = followSubstitutions(
      synthExpr(scrutinee, valueEnvironment, typeParameters)
    )
    // An unsolved scrutinee can learn its enum from the first constructor pattern.
    val enumType: Option[Type] = scrutineeType match
      case TName(name, _) if enumArity.contains(name) => Some(scrutineeType)
      case TMeta(_)                                   =>
        cases.headOption
          .flatMap(matchCase => constructorRegistry.get(matchCase.ctor)).map {
            info =>
              val inferredEnumType =
                TName(
                  info.enumName,
                  info.typeParameters.map(_ => freshVar()),
                )
              unify(scrutineeType, inferredEnumType, expression.location)
              inferredEnumType
          }
      case _ => None

    enumType match
      case None =>
        errors += TypeError(
          s"Cannot match on non-enum type: ${resolveType(scrutineeType)}",
          expression.location,
        )
        cases.foreach(matchCase =>
          synthExpr(matchCase.body, valueEnvironment, typeParameters)
        )
        freshVar()
      case Some(TName(enumName, enumArguments)) =>
        // Check constructor coverage, bind substituted field types, and unify branch results.
        val coveredConstructors = mutable.Set.empty[String]
        var resultType: Option[Type] = None
        cases.foreach { matchCase =>
          constructorRegistry.get(matchCase.ctor) match
            case None =>
              errors += TypeError(
                s"Unknown constructor in match: ${matchCase.ctor}",
                matchCase.location,
              )
              synthExpr(matchCase.body, valueEnvironment, typeParameters)
            case Some(ConstructorInfo(constructorEnum, _, _))
                if constructorEnum != enumName =>
              errors += TypeError(
                s"Constructor ${matchCase.ctor} does not belong to enum $enumName",
                matchCase.location,
              )
              synthExpr(matchCase.body, valueEnvironment, typeParameters)
            case Some(
                  ConstructorInfo(_, constructorTypeParameters, fieldTypes)
                ) =>
              if !coveredConstructors.add(matchCase.ctor) then
                errors += TypeError(
                  s"Duplicate case for constructor ${matchCase.ctor}",
                  matchCase.location,
                )
              if matchCase.bindings.length != fieldTypes.length then
                errors += TypeError(
                  s"Pattern ${matchCase.ctor} expects ${fieldTypes
                      .length} bindings, got ${matchCase.bindings.length}",
                  matchCase.location,
                )
              val typeArgumentsByParameter = constructorTypeParameters
                .zip(enumArguments).toMap
              val bindingTypes = fieldTypes
                .map(substituteTypeParameters(_, typeArgumentsByParameter))
              matchCase.bindingTypes = bindingTypes
              val caseEnvironment = valueEnvironment ++
                matchCase.bindings.zip(bindingTypes)
                  .map((name, bindingType) => name -> Scheme(Nil, bindingType))
              val bodyType =
                synthExpr(matchCase.body, caseEnvironment, typeParameters)
              resultType match
                case None                     => resultType = Some(bodyType)
                case Some(previousResultType) =>
                  unify(bodyType, previousResultType, matchCase.body.location)
        }
        val missingConstructors = enumConstructors.getOrElse(enumName, Nil)
          .filterNot(coveredConstructors.contains)
        if missingConstructors.nonEmpty then
          errors += TypeError(
            s"Non-exhaustive match on $enumName: missing ${missingConstructors.mkString(", ")}",
            expression.location,
          )
        resultType.getOrElse(freshVar())
      case _ => TError

  private def applyArgs(
      functionType: Type,
      arguments: List[Expr],
      valueEnvironment: Map[String, Scheme],
      typeParameters: Map[String, Type],
      location: Location,
  ): Type =
    // Synthesize every argument even when the callee or arity is invalid.
    val argumentTypes = arguments
      .map(synthExpr(_, valueEnvironment, typeParameters))
    followSubstitutions(functionType) match
      case TFun(parameterTypes, result) =>
        if parameterTypes.length != arguments.length then
          errors += TypeError(
            s"Function expects ${parameterTypes.length} args, got ${arguments
                .length}",
            location,
          )
        argumentTypes.zip(parameterTypes)
          .foreach((argumentType, parameterType) =>
            unify(argumentType, parameterType, location)
          )
        result
      case TMeta(_) =>
        val result = freshVar()
        unify(functionType, TFun(argumentTypes, result), location)
        result
      case TError => TError
      case other  =>
        errors += TypeError(
          s"Applying non-function type: ${resolveType(other)}",
          location,
        )
        TError

  // Later uses may determine Int or Double, so record this requirement for phase 4.
  private def requireNumeric(
      inferredType: Type,
      diagnosticContext: String,
      location: Location,
  ): Unit =
    numericRequirements +=
      NumericRequirement(inferredType, diagnosticContext, location)

  // ---------------------------------------------------------------------------
  // Unification and substitution
  // ---------------------------------------------------------------------------

  private def freshVar(): Type =
    nextTypeVariableId += 1
    TMeta(nextTypeVariableId)

  // Follow solved variables at the root only: for List[TMeta(1)], this does
  // not follow substitutions in the element. resolveType also visits that child.
  private def followSubstitutions(inferredType: Type): Type = inferredType match
    case TMeta(id) =>
      substitutions.get(id).fold(inferredType)(followSubstitutions)
    case _ => inferredType

  private def children(inferredType: Type): List[Type] = inferredType match
    case TList(elementType)               => List(elementType)
    case TFun(parameterTypes, resultType) => parameterTypes :+ resultType
    case TTuple(elementTypes)             => elementTypes
    case TName(_, arguments)              => arguments
    case _                                => Nil

  // Reject bindings such as ?a = List[?a], which would create an infinite type.
  private def occurs(id: Int, inferredType: Type): Boolean =
    val resolvedType = followSubstitutions(inferredType)
    resolvedType == TMeta(id) || children(resolvedType).exists(occurs(id, _))

  /** Enforce equality, solving inference variables and comparing type
    * components. Rigid parameters unify only with themselves (or an unsolved
    * variable).
    */
  def unify(actual: Type, expected: Type, location: Location): Boolean =
    val resolvedActual = followSubstitutions(actual)
    val resolvedExpected = followSubstitutions(expected)
    (resolvedActual, resolvedExpected) match
      case (TError, _) | (_, TError)               => false
      case _ if resolvedActual == resolvedExpected => true
      case (TMeta(id), _) => bind(id, resolvedExpected, location)
      case (_, TMeta(id)) => bind(id, resolvedActual, location)
      case (TList(actualElement), TList(expectedElement)) =>
        unify(actualElement, expectedElement, location)
      case (
            TFun(actualComponents, actualResult),
            TFun(expectedComponents, expectedResult),
          ) if actualComponents.length == expectedComponents.length =>
        val parametersMatch =
          unifyAll(actualComponents, expectedComponents, location)
        val resultsMatch = unify(actualResult, expectedResult, location)
        parametersMatch && resultsMatch
      case (TTuple(actualComponents), TTuple(expectedComponents))
          if actualComponents.length == expectedComponents.length =>
        unifyAll(actualComponents, expectedComponents, location)
      case (
            TName(actualName, actualComponents),
            TName(expectedName, expectedComponents),
          )
          if actualName == expectedName &&
            actualComponents.length == expectedComponents.length =>
        unifyAll(actualComponents, expectedComponents, location)
      case _ =>
        errors += TypeError(
          s"Type mismatch: expected ${resolveType(resolvedExpected)} but got ${resolveType(resolvedActual)}",
          location,
        )
        false

  private def bind(id: Int, inferredType: Type, location: Location): Boolean =
    if occurs(id, inferredType) then
      errors += TypeError("Occurs check failed: infinite type", location)
      false
    else
      substitutions(id) = inferredType
      true

  // Evaluate every pair before combining results so later errors are reported too.
  private def unifyAll(
      actualTypes: List[Type],
      expectedTypes: List[Type],
      location: Location,
  ): Boolean =
    actualTypes.zip(expectedTypes)
      .map((actual, expected) => unify(actual, expected, location))
      .forall(identity)

  // Resolve substitutions throughout a type, keeping unsolved variable identities
  // intact for the final ambiguity check and AST dump.
  private def resolveType(inferredType: Type): Type = followSubstitutions(
    inferredType
  ) match
    case TList(elementType)               => TList(resolveType(elementType))
    case TFun(parameterTypes, resultType) =>
      TFun(parameterTypes.map(resolveType), resolveType(resultType))
    case TTuple(elementTypes)   => TTuple(elementTypes.map(resolveType))
    case TName(name, arguments) => TName(name, arguments.map(resolveType))
    case other                  => other

  private def substituteTypeParameters(
      inferredType: Type,
      mapping: Map[Type, Type],
  ): Type = inferredType match
    case TParam(_, _)       => mapping.getOrElse(inferredType, inferredType)
    case TList(elementType) =>
      TList(substituteTypeParameters(elementType, mapping))
    case TFun(parameterTypes, resultType) =>
      TFun(
        parameterTypes.map(substituteTypeParameters(_, mapping)),
        substituteTypeParameters(resultType, mapping),
      )
    case TTuple(elementTypes) =>
      TTuple(elementTypes.map(substituteTypeParameters(_, mapping)))
    case TName(name, arguments) =>
      TName(name, arguments.map(substituteTypeParameters(_, mapping)))
    case _ => inferredType

  // Each generic use gets fresh TMeta arguments in place of its TParam parameters.
  // With no declared parameters, substitution retains shared monomorphic variables.
  private def instantiate(scheme: Scheme, reference: Expr): Type =
    val arguments = scheme.vars.map(_ => freshVar())
    reference.typeArguments = arguments
    substituteTypeParameters(scheme.body, scheme.vars.zip(arguments).toMap)

  private def mapScheme: Scheme =
    val a = TParam("A", 0)
    val b = TParam("B", 0)
    Scheme(List(a, b), TFun(List(TFun(List(a), b), TList(a)), TList(b)))

  private def filterScheme: Scheme =
    val a = TParam("A", 0)
    Scheme(List(a), TFun(List(TList(a), TFun(List(a), TBool)), TList(a)))

  private def foldLeftScheme: Scheme =
    val a = TParam("A", 0)
    val b = TParam("B", 0)
    Scheme(List(a, b), TFun(List(TList(a), b, TFun(List(b, a), b)), b))

  private def foldRightScheme: Scheme =
    val a = TParam("A", 0)
    val b = TParam("B", 0)
    Scheme(List(a, b), TFun(List(TList(a), b, TFun(List(a, b), b)), b))

  // ---------------------------------------------------------------------------
  // Finalization
  // ---------------------------------------------------------------------------

  private def checkNumericRequirements(): Unit =
    numericRequirements.foreach { requirement =>
      val NumericRequirement(inferredType, diagnosticContext, location) =
        requirement
      followSubstitutions(inferredType) match
        case TInt | TDouble | TError => ()
        case TMeta(_)                =>
          errors += TypeError(
            s"Cannot infer a numeric type$diagnosticContext",
            location,
          )
        case other =>
          errors += TypeError(
            s"Expected int or double$diagnosticContext, got ${resolveType(other)}",
            location,
          )
    }

  private def finalizeTypes(program: Program): Unit =
    // Later uses may resolve earlier types. Diagnose unsolved variables and rigid
    // parameters outside their declaring scope only after all constraints are known.
    // Earlier errors suppress these secondary diagnostics, but annotations are still resolved.
    val reportAmbiguity = errors.isEmpty
    val reportedVariables = mutable.Set.empty[Int]
    val escapedParameters = mutable.Set.empty[Type]
    def finalizeType(
        inferredType: Type,
        location: Location,
        allowedTypeParameters: Set[Type],
    ): Type =
      val resolvedType = resolveType(inferredType)
      def check(inferredType: Type): Unit = inferredType match
        case TMeta(id) if reportAmbiguity && reportedVariables.add(id) =>
          errors += TypeError(s"Ambiguous inferred type: TMeta($id)", location)
        case TParam(_, _)
            if reportAmbiguity &&
              !allowedTypeParameters.contains(inferredType) &&
              escapedParameters.add(inferredType) =>
          errors += TypeError(
            s"Declared type parameter escapes its scope: $inferredType",
            location,
          )
        case _ => children(inferredType).foreach(check)
      check(resolvedType)
      resolvedType
    inferredExpressions.foreach { inferred =>
      val expression = inferred.expression
      expression.tpe = Some(
        finalizeType(
          inferred.inferredType,
          expression.location,
          inferred.allowedTypeParameters,
        )
      )
      expression.typeArguments = expression.typeArguments
        .map(
          finalizeType(_, expression.location, inferred.allowedTypeParameters)
        )
    }
    program.decls.foreach { declaration =>
      declaration.resolvedType = declaration.resolvedType
        .map(
          finalizeType(
            _,
            declaration.location,
            declarationTypeParameters.get(declaration).values.toSet,
          )
        )
      declaration match
        case EnumDecl(_, _, cases) =>
          cases.foreach(constructor =>
            constructor.fieldTypes = constructor.fieldTypes.map(resolveType)
          )
        case _ => ()
    }
    inferredExpressions.foreach { inferred =>
      inferred.expression match
        case Match(_, cases, _) =>
          cases.foreach(matchCase =>
            matchCase.bindingTypes = matchCase.bindingTypes.map(resolveType)
          )
        case _ => ()
    }
