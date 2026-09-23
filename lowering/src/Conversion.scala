package chocola.lowering

import chocola.dialects.*
import scair.dialects.builtin.*
import scair.ir.*
import scair.transformations.*

val F64: Float64Type = Float64Type()

/** ChocoLa scalar types and the builtin types they lower to. Any other type
  * converts to itself.
  */
private val ScalarToBuiltin = typeConversion {
  case _: IntType    => I32
  case _: DoubleType => F64
  case _: BoolType   => I1
}

private[lowering] val chocoTypeConverter = TypeConverter(Seq(ScalarToBuiltin))

private[lowering] def isConvertible(typ: Attribute): Boolean =
  chocoTypeConverter.convertType(typ) != typ

private[lowering] def convertFunctionType(
    typ: FunctionType
): Option[FunctionType] =
  val inputs = typ.inputs.data
  val outputs = typ.outputs.data
  Option
    .when((inputs ++ outputs).forall(isConvertible))(
      FunctionType(
        ArrayAttribute(inputs.map(chocoTypeConverter.convertType)*),
        ArrayAttribute(outputs.map(chocoTypeConverter.convertType)*),
      )
    )

private[lowering] def directRegionOwner(op: Operation): Option[Operation] =
  op.containerBlock.flatMap(_.containerRegion).flatMap(_.containerOperation)

private[lowering] def hasNoCaptures(function: FuncDeclOp): Boolean =
  def operationHasNoCaptures(op: Operation): Boolean =
    op.operands.forall(_.owner.exists(function.isAncestor)) &&
      op.regions
        .forall(
          _.blocks.forall(_.operations.forall(operationHasNoCaptures))
        )

  function.body.blocks.forall(_.operations.forall(operationHasNoCaptures))

private[lowering] def convertedFunctionType(
    function: FuncDeclOp
): Option[FunctionType] =
  Option
    .when(function.typeParams.data.isEmpty && hasNoCaptures(function))(
      convertFunctionType(function.functionType)
    ).flatten
