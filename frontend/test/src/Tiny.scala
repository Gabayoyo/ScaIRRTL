package scairrtl.frontend

import chisel3.*
import chisel3.util.Cat

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

class ElseWhenTiny extends Module:

  val io = IO(new Bundle:
    val a = Input(Bool())
    val b = Input(Bool())
    val y = Output(UInt(8.W)))

  val r = RegInit(0.U(8.W))

  when(io.a) {
    r := 1.U(8.W)
  }.elsewhen(io.b) {
    r := 2.U(8.W)
  }
  .otherwise {
    r := 3.U(8.W)
  }

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

class BitwiseTiny extends Module:

  val io = IO(new Bundle:
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val and = Output(UInt(8.W))
    val or = Output(UInt(8.W))
    val xor = Output(UInt(8.W))
    val not = Output(UInt(8.W))
    val andReduce = Output(Bool())
    val orReduce = Output(Bool())
    val xorReduce = Output(Bool()))

  io.and := io.a & io.b
  io.or := io.a | io.b
  io.xor := io.a ^ io.b
  io.not := ~io.a
  io.andReduce := io.a.andR
  io.orReduce := io.a.orR
  io.xorReduce := io.a.xorR

class ArithTiny extends Module:

  val io = IO(new Bundle:
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val sh = Input(UInt(3.W))
    val diff = Output(UInt(16.W))
    val prod = Output(UInt(16.W))
    val quot = Output(UInt(8.W))
    val rem = Output(UInt(8.W))
    val cat = Output(UInt(16.W))
    val shifted = Output(UInt(16.W)))

  io.diff := (io.a - io.b).pad(16)
  io.prod := io.a * io.b
  io.quot := io.a / io.b
  io.rem := io.a % io.b
  io.cat := Cat(io.a, io.b)
  io.shifted := (io.a << io.sh).pad(16)

object TinyState extends ChiselEnum:
  val idle, run = Value

class EnumTiny extends Module:

  val io = IO(new Bundle:
    val go = Input(Bool())
    val state = Output(TinyState()))

  val state = RegInit(TinyState.idle)
  when(io.go)(state := TinyState.run)
  io.state := state

class ResetPortsTiny extends RawModule:

  val reset = IO(Input(Reset()))
  val asyncReset = IO(Input(AsyncReset()))

class ResetRegTiny extends Module:

  val io = IO(new Bundle:
    val rst = Input(Reset())
    val out = Output(UInt(8.W)))

  val r = withReset(io.rst) {
    RegInit(0.U(8.W))
  }

  io.out := r

class PrintfTiny extends Module:

  val io = IO(
    new Bundle:
      val a = Input(UInt(8.W))
  )

  printf(p"a=${io.a}\n")
