package scairrtl.frontend

import circt.stage.ChiselStage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.scalatest.funsuite.AnyFunSuite
import scair.MLContext
import scair.parse.*
import scair.utils.*
import scairrtl.dialects.CHIRRTLDialect
import scairrtl.dialects.CircuitOp
import scairrtl.dialects.FIRRTLDialect

class EmitMLIRTest extends AnyFunSuite:

  private def translate(module: => chisel3.Module): CircuitOp =
    ChiselStage.elaborate(module).toCircuitOp match
      case Right(circuit) => circuit
      case Left(error)    => fail(s"translation failed: $error")

  private def parses(text: String): Boolean =
    val ctx = MLContext()
    ctx.registerDialect(FIRRTLDialect)
    ctx.registerDialect(CHIRRTLDialect)
    val parser = Parser(
      ctx,
      inputPath = None,
      parsingDiagnostics = false,
      allowUnregisteredDialect = false,
    )
    parser.parse(text, parser = moduleP(using _, parser)) match
      case _: fastparse.Parsed.Failure    => false
      case _: fastparse.Parsed.Success[?] => true

  test("translates an elaborated circuit"):
    val result = ChiselStage.elaborate(new CombMemTiny).toCircuitOp
    assert(result.isRight)

  test("reports translation errors"):
    ChiselStage.elaborate(new PrintfTiny).toCircuitOp match
      case Left(error) => assert(error.contains("LayerBlock"))
      case Right(_)    => fail("expected a translation error")

  test("prints firrtl and chirrtl operations into one module"):
    val text = EmitMLIR(translate(new CombMemTiny))
    assert(text.contains("\"firrtl.module\""))
    assert(text.contains("\"firrtl.matchingconnect\""))
    assert(text.contains("\"chirrtl.combmem\""))
    assert(text.contains("\"chirrtl.memoryport\""))
    assert(parses(text))

  test("prints nested when regions that parse back"):
    val text = EmitMLIR(translate(new WhenTiny))
    assert(text.contains("\"firrtl.when\""))
    assert(parses(text))

  test("prints source locations when requested"):
    val circuit = translate(new Tiny)
    assert(!EmitMLIR(circuit).contains("loc("))
    val located = EmitMLIR(circuit, EmitOptions(locations = true))
    assert(located.contains("loc("))
    assert(parses(located))

  test("writes a file that parses back"):
    val circuit = translate(new CombMemTiny)
    val path = Files.createTempFile("scairrtl-", ".mlir")
    try
      assert(EmitMLIR.writeToFile(circuit, path) == Right(path))
      val written = Files.readString(path, StandardCharsets.UTF_8)
      assert(written == EmitMLIR(circuit))
      assert(parses(written))
    finally Files.deleteIfExists(path)

  test("names files after the circuit"):
    val circuit = translate(new Tiny)
    val expected = s"${circuit.sym_name.data}.mlir"
    assert(EmitMLIR.fileName(circuit) == expected)
    assert(
      EmitMLIR.pathFor(circuit, Path.of("build", "mlir")).endsWith(expected)
    )

  test("reports an unwritable path"):
    val path = Files.createTempDirectory("scairrtl-")
    try
      val result = EmitMLIR.writeToFile(translate(new Tiny), path)
      result match
        case Left(error) => assert(error.contains("cannot write"))
        case Right(_)    => fail("expected a write error")
    finally Files.deleteIfExists(path)
