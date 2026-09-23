package chocola.lowering

import chocola.dialects.*
import scair.MLContext
import scair.dialects.arith
import scair.dialects.builtin.*
import scair.ir.*
import scair.transformations.*

private def integerConstant[T <: IntegerType](value: BigInt, typ: T) =
  val op = arith.Constant(IntegerAttr(IntData(value), typ), Result(typ))
  (op, op.results.head.asInstanceOf[Operand[T]])

private val ConvertConstant = conversionPattern {
  case op: ConstantOp if op.result.typ.isInstanceOf[IntType] =>
    integerConstant(BigInt(op.value.data), I32)._1
  case op: ConstantOp if op.result.typ.isInstanceOf[BoolType] =>
    integerConstant(if op.value.data.toBoolean then 1 else 0, I1)._1
  case op: ConstantOp if op.result.typ.isInstanceOf[DoubleType] =>
    arith
      .Constant(FloatAttr(FloatData(op.value.data.toDouble), F64), Result(F64))
}

private val ConvertAdd = conversionPattern {
  case op: AddOp if op.result.typ.isInstanceOf[IntType] =>
    arith.AddI(adaptor(0), adaptor(1), Result(I32))
  case op: AddOp if op.result.typ.isInstanceOf[DoubleType] =>
    arith.AddF(adaptor(0), adaptor(1), Result(F64))
}

private val ConvertSub = conversionPattern {
  case op: SubOp if op.result.typ.isInstanceOf[IntType] =>
    arith.SubI(adaptor(0), adaptor(1), Result(I32))
  case op: SubOp if op.result.typ.isInstanceOf[DoubleType] =>
    arith.SubF(adaptor(0), adaptor(1), Result(F64))
}

private val ConvertMul = conversionPattern {
  case op: MulOp if op.result.typ.isInstanceOf[IntType] =>
    arith.MulI(adaptor(0), adaptor(1), Result(I32))
  case op: MulOp if op.result.typ.isInstanceOf[DoubleType] =>
    arith.MulF(adaptor(0), adaptor(1), Result(F64))
}

private val ConvertDiv = conversionPattern {
  case op: DivOp if op.result.typ.isInstanceOf[IntType] =>
    arith.DivSI(adaptor(0), adaptor(1), Result(I32))
  case op: DivOp if op.result.typ.isInstanceOf[DoubleType] =>
    arith.DivF(adaptor(0), adaptor(1), Result(F64))
}

private val ConvertEq = conversionPattern {
  case op: EqOp
      if op.lhs.typ.isInstanceOf[IntType] ||
        op.lhs.typ.isInstanceOf[BoolType] =>
    arith.CmpI(adaptor(0), adaptor(1), Result(I1), arith.CmpIPredicate.eq)
  case op: EqOp if op.lhs.typ.isInstanceOf[DoubleType] =>
    arith.CmpF(adaptor(0), adaptor(1), Result(I1), arith.CmpFPredicate.OEQ)
}

private val ConvertNeq = conversionPattern {
  case op: NeqOp
      if op.lhs.typ.isInstanceOf[IntType] ||
        op.lhs.typ.isInstanceOf[BoolType] =>
    arith.CmpI(adaptor(0), adaptor(1), Result(I1), arith.CmpIPredicate.ne)
  case op: NeqOp if op.lhs.typ.isInstanceOf[DoubleType] =>
    arith.CmpF(adaptor(0), adaptor(1), Result(I1), arith.CmpFPredicate.UNE)
}

private val ConvertLt = conversionPattern {
  case op: LtOp if op.lhs.typ.isInstanceOf[IntType] =>
    arith.CmpI(adaptor(0), adaptor(1), Result(I1), arith.CmpIPredicate.slt)
  case op: LtOp if op.lhs.typ.isInstanceOf[DoubleType] =>
    arith.CmpF(adaptor(0), adaptor(1), Result(I1), arith.CmpFPredicate.OLT)
}

private val ConvertGt = conversionPattern {
  case op: GtOp if op.lhs.typ.isInstanceOf[IntType] =>
    arith.CmpI(adaptor(0), adaptor(1), Result(I1), arith.CmpIPredicate.sgt)
  case op: GtOp if op.lhs.typ.isInstanceOf[DoubleType] =>
    arith.CmpF(adaptor(0), adaptor(1), Result(I1), arith.CmpFPredicate.OGT)
}

private val ConvertLeq = conversionPattern {
  case op: LeqOp if op.lhs.typ.isInstanceOf[IntType] =>
    arith.CmpI(adaptor(0), adaptor(1), Result(I1), arith.CmpIPredicate.sle)
  case op: LeqOp if op.lhs.typ.isInstanceOf[DoubleType] =>
    arith.CmpF(adaptor(0), adaptor(1), Result(I1), arith.CmpFPredicate.OLE)
}

private val ConvertGeq = conversionPattern {
  case op: GeqOp if op.lhs.typ.isInstanceOf[IntType] =>
    arith.CmpI(adaptor(0), adaptor(1), Result(I1), arith.CmpIPredicate.sge)
  case op: GeqOp if op.lhs.typ.isInstanceOf[DoubleType] =>
    arith.CmpF(adaptor(0), adaptor(1), Result(I1), arith.CmpFPredicate.OGE)
}

private val ConvertAnd = conversionPattern { case _: AndOp =>
  arith.AndI(adaptor(0), adaptor(1), Result(I1))
}

private val ConvertOr = conversionPattern { case _: OrOp =>
  arith.OrI(adaptor(0), adaptor(1), Result(I1))
}

private val ConvertNot = conversionPattern { case _: NotOp =>
  val (one, oneValue) = integerConstant(1, I1)
  Seq(one, arith.XOrI(adaptor(0), oneValue, Result(I1)))
}

private val ConvertNeg = conversionPattern {
  case op: NegOp if op.result.typ.isInstanceOf[IntType] =>
    val (zero, zeroValue) = integerConstant(0, I32)
    Seq(zero, arith.SubI(zeroValue, adaptor(0), Result(I32)))
  case op: NegOp if op.result.typ.isInstanceOf[DoubleType] =>
    arith.NegF(adaptor(0), Result(F64))
}

final class ConvertChocoToArith(ctx: MLContext) extends ConversionPass(ctx):
  override val name: String = "convert-choco-to-arith"

  override val typeConverter: TypeConverter = chocoTypeConverter

  override val patterns: Seq[ConversionPattern] =
    Seq(
      ConvertConstant,
      ConvertAdd,
      ConvertSub,
      ConvertMul,
      ConvertDiv,
      ConvertEq,
      ConvertNeq,
      ConvertLt,
      ConvertGt,
      ConvertLeq,
      ConvertGeq,
      ConvertAnd,
      ConvertOr,
      ConvertNot,
      ConvertNeg,
    )
