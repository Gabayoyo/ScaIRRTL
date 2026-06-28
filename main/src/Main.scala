package chocola.main

import caseapp.{CaseApp, RemainingArgs}

import chocola.dialects.lowerProgram
import chocola.parser.parseFile
import scair.print.AssemblyPrinter

case class Options(dumpAst: Boolean = false)

object Main extends CaseApp[Options]:

  def run(options: Options, remainingArgs: RemainingArgs): Unit =
    val file = remainingArgs.all match
      case Seq(file) => file
      case _         =>
        throw new IllegalArgumentException(
          "Please provide exactly one file to compile."
        )

    parseFile(file) match
      case Some(ast) =>
        println("Parsing succeeded.")
        if options.dumpAst then println(ast)
        else
          val printer =
            AssemblyPrinter(p = java.io.OutputStreamWriter(System.out))
          lowerProgram(ast).foreach { op =>
            op.customPrint(printer)
            printer.print("\n")
          }
          printer.flush()
      case None => println("Parsing failed.")
