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
||   STRUCTURAL TRAITS       ||
\*≡==---==≡≡≡≡≡≡≡≡≡≡≡==---==≡*/

// TODO: move these to scair.ir

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

/** Restricts the kind of operation this operation may be nested in. */
trait HasParent[T <: Operation](using tag: scala.reflect.ClassTag[T])
    extends Operation:

  override def traitVerify(): OK[Operation] =
    val parent = containerBlock.flatMap(_.containerRegion)
      .flatMap(_.containerOperation)
    val checked = parent match
      case Some(_: T) => OK(this)
      case _          =>
        Err(
          s"Operation '$name' must be nested in ${tag.runtimeClass.getSimpleName}"
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

/*≡==--==≡≡≡≡≡≡≡≡≡≡≡≡≡==--=≡≡*\
||      ScaIRRTL TRAITS      ||
\*≡==---==≡≡≡≡≡≡≡≡≡≡≡==---==≡*/

/** Both operands are `UInt` or both are `SInt`, as FIRRTL's integer primitives
  * require: mixing them would make the result width rule meaningless.
  */
trait SameOperandsIntTypeKind extends Operation:

  override def traitVerify(): OK[Operation] =
    val types = operands.map(_.typ)
    val sameKind = types.forall(_.isInstanceOf[UIntType]) ||
      types.forall(_.isInstanceOf[SIntType])
    val checked =
      if types.isEmpty || sameKind then OK(this)
      else Err(s"Operands of '$name' must be all UInt or all SInt")
    checked.flatMap(_ => super.traitVerify())

/** Every operand and result is an integer type. CIRCT gets this from the
  * operand and result type constraints on its integer primitives, which ScaIR
  * never checks, so it is verified here instead.
  */
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

// Integer-backed, matching CIRCT's I32EnumAttr spelling. They print as ordinals
// in generic assembly, which can only be turned into keywords by a custom op
// syntax.
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

/** Either signedness of integer, as CIRCT's `IntType` is: the supertype of
  * `UIntType` and `SIntType`, and the constraint integer operations take so a
  * clock cannot be added.
  */
sealed trait IntType extends RTLType

/** Width of an integer whose width has not been inferred yet. Derived attribute
  * parameters must themselves be attributes, so an uninferred width is spelled
  * with this sentinel rather than an `Option`, as ScaIR does for dynamic
  * dimensions.
  */
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
    // default_layer_specialization,
    select_inst_choice: Option[ArrayAttribute[Attribute]] = None,
) extends DerivedOperation["scairrtl.circuit"]
    with IsolatedFromAbove
    with SymbolTable
    with SingleBlock
    with NoTerminator
    with NoRegionArguments derives OpDefs
// [HIGH]     InnerRefNamespace — symbol/ref resolution across the circuit
//            boundary. TODO
// [TODO]     default_layer_specialization (LayerSpecializationAttr)
// [TODO]     OpAsmOpInterface, for named entry-block arguments

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
    layers: ArrayAttribute[Attribute] = ArrayAttribute(),
    domain_info: ArrayAttribute[Attribute] = ArrayAttribute(),
) extends DerivedOperation["scairrtl.module"]
    with IsolatedFromAbove
    with Symbol
    with SymbolTable
    with SingleBlock
    with NoTerminator
    with HasParent[CircuitOp] derives OpDefs
// [BLOCKING] entry-block arguments — CIRCT's verifier requires exactly one
//            argument per port, with argument types matching port_types and
//            argument locations matching port_locations. Without that verifier a
//            module body cannot reference a single port. TODO
// [HIGH]     port_directions holds untyped attributes, so a malformed direction
//            cannot be rejected. TODO: give direction a type.
// [TODO]     convention is required in CIRCT; optional here until its
//            semantics are implemented.
// [TODO]     InnerSymbolTable, SymbolUserOpInterface, PortList, FModuleLike,
//            SymbolName, SymbolVisibility, InstanceGraphModuleOpInterface,
//            OpAsmOpInterface

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
    with NoTerminator derives OpDefs
// [HIGH]     ParentOneOf[CircuitOp, LayerOp] — a layer may only be nested in
//            the circuit or another layer. HasParent takes one parent, so this
//            needs a ParentOneOf variant. TODO
// [TODO]     SymbolName, SymbolVisibility, and LayerSpecialization
//            ("enable" or "disable" for the circuit's default).

case class WireOp(
    result: Result[RTLType],
    sym_name: StringData,
    nameKind: NameKindEnum,
    annotations: ArrayAttribute[Attribute],
    forceable: Option[UnitAttr] = None,
) extends DerivedOperation["scairrtl.wire"] derives OpDefs
// [HIGH]     nameKind is recorded but nothing consumes it, so no name is
//            preserved yet. TODO: a name-preservation pass.
// [HIGH]     CIRCT splits the name into a plain string plus an optional
//            inner_sym; `sym_name` covers only the first, so inner-symbol
//            lookup has no home. TODO
// [TODO]     domains operand, and the optional ref result
// [TODO]     ParentOneOf over [ModuleOp, LayerBlockOp, WhenOp, MatchOp]:
//            CIRCT constrains the parent with ParentOneOf, not a single parent.
// [TODO]     ForceableOpInterface, whose verifier is what makes `forceable`
//            meaningful; FNamableOpInterface, OpAsmOpInterface (CIRCT's
//            HasCustomSSAName), InnerSymbolOpInterface, SymbolUserOpInterface

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

/** Adds two integers of the same signedness. */
case class AddOp(
    lhs: Operand[IntType],
    rhs: Operand[IntType],
    result: Result[IntType],
) extends DerivedOperation["scairrtl.add"]
    with SameOperandsIntTypeKind
    with IntTypeOperandsAndResult
    with Commutative
    with Pure derives OpDefs
// [HIGH]     result width — FIRRTL sizes the result at max(w1, w2) + 1, taking
//            the left operand's signedness and leaving the width uninferred when
//            either operand is. CIRCT derives this through
//            InferTypeOpInterface; ScaIR has no equivalent, so the width must be
//            written by hand and nothing checks it. TODO: an inference hook plus
//            a verifier.
// [TODO]     HasCustomSSAName, InferTypeOpInterface, and the sibling primitives
//            (sub, mul, div, rem, and/or/xor, cat, bits)

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
