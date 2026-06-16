import caseapp.{CaseApp, RemainingArgs}

case class Options()

object Main extends CaseApp[Options]:

  def run(options: Options, remainingArgs: RemainingArgs): Unit =
    println("Hello, World!")
