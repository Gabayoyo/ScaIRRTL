package scairrtl.frontend

import chisel3.ChiselImport
import chisel3.ElaboratedCircuit
import scair.utils.*
import scairrtl.dialects.CircuitOp

extension (elaborated: ElaboratedCircuit)

  /** Translates an elaborated Chisel circuit into firrtl and chirrtl
    * operations.
    */
  def toCircuitOp: Either[String, CircuitOp] =
    ChiselImport(elaborated).fold[Either[String, CircuitOp]](
      error => Left(error.msg),
      circuit => Right(circuit),
    )
