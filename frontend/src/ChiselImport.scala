package chisel3

import chisel3.experimental.{SourceInfo, SourceLine}
import chisel3.internal.firrtl.ir
import scair.dialects.builtin.{
  ArrayAttribute,
  I1,
  IntData,
  IntegerAttr,
  StringData,
}
import scair.ir.{FileLineColLoc, Location, UnknownLoc, Value}
import scair.utils.{Err, OK}
import scairrtl.dialects.*
import scairrtl.frontend.*

import scala.collection.mutable

/** Imports an elaborated Chisel circuit into firrtl operations. */
object ChiselImport:

  private final class ImportError(message: String)
      extends RuntimeException(message)

  private object ImportError:
    def apply(message: String): ImportError = new ImportError(message)

  // Identity-based values for one module, with a name fallback for `Ref`.
  private final class ValueTable:

    private val byData =
      java.util.IdentityHashMap[Data, Value[FIRRTLBaseType]]()

    private val byName = mutable.Map.empty[String, Value[FIRRTLBaseType]]

    def bindData(data: Data, value: Value[FIRRTLBaseType]): Unit =
      byData.put(data, value)

    def bindName(name: String, value: Value[FIRRTLBaseType]): Unit =
      byName(name) = value

    def data(data: Data): Option[Value[FIRRTLBaseType]] =
      Option(byData.get(data))

    def name(name: String): Option[Value[FIRRTLBaseType]] =
      byName.get(name)

  private object ValueTable:
    def apply(): ValueTable = new ValueTable

  def apply(elaborated: ElaboratedCircuit): OK[CircuitOp] =
    try
      val source = ChiselCircuit(elaborated)
      val builder = CircuitBuilder(source.name)
      source.components.foreach {
        case module: ir.DefModule =>
          val ports = modulePorts(module)
          builder.module(module.name, ports.map(_._1)) { body =>
            val values = ValueTable()
            ports.foreach { case (spec, data) =>
              body.portValue(spec.name) match
                case Some(value) =>
                  values.bindData(data, value)
                  values.bindName(spec.name, value)
                case None =>
                  throw ImportError(
                    s"missing block argument for port ${spec.name}"
                  )
            }
            commands(body, module, values)
          }
        case other =>
          throw ImportError(
            s"unsupported component ${other.getClass.getSimpleName}"
          )
      }
      builder.finish()
    catch case error: ImportError => Err(error.getMessage)

  // Command and expression lowering.
  private def commands(
      builder: ModuleBuilder,
      module: ir.DefModule,
      values: ValueTable,
  ): Unit =
    module.block.getAllCommands().foreach(command(builder, module, values, _))

  private def command(
      builder: ModuleBuilder,
      module: ir.DefModule,
      values: ValueTable,
      command: ir.Command,
  ): Unit =
    command match
      case wire: ir.DefWire =>
        val value = builder.wire(
          wire.name,
          rtlType(wire.id),
          location = location(wire.sourceInfo),
        )
        bind(module, values, wire.id, value)
      case reg: ir.DefReg =>
        val loc = location(reg.sourceInfo)
        val value = builder.reg(
          reg.name,
          rtlType(reg.id),
          clockValue(operand(builder, module, values, reg.clock, loc)),
          location = loc,
        )
        bind(module, values, reg.id, value)
      case reg: ir.DefRegInit =>
        val loc = location(reg.sourceInfo)
        val value = builder.regReset(
          reg.name,
          rtlType(reg.id),
          clockValue(operand(builder, module, values, reg.clock, loc)),
          uintValue(operand(builder, module, values, reg.reset, loc)),
          operand(builder, module, values, reg.init, loc),
          location = loc,
        )
        bind(module, values, reg.id, value)
      case prim: ir.DefPrim[?] =>
        val loc = location(prim.sourceInfo)
        bind(
          module,
          values,
          prim.id,
          primitive(builder, module, values, prim, loc),
        )
      case connect: ir.Connect =>
        val loc = location(connect.sourceInfo)
        builder.connect(
          operand(builder, module, values, connect.loc, loc),
          operand(builder, module, values, connect.exp, loc),
          loc,
        )
      case other =>
        throw ImportError(
          s"unsupported command ${other.getClass.getSimpleName}"
        )

  // dispatch helper function for primitives
  private def primitive(
      builder: ModuleBuilder,
      module: ir.DefModule,
      values: ValueTable,
      prim: ir.DefPrim[?],
      loc: Location,
  ): Value[FIRRTLBaseType] =
    def arg(index: Int): Value[FIRRTLBaseType] =
      operand(builder, module, values, prim.args(index), loc)
    def intArg(index: Int): Int =
      integerLiteral(prim.args(index))
    val resultType = rtlType(prim.id)

    prim.op.name match
      case "add" =>
        builder
          .add(intValue(arg(0)), intValue(arg(1)), intType(resultType), loc)
      case "mux" =>
        builder.mux(
          uintValue(arg(0)),
          arg(1),
          arg(2),
          resultType,
          loc,
        )
      case "bits" =>
        builder.bits(intValue(arg(0)), intArg(1), intArg(2), loc)
      case "eq" =>
        builder.equal(intValue(arg(0)), intValue(arg(1)), loc)
      case "neq" =>
        builder.notEqual(intValue(arg(0)), intValue(arg(1)), loc)
      case "lt" =>
        builder.lessThan(intValue(arg(0)), intValue(arg(1)), loc)
      case "leq" =>
        builder.lessThanOrEqual(intValue(arg(0)), intValue(arg(1)), loc)
      case "gt" =>
        builder.greaterThan(intValue(arg(0)), intValue(arg(1)), loc)
      case "geq" =>
        builder.greaterThanOrEqual(intValue(arg(0)), intValue(arg(1)), loc)
      case other =>
        throw ImportError(s"unsupported primitive '$other'")

  // Ports and type conversion.
  private def modulePorts(
      module: ir.DefModule
  ): Seq[(PortSpec, Data)] =
    module.ports.zipWithIndex.map { case (port, index) =>
      val name = port.id.getOptionRef.map(_.name).getOrElse(s"port$index")
      val base = portTypeDirection(port)
      (
        PortSpec(
          name,
          rtlType(port.id, base),
          if base == SpecifiedDirection.Input then PortDirection.Input
          else PortDirection.Output,
          location(port.sourceInfo),
        ),
        port.id,
      )
    }

  // Direction used to construct the port's FIRRTL type.
  private def portTypeDirection(port: ir.Port): SpecifiedDirection =
    if port.dir == SpecifiedDirection.Input then SpecifiedDirection.Input
    else SpecifiedDirection.Output

  // TODO: maybe move to a separate type converter file.
  // Type conversion function for FIRRTLBaseType
  private def rtlType(
      data: Data,
      base: SpecifiedDirection = SpecifiedDirection.Unspecified,
  ): FIRRTLBaseType =
    data match
      case _: Clock       => ClockType()
      case unsigned: UInt => UIntType(width(unsigned))
      case signed: SInt   => SIntType(width(signed))
      case record: Record =>
        if base == SpecifiedDirection.Unspecified then
          throw ImportError("aggregate values are not supported yet")
        bundleType(record, base)
      case vector: Vec[?] =>
        if base == SpecifiedDirection.Unspecified then
          throw ImportError("aggregate values are not supported yet")
        vector.getElements.headOption match
          case Some(element) =>
            VectorType(rtlType(element, base), IntData(vector.length))
          case None =>
            throw ImportError(
              "cannot infer the element type of an empty vector"
            )
      case other =>
        throw ImportError(s"unsupported type ${other.getClass.getSimpleName}")

  // constructs bundleType
  private def bundleType(
      record: Record,
      base: SpecifiedDirection,
  ): BundleType =
    val fields = record._elements.toIndexedSeq.reverse.map {
      case (name, field) =>
        val specified = field.specifiedDirection
        val flip = specified match
          case SpecifiedDirection.Flip   => true
          case SpecifiedDirection.Input  => base != SpecifiedDirection.Input
          case SpecifiedDirection.Output => base != SpecifiedDirection.Output
          case SpecifiedDirection.Unspecified => false
        val nestedBase = specified match
          case SpecifiedDirection.Flip        => SpecifiedDirection.flip(base)
          case SpecifiedDirection.Input       => SpecifiedDirection.Input
          case SpecifiedDirection.Output      => SpecifiedDirection.Output
          case SpecifiedDirection.Unspecified => base
        BundleField(
          StringData(name),
          rtlType(field, nestedBase),
          IntegerAttr(IntData(if flip then 1 else 0), I1),
        )
    }
    BundleType(ArrayAttribute(fields*))

  // Value resolution
  private def operand(
      builder: ModuleBuilder,
      module: ir.DefModule,
      values: ValueTable,
      arg: ir.Arg,
      loc: Location,
  ): Value[FIRRTLBaseType] =
    arg match
      case lit: ir.ULit =>
        builder.constant(lit.num, UIntType(IntData(lit.width.get)), loc)
      case lit: ir.SLit =>
        builder.constant(lit.num, SIntType(IntData(lit.width.get)), loc)
      case _ =>
        valueOf(builder, module, values, arg, loc)

  private def valueOf(
      builder: ModuleBuilder,
      module: ir.DefModule,
      values: ValueTable,
      arg: ir.Arg,
      loc: Location,
  ): Value[FIRRTLBaseType] =
    arg match
      // if id, recurse until data can be retrieved
      case ir.Node(id) =>
        id match
          case data: Data =>
            values.data(data)
              .getOrElse {
                data.getOptionRef match
                  case Some(ref) => valueOf(builder, module, values, ref, loc)
                  case None      =>
                    throw ImportError(
                      s"unknown value '${arg.contextualName(module)}'"
                    )
              }
          case _ =>
            throw ImportError(
              s"unsupported reference '${arg.contextualName(module)}'"
            )
      case slot: ir.Slot =>
        val child = resolveData(slot)
          .getOrElse {
            throw ImportError(s"unknown field '${slot.contextualName(module)}'")
          }
        values.data(child).getOrElse {
          val base = valueOf(builder, module, values, slot.imm, loc)
          val bundle = base.typ match
            case bundle: BundleType => bundle
            case other              =>
              throw ImportError(s"expected a bundle value, got $other")
          val index = bundle.fieldIndex(slot.name)
            .getOrElse {
              throw ImportError(s"unknown bundle field '${slot.name}'")
            }
          val projected = builder.subfield(
            base.asInstanceOf[Value[BundleType]],
            index,
            bundle.field(index),
            loc,
          )
          values.bindData(child, projected)
          values.bindName(slot.contextualName(module), projected)
          projected
        }
      case index: ir.LitIndex =>
        val child = resolveData(index)
          .getOrElse {
            throw ImportError(
              s"unknown vector element '${index.contextualName(module)}'"
            )
          }
        values.data(child).getOrElse {
          val base = valueOf(builder, module, values, index.imm, loc)
          val vector = base.typ match
            case vector: VectorType => vector
            case other              =>
              throw ImportError(s"expected a vector value, got $other")
          val projected = builder.subindex(
            base.asInstanceOf[Value[VectorType]],
            index.value,
            vector.element,
            loc,
          )
          values.bindData(child, projected)
          values.bindName(index.contextualName(module), projected)
          projected
        }
      case index: ir.Index =>
        throw ImportError(
          s"dynamic vector index is not supported: ${index.contextualName(module)}"
        )
      case ref: ir.Ref =>
        values.name(ref.name)
          .getOrElse {
            throw ImportError(s"unknown name '${ref.name}'")
          }
      case other =>
        throw ImportError(
          s"unsupported operand ${other.getClass.getSimpleName}"
        )

  private def resolveData(arg: ir.Arg): Option[Data] =
    arg match
      case ir.Node(id) =>
        id match
          case data: Data => Some(data)
          case _          => None
      case ir.Slot(imm, fieldName) =>
        resolveData(imm).flatMap {
          case record: Record => record._elements.get(fieldName)
          case _              => None
        }
      case ir.LitIndex(imm, index) =>
        resolveData(imm).flatMap {
          case vector: Vec[?] => vector.getElements.lift(index)
          case _              => None
        }
      case _ => None

  // Value binding
  private def bind(
      module: ir.DefModule,
      values: ValueTable,
      id: Data,
      value: Value[FIRRTLBaseType],
  ): Unit =
    values.bindData(id, value)
    values.bindName(ir.Node(id).contextualName(module), value)

  // Typed value access.
  private trait TypeCheck[T <: FIRRTLBaseType]:
    def accepts(tpe: FIRRTLBaseType): Boolean
    def description: String

    def cast(value: Value[FIRRTLBaseType]): Value[T] =
      if accepts(value.typ) then value.asInstanceOf[Value[T]]
      else throw ImportError(s"expected $description, got ${value.typ}")

    def narrow(tpe: FIRRTLBaseType): T =
      if accepts(tpe) then tpe.asInstanceOf[T]
      else throw ImportError(s"expected $description, got $tpe")

  private object TypeCheck:

    given TypeCheck[IntType] with
      def accepts(tpe: FIRRTLBaseType): Boolean = tpe.isInstanceOf[IntType]
      def description = "an integer"

    given TypeCheck[UIntType] with
      def accepts(tpe: FIRRTLBaseType): Boolean = tpe.isInstanceOf[UIntType]
      def description = "an unsigned integer"

    given TypeCheck[ClockType] with
      def accepts(tpe: FIRRTLBaseType): Boolean = tpe.isInstanceOf[ClockType]
      def description = "a clock"

  private def castValue[T <: FIRRTLBaseType](value: Value[FIRRTLBaseType])(using
      check: TypeCheck[T]
  ): Value[T] =
    check.cast(value)

  private def castType[T <: FIRRTLBaseType](tpe: FIRRTLBaseType)(using
      check: TypeCheck[T]
  ): T =
    check.narrow(tpe)

  private def intType(tpe: FIRRTLBaseType): IntType =
    castType[IntType](tpe)

  private def intValue(value: Value[FIRRTLBaseType]): Value[IntType] =
    castValue[IntType](value)

  private def uintValue(value: Value[FIRRTLBaseType]): Value[UIntType] =
    castValue[UIntType](value)

  private def clockValue(value: Value[FIRRTLBaseType]): Value[ClockType] =
    castValue[ClockType](value)

  // Leaf helpers.
  private def integerLiteral(arg: ir.Arg): Int =
    arg match
      case lit: ir.ULit => lit.num.toInt
      case lit: ir.SLit => lit.num.toInt
      case lit: ir.ILit => lit.n.toInt
      case other        =>
        throw ImportError(
          s"expected an integer literal, got ${other.getClass.getSimpleName}"
        )

  private def width(data: Data): IntData =
    if data.width.known then IntData(data.width.get) else unknownWidth

  private def location(sourceInfo: SourceInfo): Location =
    sourceInfo match
      case line: SourceLine =>
        FileLineColLoc(line.filename, line.line, line.col)
      case _ => UnknownLoc
