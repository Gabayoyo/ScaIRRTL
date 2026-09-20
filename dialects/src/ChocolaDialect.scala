package chocola.dialects

import scair.clair.*
import scair.dialects.builtin.*
import scair.ir.*

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

sealed trait ChocolaType extends TypeAttribute

final case class UnknownType()
    extends DerivedAttribute["chocola.unknown"]
    with ChocolaType derives AttrDefs

val unknown: UnknownType = UnknownType()

final case class IntType()
    extends DerivedAttribute["chocola.int"]
    with ChocolaType derives AttrDefs

final case class DoubleType()
    extends DerivedAttribute["chocola.double"]
    with ChocolaType derives AttrDefs

final case class StringType()
    extends DerivedAttribute["chocola.string"]
    with ChocolaType derives AttrDefs

final case class BoolType()
    extends DerivedAttribute["chocola.bool"]
    with ChocolaType derives AttrDefs

final case class ListType(element: ChocolaType)
    extends DerivedAttribute["chocola.list"]
    with ChocolaType derives AttrDefs

final case class FunType(from: ChocolaType, to: ChocolaType)
    extends DerivedAttribute["chocola.fun"]
    with ChocolaType derives AttrDefs

/** Work around a ScaIR derived-attribute printing bug: the generated parser
  * treats an ArrayAttribute parameter as a flattened comma-separated list, but
  * the default printer includes the array's square brackets. Keep this custom
  * printer until ScaIR's generated printer and parser agree.
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

final case class NamedType(symbol: SymbolRefAttr)
    extends DerivedAttribute["chocola.named"]
    with ChocolaType derives AttrDefs

/** A named field and its structured type within an enum case declaration. */
final case class EnumFieldAttr(fieldName: SymbolRefAttr, fieldType: ChocolaType)
    extends DerivedAttribute["chocola.enum_field"] derives AttrDefs

// ---------------------------------------------------------------------------
// Operations — all untyped (Operand[Attribute] / Result[Attribute])
// No customPrint / AssemblyFormat: generic ScaIR assembly output is used.
// ---------------------------------------------------------------------------

/** Integer / double / bool / string / Nil / primitive literals. */
case class ConstantOp(
    value: StringData,
    result: Result[Attribute],
) extends DerivedOperation["chocola.constant"]
    with NoMemoryEffect derives OpDefs

// Binary arithmetic
case class AddOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.add"]
    with NoMemoryEffect derives OpDefs

case class SubOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.sub"]
    with NoMemoryEffect derives OpDefs

case class MulOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.mul"]
    with NoMemoryEffect derives OpDefs

case class DivOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.div"]
    with NoMemoryEffect derives OpDefs

// Binary comparison
case class EqOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.eq"]
    with NoMemoryEffect derives OpDefs

case class NeqOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.neq"]
    with NoMemoryEffect derives OpDefs

case class LtOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.lt"]
    with NoMemoryEffect derives OpDefs

case class GtOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.gt"]
    with NoMemoryEffect derives OpDefs

case class LeqOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.leq"]
    with NoMemoryEffect derives OpDefs

case class GeqOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.geq"]
    with NoMemoryEffect derives OpDefs

// Binary logic
case class OrOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.or"]
    with NoMemoryEffect derives OpDefs

case class AndOp(
    lhs: Operand[Attribute],
    rhs: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.and"]
    with NoMemoryEffect derives OpDefs

// List cons
case class ConsOp(
    head: Operand[Attribute],
    tail: Operand[Attribute],
    result: Result[Attribute],
) extends DerivedOperation["chocola.cons"]
    with NoMemoryEffect derives OpDefs

// Unary operators
case class NegOp(operand: Operand[Attribute], result: Result[Attribute])
    extends DerivedOperation["chocola.neg"]
    with NoMemoryEffect derives OpDefs

case class NotOp(operand: Operand[Attribute], result: Result[Attribute])
    extends DerivedOperation["chocola.not"]
    with NoMemoryEffect derives OpDefs

/** Function application. */
case class CallOp(
    callee: Operand[Attribute],
    args: Seq[Operand[Attribute]],
    result: Result[Attribute],
) extends DerivedOperation["chocola.call"] derives OpDefs

/** Enum constructor application. */
case class ConstructOp(
    ctor: SymbolRefAttr,
    args: Seq[Operand[Attribute]],
    result: Result[Attribute],
) extends DerivedOperation["chocola.construct"]
    with NoMemoryEffect derives OpDefs

/** Tuple construction. */
case class TupleOp(
    elems: Seq[Operand[Attribute]],
    result: Result[Attribute],
) extends DerivedOperation["chocola.tuple"]
    with NoMemoryEffect derives OpDefs

/** if–then–else; branches terminate with YieldOp. */
case class IfOp(
    cond: Operand[Attribute],
    result: Result[Attribute],
    thenRegion: Region,
    elseRegion: Region,
) extends DerivedOperation["chocola.if"] derives OpDefs

/** Pattern match; `cases` region holds a block of CaseOp operations. */
case class MatchOp(
    scrutinee: Operand[Attribute],
    result: Result[Attribute],
    cases: Region,
) extends DerivedOperation["chocola.match"] derives OpDefs

/** One match arm; body entry block args are the pattern bindings. */
case class CaseOp(
    ctor: SymbolRefAttr,
    body: Region,
) extends DerivedOperation["chocola.case"] derives OpDefs

/** Anonymous function; body entry block args are the lambda parameters. */
case class LambdaOp(
    result: Result[Attribute],
    body: Region,
) extends DerivedOperation["chocola.lambda"] derives OpDefs

/** Terminates any region except top-level function bodies. */
case class YieldOp(
    value: Operand[Attribute]
) extends DerivedOperation["chocola.yield"]
    with IsTerminator derives OpDefs

/** Terminates a FuncDeclOp body. */
case class ReturnOp(
    value: Operand[Attribute]
) extends DerivedOperation["chocola.return"]
    with IsTerminator derives OpDefs

/** Top-level `def` declaration; body entry block args are the parameters. */
case class FuncDeclOp(
    sym: SymbolRefAttr,
    typeParams: ArrayAttribute[SymbolRefAttr],
    result: Result[Attribute],
    body: Region,
) extends DerivedOperation["chocola.func"] derives OpDefs

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
