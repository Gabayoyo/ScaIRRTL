package chocola.parser

import fastparse.*
import fastparse.ScalaWhitespace.given

// This is copy-pasta from fastparse/scalaparse, at least to get started quickly.

def If[$: P] = P(`if` ~/ Expr ~ `then` ~ Expr ~ `else` ~ Expr)

def Expr[$: P]: P[Unit] = P(
  Literal | Id | If
)

def Literal[$: P] = P(IntLiteral | FloatLiteral | BooleanLiteral)

def IntLiteral[$: P] = P(Int)
def FloatLiteral[$: P] = P(Float)
def BooleanLiteral[$: P] = P(`true` | `false`)

def Def[$: P] = P(ValDef)
def ValDef[$: P] = P(`val` ~/ VarId ~ `=` ~ Expr)

def Program[$: P] = P(Def.rep ~ Expr ~ End)

def parseFile(file: String): Option[Any] =
  val source = scala.io.Source.fromFile(file)
  val input =
    try source.mkString
    finally source.close()
  parse(input, Program(using _)) match
    case Parsed.Success(value, index) =>
      println(s"Parsed successfully up to index $index")
      Some(value)
    case f: Parsed.Failure =>
      println(f.trace().longMsg)
      None
