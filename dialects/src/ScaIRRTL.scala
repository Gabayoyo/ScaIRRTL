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

/** Every operand and result is an integer type. */
trait IntTypeOperandsAndResult extends Operation:

  override def traitVerify(): OK[Operation] =
    val offending = (operands ++ results).map(_.typ)
      .filterNot(_.isInstanceOf[IntType])
    val checked =
      if offending.isEmpty then OK(this)
      else
        Err(
          s"Operands and results of '$name' must be integer types, got " +
            offending.map(_.name).mkString(", ")
        )
    checked.flatMap(_ => super.traitVerify())

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
    with IntTypeOperandsAndResult derives OpDefs:

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
      MatchingConnectOp,
      AddOp,
  ),
]
