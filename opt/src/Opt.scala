package chocola.main

import chocola.dialects.ChocolaDialect
import chocola.lowering.allPasses as chocolaPasses
import chocola.parser.SourceError
import chocola.parser.TypeError
import chocola.parser.TypeInferencer
import chocola.parser.diagnostic
import chocola.parser.generateProgram
import chocola.parser.parseSource
import scair.MLContext
import scair.ir.*
import scair.parse.*
import scair.print.AssemblyPrinter
import scair.print.ErrorPrinter
import scair.utils.*
import scair.verify.Verifier
import scopt.OParser

import java.io.IOException
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.lang.ProcessBuilder.Redirect
import java.nio.charset.StandardCharsets
import scala.io.BufferedSource
import scala.io.Source
import scala.jdk.CollectionConverters.*

case class ChocoOptArgs(
    allowUnregistered: Boolean = false,
    input: Option[String] = None,
    skipVerify: Boolean = false,
    splitInputFile: Boolean = false,
    parsingDiagnostics: Boolean = false,
    printGeneric: Boolean = false,
    printLocations: Boolean = false,
    passes: Seq[String] = Seq(),
    verifyDiagnostics: Boolean = false,
    dumpAst: Boolean = false,
    run: Boolean = false,
)

object Main:

  private val ctx = MLContext()

  private val dialects = scair.dialects.allDialects :+ ChocolaDialect

  private val passes = scair.passes.allPasses ++ chocolaPasses

  dialects.foreach(ctx.registerDialect)
  passes.foreach(ctx.registerPass)

  private def commonHeaders =
    val argbuilder = OParser.builder[ChocoOptArgs]
    OParser.sequence(
      argbuilder.programName("choco-opt"),
      argbuilder.head("choco-opt", "0"),
    )

  private def inputChunks(
      input: BufferedSource,
      splitInputFile: Boolean,
  ): Array[String] =
    if splitInputFile then input.mkString.split("\n// -----\n")
    else Array(input.mkString)

  private def reportTypeErrors(
      errors: List[TypeError],
      diagnostics: Boolean,
  ): Unit =
    errors.foreach { error =>
      val message = diagnostic(error.location, s"Type error: ${error.msg}")
      if diagnostics then println(message) else Console.err.println(message)
    }

  /** Diagnostic mode reports errors as output; compilation stops on errors. */
  private def handleParsingError(error: String, args: ChocoOptArgs): Unit =
    if args.parsingDiagnostics then println(error)
    else throw Exception(error)

  private def parseChocola(
      args: ChocoOptArgs,
      input: BufferedSource,
  ): Array[OK[Operation]] =
    Array(parseSource(input.mkString, args.input.getOrElse("-")) match
      case Right(ast) =>
        val errors = TypeInferencer().inferProgram(ast)
        if errors.nonEmpty then
          Err(
            errors.map(e => diagnostic(e.location, s"Type error: ${e.msg}"))
              .mkString("\n")
          ): OK[Operation]
        else
          try OK(generateProgram(ast): Operation)
          catch
            case SourceError(msg, location) =>
              Err(diagnostic(location, s"error: $msg")): OK[Operation]
      case Left(error) => Err(error): OK[Operation])

  private def parseMlir(
      args: ChocoOptArgs
  )(input: BufferedSource): Array[OK[Operation]] =
    var indexOffset = 0
    val chunks = inputChunks(input, args.splitInputFile)
    chunks.map { input =>
      val parser = new Parser(
        ctx,
        inputPath = args.input,
        parsingDiagnostics = args.parsingDiagnostics,
        allowUnregisteredDialect = args.allowUnregistered,
      )
      val parsed = parser.parse(
        input,
        parser = moduleP(using _, parser),
      ) match
        case fastparse.Parsed.Success(inputModule, _) =>
          OK(inputModule: Operation)
        case failure: fastparse.Parsed.Failure =>
          Err(parser.error(failure, indexOffset)): OK[Operation]

      if args.splitInputFile && !(input eq chunks.last) then
        indexOffset += input.count(_ == '\n') + 2
      parsed
    }

  private def parse(args: ChocoOptArgs)(
      input: BufferedSource
  ): Array[OK[Operation]] =
    if args.input.exists(_.endsWith(".cola")) then parseChocola(args, input)
    else parseMlir(args)(input)

  private def parseArgs(args: Array[String]): ChocoOptArgs =
    val argbuilder = OParser.builder[ChocoOptArgs]
    val argparser =
      import argbuilder.*
      OParser.sequence(
        commonHeaders,
        arg[String]("file").optional().text("input file")
          .action((x, c) => c.copy(input = Some(x))),
        opt[Unit]('a', "allow-unregistered-dialect").optional().text(
          "Accept unregistered operations and attributes, best effort with generic syntax."
        ).action((_, c) => c.copy(allowUnregistered = true)),
        opt[Unit]('s', "skip-verify").optional().text("Skip verification")
          .action((_, c) => c.copy(skipVerify = true)),
        opt[Unit]("split-input-file").optional()
          .text(
            "Split MLIR input on `// -----` (ChocoLa files are parsed whole)"
          ).action((_, c) => c.copy(splitInputFile = true)),
        opt[Unit]("parsing-diagnostics").optional().text(
          "Parsing diagnose mode, i.e parse errors are not fatal for the whole run"
        ).action((_, c) => c.copy(parsingDiagnostics = true)),
        opt[Unit]('g', "print-generic").optional()
          .text("Print strictly in generic format")
          .action((_, c) => c.copy(printGeneric = true)),
        opt[Unit]("print-locations").optional()
          .text("Print operation source locations")
          .action((_, c) => c.copy(printLocations = true)),
        opt[Seq[String]]('p', "passes").optional()
          .text("Specify passes to apply to the IR")
          .action((x, c) => c.copy(passes = x)),
        opt[Unit]("verify-diagnostics").optional().text(
          "Verification diagnose mode, i.e verification errors are not fatal for the whole run"
        ).action((_, c) => c.copy(verifyDiagnostics = true)),
        opt[Unit]("dump-ast").optional()
          .text("Print the parsed ChocoLa AST instead of generating IR")
          .action((_, c) => c.copy(dumpAst = true)),
        opt[Unit]("run").optional()
          .text("Execute the lowered main function with MLIR tools")
          .action((_, c) => c.copy(run = true)),
      )

    OParser.parse(argparser, args, ChocoOptArgs()).get

  private def handleVerificationError(
      error: Err,
      operation: Operation,
      args: ChocoOptArgs,
  ): OK[Operation] =
    val printer = ErrorPrinter(error, _printLocations = args.printLocations)
    printer.printTopLevel(operation)
    if args.verifyDiagnostics then error else sys.exit(42)

  private def dumpAst(args: ChocoOptArgs, input: BufferedSource): Unit =
    if !args.input.exists(_.endsWith(".cola")) then
      throw IllegalArgumentException("--dump-ast requires a .cola input file")

    parseSource(input.mkString, args.input.getOrElse("-")) match
      case Right(ast) =>
        println("Parsing succeeded.")
        val errors = TypeInferencer().inferProgram(ast)
        reportTypeErrors(errors, args.parsingDiagnostics)
        println(ast)
        if errors.nonEmpty && !args.parsingDiagnostics then sys.exit(1)
      case Left(error) =>
        println(error)
        println("Parsing failed.")
        if !args.parsingDiagnostics then sys.exit(1)

  private def runModule(operation: Operation, printGeneric: Boolean): Unit =
    val writer = StringWriter()
    val printer = AssemblyPrinter(printGeneric, p = writer)
    printer.printTopLevel(operation)
    printer.flush()

    val mlirOpt = ProcessBuilder(
      "mlir-opt",
      "--convert-scf-to-cf",
      "--convert-to-llvm",
    ).redirectError(Redirect.INHERIT)
    val mlirRunner = ProcessBuilder(
      "mlir-runner",
      "-e",
      "main",
      "--entry-point-result=i32",
    ).redirectError(Redirect.INHERIT).redirectOutput(Redirect.INHERIT)

    val processes =
      try ProcessBuilder.startPipeline(List(mlirOpt, mlirRunner).asJava)
      catch
        case error: IOException =>
          Console.err.println(
            s"error: unable to start MLIR execution pipeline: ${error.getMessage}"
          )
          sys.exit(1)

    val optimizer = processes.get(0)
    val runner = processes.get(1)
    val optimizerInput = OutputStreamWriter(
      optimizer.getOutputStream,
      StandardCharsets.UTF_8,
    )
    try optimizerInput.write(writer.toString)
    finally optimizerInput.close()

    val optimizerStatus = optimizer.waitFor()
    val runnerStatus = runner.waitFor()
    if optimizerStatus != 0 then
      Console.err
        .println(s"error: mlir-opt exited with status $optimizerStatus")
      sys.exit(optimizerStatus)
    if runnerStatus != 0 then
      Console.err
        .println(s"error: mlir-runner exited with status $runnerStatus")
      sys.exit(runnerStatus)

  def main(args: Array[String]): Unit =
    val parsedArgs = parseArgs(args)
    val input = parsedArgs.input match
      case Some(file) => Source.fromFile(file)
      case None       => Source.stdin

    if parsedArgs.dumpAst then
      dumpAst(parsedArgs, input)
      return

    val parsedModules = parse(parsedArgs)(input)
    parsedModules.zipWithIndex.foreach { case (parsedModule, index) =>
      parsedModule match
        case OK(inputModule) =>
          var processedModule: OK[Operation] =
            if parsedArgs.skipVerify then OK(inputModule)
            else inputModule.structured.flatMap(op => Verifier.verify(op))

          processedModule match
            case OK(_) =>
              parsedArgs.passes.foreach { passName =>
                val pass = ctx.passContext.get(passName) match
                  case Some(pass) => pass
                  case None       =>
                    Console.err.println(
                      s"error: '$passName' does not refer to a registered pass."
                    )
                    Console.err.println("Currently registered passes are:")
                    ctx.passContext.keysIterator.toSeq.sorted
                      .foreach(name => Console.err.println(s"  - $name"))
                    sys.exit(1)

                processedModule = processedModule.map { module =>
                  val output = pass.transform(module)
                  if !parsedArgs.skipVerify then
                    Verifier.verify(output).fold(
                      handleVerificationError(_, output, parsedArgs),
                      _ => (),
                    )
                  output
                }
              }
            case error: Err =>
              processedModule =
                handleVerificationError(error, inputModule, parsedArgs)

          if parsedArgs.run then
            processedModule.fold(
              _ => (),
              runModule(_, parsedArgs.printGeneric),
            )
          else
            val printer = AssemblyPrinter(
              parsedArgs.printGeneric,
              printLocations = parsedArgs.printLocations,
            )
            processedModule.fold(_ => (), printer.printTopLevel)
            printer.flush()
        case Err(msg = errorMsg) =>
          handleParsingError(errorMsg, parsedArgs)

      if index + 1 < parsedModules.length && !parsedArgs.run then
        println("// -----")
    }
