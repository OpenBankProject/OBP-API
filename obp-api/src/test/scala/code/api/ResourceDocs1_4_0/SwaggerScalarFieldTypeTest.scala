package code.api.ResourceDocs1_4_0

import java.util.Date
import org.json4s.jvalue2monadic
import org.json4s.JsonAST.{JNothing, JString, JValue}
import org.json4s.native.JsonMethods.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The scalar types with no fallback: pins the shapes nothing else in this test package checks.
 *
 * `buildSwaggerSchema` dispatches through a chain of `isOneOfType`/`isAnyOfType` guards, one pair
 * of `SwaggerTypes.tXxx` constants per scalar kind. For String and the collection-shaped cases, a
 * wrong pair still produces the right JSON: the case falls through to the generic
 * "List or Array data" branch (or the final `$ref` case, filtered elsewhere), which recurses and
 * reconstructs an equivalent shape. It is exactly this rescue that let a deliberately swapped
 * constant pass `SwaggerOptionFieldTypeTest`'s full suite unnoticed while writing the SwaggerTypes
 * migration (obp-commons holding the `Type` constants that used to be `TypeTag`-synthesised inline)
 * - proven by injecting the swap and watching all 39 existing Swagger tests stay green.
 *
 * Int, Long, Float, Double, BigDecimal and Date have no such rescue: a mis-wired constant sends
 * them straight to the final `case t => {"$ref": "#/definitions/..."}`, a structurally different
 * shape a correctly-typed field would never take. That fall-through actually happened for
 * BigDecimal in the same injection - which none of the existing Swagger tests catch, because none
 * asserts the JSON shape of one of these six types specifically. These do.
 */
class SwaggerScalarFieldTypeTest extends AnyFlatSpec with Matchers {

  case class Scalars(
    anInt: Int,
    aLong: Long,
    aFloat: Float,
    aDouble: Double,
    aBigDecimal: BigDecimal,
    aDate: Date
  )

  private val scalars = Scalars(1, 2L, 3.0f, 4.0, BigDecimal(5), new Date())

  private def schemaOf(field: String): JValue = {
    // translateEntity returns a definitions *fragment* - `"EntityName":{...}` - not a document, so
    // it has to be wrapped before it will parse.
    val json = SwaggerJSONFactory.translateEntity(scalars)
    val parsed = parse(s"{$json}")
    (parsed \\ field) match {
      case JNothing => fail(s"$field is absent from the generated schema:\n$json")
      case found => found
    }
  }

  private def fieldOf(schema: JValue, name: String): Option[String] =
    (schema \ name).toOption.collect { case JString(v) => v }

  /** A `$ref` is the shape every one of these types falls through to when its guard mis-fires. */
  private def isRef(schema: JValue): Boolean = (schema \ "$ref") != JNothing

  "an Int field" should "be documented as an integer, not a $ref" in {
    val schema = schemaOf("anInt")
    withClue(s"anInt schema was $schema: ") {
      isRef(schema) should equal(false)
      fieldOf(schema, "type") should equal(Some("integer"))
      fieldOf(schema, "format") should equal(Some("int32"))
    }
  }

  "a Long field" should "be documented as an int64 integer, not a $ref" in {
    val schema = schemaOf("aLong")
    withClue(s"aLong schema was $schema: ") {
      isRef(schema) should equal(false)
      fieldOf(schema, "type") should equal(Some("integer"))
      fieldOf(schema, "format") should equal(Some("int64"))
    }
  }

  "a Float field" should "be documented as a float number, not a $ref" in {
    val schema = schemaOf("aFloat")
    withClue(s"aFloat schema was $schema: ") {
      isRef(schema) should equal(false)
      fieldOf(schema, "type") should equal(Some("number"))
      fieldOf(schema, "format") should equal(Some("float"))
    }
  }

  "a Double field" should "be documented as a double number, not a $ref" in {
    val schema = schemaOf("aDouble")
    withClue(s"aDouble schema was $schema: ") {
      isRef(schema) should equal(false)
      fieldOf(schema, "type") should equal(Some("number"))
      fieldOf(schema, "format") should equal(Some("double"))
    }
  }

  "a BigDecimal field" should "be documented as a double-format string, not a $ref" in {
    val schema = schemaOf("aBigDecimal")
    withClue(s"aBigDecimal schema was $schema: ") {
      isRef(schema) should equal(false)
      fieldOf(schema, "type") should equal(Some("string"))
      fieldOf(schema, "format") should equal(Some("double"))
    }
  }

  "a Date field" should "be documented as a date-format string, not a $ref" in {
    val schema = schemaOf("aDate")
    withClue(s"aDate schema was $schema: ") {
      isRef(schema) should equal(false)
      fieldOf(schema, "type") should equal(Some("string"))
      fieldOf(schema, "format") should equal(Some("date"))
    }
  }
}
