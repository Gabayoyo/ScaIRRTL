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

// Layout helpers read the original input; they never insert tokens or keep a
// mutable indentation stack. A failed alternative can therefore backtrack safely.
def lineComment[$: P]: P[Unit] = P("//" ~ (!CharIn("\r\n") ~ AnyChar).rep)

def inlineSpace[$: P]: P[Unit] = P(CharsWhileIn(" \t", 0))

def lineTrivia[$: P]: P[Unit] = P(inlineSpace ~ lineComment.?)

def newline[$: P]: P[Unit] = P("\r\n" | "\n")

private def lineStart[$: P](index: Int): Int =
  summon[P[Any]].input.slice(0, index).lastIndexOf('\n') + 1

private def indentation[$: P](index: Int): Int =
  val prefix = summon[P[Any]].input.slice(lineStart(index), index)
  prefix.takeWhile(c => c == ' ' || c == '\t').length

private def spacesOnly[$: P](index: Int): Boolean =
  val prefix = summon[P[Any]].input.slice(lineStart(index), index)
  !prefix.takeWhile(c => c == ' ' || c == '\t').contains('\t')

// Blank and comment-only lines have no layout significance. Check indentation
// at the next code token, including inside freely wrapped argument/type lists.
def ws[$: P]: P[Unit] =
  P(
    (CharsWhileIn(" \t\r\n") | lineComment).rep ~
      (End | Index.filter(spacesOnly(_))
        .opaque("spaces instead of tabs in indentation"))
  ).map(_ => ())

private def atIndent[$: P](width: Int): P[Unit] =
  P(Index.filter(i => i == lineStart(i) + width && indentation(i) == width))
    .opaque(s"indentation of $width spaces").map(_ => ())

private def deeperIndent[$: P](width: Int): P[Int] =
  P(Index.filter(i => indentation(i) > width))
    .opaque(s"indentation greater than $width spaces")

// Binary expression rules inherit a contextual minIndent. Delimiters set it
// to zero; a multiline body uses its first line's indentation; an inline body
// requires continuation lines to be deeper than its enclosing line.

private def body[$: P](ownerIndent: Int): P[Expr] =
  P(
    lineTrivia ~
      (
        (newline ~/ ws ~ deeperIndent(ownerIndent))
          .flatMap(start => expr(using indentation(start))) | expr(using
          ownerIndent + 1
        )
      )
  )

// Only a trailing operator can continue an expression. Comment lines are fine,
// but a genuinely empty line must not bridge two statements (Scala's rule).
private def operandSpace[$: P](using minIndent: Int): P[Unit] =
  P(
    lineTrivia ~
      (newline ~/ (inlineSpace ~ lineComment ~ newline).rep ~ inlineSpace ~
        Index.filter(spacesOnly(_))
          .opaque("spaces instead of tabs in indentation") ~
        Index.filter(i => indentation(i) >= minIndent).opaque(
          s"continued operand indented at least $minIndent spaces"
        )).?
  ).map(_ => ())

// A branch keyword can stay inline, or start a line aligned with its owning if.
// In particular, `else` cannot hang off the last line of an indented then-body.
private def branchKeyword[$: P](name: String, owner: Int): P[Unit] =
  P(
    lineTrivia ~
      (
        (newline ~/ ws ~ atIndent(indentation(owner))) |
          Index.filter(i => lineStart(i) == lineStart(owner))
            .opaque(s"$name on the if's line or aligned with its indentation")
      ) ~ kw(name)
  ).map(_ => ())

// The separator backtracks when indentation differs: an outer case then remains
// available to its enclosing match instead of being swallowed by an inner one.
private def caseSeparator[$: P](width: Int): P[Unit] =
  P(lineTrivia ~ newline ~ ws ~ atIndent(width))

private def blockEnd[$: P](ownerIndent: Int): P[Unit] =
  def closing = P(CharIn(",)]") | End)
  def dedented = P(Index.filter(i => indentation(i) <= ownerIndent))
  def boundary = P(
    lineTrivia ~ (closing | (newline ~ ws ~ (closing | dedented)))
  )
  // Look ahead on success so ranges end at the last case's token. Only the
  // failing path consumes trivia, to diagnose the next offending code token.
  P(
    &(boundary) |
      (lineTrivia ~ (newline ~ ws).? ~/
        Fail.opaque("an aligned case, dedentation, or an enclosing delimiter"))
  )

private def caseBlock[$: P, T](ownerIndent: Int)(
    item: Int => P[T]
): P[List[T]] =
  P(lineTrivia ~ newline ~/ ws ~ deeperIndent(ownerIndent)).flatMap { start =>
    val width = indentation(start)
    P(item(width).rep(1, caseSeparator(width)) ~ blockEnd(ownerIndent))
      .map(_.toList)
  }

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

// The parentheses immediately before an arrow describe its parameter list.
// Trying this before simpleType preserves a tuple wrapped in another pair.
def typeExpr[$: P]: P[Type] =
  P(
    ("(" ~ ws ~ typeList ~ ws ~ ")" ~ ws ~ "=>" ~ ws ~ typeExpr)
      .map((ps, r) => TFun(ps, r)) |
      (simpleType ~ (ws ~ "=>" ~ ws ~ typeExpr).?).map {
        case (t, None)    => t
        case (t, Some(r)) => TFun(List(t), r)
      }
  )

def typeList[$: P]: P[List[Type]] =
  P((typeExpr ~ (ws ~ "," ~ ws ~ typeExpr).rep).?).map {
    case None         => Nil
    case Some((h, t)) => h :: t.toList
  }

def simpleType[$: P]: P[Type] =
  P(
    parenType |
      ((upperIdent | lowerIdent) ~ (ws ~ "[" ~ ws ~ typeList ~ ws ~ "]").?)
        .map { (n, args) =>
          (n, args.getOrElse(Nil)) match
            case ("Int", Nil)         => TInt
            case ("Double", Nil)      => TDouble
            case ("String", Nil)      => TString
            case ("Bool", Nil)        => TBool
            case ("List", List(elem)) => TList(elem)
            case (_, ts)              => TName(n, ts)
        }
  )

def parenType[$: P]: P[Type] =
  P("(" ~ ws ~ typeExpr ~ (ws ~ "," ~ ws ~ typeExpr).rep ~ ws ~ ")").map {
    case (t, rest) if rest.isEmpty => t
    case (t, rest)                 => TTuple(t :: rest.toList)
  }

// ─── Expressions ─────────────────────────────────────────────────────────────

// Entry point — lowest precedence first. Put minIndent first so boundaries
// can supply it explicitly; fastparse's parsing context is still inferred.
def expr[$](using minIndent: Int)(using P[$]): P[Expr] = P(orExpr)

def orExpr[$: P](using minIndent: Int): P[Expr] =
  P(
    Index ~ andExpr ~ (inlineSpace ~ "||" ~/ operandSpace ~ andExpr ~ Index).rep
  ).map { case (start, h, t) =>
    t.foldLeft(h) { case (acc, (e, end)) =>
      BinOp("||", acc, e).at(span(start, end))
    }
  }

def andExpr[$: P](using minIndent: Int): P[Expr] =
  P(
    Index ~ eqExpr ~ (inlineSpace ~ "&&" ~/ operandSpace ~ eqExpr ~ Index).rep
  ).map { case (start, h, t) =>
    t.foldLeft(h) { case (acc, (e, end)) =>
      BinOp("&&", acc, e).at(span(start, end))
    }
  }

def eqExpr[$: P](using minIndent: Int): P[Expr] =
  P(
    Index ~ cmpExpr ~
      (inlineSpace ~ ("==" | "!=").! ~/ operandSpace ~ cmpExpr ~ Index).rep
  ).map { case (start, h, t) =>
    t.foldLeft(h) { case (acc, (op, e, end)) =>
      BinOp(op, acc, e).at(span(start, end))
    }
  }

// <= and >= must be tried before < and > to avoid partial matches.
def cmpExpr[$: P](using minIndent: Int): P[Expr] =
  P(
    Index ~ consExpr ~
      (inlineSpace ~ ("<=" | ">=" | "<" | ">").! ~/ operandSpace ~ consExpr ~
        Index).rep
  ).map { case (start, h, t) =>
    t.foldLeft(h) { case (acc, (op, e, end)) =>
      BinOp(op, acc, e).at(span(start, end))
    }
  }

// :: is right-associative (list cons).
def consExpr[$: P](using minIndent: Int): P[Expr] =
  located(
    P(
      addExpr ~ (inlineSpace ~ "::" ~/ operandSpace ~ consExpr).?
    ).map {
      case (e, None)    => e
      case (e, Some(r)) => BinOp("::", e, r)
    }
  )

def addExpr[$: P](using minIndent: Int): P[Expr] =
  P(
    Index ~ mulExpr ~
      (inlineSpace ~ CharIn("+\\-").! ~/ operandSpace ~ mulExpr ~ Index).rep
  ).map { case (start, h, t) =>
    t.foldLeft(h) { case (acc, (op, e, end)) =>
      BinOp(op, acc, e).at(span(start, end))
    }
  }

def mulExpr[$: P](using minIndent: Int): P[Expr] =
  P(
    Index ~ unaryExpr ~
      (inlineSpace ~ !"//" ~ CharIn("*/").! ~/ operandSpace ~ unaryExpr ~ Index)
        .rep
  ).map { case (start, h, t) =>
    t.foldLeft(h) { case (acc, (op, e, end)) =>
      BinOp(op, acc, e).at(span(start, end))
    }
  }

// Unary operators touch their operands everywhere. This also prevents Scala's
// spaced leading operators from being mistaken for separate final expressions.
def unaryExpr[$: P]: P[Expr] =
  P(
    located(
      ("-" ~/ unaryExpr.opaque("unary operand adjacent to its operator"))
        .map(UnOp("-", _))
    ) | located(
      ("!" ~/ unaryExpr.opaque("unary operand adjacent to its operator"))
        .map(UnOp("!", _))
    ) | postfixExpr
  )

// Function calls and match are postfix operations chained left-to-right.
def postfixExpr[$: P]: P[Expr] =
  P(Index ~ atom ~ (postfix ~ Index).rep).map { case (start, base, ops) =>
    ops.foldLeft(base) { case (acc, (f, end)) => f(acc).at(span(start, end)) }
  }

def postfix[$: P]: P[Expr => Expr] =
  P(
    (inlineSpace ~ "(" ~/ ws ~ exprList ~ ws ~ ")")
      .map(args => (e: Expr) => Apply(e, args)) |
      (inlineSpace ~ Index ~ kw("match") ~/ Pass).flatMap { start =>
        caseBlock(indentation(start))(matchCase)
          .map(cases => (e: Expr) => Match(e, cases))
      }
  )

def exprList[$: P]: P[List[Expr]] =
  P((expr(using 0) ~ (ws ~ "," ~ ws ~ expr(using 0)).rep).?).map {
    case None         => Nil
    case Some((h, t)) => h :: t.toList
  }

// ─── Atoms ───────────────────────────────────────────────────────────────────

// Atoms introduce their own body or delimiter context; the enclosing operand
// indentation is only needed by the binary precedence layers.

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
  P(Index ~ kw("if") ~/ Pass).flatMap { start =>
    val owner = indentation(start)
    P(
      body(owner) ~ branchKeyword("then", start) ~/ body(owner) ~
        branchKeyword("else", start) ~/ body(owner)
    ).map(If(_, _, _))
  }

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
  P(
    Index ~ "(" ~ ws ~ lambdaParamList.? ~ ws ~ ")" ~ inlineSpace ~ "=>" ~/ Pass
  ).flatMap { (start, params) =>
    P(body(indentation(start))).map(b => Lambda(params.getOrElse(Nil), b))
  }

// Parenthesised expression or tuple. Delimiters allow freely wrapped contents;
// a nested lambda, if or match still establishes its own body boundaries.
def parenExpr[$: P]: P[Expr] =
  located(
    P(
      "(" ~/ ws ~ expr(using 0) ~ (ws ~ "," ~ ws ~ expr(using 0)).rep ~ ws ~ ")"
    ).map {
      case (e, rest) if rest.isEmpty => e
      case (e, rest)                 => Tuple(e :: rest.toList)
    }
  )

// ─── Match cases ─────────────────────────────────────────────────────────────

def matchBindings[$: P]: P[List[String]] =
  P((lowerIdent ~ (ws ~ "," ~ ws ~ lowerIdent).rep).?).map {
    case None         => Nil
    case Some((h, t)) => h :: t.toList
  }

def matchCase[$: P](width: Int): P[MatchCase] =
  located(
    P(
      kw("case") ~/ inlineSpace ~ upperIdent ~ "(" ~ ws ~ matchBindings ~ ws ~
        ")" ~ ws ~ "=>" ~/ body(width)
    ).map { case (ctor, bindings, body) => MatchCase(ctor, bindings, body) }
  )

// ─── Declarations ────────────────────────────────────────────────────────────

def decl[$: P]: P[Decl] =
  P(atIndent(0) ~ located(enumDecl | defDecl | typeDecl | valDecl))

def valDecl[$: P]: P[Decl] =
  P(
    kw("val") ~/ ws ~ lowerIdent.opaque("variable name") ~
      (ws ~ ":" ~ ws ~ typeExpr).? ~ inlineSpace ~ "=" ~/ body(0)
  ).map((name, annotation, body) => ValDecl(name, body, annotation))

def typeDecl[$: P]: P[Decl] =
  P(kw("type") ~/ inlineSpace ~ upperIdent ~ ws ~ "=" ~ ws ~ typeExpr)
    .map(TypeDecl(_, _))

def typeParamList[$: P]: P[List[String]] =
  P("[" ~ ws ~ lowerIdent ~ (ws ~ "," ~ ws ~ lowerIdent).rep ~ ws ~ "]").map {
    case (h, t) => h :: t.toList
  }

def defParamList[$: P]: P[List[(String, Type)]] =
  P("(" ~ ws ~ lambdaParamList.? ~ ws ~ ")").map(_.getOrElse(Nil))

def defDecl[$: P]: P[Decl] =
  P(
    kw("def") ~/ inlineSpace ~ lowerIdent ~
      (ws ~ typeParamList).? ~ ws ~ defParamList ~ ws ~ ":" ~ ws ~ typeExpr ~
      inlineSpace ~ "=" ~/ body(0)
  ).map { case (name, tps, params, ret, body) =>
    DefDecl(name, tps.getOrElse(Nil), params, ret, body)
  }

def enumDecl[$: P]: P[Decl] =
  P(
    kw("enum") ~/ inlineSpace ~ upperIdent ~ (inlineSpace ~ typeParamList).? ~
      inlineSpace ~ ":" ~ caseBlock(0)(_ => enumCaseDecl)
  ).map { case (name, tps, cases) =>
    EnumDecl(name, tps.getOrElse(Nil), cases)
  }

def enumCaseDecl[$: P]: P[EnumCase] =
  located(
    P(
      kw("case") ~/ inlineSpace ~ upperIdent ~ "(" ~ ws ~ lambdaParamList.? ~
        ws ~ ")"
    ).map { case (name, fields) => EnumCase(name, fields.getOrElse(Nil)) }
  )

// ─── Program ─────────────────────────────────────────────────────────────────

private def topSeparator[$: P]: P[Unit] =
  P(lineTrivia ~ newline ~ ws ~ atIndent(0))

def program[$: P]: P[Program] =
  P(ws ~ decl.rep(sep = topSeparator)).flatMap { ds =>
    def finalExpression =
      if ds.nonEmpty then P(topSeparator ~ expr(using 1))
      else P(atIndent(0) ~ expr(using 1))
    P(finalExpression.? ~ ws ~ End).map(e => Program(ds.toList, e))
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
