package scairrtl.frontend

import chisel3.ChiselImport
import circt.stage.ChiselStage
import java.io.StringWriter
import org.scalatest.funsuite.AnyFunSuite
import scair.print.AssemblyPrinter
import scair.utils.*
import scairrtl.dialects.*

class ChiselImportTest extends AnyFunSuite:

  private def render(circuit: CircuitOp): String =
    val writer = StringWriter()
    val printer = AssemblyPrinter(false, p = writer)
    printer.printTopLevel(circuit)
    printer.flush()
    writer.toString

  private def count(text: String, needle: String): Int =
    text.split(java.util.regex.Pattern.quote(needle), -1).length - 1

  test("imports a scalar module as firrtl operations"):
    val result = ChiselImport(ChiselStage.elaborate(new Tiny))
    assert(result.isOK)
    val text = render(result.get)
    assert(text.contains("\"firrtl.module\""))
    assert(text.contains("\"firrtl.add\""))
    assert(text.contains("\"firrtl.matchingconnect\""))
    assert(
      text.contains(
        "!firrtl.bundle<a flip: !firrtl.uint<8>, b flip: !firrtl.uint<8>, y: !firrtl.uint<9>>"
      )
    )
    assert(text.contains("\"firrtl.subfield\""))
    assert(text.contains("fieldIndex = 0 : i32"))
    assert(text.contains("fieldIndex = 1 : i32"))
    assert(text.contains("fieldIndex = 2 : i32"))
    assert(!text.contains("\"io_a\""))

  test("keeps nested bundle flips"):
    val result = ChiselImport(ChiselStage.elaborate(new Nested))
    assert(result.isOK)
    val text = render(result.get)
    assert(
      text.contains(
        "!firrtl.bundle<inner: !firrtl.bundle<x flip: !firrtl.uint<8>, y: !firrtl.uint<8>>>"
      )
    )

  test("projects vector elements"):
    val result = ChiselImport(ChiselStage.elaborate(new VecTiny))
    assert(result.isOK)
    val text = render(result.get)
    assert(text.contains("!firrtl.vector<!firrtl.uint<8>, 2>"))
    assert(text.contains("\"firrtl.subindex\""))
    assert(text.contains("index = 0 : i32"))

  test("imports combinational memories as chirrtl operations"):
    val result = ChiselImport(ChiselStage.elaborate(new CombMemTiny))
    assert(result.isOK)
    val text = render(result.get)
    assert(text.contains("\"chirrtl.combmem\""))
    assert(text.contains("!chirrtl.cmemory<!firrtl.uint<8>, 16>"))
    assert(text.contains("\"chirrtl.memoryport\""))
    assert(text.contains("direction = 2 : i32"))
    assert(text.contains("direction = 1 : i32"))
    assert(text.contains("\"chirrtl.memoryport.access\""))
    assert(count(text, "\"chirrtl.memoryport\"") == 2)
    assert(count(text, "\"chirrtl.memoryport.access\"") == 2)

  test("imports sequential memories with their read-under-write behavior"):
    val result = ChiselImport(ChiselStage.elaborate(new SeqMemTiny))
    assert(result.isOK, result.fold(error => error.msg, _ => "ok"))
    val text = render(result.get)
    assert(text.contains("\"chirrtl.seqmem\""))
    assert(text.contains("ruw = 1 : i32"))
    assert(text.contains("!chirrtl.cmemory<!firrtl.uint<8>, 16>"))
    assert(text.contains("\"chirrtl.memoryport.access\""))

  test("rejects unsupported commands"):
    val result = ChiselImport(ChiselStage.elaborate(new WhenTiny))
    assert(result.isError)
    result.fold(
      error => assert(error.msg.contains("When")),
      _ => fail("expected an unsupported-command error"),
    )
