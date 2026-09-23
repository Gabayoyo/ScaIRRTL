package chocola.lowering

import chocola.dialects.*
import scair.MLContext
import scair.dialects.scf
import scair.ir.*
import scair.transformations.*

private val ConvertIf = conversionPattern {
  case op: IfOp if isConvertible(op.result.typ) =>
    scf.IfOp(
      adaptor(0),
      op.thenRegion.detached,
      op.elseRegion.detached,
      Seq(Result(convertType(op.result.typ))),
    )
}

private val ConvertYield = conversionPattern { case op: YieldOp =>
  directRegionOwner(op) match
    case Some(_: scf.IfOp) => scf.YieldOp(adaptor.operands)
    case _                 => PatternAction.Abort
}

final class ConvertChocoToScf(ctx: MLContext) extends ConversionPass(ctx):
  override val name: String = "convert-choco-to-scf"

  override val typeConverter: TypeConverter = chocoTypeConverter

  override val patterns: Seq[ConversionPattern] =
    Seq(ConvertIf, ConvertYield)
