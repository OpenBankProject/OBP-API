package com.openbankproject.commons.util

import java.math.{BigDecimal => JBigDecimal}
import java.util.Date
import java.lang.{Boolean => XBoolean, Double => XDouble, Float => XFloat, Integer => XInt, Long => XLong, String => XString}

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
 *
 * The json4s AST types (JObject/JArray/JValue/JBool/JString/JInt/JDouble) are a further wrinkle on
 * top of the Scala-2-vs-3 TypeTag-synthesis split documented above: `typeOf[JObject]` doesn't just
 * need the *compiler* synthesizing a TypeTag, it needs `scala.reflect.runtime.universe` to resolve
 * `org.json4s.JsonAST.JObject`'s own type symbol - and json4s-native_2.13 is deliberately excluded
 * from obp-api's classpath (see obp-api/pom.xml), leaving only the Scala-3-compiled json4s-native_3
 * jar, which the 2.13 reflection library that builds this TypeTag can't read (no TASTy support).
 * `typeOf[JObject]` throws `ScalaReflectionException: type JObject in org.json4s.JsonAST not
 * found`. `ReflectUtils.forType` sidesteps it: it resolves a class by name via the classloader
 * (`mirror.staticClass`), which needs no ScalaSig/TASTy reading at all, only the class being
 * loadable - true regardless of which compiler produced it. The generic composites built from a
 * json4s leaf type (`Option[JValue]`, `Coll[JBool]`, `Option[Coll[JString]]`, ...) are then
 * assembled at runtime with `ru.appliedType`, which needs the same thing `forType` provides (a
 * `Type` value for each argument) rather than compile-time reification of the whole composite.
 */
object SwaggerTypes {

  type Coll[T] = IterableOnce[T]

  private def optionOf(t: Type): Type = appliedType(typeOf[Option[_]].typeConstructor, List(t))
  private def collOf(t: Type): Type = appliedType(typeOf[IterableOnce[_]].typeConstructor, List(t))

  // org.json4s.JsonAST is a legacy compatibility object re-exporting these as type aliases; the
  // classes themselves live directly under org.json4s (confirmed against the json4s-ast_3 jar),
  // and forType needs the class's own binary location, not the alias's.
  private val jObjectT: Type = ReflectUtils.forType("org.json4s.JObject")
  private val jArrayT: Type = ReflectUtils.forType("org.json4s.JArray")
  private val jValueT: Type = ReflectUtils.forType("org.json4s.JValue")
  private val jBoolT: Type = ReflectUtils.forType("org.json4s.JBool")
  private val jStringT: Type = ReflectUtils.forType("org.json4s.JString")
  private val jIntT: Type = ReflectUtils.forType("org.json4s.JInt")
  private val jDoubleT: Type = ReflectUtils.forType("org.json4s.JDouble")

  val tJObject: Type = jObjectT
  val tJArray: Type = jArrayT
  val tOptionWildcard: Type = typeOf[Option[_]]
  val tJValue: Type = jValueT
  val tOptionJValue: Type = optionOf(jValueT)
  val tCollJValue: Type = collOf(jValueT)
  val tOptionCollJValue: Type = optionOf(tCollJValue)
  val tBoolean: Type = typeOf[Boolean]
  val tJBool: Type = jBoolT
  val tXBoolean: Type = typeOf[XBoolean]
  val tOptionBoolean: Type = typeOf[Option[Boolean]]
  val tOptionJBool: Type = optionOf(jBoolT)
  val tOptionXBoolean: Type = typeOf[Option[XBoolean]]
  val tCollBoolean: Type = typeOf[Coll[Boolean]]
  val tCollJBool: Type = collOf(jBoolT)
  val tCollXBoolean: Type = typeOf[Coll[XBoolean]]
  val tOptionCollBoolean: Type = typeOf[Option[Coll[Boolean]]]
  val tOptionCollJBool: Type = optionOf(tCollJBool)
  val tOptionCollXBoolean: Type = typeOf[Option[Coll[XBoolean]]]
  val tString: Type = typeOf[String]
  val tJString: Type = jStringT
  val tXString: Type = typeOf[XString]
  val tOptionString: Type = typeOf[Option[String]]
  val tOptionJString: Type = optionOf(jStringT)
  val tOptionXString: Type = typeOf[Option[XString]]
  val tCollString: Type = typeOf[Coll[String]]
  val tCollJString: Type = collOf(jStringT)
  val tCollXString: Type = typeOf[Coll[XString]]
  val tOptionCollString: Type = typeOf[Option[Coll[String]]]
  val tOptionCollJString: Type = optionOf(tCollJString)
  val tOptionCollXString: Type = typeOf[Option[Coll[XString]]]
  val tInt: Type = typeOf[Int]
  val tJInt: Type = jIntT
  val tXInt: Type = typeOf[XInt]
  val tOptionInt: Type = typeOf[Option[Int]]
  val tOptionJInt: Type = optionOf(jIntT)
  val tOptionXInt: Type = typeOf[Option[XInt]]
  val tCollInt: Type = typeOf[Coll[Int]]
  val tCollJInt: Type = collOf(jIntT)
  val tCollXInt: Type = typeOf[Coll[XInt]]
  val tOptionCollInt: Type = typeOf[Option[Coll[Int]]]
  val tOptionCollJInt: Type = optionOf(tCollJInt)
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
  val tJDouble: Type = jDoubleT
  val tXDouble: Type = typeOf[XDouble]
  val tOptionDouble: Type = typeOf[Option[Double]]
  val tOptionJDouble: Type = optionOf(jDoubleT)
  val tOptionXDouble: Type = typeOf[Option[XDouble]]
  val tCollDouble: Type = typeOf[Coll[Double]]
  val tCollJDouble: Type = collOf(jDoubleT)
  val tCollXDouble: Type = typeOf[Coll[XDouble]]
  val tOptionCollDouble: Type = typeOf[Option[Coll[Double]]]
  val tOptionCollJDouble: Type = optionOf(tCollJDouble)
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
