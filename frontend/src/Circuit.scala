package chisel3

import chisel3.internal.firrtl.ir

// Exposes the internal circuit
object ChiselCircuit:
  def apply(elaborated: ElaboratedCircuit): ir.Circuit = elaborated._circuit
