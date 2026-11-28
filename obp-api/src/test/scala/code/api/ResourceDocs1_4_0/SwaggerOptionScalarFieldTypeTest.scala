package code.api.ResourceDocs1_4_0

import java.util.Date
import org.json4s.jvalue2monadic
import org.json4s.JsonAST.{JNothing, JString, JValue}
import org.json4s.native.JsonMethods.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * `Option[<value type>]` must be documented as the value it holds, exactly as the bare value type
 * is - the gap `SwaggerScalarFieldTypeTest` (bare scalars) and `SwaggerOptionFieldTypeTest`
 * (Option of String / case class / List) leave between them.
 *
 * It is a gap the Scala 3 flip walks straight into. `buildSwaggerSchema` decides a field's shape
 * by comparing the field's runtime `Type` against constants such as `typeOf[Option[Boolean]]`, and
 * that runtime `Type` comes from `scala-reflect`, which reads ScalaSig - an attribute only Scala 2
 * classes carry. On a Scala 3-compiled class it falls back to the class file's Java generic
 * signature, and there `Option[Boolean]` is erased to `scala.Option<java.lang.Object>`, because a
 * value type cannot be a Java type argument (`javap -v` on any of these confirms it). So the
 * `Option[Boolean]` / `Option[Int]` / `Option[Long]` / `Option[Double]` / `Option[Float]` guards
 * cannot match any more, and the field falls all the way through to the final
 * `case t => {"$ref": ...}` - publishing `{"$ref":"#/definitions/Object"}` where the contract says
 * `{"type":"boolean"}`.
 *
 * Reference types are unaffected and are asserted here as controls: `Option[String]`,
 * `Option[Date]` and `Option[BigDecimal]` keep their type argument in the Java signature, so they
 * pin that the diagnosis is specifically about value types rather than about Option as such.
 *
 * These are checks on the published contract: the swagger definitions are what clients generate
 * code from, and a boolean that claims to be a `$ref` to an undefined `Object` breaks them.
 */
// Declared at file scope, not nested inside the test class - see SwaggerScalarFieldTypeTest's
// comment for why a nested case class makes translateEntity's reflection walk the ScalaTest
// hierarchy and throw.
case class OptionalValueTypes(
  optBoolean: Option[Boolean],
  optInt: Option[Int],
  optLong: Option[Long],
  optFloat: Option[Float],
  optDouble: Option[Double],
  optBigDecimal: Option[BigDecimal],
  optString: Option[String],
  optDate: Option[Date]
)

class SwaggerOptionScalarFieldTypeTest extends AnyFlatSpec with Matchers {

  private val optionals = OptionalValueTypes(
    optBoolean = Some(true),
    optInt = Some(1),
    optLong = Some(2L),
    optFloat = Some(3.0f),
    optDouble = Some(4.0),
    optBigDecimal = Some(BigDecimal(5)),
    optString = Some("six"),
    optDate = Some(new Date())
  )

  private def schemaOf(field: String): JValue = {
    // translateEntity returns a definitions *fragment* - `"EntityName":{...}` - not a document, so
    // it has to be wrapped before it will parse.
    val json = SwaggerJSONFactory.translateEntity(optionals)
    val parsed = parse(s"{$json}")
    (parsed \\ field) match {
      case JNothing => fail(s"$field is absent from the generated schema:\n$json")
      case found => found
    }
  }

  private def fieldOf(schema: JValue, name: String): Option[String] =
    (schema \ name).toOption.collect { case JString(v) => v }

  /** The shape every one of these fields falls through to once its guard stops matching. */
  private def isRef(schema: JValue): Boolean = (schema \ "$ref") != JNothing

  private def assertShape(field: String, expectedType: String, expectedFormat: Option[String]): Unit = {
    val schema = schemaOf(field)
    withClue(s"$field schema was $schema: ") {
      isRef(schema) should equal(false)
      fieldOf(schema, "type") should equal(Some(expectedType))
      expectedFormat.foreach(f => fieldOf(schema, "format") should equal(Some(f)))
    }
  }

  "an Option[Boolean] field" should "be documented as a boolean, not a $ref" in {
    assertShape("optBoolean", "boolean", None)
  }

  "an Option[Int] field" should "be documented as an int32 integer, not a $ref" in {
    assertShape("optInt", "integer", Some("int32"))
  }

  "an Option[Long] field" should "be documented as an int64 integer, not a $ref" in {
    assertShape("optLong", "integer", Some("int64"))
  }

  "an Option[Float] field" should "be documented as a float number, not a $ref" in {
    assertShape("optFloat", "number", Some("float"))
  }

  "an Option[Double] field" should "be documented as a double number, not a $ref" in {
    assertShape("optDouble", "number", Some("double"))
  }

  // Controls: reference types keep their type argument in the Java generic signature, so these
  // must stay green both before and after the fix. If one of them ever goes red the diagnosis
  // above is wrong and the fix is aimed at the wrong thing.
  "an Option[BigDecimal] field" should "be documented as a double-format string, not a $ref" in {
    assertShape("optBigDecimal", "string", Some("double"))
  }

  "an Option[String] field" should "be documented as a string, not a $ref" in {
    assertShape("optString", "string", None)
  }

  "an Option[Date] field" should "be documented as a date-format string, not a $ref" in {
    assertShape("optDate", "string", Some("date"))
  }
}
