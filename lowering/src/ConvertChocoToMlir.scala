package chocola.lowering

import scair.MLContext
import scair.ir.*
import scair.passes.reconcile.ReconcileUnrealizedCasts
import scair.transformations.*
import scair.utils.Err
import scair.verify.Verifier

final class ConvertChocoToMlir(ctx: MLContext) extends ConversionPass(ctx):
  override val name = "convert-choco-to-mlir"

  private val partials: Seq[ConversionPass] = Seq(
    ConvertChocoToFunc(ctx),
    ConvertChocoToScf(ctx),
    ConvertChocoToArith(ctx),
  )

  override def patterns: Seq[ConversionPattern] =
    partials.flatMap(_.patterns)

  override def typeConverter: TypeConverter = chocoTypeConverter

val allPasses: Seq[MLContext => ModulePass] = Seq(
  ConvertChocoToFunc(_),
  ConvertChocoToScf(_),
  ConvertChocoToArith(_),
  ConvertChocoToMlir(_),
)
