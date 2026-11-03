package scairrtl.frontend

import chisel3.*

class Tiny extends Module:

  val io = IO(new Bundle:
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val y = Output(UInt(9.W)))

  io.y := io.a +& io.b

class WhenTiny extends Module:

  val io = IO(new Bundle:
    val en = Input(Bool())
    val a = Input(UInt(8.W))
    val y = Output(UInt(8.W)))

  val r = RegInit(0.U(8.W))
  when(io.en)(r := io.a)
  io.y := r

class Nested extends Module:

  val io = IO(
    new Bundle:
      val inner = new Bundle:
        val x = Input(UInt(8.W))
        val y = Output(UInt(8.W))
  )

  io.inner.y := io.inner.x

class VecTiny extends Module:

  val io = IO(new Bundle:
    val a = Input(Vec(2, UInt(8.W)))
    val y = Output(UInt(8.W)))

  io.y := io.a(0)

class CombMemTiny extends Module:

  val io = IO(new Bundle:
    val addr = Input(UInt(4.W))
    val wdata = Input(UInt(8.W))
    val rdata = Output(UInt(8.W)))

  val mem = Mem(16, UInt(8.W))
  mem.write(io.addr, io.wdata)
  io.rdata := mem.read(io.addr)

class SeqMemTiny extends Module:

  val io = IO(new Bundle:
    val addr = Input(UInt(4.W))
    val wdata = Input(UInt(8.W))
    val y = Output(UInt(8.W)))

  val mem = SyncReadMem(16, UInt(8.W), SyncReadMem.ReadFirst)
  mem.write(io.addr, io.wdata)
  io.y := io.wdata
