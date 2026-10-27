package scairrtl.frontend

import scair.ir.*
import scair.utils.*
import scair.verify.Verifier
import scairrtl.dialects.*
import scair.dialects.builtin.{
  ArrayAttribute,
  I32,
  IntData,
  IntegerAttr,
  StringData,
}

/** One module port. */
final case class PortSpec(
    name: String,
    tpe: FIRRTLBaseType,
    direction: PortDirection,
    location: Location = UnknownLoc,
)

/** Builds a firrtl circuit from calls made by a frontend walker. */
final class CircuitBuilder(circuitName: String):

  private val modules = scala.collection.mutable.ArrayBuffer.empty[ModuleOp]
  private var closed = false

  def module(
      name: String,
      ports: Seq[PortSpec],
      convention: ConventionEnum = ConventionEnum.Scalarized,
  )(body: ModuleBuilder => Unit): Unit =
    if closed then
      throw new IllegalStateException("circuit builder is already finished")
    val builder = ModuleBuilder(name, ports, convention)
    body(builder)
    modules += builder.finish()

  def finish(): OK[CircuitOp] =
    if closed then Err("circuit builder is already finished")
    else
      closed = true
      val circuit = CircuitOp(
        body = Region(Block(modules.toSeq)),
        sym_name = StringData(circuitName),
      )
      Verifier.verify(circuit).map(_ => circuit)

/** Builds one firrtl module body. */
final class ModuleBuilder private[frontend] (
    name: String,
    ports: Seq[PortSpec],
    convention: ConventionEnum,
):

  private val block = Block(ports.map(_.tpe), Seq.empty[Operation])

  private val portValueTable: Map[String, Value[FIRRTLBaseType]] =
    val names = ports.map(_.name)
    require(
      names.distinct.size == names.size,
      s"duplicate port names in module '$name': ${names.mkString(", ")}",
    )
    ports.zip(block.arguments.toSeq).map { case (spec, value) =>
      spec.name -> value.asInstanceOf[Value[FIRRTLBaseType]]
    }.toMap

  def portValue(portName: String): Option[Value[FIRRTLBaseType]] =
    portValueTable.get(portName)

  def wire[T <: FIRRTLBaseType](
      name: String,
      typ: T,
      nameKind: NameKindEnum = NameKindEnum.InterestingName,
      location: Location = UnknownLoc,
  ): Value[T] =
    val op = WireOp(
      result = Result(typ),
      sym_name = StringData(name),
      nameKind = nameKind,
      annotations = ArrayAttribute(),
      forceable = None,
    ).at(location)
    block.addOp(op)
    op.result.asInstanceOf[Value[T]]

  def reg[T <: FIRRTLBaseType](
      name: String,
      typ: T,
      clock: Value[ClockType],
      nameKind: NameKindEnum = NameKindEnum.InterestingName,
      location: Location = UnknownLoc,
  ): Value[T] =
    val op = RegOp(
      result = Result(typ),
      clock = clock,
      sym_name = StringData(name),
      nameKind = nameKind,
      annotations = ArrayAttribute(),
      forceable = None,
    ).at(location)
    block.addOp(op)
    op.result.asInstanceOf[Value[T]]

  def regReset[T <: FIRRTLBaseType](
      name: String,
      tpe: T,
      clock: Value[ClockType],
      reset: Value[UIntType],
      init: Value[T],
      nameKind: NameKindEnum = NameKindEnum.InterestingName,
      location: Location = UnknownLoc,
  ): Value[T] =
    val op = RegResetOp(
      result = Result(tpe),
      clock = clock,
      reset = reset,
      init = init,
      sym_name = StringData(name),
      nameKind = nameKind,
      annotations = ArrayAttribute(),
      forceable = None,
    ).at(location)
    block.addOp(op)
    op.result.asInstanceOf[Value[T]]

  def constant[T <: IntType](
      value: BigInt,
      tpe: T,
      location: Location = UnknownLoc,
  ): Value[T] =
    val op = ConstantOp(
      value = IntegerAttr(IntData(value)),
      result = Result(tpe),
    ).at(location)
    block.addOp(op)
    op.result.asInstanceOf[Value[T]]

  def add(
      lhs: Value[IntType],
      rhs: Value[IntType],
      resultType: IntType,
      location: Location = UnknownLoc,
  ): Value[IntType] =
    val op = AddOp(lhs, rhs, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def mux(
      cond: Value[UIntType],
      high: Value[FIRRTLBaseType],
      low: Value[FIRRTLBaseType],
      resultType: FIRRTLBaseType,
      location: Location = UnknownLoc,
  ): Value[FIRRTLBaseType] =
    val op = MuxOp(cond, high, low, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def bits(
      input: Value[IntType],
      hi: Int,
      lo: Int,
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = BitsOp(
      input = input,
      hi = IntegerAttr(IntData(hi), I32),
      lo = IntegerAttr(IntData(lo), I32),
      result = Result(UIntType(IntData(hi - lo + 1))),
    ).at(location)
    block.addOp(op)
    op.result

  def equal(
      lhs: Value[IntType],
      rhs: Value[IntType],
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = EqOp(lhs, rhs, Result(UIntType(IntData(1)))).at(location)
    block.addOp(op)
    op.result

  def notEqual(
      lhs: Value[IntType],
      rhs: Value[IntType],
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = NeqOp(lhs, rhs, Result(UIntType(IntData(1)))).at(location)
    block.addOp(op)
    op.result

  def lessThan(
      lhs: Value[IntType],
      rhs: Value[IntType],
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = LtOp(lhs, rhs, Result(UIntType(IntData(1)))).at(location)
    block.addOp(op)
    op.result

  def lessThanOrEqual(
      lhs: Value[IntType],
      rhs: Value[IntType],
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = LeqOp(lhs, rhs, Result(UIntType(IntData(1)))).at(location)
    block.addOp(op)
    op.result

  def greaterThan(
      lhs: Value[IntType],
      rhs: Value[IntType],
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = GtOp(lhs, rhs, Result(UIntType(IntData(1)))).at(location)
    block.addOp(op)
    op.result

  def greaterThanOrEqual(
      lhs: Value[IntType],
      rhs: Value[IntType],
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = GeqOp(lhs, rhs, Result(UIntType(IntData(1)))).at(location)
    block.addOp(op)
    op.result

  def subfield(
      input: Value[BundleType],
      fieldIndex: Int,
      resultType: FIRRTLBaseType,
      location: Location = UnknownLoc,
  ): Value[FIRRTLBaseType] =
    val op = SubfieldOp(
      input = input,
      fieldIndex = IntegerAttr(IntData(fieldIndex), I32),
      result = Result(resultType),
    ).at(location)
    block.addOp(op)
    op.result

  def subindex(
      input: Value[VectorType],
      index: Int,
      resultType: FIRRTLBaseType,
      location: Location = UnknownLoc,
  ): Value[FIRRTLBaseType] =
    val op = SubindexOp(
      input = input,
      index = IntegerAttr(IntData(index), I32),
      result = Result(resultType),
    ).at(location)
    block.addOp(op)
    op.result

  def connect(
      dest: Value[FIRRTLBaseType],
      src: Value[FIRRTLBaseType],
      location: Location = UnknownLoc,
  ): Unit =
    block.addOp(MatchingConnectOp(dest, src).at(location))

  private[frontend] def finish(): ModuleOp =
    val portTypes = ports.map(_.tpe)
    ModuleOp(
      body = Region(block),
      sym_name = StringData(name),
      port_directions = ArrayAttribute[Attribute](ports.map(_.direction)*),
      port_types = ArrayAttribute[Attribute](portTypes*),
      port_names =
        ArrayAttribute[Attribute](ports.map(p => StringData(p.name))*),
      port_locations = ArrayAttribute[Attribute](),
      port_annotations = ArrayAttribute[Attribute](),
      port_symbols = ArrayAttribute[Attribute](),
      convention = convention,
    )
