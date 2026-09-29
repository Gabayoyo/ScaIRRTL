package chocola.parser

import fastparse.*
import fastparse.NoWhitespace.*
import scair.ir.FileLineColLoc
import scair.ir.FileLineColRange
import scair.ir.Location
import scair.ir.UnknownLoc

import Literal.*
import Type.*
import Expr.*
import Decl.*

// ─── Whitespace & lexical ────────────────────────────────────────────────────

def lineComment[$: P]: P[Unit] = P("//" ~ (!"\n" ~ AnyChar).rep)

def ws[$: P]: P[Unit] = P((CharsWhileIn(" \t\r\n") | lineComment).rep)
  .map(_ => ())

def identChar[$: P]: P[Unit] = P(CharIn("a-zA-Z0-9_"))

// Matches s and asserts it is not followed by an identifier character.
def kw[$: P](s: String): P[Unit] = P(s ~ !identChar)

def lowerIdent[$: P]: P[String] =
  P((CharIn("a-z") ~ CharsWhileIn("a-zA-Z0-9_").?).!)
    .filter(!keywords.contains(_))

def upperIdent[$: P]: P[String] =
  P((CharIn("A-Z") ~ CharsWhileIn("a-zA-Z0-9_").?).!)

val keywords: Set[String] = Set(
  "val",
  "def",
  "type",
  "enum",
  "case",
  "match",
  "if",
  "then",
  "else",
  "true",
  "false",
  "Nil",
  "map",
  "filter",
  "foldLeft",
  "foldRight",
)

// ─── Source locations ────────────────────────────────────────────────────────

// Source text that knows which file it comes from. `lineOffset` shifts the
// lines of a chunk parsed out of a larger file (e.g. --split-input-file).
final class SourceInput(data: String, file: String, lineOffset: Int)
    extends IndexedParserInput(data):

  // The 1-based line and column fastparse reports, as a ScaIR location.
  def location(index: Int): FileLineColLoc =
    val lineCol = prettyIndex(index).split(':')
    FileLineColLoc(file, lineCol(0).toInt + lineOffset, lineCol(1).toInt)

  // The text between two indices; the end column is one past the last
  // character, as in MLIR.
  def range(start: Int, end: Int): FileLineColRange =
    val from = location(start)
    val to = location(end)
    FileLineColRange(file, from.line, from.column, to.line, to.column)

// The location of the text between two indices of the current input.
private def span[$: P](start: Int, end: Int): Location =
  summon[P[Any]].input match
    case input: SourceInput => input.range(start, end)
    case _                  => UnknownLoc

// Annotates the node parsed by p with the range of text it was parsed from,
// unless it already has one: an operand passed through unchanged (e.g. the
// inner expression of a parenthesised group) keeps its own range.
def located[$: P, T <: Located](p: => P[T]): P[T] =
  P(Index ~ p ~ Index).map { (start, node, end) =>
    if node.location == UnknownLoc then node.at(span(start, end)) else node
  }

// ─── Literals ────────────────────────────────────────────────────────────────

// Double must be tried before Int because both start with digits.
def doubleLit[$: P]: P[Literal] =
  P((CharsWhileIn("0-9") ~ "." ~ CharsWhileIn("0-9")).!)
    .map(s => DoubleLit(s.toDouble))

def intLit[$: P]: P[Literal] =
  P(CharsWhileIn("0-9").!).map(s => IntLit(s.toInt))

def strLit[$: P]: P[Literal] =
  P("\"" ~ (!"\"" ~ AnyChar).rep.! ~ "\"").map(StrLit(_))

def boolLit[$: P]: P[Literal] =
  P(kw("true").map(_ => BoolLit(true)) | kw("false").map(_ => BoolLit(false)))

def literal[$: P]: P[Literal] =
  P(
    doubleLit | intLit | strLit | boolLit | kw("Nil").map(_ => NilLit) |
      kw("map").map(_ => MapPrim) | kw("filter").map(_ => FilterPrim) |
      kw("foldLeft").map(_ => FoldLeftPrim) | kw("foldRight")
        .map(_ => FoldRightPrim)
  )

// ─── Types ───────────────────────────────────────────────────────────────────

// Function type T1 => T2 is right-associative.
def typeExpr[$: P]: P[Type] =
  P(simpleType ~ (ws ~ "=>" ~ ws ~ typeExpr).?).map {
    case (t, None)    => t
    case (t, Some(r)) => TFun(t, r)
  }

def simpleType[$: P]: P[Type] =
  P(
    kw("Int").map(_ => TInt) | kw("Double").map(_ => TDouble) | kw("String")
      .map(_ => TString) | kw("Bool").map(_ => TBool) |
      (kw("List") ~ ws ~ "[" ~ ws ~ typeExpr ~ ws ~ "]").map(TList(_)) |
      parenType | upperIdent.map(TName(_)) | lowerIdent.map(TName(_))
  )

// A single type in parens is a grouping; multiple are a tuple.
def parenType[$: P]: P[Type] =
  P("(" ~ ws ~ typeExpr ~ (ws ~ "," ~ ws ~ typeExpr).rep ~ ws ~ ")").map {
    case (t, rest) if rest.isEmpty => t
    case (t, rest)                 => TTuple(t :: rest.toList)
  }

// ─── Expressions ─────────────────────────────────────────────────────────────

// Entry point — lowest precedence first.
def expr[$: P]: P[Expr] = P(orExpr)

def orExpr[$: P]: P[Expr] =
  P(Index ~ andExpr ~ (ws ~ "||" ~ ws ~ andExpr ~ Index).rep).map {
    case (start, h, t) =>
      t.foldLeft(h) { case (acc, (e, end)) =>
        BinOp("||", acc, e).at(span(start, end))
      }
  }

def andExpr[$: P]: P[Expr] =
  P(Index ~ eqExpr ~ (ws ~ "&&" ~ ws ~ eqExpr ~ Index).rep).map {
    case (start, h, t) =>
      t.foldLeft(h) { case (acc, (e, end)) =>
        BinOp("&&", acc, e).at(span(start, end))
      }
  }

def eqExpr[$: P]: P[Expr] =
  P(Index ~ cmpExpr ~ (ws ~ ("==" | "!=").! ~ ws ~ cmpExpr ~ Index).rep).map {
    case (start, h, t) =>
      t.foldLeft(h) { case (acc, (op, e, end)) =>
        BinOp(op, acc, e).at(span(start, end))
      }
  }

// <= and >= must be tried before < and > to avoid partial matches.
def cmpExpr[$: P]: P[Expr] =
  P(
    Index ~ consExpr ~
      (ws ~ ("<=" | ">=" | "<" | ">").! ~ ws ~ consExpr ~ Index).rep
  ).map { case (start, h, t) =>
    t.foldLeft(h) { case (acc, (op, e, end)) =>
      BinOp(op, acc, e).at(span(start, end))
    }
  }

// :: is right-associative (list cons).
def consExpr[$: P]: P[Expr] =
  located(P(addExpr ~ (ws ~ "::" ~ ws ~ consExpr).?).map {
    case (e, None)    => e
    case (e, Some(r)) => BinOp("::", e, r)
  })

def addExpr[$: P]: P[Expr] =
  P(Index ~ mulExpr ~ (ws ~ CharIn("+\\-").! ~ ws ~ mulExpr ~ Index).rep).map {
    case (start, h, t) =>
      t.foldLeft(h) { case (acc, (op, e, end)) =>
        BinOp(op, acc, e).at(span(start, end))
      }
  }

def mulExpr[$: P]: P[Expr] =
  P(Index ~ unaryExpr ~ (ws ~ CharIn("*/").! ~ ws ~ unaryExpr ~ Index).rep)
    .map { case (start, h, t) =>
      t.foldLeft(h) { case (acc, (op, e, end)) =>
        BinOp(op, acc, e).at(span(start, end))
      }
    }

def unaryExpr[$: P]: P[Expr] =
  P(
    located(("-" ~ ws ~ unaryExpr).map(UnOp("-", _))) |
      located(("!" ~ ws ~ unaryExpr).map(UnOp("!", _))) | postfixExpr
  )

// Function calls and match are postfix operations chained left-to-right.
def postfixExpr[$: P]: P[Expr] =
  P(Index ~ atom ~ (postfix ~ Index).rep).map { case (start, base, ops) =>
    ops.foldLeft(base) { case (acc, (f, end)) => f(acc).at(span(start, end)) }
  }

def postfix[$: P]: P[Expr => Expr] =
  P(
    (ws ~ "(" ~ ws ~ exprList ~ ws ~ ")")
      .map(args => (e: Expr) => Apply(e, args)) |
      (ws ~ kw("match") ~ ws ~ "{" ~ ws ~ matchCase.rep(1, ws) ~ ws ~ "}")
        .map(cases => (e: Expr) => Match(e, cases.toList))
  )

def exprList[$: P]: P[List[Expr]] =
  P((expr ~ (ws ~ "," ~ ws ~ expr).rep).?).map {
    case None         => Nil
    case Some((h, t)) => h :: t.toList
  }

// ─── Atoms ───────────────────────────────────────────────────────────────────

def atom[$: P]: P[Expr] =
  P(
    located(ifExpr) | located(lambdaExpr) | parenExpr | located(constructExpr) |
      located(literal.map(Lit(_))) | located(variable)
  )

def variable[$: P]: P[Expr] = P(lowerIdent.map(Var(_)))

// Constructor call: uppercase name followed by parenthesised args.
def constructExpr[$: P]: P[Expr] =
  P(upperIdent ~ "(" ~ ws ~ exprList ~ ws ~ ")").map(Construct(_, _))

def ifExpr[$: P]: P[Expr] =
  P(
    kw("if") ~ ws ~ expr ~ ws ~ kw("then") ~ ws ~ expr ~ ws ~ kw("else") ~ ws ~
      expr
  ).map(If(_, _, _))

// Typed parameter: x : T.
// Defined as P[List[...]] to prevent fastparse's tuple-flattening when composed.
def lambdaParam[$: P]: P[(String, Type)] =
  P(lowerIdent ~ ws ~ ":" ~ ws ~ typeExpr)

def lambdaParamList[$: P]: P[List[(String, Type)]] =
  P(lambdaParam ~ (ws ~ "," ~ ws ~ lambdaParam).rep).map {
    case (name, tpe, rest) => (name, tpe) :: rest.toList
  }

// Lambda: (x: T, ...) => body. Tried before parenExpr; backtracks on failure.
def lambdaExpr[$: P]: P[Expr] =
  P("(" ~ ws ~ lambdaParamList ~ ws ~ ")" ~ ws ~ "=>" ~ ws ~ expr).map {
    case (params, body) => Lambda(params, body)
  }

// Parenthesised expression or tuple (single element is just grouping, and
// keeps the location of the inner expression).
def parenExpr[$: P]: P[Expr] =
  located(P("(" ~ ws ~ expr ~ (ws ~ "," ~ ws ~ expr).rep ~ ws ~ ")").map {
    case (e, rest) if rest.isEmpty => e
    case (e, rest)                 => Tuple(e :: rest.toList)
  })

// ─── Match cases ─────────────────────────────────────────────────────────────

def matchBindings[$: P]: P[List[String]] =
  P((lowerIdent ~ (ws ~ "," ~ ws ~ lowerIdent).rep).?).map {
    case None         => Nil
    case Some((h, t)) => h :: t.toList
  }

def matchCase[$: P]: P[MatchCase] =
  located(
    P(
      kw("case") ~ ws ~ upperIdent ~ "(" ~ ws ~ matchBindings ~ ws ~ ")" ~ ws ~
        "=>" ~ ws ~ expr
    ).map { case (ctor, bindings, body) => MatchCase(ctor, bindings, body) }
  )

// ─── Declarations ────────────────────────────────────────────────────────────

def decl[$: P]: P[Decl] =
  P(ws ~ located(enumDecl | defDecl | typeDecl | valDecl))

def valDecl[$: P]: P[Decl] =
  P(kw("val") ~/ ws ~ lowerIdent.opaque("variable name") ~ ws ~ "=" ~ ws ~ expr)
    .map(ValDecl(_, _))

def typeDecl[$: P]: P[Decl] =
  P(kw("type") ~ ws ~ upperIdent ~ ws ~ "=" ~ ws ~ typeExpr).map(TypeDecl(_, _))

def typeParamList[$: P]: P[List[String]] =
  P("[" ~ ws ~ lowerIdent ~ (ws ~ "," ~ ws ~ lowerIdent).rep ~ ws ~ "]").map {
    case (h, t) => h :: t.toList
  }

def defParamList[$: P]: P[List[(String, Type)]] =
  P("(" ~ ws ~ lambdaParamList.? ~ ws ~ ")").map(_.getOrElse(Nil))

def defDecl[$: P]: P[Decl] =
  P(
    kw("def") ~ ws ~ lowerIdent ~
      (ws ~ typeParamList).? ~ ws ~ defParamList ~ ws ~ ":" ~ ws ~ typeExpr ~
      ws ~ "=" ~ ws ~ expr
  ).map { case (name, tps, params, ret, body) =>
    DefDecl(name, tps.getOrElse(Nil), params, ret, body)
  }

def enumDecl[$: P]: P[Decl] =
  P(
    kw("enum") ~ ws ~ upperIdent ~ (ws ~ typeParamList).? ~ ws ~ "{" ~ ws ~
      enumCaseDecl.rep(1, ws) ~ ws ~ "}"
  ).map { case (name, tps, cases) =>
    EnumDecl(name, tps.getOrElse(Nil), cases.toList)
  }

def enumCaseDecl[$: P]: P[EnumCase] =
  located(
    P(kw("case") ~ ws ~ upperIdent ~ "(" ~ ws ~ lambdaParamList.? ~ ws ~ ")")
      .map { case (name, fields) => EnumCase(name, fields.getOrElse(Nil)) }
  )

// ─── Program ─────────────────────────────────────────────────────────────────

def program[$: P]: P[Program] =
  P(decl.rep ~ ws ~ expr.? ~ ws ~ End).map { case (ds, e) =>
    Program(ds.toList, e)
  }

// ─── Entry point ─────────────────────────────────────────────────────────────

private def indexToLineCol(source: String, index: Int): (Int, Int) =
  val before = source.take(index)
  val line = before.count(_ == '\n') + 1
  val col = index - before.lastIndexOf('\n')
  (line, col)

def parseSource(
    source: String,
    file: String = "-",
    lineOffset: Int = 0,
): Either[String, Program] =
  val input = SourceInput(source, file, lineOffset)
  fastparse.parse(input, program(using _)) match
    case Parsed.Success(prog, _) => Right(prog)
    case f: Parsed.Failure       =>
      val (line, col) = indexToLineCol(source, f.index)
      val sourceLine = source.split('\n').lift(line - 1).getOrElse("")
      val caret = " " * (col - 1) + "^"
      val expected = f.trace().msg.replaceAll(":\\d+:\\d+,.*", "")
        .stripPrefix("Expected ")
      val header =
        diagnostic(input.location(f.index), s"Parse error: expected $expected")
      Left(s"$header\n$sourceLine\n$caret")

def parseFile(file: String): Option[Program] =
  val input = scala.io.Source.fromFile(file)
  try
    parseSource(input.mkString, file) match
      case Right(program) => Some(program)
      case Left(error)    =>
        println(error)
        None
  finally input.close()
