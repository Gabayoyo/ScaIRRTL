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

  private val entryBlock = Block(ports.map(_.tpe), Seq.empty[Operation])

  // The block receiving new operations; `when` swaps it while building a region.
  private var block: Block = entryBlock

  private val portValueTable: Map[String, Value[FIRRTLBaseType]] =
    val names = ports.map(_.name)
    require(
      names.distinct.size == names.size,
      s"duplicate port names in module '$name': ${names.mkString(", ")}",
    )
    ports.zip(entryBlock.arguments.toSeq).map { case (spec, value) =>
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
      reset: Value[FIRRTLBaseType],
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

  def sub(
      lhs: Value[IntType],
      rhs: Value[IntType],
      resultType: IntType,
      location: Location = UnknownLoc,
  ): Value[IntType] =
    val op = SubOp(lhs, rhs, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def mul(
      lhs: Value[IntType],
      rhs: Value[IntType],
      resultType: IntType,
      location: Location = UnknownLoc,
  ): Value[IntType] =
    val op = MulOp(lhs, rhs, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def div(
      lhs: Value[IntType],
      rhs: Value[IntType],
      resultType: IntType,
      location: Location = UnknownLoc,
  ): Value[IntType] =
    val op = DivOp(lhs, rhs, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def rem(
      lhs: Value[IntType],
      rhs: Value[IntType],
      resultType: IntType,
      location: Location = UnknownLoc,
  ): Value[IntType] =
    val op = RemOp(lhs, rhs, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def bitAnd(
      lhs: Value[IntType],
      rhs: Value[IntType],
      resultType: UIntType,
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = AndOp(lhs, rhs, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def bitOr(
      lhs: Value[IntType],
      rhs: Value[IntType],
      resultType: UIntType,
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = OrOp(lhs, rhs, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def bitXor(
      lhs: Value[IntType],
      rhs: Value[IntType],
      resultType: UIntType,
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = XorOp(lhs, rhs, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def bitNot(
      input: Value[IntType],
      resultType: UIntType,
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = NotOp(input, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def andReduce(
      input: Value[IntType],
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = AndReduceOp(input, Result(UIntType(IntData(1)))).at(location)
    block.addOp(op)
    op.result

  def orReduce(
      input: Value[IntType],
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = OrReduceOp(input, Result(UIntType(IntData(1)))).at(location)
    block.addOp(op)
    op.result

  def xorReduce(
      input: Value[IntType],
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = XorReduceOp(input, Result(UIntType(IntData(1)))).at(location)
    block.addOp(op)
    op.result

  def cat(
      lhs: Value[IntType],
      rhs: Value[IntType],
      resultType: UIntType,
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = CatOp(lhs, rhs, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def dynamicShiftLeft(
      input: Value[IntType],
      amount: Value[UIntType],
      resultType: IntType,
      location: Location = UnknownLoc,
  ): Value[IntType] =
    val op = DshlOp(input, amount, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def dynamicShiftRight(
      input: Value[IntType],
      amount: Value[UIntType],
      resultType: IntType,
      location: Location = UnknownLoc,
  ): Value[IntType] =
    val op = DshrOp(input, amount, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def pad(
      input: Value[IntType],
      resultType: IntType,
      location: Location = UnknownLoc,
  ): Value[IntType] =
    val op = PadOp(input, Result(resultType)).at(location)
    block.addOp(op)
    op.result

  def tail(
      input: Value[IntType],
      n: Int,
      resultType: UIntType,
      location: Location = UnknownLoc,
  ): Value[UIntType] =
    val op = TailOp(
      input = input,
      n = IntegerAttr(IntData(n), I32),
      result = Result(resultType),
    ).at(location)
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

  /** Builds `firrtl.when`; the first region is the then-region, and the second
    * is added only when `hasElse` is set.
    */
  def when(
      cond: Value[UIntType],
      hasElse: Boolean,
      location: Location = UnknownLoc,
  )(thenBody: => Unit)(elseBody: => Unit): Unit =
    val thenRegion = region(thenBody)
    val elseRegions = if hasElse then Seq(region(elseBody)) else Seq.empty
    block.addOp(WhenOp(cond, thenRegion +: elseRegions).at(location))

  // Collects the operations of `body` into a region with one block.
  private def region(body: => Unit): Region =
    val previous = block
    val inner = Block(Seq.empty[Operation])
    block = inner
    try body
    finally block = previous
    Region(inner)

  def combMemory(
      name: String,
      element: FIRRTLBaseType,
      depth: BigInt,
      location: Location = UnknownLoc,
  ): Value[CMemoryType] =
    val op = CombMemOp(
      result = Result(CMemoryType(element, IntData(depth))),
      sym_name = StringData(name),
      nameKind = NameKindEnum.InterestingName,
      annotations = ArrayAttribute(),
    ).at(location)
    block.addOp(op)
    op.result

  def seqMemory(
      name: String,
      element: FIRRTLBaseType,
      depth: BigInt,
      ruw: RuwBehaviorEnum,
      location: Location = UnknownLoc,
  ): Value[CMemoryType] =
    val op = SeqMemOp(
      result = Result(CMemoryType(element, IntData(depth))),
      ruw = ruw,
      sym_name = StringData(name),
      nameKind = NameKindEnum.InterestingName,
      annotations = ArrayAttribute(),
    ).at(location)
    block.addOp(op)
    op.result

  def memoryPort(
      name: String,
      memory: Value[CMemoryType],
      direction: MemDirEnum,
      dataType: FIRRTLBaseType,
      location: Location = UnknownLoc,
  ): (Value[FIRRTLBaseType], Value[CMemoryPortType]) =
    val op = MemoryPortOp(
      data = Result(dataType),
      port = Result(CMemoryPortType()),
      memory = memory,
      direction = direction,
      sym_name = StringData(name),
      annotations = ArrayAttribute(),
    ).at(location)
    block.addOp(op)
    (op.data, op.port)

  def memoryPortAccess(
      port: Value[CMemoryPortType],
      index: Value[IntType],
      clock: Value[ClockType],
      location: Location = UnknownLoc,
  ): Unit =
    block.addOp(MemoryPortAccessOp(port, index, clock).at(location))

  def connect(
      dest: Value[FIRRTLBaseType],
      src: Value[FIRRTLBaseType],
      location: Location = UnknownLoc,
  ): Unit =
    block.addOp(MatchingConnectOp(dest, src).at(location))

  private[frontend] def finish(): ModuleOp =
    val portTypes = ports.map(_.tpe)
    ModuleOp(
      body = Region(entryBlock),
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
