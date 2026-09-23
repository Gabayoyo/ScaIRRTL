package chocola.lowering

import scair.MLContext
import scair.ir.*
import scair.passes.reconcile.ReconcileUnrealizedCasts
import scair.transformations.*
import scair.utils.Err
import scair.verify.Verifier

final class ConvertChocoToMlir(ctx: MLContext) extends ModulePass(ctx):
  override val name = "convert-choco-to-mlir"

  private val passes: Seq[ModulePass] = Seq(
    ConvertChocoToFunc(ctx),
    ConvertChocoToScf(ctx),
    ConvertChocoToArith(ctx),
    ReconcileUnrealizedCasts(ctx),
  )

  override def transform(module: Operation): Operation =
    passes.foldLeft(module) { (current, pass) =>
      val transformed = pass.transform(current)
      Verifier.verify(transformed) match
        case error: Err =>
          throw IllegalStateException(s"${pass.name}: ${error.msg}")
        case _ => transformed
    }

val allPasses: Seq[MLContext => ModulePass] = Seq(
  ConvertChocoToFunc(_),
  ConvertChocoToScf(_),
  ConvertChocoToArith(_),
  ConvertChocoToMlir(_),
)
