package chocola.main

import caseapp.{CaseApp, RemainingArgs}

import chocola.parser.parseFile

case class Options()

object Main extends CaseApp[Options]:

  def run(options: Options, remainingArgs: RemainingArgs): Unit =
    // get the unique file from remainingArgs or error out
    val file = remainingArgs.all match
      case Seq(file) => file
      case _         =>
        throw new IllegalArgumentException(
          "Please provide exactly one file to compile."
        )

    parseFile(file) match
      case Some(value) =>
        println("Parsing succeeded.")
        println(value)
      case None => println("Parsing failed.")
