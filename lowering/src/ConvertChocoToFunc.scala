package chocola.lowering

import chocola.dialects.*
import scair.MLContext
import scair.dialects.builtin.*
import scair.dialects.func
import scair.ir.*
import scair.transformations.*

import scala.annotation.tailrec

@tailrec
private def enclosingSymbolTable(op: Operation): Option[SymbolTable] =
  directRegionOwner(op) match
    case Some(table: SymbolTable) => Some(table)
    case Some(parent)             => enclosingSymbolTable(parent)
    case None                     => None

/** The signature the function `name` has once converted, if it converts at all.
  * It is either converted already - to a `func.func` - or still to be, when
  * called before its conversion or from a sibling.
  */
private def convertedSignature(
    from: Operation,
    name: String,
): Option[FunctionType] =
  enclosingSymbolTable(from).flatMap(SymbolTable.lookupSymbol(_, name))
    .flatMap {
      case function: func.Func  => Some(function.function_type)
      case function: FuncDeclOp => convertedFunctionType(function)
      case _                    => None
    }

/** The driver converts the entry block's arguments of the new `func.func`, as
  * it holds a region of a converted operation.
  */
private val ConvertFuncDecl = conversionPattern { case op: FuncDeclOp =>
  convertedFunctionType(op) match
    case Some(signature) =>
      func.Func(op.sym.rootRef, signature, body = op.body.detached)
    case None => PatternAction.Abort
}

private val ConvertReturn = conversionPattern { case op: ReturnOp =>
  directRegionOwner(op) match
    case Some(_: func.Func) => func.Return(adaptor.operands)
    case _                  => PatternAction.Abort
}

private val ConvertDirectCall = conversionPattern { case op: DirectCallOp =>
  convertedSignature(op, op.callee.rootRef.data) match
    case Some(signature) =>
      func.Call(
        op.callee,
        adaptor.operands,
        signature.outputs.data.map(Result(_)),
      )
    case None => PatternAction.Abort
}

final class ConvertChocoToFunc(ctx: MLContext) extends ConversionPass(ctx):
  override val name: String = "convert-choco-to-func"

  override val typeConverter: TypeConverter = chocoTypeConverter

  override val patterns: Seq[ConversionPattern] =
    Seq(ConvertFuncDecl, ConvertReturn, ConvertDirectCall)
