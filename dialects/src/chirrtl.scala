package scairrtl.dialects

import fastparse.*
import scair.utils.*
import scair.ir.*
import scair.dialects.builtin.{ArrayAttribute, IntData, StringData}
import scair.clair.*
import scair.parse.{Parser, intDataP, typeP}

/*≡==--==≡≡≡≡≡≡≡≡≡≡≡==--=≡≡*\
||         TYPES           ||
\*≡==---==≡≡≡≡≡≡≡≡≡≡≡==---==≡*/

sealed trait CHIRRTLType extends TypeAttribute

/** A behavioral memory of `depth` elements, similar to a vector of its element
  * type.
  */
final case class CMemoryType(element: FIRRTLBaseType, depth: IntData)
    extends CHIRRTLType
    with DerivedAttribute["chirrtl.cmemory"] derives AttrDefs:

  override def customVerify(): OK[Unit] =
    if depth.value < 0 then Err("cmemory depth must be non-negative")
    else if !isPassive(element) then Err("cmemory element type must be passive")
    else OK(())

object CMemoryType:

  given AttributeCustomParser[CMemoryType] with

    def parse[$: P](using Parser): P[CMemoryType] =
      P("<" ~/ typeP ~ "," ~ intDataP ~ ">").flatMap { case (tpe, depth) =>
        tpe match
          case base: FIRRTLBaseType => Pass(CMemoryType(base, depth))
          case other                =>
            Fail(s"expected a FIRRTL base type in cmemory element, got $other")
      }

// [TODO]     the depth is a signed BigInt, where CIRCT admits a uint64_t, so a
//            count beyond 64 bits is accepted.
// [TODO]     a zero depth is accepted, where the FIRRTL spec requires a
//            positive count.

/** A port declared on a `chirrtl.cmemory`. */
final case class CMemoryPortType()
    extends CHIRRTLType
    with DerivedAttribute["chirrtl.cmemoryport"] derives AttrDefs

/*≡==--==≡≡≡≡≡≡≡≡≡≡≡==--=≡≡*\
||       OPERATIONS        ||
\*≡==---==≡≡≡≡≡≡≡≡≡≡≡==---==≡*/

/** Declares a memory port on a `chirrtl.cmemory` value. */
case class MemoryPortOp(
    data: Result[FIRRTLBaseType],
    port: Result[CMemoryPortType],
    memory: Operand[CMemoryType],
    direction: MemDirEnum,
    sym_name: StringData,
    annotations: ArrayAttribute[Attribute] = ArrayAttribute(),
) extends DerivedOperation["chirrtl.memoryport"]
    with OperandAndResultTypes
    with InfersResultTypes derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(classOf[CMemoryType], classOf[FIRRTLBaseType], classOf[CMemoryPortType])

  def inferResultTypes: Seq[Attribute] =
    Seq(memory.typ.element, CMemoryPortType())

  override def customVerify(): OK[Operation] =
    val users = port.uses.map(_.operation).toSeq
    users match
      case Seq(_: MemoryPortAccessOp) => super.customVerify()
      case _                          =>
        Err(
          s"Operation '$name' needs its port used by exactly one " +
            "'chirrtl.memoryport.access'"
        )

  // [HIGH]     MemDirEnum.Infer is recorded, but no pass resolves it to the
  //            concrete direction the port is used with. TODO
  // [TODO]     HasCustomSSAName, blocked on a naming hook in ScaIR's printer
  // [TODO]     custom assembly, whose direction keyword precedes the memory
  //            operand
  // [TODO]     a parent restriction: CIRCT declares none, so this op parses
  //            anywhere until the legal placements are settled.

/** Enables a declared memory port for one clock edge. */
case class MemoryPortAccessOp(
    port: Operand[CMemoryPortType],
    index: Operand[IntType],
    clock: Operand[ClockType],
) extends DerivedOperation["chirrtl.memoryport.access"]
    with OperandAndResultTypes derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(classOf[CMemoryPortType], classOf[IntType], classOf[ClockType])

  // [TODO] custom assembly, which prints the port, index, and clock as
  //        `%port[%index], %clock`

/** Declares a combinational memory: no read latency, one cycle write. */
case class CombMemOp(
    result: Result[CMemoryType],
    sym_name: StringData,
    nameKind: NameKindEnum,
    annotations: ArrayAttribute[Attribute] = ArrayAttribute(),
    inner_sym: Option[StringData] = None,
    init: Option[MemoryInitAttr] = None,
    prefix: Option[StringData] = None,
) extends DerivedOperation["chirrtl.combmem"]
    with OperandAndResultTypes derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(classOf[CMemoryType])

  // [TODO] custom assembly, which prints the optional symbol and the name kind
  //        before the attributes
  // [TODO] HasCustomSSAName, blocked on a naming hook in ScaIR's printer
  // [TODO] InnerSymbol and FNamableOp, so `inner_sym` and `prefix` name the
  //        memory instead of sitting unused
  // [TODO] MemoryInitAttr is recorded but nothing initializes the memory from
  //        the file it names

/** Declares a sequential memory: one cycle read and write latency. */
case class SeqMemOp(
    result: Result[CMemoryType],
    ruw: RuwBehaviorEnum,
    sym_name: StringData,
    nameKind: NameKindEnum,
    annotations: ArrayAttribute[Attribute] = ArrayAttribute(),
    inner_sym: Option[StringData] = None,
    init: Option[MemoryInitAttr] = None,
    prefix: Option[StringData] = None,
) extends DerivedOperation["chirrtl.seqmem"]
    with OperandAndResultTypes derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(classOf[CMemoryType])

  // [TODO] custom assembly, which prints the optional symbol, the name kind, and
  //        the read-under-write behavior before the attributes
  // [TODO] HasCustomSSAName, blocked on a naming hook in ScaIR's printer
  // [TODO] InnerSymbol and FNamableOp, so `inner_sym` and `prefix` name the
  //        memory instead of sitting unused
  // [TODO] `ruw` and MemoryInitAttr are recorded but no pass consumes them

/** Declares a debug port that reads a memory as a vector. */
case class DebugPortOp(
    data: Result[ProbeType],
    memory: Operand[CMemoryType],
    sym_name: StringData,
    annotations: ArrayAttribute[Attribute] = ArrayAttribute(),
) extends DerivedOperation["chirrtl.debugport"]
    with OperandAndResultTypes
    with InfersResultTypes derives OpDefs:

  def operandResultTypes: Seq[Class[? <: Attribute]] =
    Seq(classOf[CMemoryType], classOf[ProbeType])

  def inferResultTypes: Seq[Attribute] =
    Seq(ProbeType(VectorType(memory.typ.element, memory.typ.depth)))

  // [TODO] custom assembly, which prints the memory operand and attributes
  // [TODO] HasCustomSSAName, blocked on a naming hook in ScaIR's printer
  // [TODO] the `ref` operations that consume `data`, so a debug port's result
  //        has somewhere to go

val CHIRRTLDialect: Dialect = summonDialect[
  (CMemoryType, CMemoryPortType),
  (MemoryPortOp, MemoryPortAccessOp, CombMemOp, SeqMemOp, DebugPortOp),
]
