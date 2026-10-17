package scairrtl.dialects

import fastparse.*
import scair.clair.*
import scair.dialects.builtin.*
import scair.enums.I32Enum
import scair.ir.*
import scair.parse.Parser
import scair.parse.intDataP
import scair.print.Printer
import scair.utils.*

/*≡==--==≡≡≡≡≡≡≡≡≡≡≡≡≡==--=≡≡*\
||     GENERIC TRAITS        ||
\*≡==---==≡≡≡≡≡≡≡≡≡≡≡==---==≡*/

/** Every region of this operation holds exactly one block. */
trait SingleBlock extends Operation:

  override def traitVerify(): OK[Operation] =
    val checked =
      if regions.exists(_.blocks.length != 1) then
        Err(s"SingleBlock operation '$name' requires single-block regions")
      else OK(this)
    checked.flatMap(_ => super.traitVerify())

/** No region entry block declares arguments. */
trait NoRegionArguments extends Operation:

  override def traitVerify(): OK[Operation] =
    val hasArguments =
      regions.exists(_.blocks.exists(_.arguments.nonEmpty))
    val checked =
      if hasArguments then
        Err(
          s"NoRegionArguments operation '$name' must not declare region arguments"
        )
      else OK(this)
    checked.flatMap(_ => super.traitVerify())

/** Restricts the kinds of operation this one may be nested in. */
trait HasParent extends Operation:

  /** The operation kinds this operation may be nested in. */
  def permittedParents: Seq[Class[? <: Operation]]

  override def traitVerify(): OK[Operation] =
    val parent = containerBlock.flatMap(_.containerRegion)
      .flatMap(_.containerOperation)
    val names = permittedParents.map(_.getSimpleName)
    val checked =
      if parent.exists(p => permittedParents.exists(_.isInstance(p))) then
        OK(this)
      else if names.length == 1 then
        Err(s"Operation '$name' must be nested in ${names.head}")
      else
        Err(
          s"Operation '$name' must be nested in one of ${names.mkString(", ")}"
        )
    checked.flatMap(_ => super.traitVerify())

/** All operands carry the same type. */
trait SameTypeOperands extends Operation:

  override def traitVerify(): OK[Operation] =
    val checked =
      if operands.isEmpty then OK(this)
      else
        val first = operands.head.typ
        if operands.forall(_.typ == first) then OK(this)
        else Err(s"All operands of '$name' must have the same type")
    checked.flatMap(_ => super.traitVerify())

/** The operation's symbol name is non-empty. */
trait SymbolName extends Operation:
  def sym_name: StringData

  override def traitVerify(): OK[Operation] =
    val checked =
      if sym_name.stringLiteral.nonEmpty then OK(this)
      else
        Err(s"SymbolName operation '$name' must have a non-empty symbol name")
    checked.flatMap(_ => super.traitVerify())

/** `sym_visibility`, when present, is a legal visibility keyword. */
trait SymbolVisibility extends Operation:
  def sym_visibility: Option[StringData]

  override def traitVerify(): OK[Operation] =
    val checked = sym_visibility match
      case None             => OK(this)
      case Some(visibility) =>
        if symbolVisibilities.contains(visibility.stringLiteral) then OK(this)
        else
          Err(
            s"Operation '$name' has symbol visibility " +
              s"'${visibility.stringLiteral}'; expected one of " +
              symbolVisibilities.mkString(", ")
          )
    checked.flatMap(_ => super.traitVerify())

/** The operation holds symbol references that must resolve. */
trait SymbolUserOpInterface extends Operation:

  /** Every symbol reference this operation holds. */
  def symbolUses: Seq[SymbolRefAttr]

  /** The table references resolve against: this operation when it is itself a
    * symbol table, otherwise the nearest enclosing one.
    */
  protected def symbolScope: Option[SymbolTable] = nearestSymbolTable(this)

  override def traitVerify(): OK[Operation] =
    val scope = symbolScope
    val unresolved = symbolUses.filter(ref => resolveSymbol(scope, ref).isEmpty)
    val checked =
      if unresolved.isEmpty then OK(this)
      else
        Err(
          s"Operation '$name' references undefined symbols: " +
            unresolved.map(_.toString).mkString(", ")
        )
    checked.flatMap(_ => super.traitVerify())

/** The class each operand and result must be: operands first, then results. */
trait OperandAndResultTypes extends Operation:

  /** The required class of each operand, then of each result. */
  def operandResultTypes: Seq[Class[? <: Attribute]]

  override def traitVerify(): OK[Operation] =
    val actual = (operands ++ results).map(_.typ)
    val expected = operandResultTypes
    val found =
      if actual.length != expected.length then
        Seq(
          s"${actual.length} operands and results, expected ${expected.length}"
        )
      else
        actual.zip(expected).collect {
          case (value, required) if !required.isInstance(value) =>
            s"${value.name}, expected ${required.getSimpleName}"
        }
    val checked =
      if found.isEmpty then OK(this)
      else
        Err(
          s"Operation '$name' has ill-typed operands or results: " +
            found.mkString(", ")
        )
    checked.flatMap(_ => super.traitVerify())

/** The declared results must match the types this operation infers.
  */
trait InfersResultTypes extends Operation:

  /** The types this operation's results should have. */
  def inferResultTypes: Seq[Attribute]

  override def traitVerify(): OK[Operation] =
    val inferred = inferResultTypes
    val declared = results.map(_.typ)
    val checked =
      if inferred == declared then OK(this)
      else
        Err(
          s"Operation '$name' declares results ${declared.mkString(", ")} " +
            s"but infers ${inferred.mkString(", ")}"
        )
    checked.flatMap(_ => super.traitVerify())

/** The keyword set `sym_visibility` accepts. */
private val symbolVisibilities = Seq("public", "private", "nested")

/** The nearest symbol table at or above `op`. */
private def nearestSymbolTable(op: Operation): Option[SymbolTable] =
  op match
    case table: SymbolTable => Some(table)
    case _                  =>
      op.containerBlock.flatMap(_.containerRegion).flatMap(_.containerOperation)
        .flatMap(nearestSymbolTable)

/** Look `name` up in `table` without throwing when it is absent. */
private def lookupSymbol(table: SymbolTable, name: String): Option[Operation] =
  table.regions.headOption.flatMap(_.blocks.headOption)
    .flatMap(_.operations.find {
      case symbol: Symbol => symbol.sym_name.stringLiteral == name
      case _              => false
    })

/** Resolve a symbol reference through the nested tables it names. */
private def resolveSymbol(
    scope: Option[SymbolTable],
    ref: SymbolRefAttr,
): Option[Operation] =
  val root = scope
    .flatMap(table => lookupSymbol(table, ref.rootRef.stringLiteral))
  ref.nestedRefs.data.foldLeft(root) { (current, nested) =>
    current.flatMap {
      case table: SymbolTable => lookupSymbol(table, nested.stringLiteral)
      case _                  => None
    }
  }

/*≡==--==≡≡≡≡≡≡≡≡≡≡≡==--=≡≡*\
||   DIALECT-LOCAL TRAITS    ||
\*≡==---==≡≡≡≡≡≡≡≡≡≡≡==---==≡*/

/** Both operands are `UInt`, or both are `SInt`. */
trait SameOperandsIntTypeKind extends Operation:

  override def traitVerify(): OK[Operation] =
    val types = operands.map(_.typ)
    val sameKind = types.forall(_.isInstanceOf[UIntType]) ||
      types.forall(_.isInstanceOf[SIntType])
    val checked =
      if types.isEmpty || sameKind then OK(this)
      else Err(s"Operands of '$name' must be all UInt or all SInt")
    checked.flatMap(_ => super.traitVerify())

/** The listed operand positions hold a `UInt` of at most one bit, or one whose
  * width is uninferred, which is what a select or a reset may be.
  */
trait OneBitOperands extends Operation:

  /** Operand positions that must hold a one-bit or unsized `UInt`. */
  def oneBitOperands: Seq[Int]

  override def traitVerify(): OK[Operation] =
    val offending = oneBitOperands.filter { index =>
      operands.lift(index).map(_.typ) match
        case Some(UIntType(width)) => width.value > 1
        case _                     => true
    }
    val checked =
      if offending.isEmpty then OK(this)
      else
        Err(
          s"Operation '$name' needs a one-bit UInt at operand " +
            offending.mkString(", ")
        )
    checked.flatMap(_ => super.traitVerify())

/** A `bits` selection is ordered, non-negative, and inside the input width. */
trait BitsSelectionRange extends Operation:

  def input: Operand[IntType]
  def hi: IntegerAttr
  def lo: IntegerAttr

  override def traitVerify(): OK[Operation] =
    val (high, low) = (hi.value.value, lo.value.value)
    val width: BigInt = input.typ match
      case t: UIntType => t.width.value
      case t: SIntType => t.width.value
      case _           => BigInt(-1)
    val problem =
      if low < 0 || high < low then Some(s"$high to $low is not a valid range")
      else if width >= 0 && high >= width then
        Some(s"$high to $low exceeds the $width-bit input")
      else None
    val checked = problem match
      case None      => OK(this)
      case Some(why) => Err(s"Operation '$name' has a bad bits range: $why")
    checked.flatMap(_ => super.traitVerify())

/** The wider of two widths, or uninferred when either is unknown. */
private def widerWidth(left: IntData, right: IntData): IntData =
  if left.value < 0 || right.value < 0 then unknownWidth
  else IntData(left.value.max(right.value))

/*≡==--==≡≡≡≡≡≡≡≡≡≡≡==--=≡≡*\
||         ENUMS           ||
\*≡==---==≡≡≡≡≡≡≡==---==≡*/

// Integer-backed, so they print as ordinals and only a custom op syntax can turn
// them into keywords.
// TODO: switch to a keyword-preserving form once ScaIR supports enum assembly.

/** Whether a wire's name may be dropped by name-preservation passes. */
enum NameKindEnum(name: String) extends I32Enum(name):
  case DroppableName extends NameKindEnum("droppable_name")
  case InterestingName extends NameKindEnum("interesting_name")

/** The lowering convention a module is instantiated with. */
enum ConventionEnum(name: String) extends I32Enum(name):
  case Internal extends ConventionEnum("internal")
  case Scalarized extends ConventionEnum("scalarized")

/** How a layer's contents are lowered into the design. */
enum LayerConventionEnum(name: String) extends I32Enum(name):
  case Bind extends LayerConventionEnum("bind")
  case Inline extends LayerConventionEnum("inline")

/*≡==--==≡≡≡≡≡≡≡≡≡≡≡==--=≡≡*\
||         TYPES           ||
\*≡==---==≡≡≡≡≡≡==---==≡*/

/** Common supertype of every ScaIRRTL value type, so operations can accept any
  * of them in an operand or result position.
  */
sealed trait RTLType extends TypeAttribute

/** Both integer signednesses, so integer operations cannot take a clock. */
sealed trait IntType extends RTLType

/** The width of an integer whose width has not been inferred yet. */
val unknownWidth: IntData = IntData(-1)

/** An unsigned integer of `width` bits, or uninferred when written without one.
  */
final case class UIntType(width: IntData = unknownWidth)
    extends IntType
    with DerivedAttribute["scairrtl.uint"] derives AttrDefs:

  override def customPrint(p: Printer) =
    if width.value < 0 then p.print("!scairrtl.uint")
    else p.print("!scairrtl.uint<", width.value.toString, ">")

object UIntType:

  given AttributeCustomParser[UIntType] with

    def parse[$: P](using Parser): P[UIntType] =
      P(("<" ~/ intDataP ~ ">").?.map {
        case Some(width) => UIntType(width)
        case None        => UIntType()
      })

/** A signed two's-complement integer of `width` bits. */
final case class SIntType(width: IntData = unknownWidth)
    extends IntType
    with DerivedAttribute["scairrtl.sint"] derives AttrDefs:

  override def customPrint(p: Printer) =
    if width.value < 0 then p.print("!scairrtl.sint")
    else p.print("!scairrtl.sint<", width.value.toString, ">")

object SIntType:

  given AttributeCustomParser[SIntType] with

    def parse[$: P](using Parser): P[SIntType] =
      P(("<" ~/ intDataP ~ ">").?.map {
        case Some(width) => SIntType(width)
        case None        => SIntType()
      })

/** The clock signal type. */
final case class ClockType()
    extends RTLType
    with DerivedAttribute["scairrtl.clock"] derives AttrDefs

// [TODO]     AnalogType, ResetType, AsyncResetType, RefType, and the aggregate
//            types (BundleType, FVectorType, FEnumType). RefType is required
//            before a forceable wire can expose its `ref` result.
// [TODO]     every CIRCT base type also carries an `isConst` parameter, spelled
//            with a `const.` prefix on the type name.
// [TODO]     reject a negative width when parsing, as CIRCT does.

case class CircuitOp(
    body: Region,
    sym_name: StringData,
    annotations: ArrayAttribute[Attribute] = ArrayAttribute(),
    enable_layers: Option[ArrayAttribute[SymbolRefAttr]] = None,
    disable_layers: Option[ArrayAttribute[SymbolRefAttr]] = None,
    select_inst_choice: Option[ArrayAttribute[Attribute]] = None,
) extends DerivedOperation["scairrtl.circuit"]
    with IsolatedFromAbove
    with SymbolTable
    with SingleBlock
    with NoTerminator
    with NoRegionArguments
    with SymbolUserOpInterface derives OpDefs:

  def symbolUses: Seq[SymbolRefAttr] =
    Seq(enable_layers, disable_layers).flatten.flatMap(_.data)

  // [HIGH] InnerRefNamespace — symbol/ref resolution across the circuit
  //        boundary. TODO
  // [TODO] default_layer_specialization (LayerSpecializationAttr)
  // [TODO] OpAsmOpInterface, for named entry-block arguments; blocked on a
  //        naming hook in ScaIR's printer.

case class ModuleOp(
    body: Region,
    sym_name: StringData,
    port_directions: ArrayAttribute[Attribute],
    port_types: ArrayAttribute[Attribute],
    port_names: ArrayAttribute[Attribute],
    port_locations: ArrayAttribute[Attribute],
    port_annotations: ArrayAttribute[Attribute],
    port_symbols: ArrayAttribute[Attribute],
    sym_visibility: Option[StringData] = None,
    convention: ConventionEnum,
    annotations: ArrayAttribute[Attribute] = ArrayAttribute(),
    layers: ArrayAttribute[SymbolRefAttr] = ArrayAttribute(),
    domain_info: ArrayAttribute[Attribute] = ArrayAttribute(),
) extends DerivedOperation["scairrtl.module"]
    with IsolatedFromAbove
    with Symbol
    with SymbolTable
    with SingleBlock
    with NoTerminator
    with HasParent
    with SymbolName
    with SymbolVisibility
    with SymbolUserOpInterface derives OpDefs:

  def permittedParents: Seq[Class[? <: Operation]] = Seq(classOf[CircuitOp])

  def symbolUses: Seq[SymbolRefAttr] = layers.data

  // A module enables layers defined by the enclosing circuit, so its references
  // resolve from the parent scope rather than from its own symbol table.
  override protected def symbolScope: Option[SymbolTable] =
    containerBlock.flatMap(_.containerRegion).flatMap(_.containerOperation)
      .flatMap(nearestSymbolTable)

  // [BLOCKING] entry-block arguments — CIRCT's verifier requires exactly one
  //            argument per port, with argument types matching port_types and
  //            argument locations matching port_locations. Without that verifier
  //            a module body cannot reference a single port. TODO
  // [HIGH]     port_directions holds untyped attributes, so a malformed direction
  //            cannot be rejected. TODO: give direction a type.
  // [TODO]     convention is required in CIRCT; optional here until its
  //            semantics are implemented.
  // [TODO]     InnerSymbolTable, PortList, FModuleLike,
  //            InstanceGraphModuleOpInterface, OpAsmOpInterface

case class LayerOp(
    body: Region,
    sym_name: StringData,
    sym_visibility: Option[StringData] = None,
    convention: LayerConventionEnum,
) extends DerivedOperation["scairrtl.layer"]
    with IsolatedFromAbove
    with Symbol
    with SymbolTable
    with SingleBlock
    with NoTerminator
    with HasParent
    with SymbolName
    with SymbolVisibility derives OpDefs:

  def permittedParents: Seq[Class[? <: Operation]] =
    Seq(classOf[CircuitOp], classOf[LayerOp])

  // [TODO] LayerSpecialization ("enable" or "disable" for the circuit's
  //        default).

case class WireOp(
    result: Result[RTLType],
    sym_name: StringData,
    nameKind: NameKindEnum,
    annotations: ArrayAttribute[Attribute],
    forceable: Option[UnitAttr] = None,
) extends DerivedOperation["scairrtl.wire"]
    with HasParent derives OpDefs:

  def permittedParents: Seq[Class[? <: Operation]] = Seq(classOf[ModuleOp])

  // [HIGH] nameKind is recorded but nothing consumes it, so no name is preserved
  //        yet. TODO: a name-preservation pass.
  // [HIGH] CIRCT splits the name into a plain string plus an optional inner_sym;
  //        `sym_name` covers only the first, so lookup has no home. TODO
  // [TODO] domains operand, and the optional ref result
  // [TODO] permittedParents should also admit LayerBlockOp, WhenOp and MatchOp
  //        once those exist.
  // [TODO] ForceableOpInterface, whose verifier is what makes `forceable`
  //        meaningful; FNamableOpInterface, OpAsmOpInterface (CIRCT's
  //        HasCustomSSAName), InnerSymbolOpInterface, SymbolUserOpInterface

/** A register clocked by `clock`, holding its value across cycles. */
case class RegOp(
    result: Result[IntType],
    clock: Operand[ClockType],
    sym_name: StringData,
    nameKind: NameKindEnum,
    annotations: ArrayAttribute[Attribute],
    forceable: Option[UnitAttr] = None,
) extends DerivedOperation["scairrtl.reg"]
    with OperandAndResultTypes
    with HasParent derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(classOf[ClockType], classOf[IntType])

  def permittedParents: Seq[Class[? <: Operation]] = Seq(classOf[ModuleOp])

  // [TODO] the result type has no reset value to infer from, so a wrong width
  //        goes unchecked.

/** A register clocked by `clock` that resets to `init` while `reset` is set. */
case class RegResetOp(
    result: Result[IntType],
    clock: Operand[ClockType],
    reset: Operand[UIntType],
    init: Operand[IntType],
    sym_name: StringData,
    nameKind: NameKindEnum,
    annotations: ArrayAttribute[Attribute],
    forceable: Option[UnitAttr] = None,
) extends DerivedOperation["scairrtl.regreset"]
    with OperandAndResultTypes
    with OneBitOperands
    with InfersResultTypes
    with HasParent derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(
      classOf[ClockType],
      classOf[UIntType],
      classOf[IntType],
      classOf[IntType],
    )

  def oneBitOperands: Seq[Int] = Seq(1)

  def inferResultTypes: Seq[Attribute] = Seq(init.typ)

  def permittedParents: Seq[Class[? <: Operation]] = Seq(classOf[ModuleOp])

  // [TODO] Chisel emits `firrtl.random_init_start` on every register as a
  //        discardable attribute; there is nowhere to declare it here.

/** Connects two values whose types must match exactly. */
case class MatchingConnectOp(
    dest: Operand[RTLType],
    src: Operand[RTLType],
) extends DerivedOperation["scairrtl.matchingconnect"]
    with SameTypeOperands derives OpDefs
// [HIGH]     SameTypeOperands — exact type equality is the only reason this
//            operation exists next to the permissive connect. CIRCT compares
//            anonymous types instead, which strips flips and aliases before
//            comparing, so this is the stricter rule. TODO: anonymous-type
//            equivalence.
// [TODO]     FConnectLike, parent constraints
// [TODO]     ConnectOp, the permissive sibling

/** One bit past the wider operand, or uninferred when either input is. */
private def addedWidth(left: IntData, right: IntData): IntData =
  if left.value < 0 || right.value < 0 then unknownWidth
  else IntData(left.value.max(right.value) + 1)

/** A constant of the given integer type. */
case class ConstantOp(
    value: IntegerAttr,
    result: Result[IntType],
) extends DerivedOperation["scairrtl.constant"]
    with Pure
    with OperandAndResultTypes
    with ConstantLike(value) derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] = Seq(classOf[IntType])

/** Selects `high` when `cond` is set, and `low` otherwise. */
case class MuxOp(
    cond: Operand[UIntType],
    high: Operand[IntType],
    low: Operand[IntType],
    result: Result[IntType],
) extends DerivedOperation["scairrtl.mux"]
    with Pure
    with OperandAndResultTypes
    with OneBitOperands
    with InfersResultTypes derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(classOf[UIntType], classOf[IntType], classOf[IntType], classOf[IntType])

  def oneBitOperands: Seq[Int] = Seq(0)

  def inferResultTypes: Seq[Attribute] =
    (high.typ, low.typ) match
      case (h: UIntType, l: UIntType) =>
        Seq(UIntType(widerWidth(h.width, l.width)))
      case (h: SIntType, l: SIntType) =>
        Seq(SIntType(widerWidth(h.width, l.width)))
      // Mismatched signedness is not this check's business.
      case _ => results.map(_.typ)

/** Extracts bits `hi` through `lo`; the result is unsigned, `hi - lo + 1` wide.
  */
case class BitsOp(
    input: Operand[IntType],
    hi: IntegerAttr,
    lo: IntegerAttr,
    result: Result[UIntType],
) extends DerivedOperation["scairrtl.bits"]
    with Pure
    with OperandAndResultTypes
    with BitsSelectionRange
    with InfersResultTypes derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(classOf[IntType], classOf[UIntType])

  def inferResultTypes: Seq[Attribute] =
    Seq(UIntType(IntData(hi.value.value - lo.value.value + 1)))

/** Compares two integers, producing a one-bit unsigned result. */
sealed trait ComparisonOp
    extends Operation
    with SameOperandsIntTypeKind
    with Pure
    with OperandAndResultTypes
    with InfersResultTypes:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(classOf[IntType], classOf[IntType], classOf[UIntType])

  def inferResultTypes: Seq[Attribute] = Seq(UIntType(IntData(1)))

case class EqOp(
    lhs: Operand[IntType],
    rhs: Operand[IntType],
    result: Result[UIntType],
) extends DerivedOperation["scairrtl.eq"]
    with ComparisonOp
    with Commutative derives OpDefs

case class NeqOp(
    lhs: Operand[IntType],
    rhs: Operand[IntType],
    result: Result[UIntType],
) extends DerivedOperation["scairrtl.neq"]
    with ComparisonOp
    with Commutative derives OpDefs

case class LtOp(
    lhs: Operand[IntType],
    rhs: Operand[IntType],
    result: Result[UIntType],
) extends DerivedOperation["scairrtl.lt"]
    with ComparisonOp derives OpDefs

case class LeqOp(
    lhs: Operand[IntType],
    rhs: Operand[IntType],
    result: Result[UIntType],
) extends DerivedOperation["scairrtl.leq"]
    with ComparisonOp derives OpDefs

case class GtOp(
    lhs: Operand[IntType],
    rhs: Operand[IntType],
    result: Result[UIntType],
) extends DerivedOperation["scairrtl.gt"]
    with ComparisonOp derives OpDefs

case class GeqOp(
    lhs: Operand[IntType],
    rhs: Operand[IntType],
    result: Result[UIntType],
) extends DerivedOperation["scairrtl.geq"]
    with ComparisonOp derives OpDefs

/** Adds two integers of the same signedness. */
case class AddOp(
    lhs: Operand[IntType],
    rhs: Operand[IntType],
    result: Result[IntType],
) extends DerivedOperation["scairrtl.add"]
    with SameOperandsIntTypeKind
    with Commutative
    with Pure
    with InfersResultTypes
    with OperandAndResultTypes derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(classOf[IntType], classOf[IntType], classOf[IntType])

  def inferResultTypes: Seq[Attribute] =
    (lhs.typ, rhs.typ) match
      case (l: UIntType, r: UIntType) =>
        Seq(UIntType(addedWidth(l.width, r.width)))
      case (l: SIntType, r: SIntType) =>
        Seq(SIntType(addedWidth(l.width, r.width)))
      // Mixed signedness is SameOperandsIntTypeKind's diagnostic, not this one.
      case _ => results.map(_.typ)

  // [TODO] HasCustomSSAName, and the sibling primitives (sub, mul, div, rem,
  //        and/or/xor, cat, bits)

val ScaIRRTLDialect: Dialect = summonDialect[
  (UIntType, SIntType, ClockType),
  (
      CircuitOp,
      ModuleOp,
      LayerOp,
      WireOp,
      RegOp,
      RegResetOp,
      MatchingConnectOp,
      ConstantOp,
      MuxOp,
      BitsOp,
      EqOp,
      NeqOp,
      LtOp,
      LeqOp,
      GtOp,
      GeqOp,
      AddOp,
  ),
]
