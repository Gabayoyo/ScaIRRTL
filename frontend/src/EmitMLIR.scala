package scairrtl.frontend

import java.io.IOException
import java.io.StringWriter
import java.io.UncheckedIOException
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scair.print.AssemblyPrinter
import scairrtl.dialects.*

/** Assembly text options. */
final case class EmitOptions(
    generic: Boolean = false,
    locations: Boolean = false,
)

/** Prints a translated circuit as MLIR assembly text. */
object EmitMLIR:

  /** Renders the circuit as assembly text. */
  def apply(circuit: CircuitOp, options: EmitOptions = EmitOptions()): String =
    val out = StringWriter()
    write(circuit, out, options)
    out.toString

  /** Writes the assembly to `out`, leaving the writer open. */
  def write(
      circuit: CircuitOp,
      out: Writer,
      options: EmitOptions = EmitOptions(),
  ): Unit =
    val printer =
      AssemblyPrinter(
        options.generic,
        printLocations = options.locations,
        p = out,
      )
    printer.printTopLevel(circuit)
    printer.flush()

  /** Writes the assembly to `path` as UTF-8, replacing any existing file. */
  def writeToFile(
      circuit: CircuitOp,
      path: Path,
      options: EmitOptions = EmitOptions(),
  ): Either[String, Path] =
    try
      val out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)
      try write(circuit, out, options)
      finally out.close()
      Right(path)
    catch
      case error: IOException =>
        Left(s"cannot write $path: ${error.getMessage}")
      case error: UncheckedIOException =>
        Left(s"cannot write $path: ${error.getMessage}")

  /** Name of the file holding the circuit's assembly. */
  def fileName(circuit: CircuitOp): String =
    val name = circuit.sym_name.data
    s"${if name.isBlank then "circuit" else name}.mlir"

  /** Path of the circuit's assembly file inside `dir`. */
  def pathFor(circuit: CircuitOp, dir: Path): Path =
    dir.resolve(fileName(circuit))
