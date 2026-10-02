package chocola.dialects

import scair.clair.*
import scair.dialects.arith.SameOperandsAndResultTypes
import scair.dialects.builtin.*
import scair.ir.*

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

sealed trait ChocolaType extends TypeAttribute
sealed trait NumericType extends ChocolaType

final case class UnknownType()
    extends DerivedAttribute["chocola.unknown"]
    with ChocolaType derives AttrDefs

val unknown: UnknownType = UnknownType()

final case class IntType()
    extends DerivedAttribute["chocola.int"]
    with NumericType derives AttrDefs

final case class DoubleType()
    extends DerivedAttribute["chocola.double"]
    with NumericType derives AttrDefs

final case class StringType()
    extends DerivedAttribute["chocola.string"]
    with ChocolaType derives AttrDefs

final case class BoolType()
    extends DerivedAttribute["chocola.bool"]
    with ChocolaType derives AttrDefs

final case class ListType(element: ChocolaType)
    extends DerivedAttribute["chocola.list"]
    with ChocolaType derives AttrDefs

final case class FunType(
    params: ArrayAttribute[ChocolaType],
    result: ChocolaType,
) extends DerivedAttribute["chocola.fun"]
    with ChocolaType derives AttrDefs

/** Keep the existing flattened tuple assembly spelling. Its custom parser
  * complements the printing workaround instead of relying on ScaIR's default
  * derived parser, which expects an ArrayAttribute in square brackets.
  */
final case class TupleType(elements: ArrayAttribute[ChocolaType])
    extends DerivedAttribute["chocola.tuple"]
    with ChocolaType derives AttrDefs:

  override def customPrint(p: scair.print.Printer) =
    p.print("!chocola.tuple<")
    elements.data.zipWithIndex.foreach { case (element, index) =>
      if index > 0 then p.print(", ")
      p.print(element)
    }
    p.print(">")

object TupleType:

  given AttributeCustomParser[TupleType] with

    def parse[$: fastparse.P](using
        scair.parse.Parser
    ): fastparse.P[TupleType] =
      import fastparse.*
      import scair.parse.attributeP
      P(
        "<" ~ attributeP.filter(_.isInstanceOf[ChocolaType]).rep(sep = ",") ~
          ">"
      ).map(es =>
        TupleType(ArrayAttribute(es.map(_.asInstanceOf[ChocolaType])*))
      )

final case class NamedType(
    symbol: SymbolRefAttr,
    args: ArrayAttribute[ChocolaType],
) extends DerivedAttribute["chocola.named"]
    with ChocolaType derives AttrDefs

/** A named field and its structured type within an enum case declaration. */
final case class EnumFieldAttr(fieldName: SymbolRefAttr, fieldType: ChocolaType)
    extends DerivedAttribute["chocola.enum_field"] derives AttrDefs

// ---------------------------------------------------------------------------
// Operations
// No customPrint / AssemblyFormat: generic ScaIR assembly output is used.
// ---------------------------------------------------------------------------

/** Integer / double / bool / string / Nil / primitive literals. */
case class ConstantOp(
    value: StringData,
    result: Result[ChocolaType],
) extends DerivedOperation["chocola.constant"]
    with NoMemoryEffect derives OpDefs

// Binary arithmetic
case class AddOp(
    lhs: Operand[NumericType],
    rhs: Operand[NumericType],
    result: Result[NumericType],
) extends DerivedOperation["chocola.add"]
    with SameOperandsAndResultTypes
    with NoMemoryEffect derives OpDefs

case class SubOp(
    lhs: Operand[NumericType],
    rhs: Operand[NumericType],
    result: Result[NumericType],
) extends DerivedOperation["chocola.sub"]
    with SameOperandsAndResultTypes
    with NoMemoryEffect derives OpDefs

case class MulOp(
    lhs: Operand[NumericType],
    rhs: Operand[NumericType],
    result: Result[NumericType],
) extends DerivedOperation["chocola.mul"]
    with SameOperandsAndResultTypes
    with NoMemoryEffect derives OpDefs

case class DivOp(
    lhs: Operand[NumericType],
    rhs: Operand[NumericType],
    result: Result[NumericType],
) extends DerivedOperation["chocola.div"]
    with SameOperandsAndResultTypes
    with NoMemoryEffect derives OpDefs

// Binary comparison
case class EqOp(
    lhs: Operand[ChocolaType],
    rhs: Operand[ChocolaType],
    result: Result[BoolType],
) extends DerivedOperation["chocola.eq"]
    with NoMemoryEffect derives OpDefs

case class NeqOp(
    lhs: Operand[ChocolaType],
    rhs: Operand[ChocolaType],
    result: Result[BoolType],
) extends DerivedOperation["chocola.neq"]
    with NoMemoryEffect derives OpDefs

case class LtOp(
    lhs: Operand[NumericType],
    rhs: Operand[NumericType],
    result: Result[BoolType],
) extends DerivedOperation["chocola.lt"]
    with NoMemoryEffect derives OpDefs

case class GtOp(
    lhs: Operand[NumericType],
    rhs: Operand[NumericType],
    result: Result[BoolType],
) extends DerivedOperation["chocola.gt"]
    with NoMemoryEffect derives OpDefs

case class LeqOp(
    lhs: Operand[NumericType],
    rhs: Operand[NumericType],
    result: Result[BoolType],
) extends DerivedOperation["chocola.leq"]
    with NoMemoryEffect derives OpDefs

case class GeqOp(
    lhs: Operand[NumericType],
    rhs: Operand[NumericType],
    result: Result[BoolType],
) extends DerivedOperation["chocola.geq"]
    with NoMemoryEffect derives OpDefs

// Binary logic
case class OrOp(
    lhs: Operand[BoolType],
    rhs: Operand[BoolType],
    result: Result[BoolType],
) extends DerivedOperation["chocola.or"]
    with SameOperandsAndResultTypes
    with NoMemoryEffect derives OpDefs

case class AndOp(
    lhs: Operand[BoolType],
    rhs: Operand[BoolType],
    result: Result[BoolType],
) extends DerivedOperation["chocola.and"]
    with SameOperandsAndResultTypes
    with NoMemoryEffect derives OpDefs

// List cons
case class ConsOp(
    head: Operand[ChocolaType],
    tail: Operand[ListType],
    result: Result[ListType],
) extends DerivedOperation["chocola.cons"]
    with NoMemoryEffect derives OpDefs

// Unary operators
case class NegOp(operand: Operand[NumericType], result: Result[NumericType])
    extends DerivedOperation["chocola.neg"]
    with SameOperandsAndResultTypes
    with NoMemoryEffect derives OpDefs

case class NotOp(operand: Operand[BoolType], result: Result[BoolType])
    extends DerivedOperation["chocola.not"]
    with SameOperandsAndResultTypes
    with NoMemoryEffect derives OpDefs

/** Function application. */
case class CallOp(
    callee: Operand[FunType],
    args: Seq[Operand[ChocolaType]],
    result: Result[ChocolaType],
) extends DerivedOperation["chocola.call"] derives OpDefs

/** Direct application of a known top-level function symbol. */
case class DirectCallOp(
    callee: SymbolRefAttr,
    args: Seq[Operand[ChocolaType]],
    result: Result[ChocolaType],
) extends DerivedOperation["chocola.direct_call"] derives OpDefs

/** Materialises a top-level function as a first-class value. */
case class FunctionRefOp(
    function: SymbolRefAttr,
    result: Result[FunType],
) extends DerivedOperation["chocola.function_ref"]
    with NoMemoryEffect derives OpDefs

/** Enum constructor application. */
case class ConstructOp(
    ctor: SymbolRefAttr,
    args: Seq[Operand[ChocolaType]],
    result: Result[NamedType],
) extends DerivedOperation["chocola.construct"]
    with NoMemoryEffect derives OpDefs

/** Tuple construction. */
case class TupleOp(
    elems: Seq[Operand[ChocolaType]],
    result: Result[TupleType],
) extends DerivedOperation["chocola.tuple"]
    with NoMemoryEffect derives OpDefs

/** if–then–else; branches terminate with YieldOp. */
case class IfOp(
    cond: Operand[BoolType],
    result: Result[ChocolaType],
    thenRegion: Region,
    elseRegion: Region,
) extends DerivedOperation["chocola.if"] derives OpDefs

/** Pattern match; `cases` region holds a block of CaseOp operations. */
case class MatchOp(
    scrutinee: Operand[ChocolaType],
    result: Result[ChocolaType],
    cases: Region,
) extends DerivedOperation["chocola.match"] derives OpDefs

/** One match arm; body entry block args are the pattern bindings. */
case class CaseOp(
    ctor: SymbolRefAttr,
    body: Region,
) extends DerivedOperation["chocola.case"] derives OpDefs

/** Anonymous function; body entry block args are the lambda parameters. */
case class LambdaOp(
    result: Result[FunType],
    body: Region,
) extends DerivedOperation["chocola.lambda"] derives OpDefs

/** Terminates any region except top-level function bodies. */
case class YieldOp(
    value: Operand[ChocolaType]
) extends DerivedOperation["chocola.yield"]
    with IsTerminator derives OpDefs

/** Terminates a FuncDeclOp body. */
case class ReturnOp(
    value: Operand[ChocolaType]
) extends DerivedOperation["chocola.return"]
    with IsTerminator derives OpDefs

/** Top-level `def` declaration; body entry block args are the parameters. */
case class FuncDeclOp(
    sym: SymbolRefAttr,
    typeParams: ArrayAttribute[SymbolRefAttr],
    functionType: FunctionType,
    body: Region,
) extends DerivedOperation["chocola.func"]
    with Symbol derives OpDefs:
  override def sym_name: StringData = sym.rootRef

/** Top-level `type` alias. */
case class TypeAliasDeclOp(
    sym: SymbolRefAttr,
    aliased: ChocolaType,
) extends DerivedOperation["chocola.type_alias"] derives OpDefs

/** Top-level `enum` declaration; body holds EnumCaseDeclOp operations. */
case class EnumDeclOp(
    sym: SymbolRefAttr,
    typeParams: ArrayAttribute[SymbolRefAttr],
    body: Region,
) extends DerivedOperation["chocola.enum"] derives OpDefs

/** One case inside an EnumDeclOp. */
case class EnumCaseDeclOp(
    sym: SymbolRefAttr,
    fields: ArrayAttribute[EnumFieldAttr],
) extends DerivedOperation["chocola.enum_case"] derives OpDefs

// ---------------------------------------------------------------------------
// Dialect registration
// ---------------------------------------------------------------------------

val ChocolaDialect = summonDialect[
  (
      UnknownType,
      IntType,
      DoubleType,
      StringType,
      BoolType,
      ListType,
      FunType,
      TupleType,
      NamedType,
      EnumFieldAttr,
  ),
  (
      ConstantOp,
      AddOp,
      SubOp,
      MulOp,
      DivOp,
      EqOp,
      NeqOp,
      LtOp,
      GtOp,
      LeqOp,
      GeqOp,
      OrOp,
      AndOp,
      ConsOp,
      NegOp,
      NotOp,
      CallOp,
      DirectCallOp,
      FunctionRefOp,
      ConstructOp,
      TupleOp,
      IfOp,
      MatchOp,
      CaseOp,
      LambdaOp,
      YieldOp,
      ReturnOp,
      FuncDeclOp,
      TypeAliasDeclOp,
      EnumDeclOp,
      EnumCaseDeclOp,
  ),
]
