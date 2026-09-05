package code.api.v1_4_0

import code.api.util.AuthenticationType
import org.json4s.JsonAST.{JNothing, JString, JValue}
import org.json4s.native.JsonMethods.parse
import org.json4s.jvalue2monadic
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * A bare list of enumeration values must publish the enumeration, not an anonymous object.
 *
 * The scalar vocabulary in translateEntity - string, integer, boolean, and the EnumValue case that
 * emits `{"type":"string","enum":[...]}` - lives in the per-field loop, keyed by field name. It is
 * not reachable from translateEntity(value) itself, which only knows how to describe an object by
 * reflecting over its constructor arguments.
 *
 * That matters for a body that IS a bare list. Describing it as an array of its head means
 * describing the head, and if the head is an enumeration value, reflection finds no constructor
 * arguments and answers `{"properties":{},"type":"object"}` - the enumeration's members are lost.
 *
 * 2.12 kept them by accident: it reflected over the cons cell, so `head` was a *field*, and a field
 * whose value is an EnumValue goes through the case that emits the enum. The published body was
 * `{"head":{"type":"string","enum":[...]},"tl":{...}}` - wrong shape, right members.
 *
 * Twelve published request bodies have this shape today, across createAuthenticationTypeValidation
 * and updateAuthenticationTypeValidation in five API versions. Nothing had compared them: the
 * contract suite records typed_request_body in its baseline but only ever diffs the response side.
 */
class JSONFactory1_4_0RootEnumListTest extends AnyFlatSpec with Matchers {

  private def schema(entity: Any): JValue = parse(JSONFactory1_4_0.translateEntity(entity, false))

  private def typeOf(v: JValue): Option[String] = (v \ "type").toOption.collect { case JString(t) => t }

  "a bare list of enumeration values" should "be described as an array" in {
    typeOf(schema(List(AuthenticationType.DirectLogin))) should equal(Some("array"))
  }

  it should "carry the enumeration's members under items" in {
    val items = schema(List(AuthenticationType.DirectLogin)) \ "items"

    withClue(s"items was ${org.json4s.native.JsonMethods.compact(org.json4s.native.JsonMethods.render(items))}: ") {
      typeOf(items) should equal(Some("string"))
      (items \ "enum") should not equal JNothing
      org.json4s.native.JsonMethods.compact(
        org.json4s.native.JsonMethods.render(items \ "enum")) should include("DirectLogin")
    }
  }

  // The scalar vocabulary has to reach the element for every kind it covers, not only enums.
  "a bare list of strings" should "have string items" in {
    typeOf(schema(List("a")) \ "items") should equal(Some("string"))
  }

  "a bare list of integers" should "have integer items" in {
    typeOf(schema(List(1)) \ "items") should equal(Some("integer"))
  }

  "a bare list of booleans" should "have boolean items" in {
    typeOf(schema(List(true)) \ "items") should equal(Some("boolean"))
  }

  // And an object element must still be described by reflecting over it, as before.
  case class Tag(tag_id: String)

  "a bare list of objects" should "still describe the object's fields" in {
    val items = schema(List(Tag("x"))) \ "items"

    typeOf(items) should equal(Some("object"))
    (items \\ "tag_id") should not equal JNothing
  }
}
