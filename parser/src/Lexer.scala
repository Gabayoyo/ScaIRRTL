package chocola.parser

import fastparse.*
import CharPredicates.*
import fastparse.NoWhitespace.given

// This is copy-pasta from fastparse/scalaparse, at least to get started quickly.

case class NamedFunction(f: Char => Boolean)(implicit name: sourcecode.Name)
    extends (Char => Boolean):
  def apply(t: Char) = f(t)
  override def toString() = name.value

val isOpChar = NamedFunction {
  case '!' | '#' | '%' | '&' | '*' | '+' | '-' | '/' | ':' | '<' | '=' | '>' |
      '|' =>
    true
  case c => isOtherSymbol(c) || isMathSymbol(c)
}

val OpCharNotSlash = NamedFunction(x => isOpChar(x) && x != '/')
val NotBackTick = NamedFunction(_ != '`')

def Operator[$: P] = P(
  !SymbolicKeywords ~
    (!StringIn("/*", "//") ~ (CharsWhile(OpCharNotSlash) | "/")).rep(1)
).opaque("operator")

def VarId[$: P] = P(VarId0(true)).opaque("var-id")

def VarId0[$: P](dollar: Boolean) = P(!Keywords ~ Lower ~ IdRest(dollar))

val LetterDigitDollarUnderscore =
  NamedFunction(c => isLetter(c) | isDigit(c) | c == '$' | c == '_')

val LowerChar = NamedFunction(c => isLower(c) || c == '$' | c == '_')
val UpperChar = NamedFunction(isUpper)

/** Most keywords don't just require the correct characters to match, they have
  * to ensure that subsequent characters *don't* match in order for it to be a
  * keyword. This enforces that rule for key-words (W) and key-operators (O)
  * which have different non-match criteria.
  */
def W[$: P](s: String) =
  P(s ~ !CharPred(LetterDigitDollarUnderscore))(s"`$s`", implicitly)

// If the operator is followed by a comment, stop early so we can parse the comment
def O[$: P](s: String) =
  P(s ~ (!OpChar | &(NoTrace(StringIn("/*", "//")))))(s"`$s`", implicitly)

def `=>`[$: P] = (O("=>") | O("⇒")).opaque("\"=>\"")
def `:`[$: P] = O(":")
def `=`[$: P] = O("=")
def `type`[$: P] = W("type")
def `val`[$: P] = W("val")
def `def`[$: P] = W("def")
def `case`[$: P] = W("case")
def `else`[$: P] = W("else")
def `if`[$: P] = W("if")
def `match`[$: P] = W("match")
def `then`[$: P] = W("then")
def `true`[$: P] = W("true")
def `false`[$: P] = W("false")

def Lower[$: P] = P(CharPred(LowerChar))
def Upper[$: P] = P(CharPred(UpperChar))
// Numbers and digits
def Digit[$: P] = P(CharIn("0-9"))

def HexDigit[$: P] = P(CharIn("0-9a-fA-F"))
def HexNum[$: P] = P("0x" ~ CharsWhileIn("0-9a-fA-F"))
def DecNum[$: P] = P(CharsWhileIn("0-9"))
def Exp[$: P] = P(CharIn("Ee") ~ CharIn("+\\-").? ~ DecNum)
def FloatType[$: P] = P(CharIn("fFdD"))

def WSChars[$: P] = P(NoTrace(CharsWhileIn("\u0020\u0009")))
def Newline[$: P] = P(NoTrace(StringIn("\r\n", "\n")))
def Semi[$: P] = P(";" | Newline.rep(1))
def OpChar[$: P] = P(CharPred(isOpChar))

def Float[$: P] =
  def LeadingDotFloat = P("." ~ DecNum ~ Exp.? ~ FloatType.?)
  def FloatSuffix = P(LeadingDotFloat | Exp ~ FloatType.? | Exp.? ~ FloatType)
  P(LeadingDotFloat | DecNum ~ FloatSuffix)

def Int[$: P] = P((HexNum | DecNum) ~ ("L" | "l").?)

def Bool[$: P] = P(W("true") | W("false"))

def UppercaseId[$: P](dollar: Boolean) = P(!Keywords ~ Upper ~ IdRest(dollar))

def PlainId[$: P] =
  P(UppercaseId(true) | VarId | Operator ~ (!OpChar | &(StringIn("/*", "//"))))
    .opaque("plain-id")

def PlainIdNoDollar[$: P] = P(UppercaseId(false) | VarId0(false) | Operator)
  .opaque("plain-id")

def BacktickId[$: P] = P("`" ~ CharsWhile(NotBackTick) ~ "`")

def Id[$: P]: P[Unit] = P(BacktickId | PlainId).opaque("id")

def IdRest[$: P](allowDollar: Boolean) =

  val IdCharacter =
    if allowDollar then
      NamedFunction(c => c == '$' || isLetter(c) || isDigit(c))
    else NamedFunction(c => isLetter(c) || isDigit(c))

  def IdUnderscoreChunk = P(CharsWhileIn("_", 0) ~ CharsWhile(IdCharacter))
  P(IdUnderscoreChunk.rep ~ (CharsWhileIn("_") ~ CharsWhile(isOpChar, 0)).?)

def AlphabetKeywords[$: P] = P {
  StringIn(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "extends",
    "false",
    "finally",
    "final",
    "finally",
    "forSome",
    "for",
    "if",
    "implicit",
    "import",
    "lazy",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "this",
    "throw",
    "trait",
    "try",
    "true",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield",
    "_",
    "macro",
  ) ~ !CharPred(LetterDigitDollarUnderscore)
}.opaque("AlphabetKeywords")

def SymbolicKeywords[$: P] = P {
  StringIn(
    ":",
    ";",
    "=>",
    "=",
    "<-",
    "<:",
    "<%",
    ">:",
    "#",
    "@",
    "\u21d2",
    "\u2190",
  ) ~ !OpChar
}.opaque("SymbolicKeywords")

def Keywords[$: P] = P(AlphabetKeywords | SymbolicKeywords)
