package chocola.main

import chocola.dialects.ChocolaDialect
import chocola.lowering.allPasses as chocolaPasses
import chocola.parser.generateProgram
import chocola.parser.parseSource
import chocola.parser.TypeInferencer
import scair.MLContext
import scair.ir.*
import scair.parse.*
import scair.print.AssemblyPrinter
import scair.print.ErrorPrinter
import scair.utils.*
import scair.verify.Verifier
import scopt.OParser

import scala.io.BufferedSource
import scala.io.Source

case class ChocoOptArgs(
    allowUnregistered: Boolean = false,
    input: Option[String] = None,
    skipVerify: Boolean = false,
    splitInputFile: Boolean = false,
    parsingDiagnostics: Boolean = false,
    printGeneric: Boolean = false,
    passes: Seq[String] = Seq(),
    verifyDiagnostics: Boolean = false,
    dumpAst: Boolean = false,
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

  private def parseChocola(
      input: BufferedSource,
      splitInputFile: Boolean,
  ): Array[OK[Operation]] =
    inputChunks(input, splitInputFile).map { source =>
      parseSource(source) match
        case Right(ast) =>
          TypeInferencer().inferProgram(ast)
            .foreach(error => Console.err.println(s"Type error: ${error.msg}"))
          OK(generateProgram(ast): Operation)
        case Left(error) => Err(error): OK[Operation]
    }

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
    if args.input.exists(_.endsWith(".cola")) then
      parseChocola(input, args.splitInputFile)
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
          .text("Split input file on `// -----`")
          .action((_, c) => c.copy(splitInputFile = true)),
        opt[Unit]("parsing-diagnostics").optional().text(
          "Parsing diagnose mode, i.e parse errors are not fatal for the whole run"
        ).action((_, c) => c.copy(parsingDiagnostics = true)),
        opt[Unit]('g', "print-generic").optional()
          .text("Print strictly in generic format")
          .action((_, c) => c.copy(printGeneric = true)),
        opt[Seq[String]]('p', "passes").optional()
          .text("Specify passes to apply to the IR")
          .action((x, c) => c.copy(passes = x)),
        opt[Unit]("verify-diagnostics").optional().text(
          "Verification diagnose mode, i.e verification errors are not fatal for the whole run"
        ).action((_, c) => c.copy(verifyDiagnostics = true)),
        opt[Unit]("dump-ast").optional()
          .text("Print the parsed ChocoLa AST instead of generating IR")
          .action((_, c) => c.copy(dumpAst = true)),
      )

    OParser.parse(argparser, args, ChocoOptArgs()).get

  private def handleVerificationError(
      error: Err,
      operation: Operation,
      verifyDiagnostics: Boolean,
  ): OK[Operation] =
    val printer = ErrorPrinter(error)
    printer.printTopLevel(operation)
    if verifyDiagnostics then error else sys.exit(42)

  private def dumpAst(args: ChocoOptArgs, input: BufferedSource): Unit =
    if !args.input.exists(_.endsWith(".cola")) then
      throw IllegalArgumentException("--dump-ast requires a .cola input file")

    val chunks = inputChunks(input, args.splitInputFile)
    chunks.zipWithIndex.foreach { case (source, index) =>
      parseSource(source) match
        case Right(ast) =>
          println("Parsing succeeded.")
          TypeInferencer().inferProgram(ast)
            .foreach(error => Console.err.println(s"Type error: ${error.msg}"))
          println(ast)
        case Left(error) =>
          println(error)
          println("Parsing failed.")
      if index + 1 < chunks.length then println("// -----")
    }

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
                      handleVerificationError(
                        _,
                        output,
                        parsedArgs.verifyDiagnostics,
                      ),
                      _ => (),
                    )
                  output
                }
              }
            case error: Err =>
              processedModule = handleVerificationError(
                error,
                inputModule,
                parsedArgs.verifyDiagnostics,
              )

          val printer = AssemblyPrinter(parsedArgs.printGeneric)
          processedModule.fold(_ => (), printer.printTopLevel)
          printer.flush()
        case Err(msg = errorMsg) =>
          if parsedArgs.parsingDiagnostics then println(errorMsg)
          else throw Exception(errorMsg)

      if index + 1 < parsedModules.length then println("// -----")
    }
