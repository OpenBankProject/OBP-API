package com.openbankproject.commons.util

import java.math.{BigDecimal => JBigDecimal}
import java.util.Date
import java.lang.{Boolean => XBoolean, Double => XDouble, Float => XFloat, Integer => XInt, Long => XLong, String => XString}
import org.json4s.JsonAST.{JArray, JBool, JDouble, JInt, JObject, JString, JValue}

import scala.reflect.runtime.universe._

/**
 * The `Type` constants `SwaggerJSONFactory.buildSwaggerSchema` dispatches on, computed here rather
 * than at their call sites.
 *
 * `typeOf[T]` for a fully-applied generic type such as `Option[Coll[EnumValue]]` needs a `TypeTag`
 * for that exact type, and the Scala 2 compiler synthesises one on demand - reflecting on the AST
 * at the call site and building the descriptor there. That synthesis is a Scala 2 compiler feature;
 * Scala 3 does not implement it (it has `scala.quoted`/staging instead), so every `typeOf[...]` or
 * `TypeTag`-context-bound generic method in `SwaggerJSONFactory` would stop compiling the moment
 * that file moves to the Scala 3 compiler - not because the *runtime* `Type` it produces is
 * unusable there (it is not: `scala-reflect` stays on the classpath and `Type`/`<:<`/`typeSymbol`
 * are ordinary method calls, nothing macro-shaped about consuming a `Type` value), but because
 * nothing would be left to manufacture that value from a compile-time type argument.
 *
 * obp-commons stays on Scala 2.13 (see the Scala 3 migration plan's module list), so `typeOf[T]`
 * still works here. Each `val` below is computed once, by the 2.13 compiler, and handed to
 * `SwaggerJSONFactory` - which will run under Scala 3 - as a plain value. The dispatch logic moves
 * from `isTypeOf[SomeType]` (a call the Scala 3 compiler could not satisfy) to
 * `isTypeOf(SwaggerTypes.tSomeType)` (an ordinary method call it has no trouble with).
 *
 * Names are generated mechanically from the type they hold - `tOptionCollBoolean` is
 * `typeOf[Option[Coll[Boolean]]]` - so a mismatch between a name and its value is visible on sight
 * rather than needing the definition looked up. `Coll[T]` mirrors the alias `SwaggerJSONFactory`
 * used to define locally (`IterableOnce[T]`, since 2.13's `Option` implements it and 2.12's did
 * not - see `SwaggerOptionFieldTypeTest` for what went wrong before that was accounted for).
 *
 * Generated from `SwaggerJSONFactory.scala`'s own type-literal call sites; do not hand-edit
 * individual entries without checking they still match a call site - regenerate instead.
 */
object SwaggerTypes {

  type Coll[T] = IterableOnce[T]

  val tJObject: Type = typeOf[JObject]
  val tJArray: Type = typeOf[JArray]
  val tOptionWildcard: Type = typeOf[Option[_]]
  val tJValue: Type = typeOf[JValue]
  val tOptionJValue: Type = typeOf[Option[JValue]]
  val tCollJValue: Type = typeOf[Coll[JValue]]
  val tOptionCollJValue: Type = typeOf[Option[Coll[JValue]]]
  val tBoolean: Type = typeOf[Boolean]
  val tJBool: Type = typeOf[JBool]
  val tXBoolean: Type = typeOf[XBoolean]
  val tOptionBoolean: Type = typeOf[Option[Boolean]]
  val tOptionJBool: Type = typeOf[Option[JBool]]
  val tOptionXBoolean: Type = typeOf[Option[XBoolean]]
  val tCollBoolean: Type = typeOf[Coll[Boolean]]
  val tCollJBool: Type = typeOf[Coll[JBool]]
  val tCollXBoolean: Type = typeOf[Coll[XBoolean]]
  val tOptionCollBoolean: Type = typeOf[Option[Coll[Boolean]]]
  val tOptionCollJBool: Type = typeOf[Option[Coll[JBool]]]
  val tOptionCollXBoolean: Type = typeOf[Option[Coll[XBoolean]]]
  val tString: Type = typeOf[String]
  val tJString: Type = typeOf[JString]
  val tXString: Type = typeOf[XString]
  val tOptionString: Type = typeOf[Option[String]]
  val tOptionJString: Type = typeOf[Option[JString]]
  val tOptionXString: Type = typeOf[Option[XString]]
  val tCollString: Type = typeOf[Coll[String]]
  val tCollJString: Type = typeOf[Coll[JString]]
  val tCollXString: Type = typeOf[Coll[XString]]
  val tOptionCollString: Type = typeOf[Option[Coll[String]]]
  val tOptionCollJString: Type = typeOf[Option[Coll[JString]]]
  val tOptionCollXString: Type = typeOf[Option[Coll[XString]]]
  val tInt: Type = typeOf[Int]
  val tJInt: Type = typeOf[JInt]
  val tXInt: Type = typeOf[XInt]
  val tOptionInt: Type = typeOf[Option[Int]]
  val tOptionJInt: Type = typeOf[Option[JInt]]
  val tOptionXInt: Type = typeOf[Option[XInt]]
  val tCollInt: Type = typeOf[Coll[Int]]
  val tCollJInt: Type = typeOf[Coll[JInt]]
  val tCollXInt: Type = typeOf[Coll[XInt]]
  val tOptionCollInt: Type = typeOf[Option[Coll[Int]]]
  val tOptionCollJInt: Type = typeOf[Option[Coll[JInt]]]
  val tOptionCollXInt: Type = typeOf[Option[Coll[XInt]]]
  val tLong: Type = typeOf[Long]
  val tXLong: Type = typeOf[XLong]
  val tOptionLong: Type = typeOf[Option[Long]]
  val tOptionXLong: Type = typeOf[Option[XLong]]
  val tCollLong: Type = typeOf[Coll[Long]]
  val tCollXLong: Type = typeOf[Coll[XLong]]
  val tOptionCollLong: Type = typeOf[Option[Coll[Long]]]
  val tOptionCollXLong: Type = typeOf[Option[Coll[XLong]]]
  val tFloat: Type = typeOf[Float]
  val tXFloat: Type = typeOf[XFloat]
  val tOptionFloat: Type = typeOf[Option[Float]]
  val tOptionXFloat: Type = typeOf[Option[XFloat]]
  val tCollFloat: Type = typeOf[Coll[Float]]
  val tCollXFloat: Type = typeOf[Coll[XFloat]]
  val tOptionCollFloat: Type = typeOf[Option[Coll[Float]]]
  val tOptionCollXFloat: Type = typeOf[Option[Coll[XFloat]]]
  val tDouble: Type = typeOf[Double]
  val tJDouble: Type = typeOf[JDouble]
  val tXDouble: Type = typeOf[XDouble]
  val tOptionDouble: Type = typeOf[Option[Double]]
  val tOptionJDouble: Type = typeOf[Option[JDouble]]
  val tOptionXDouble: Type = typeOf[Option[XDouble]]
  val tCollDouble: Type = typeOf[Coll[Double]]
  val tCollJDouble: Type = typeOf[Coll[JDouble]]
  val tCollXDouble: Type = typeOf[Coll[XDouble]]
  val tOptionCollDouble: Type = typeOf[Option[Coll[Double]]]
  val tOptionCollJDouble: Type = typeOf[Option[Coll[JDouble]]]
  val tOptionCollXDouble: Type = typeOf[Option[Coll[XDouble]]]
  val tBigDecimal: Type = typeOf[BigDecimal]
  val tJBigDecimal: Type = typeOf[JBigDecimal]
  val tOptionBigDecimal: Type = typeOf[Option[BigDecimal]]
  val tOptionJBigDecimal: Type = typeOf[Option[JBigDecimal]]
  val tCollBigDecimal: Type = typeOf[Coll[BigDecimal]]
  val tCollJBigDecimal: Type = typeOf[Coll[JBigDecimal]]
  val tOptionCollBigDecimal: Type = typeOf[Option[Coll[BigDecimal]]]
  val tOptionCollJBigDecimal: Type = typeOf[Option[Coll[JBigDecimal]]]
  val tDate: Type = typeOf[Date]
  val tOptionDate: Type = typeOf[Option[Date]]
  val tCollDate: Type = typeOf[Coll[Date]]
  val tOptionCollDate: Type = typeOf[Option[Coll[Date]]]
  val tEnumValue: Type = typeOf[EnumValue]
  val tOptionEnumValue: Type = typeOf[Option[EnumValue]]
  val tCollEnumValue: Type = typeOf[Coll[EnumValue]]
  val tOptionCollEnumValue: Type = typeOf[Option[Coll[EnumValue]]]
  val tCollOptionWildcard: Type = typeOf[Coll[Option[_]]]
  val tArrayOptionWildcard: Type = typeOf[Array[Option[_]]]
  val tOptionCollWildcard: Type = typeOf[Option[Coll[_]]]
  val tOptionArrayWildcard: Type = typeOf[Option[Array[_]]]
  val tCollWildcard: Type = typeOf[Coll[_]]
  val tArrayWildcard: Type = typeOf[Array[_]]
  val tOptionListWildcard: Type = typeOf[Option[List[_]]]
  val tListWildcard: Type = typeOf[List[_]]
}
