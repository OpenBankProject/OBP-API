package code.api.v1_4_0

import org.json4s.JsonAST.{JNothing, JString, JValue}
import org.json4s.jvalue2monadic
import org.json4s.native.JsonMethods.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * A response body that is a bare Scala collection must be described as an array of its element.
 *
 * translateEntity returns early for a JArray, emitting `{"type":"array","items":{…}}` from the
 * first element. A Scala List reaching it has no such branch: it falls through to the field map,
 * where it is answered with no fields at all, and the endpoint publishes `"properties": {}`.
 *
 * That case exists for a reason - reflecting over a List yields head and tail rather than API
 * fields, and on 2.13 it does not even terminate, since Nil carries a static EmptyUnzip whose
 * elements are Nil, so following it returns to Nil for ever. Answering with nothing avoids the
 * recursion but throws away the element, which is the only thing worth publishing. Under 2.12 the
 * same endpoints leaked head and tl and at least carried the element's real schema under head.
 *
 * Three endpoints return this shape today - getSystemLevelEndpointTags, getBankLevelEndpointTags,
 * createUserWithAccountAccessById - across five API versions each.
 */
class JSONFactory1_4_0RootListTest extends AnyFlatSpec with Matchers {

  case class Tag(tag_id: String, tag_name: String)

  private def schema(entity: Any): JValue = parse(JSONFactory1_4_0.translateEntity(entity, false))

  private def typeOf(v: JValue): Option[String] = (v \ "type").toOption.collect { case JString(t) => t }

  "a bare List response" should "be described as an array" in {
    typeOf(schema(List(Tag("a", "b")))) should equal(Some("array"))
  }

  it should "describe the element's fields under items" in {
    val items = schema(List(Tag("a", "b"))) \ "items"

    withClue(s"items was ${org.json4s.native.JsonMethods.compact(org.json4s.native.JsonMethods.render(items))}: ") {
      (items \\ "tag_id") should not equal JNothing
      (items \\ "tag_name") should not equal JNothing
    }
  }

  it should "not leak the cons cell's own members" in {
    val rendered = JSONFactory1_4_0.translateEntity(List(Tag("a", "b")), false)

    rendered should not include "\"tl\""
    rendered should not include "\"next\""
    rendered should not include "\"head\""
  }

  // The empty case is what makes the recursion impossible: nothing is reflected over, so Nil's
  // EmptyUnzip is never followed.
  "an empty List response" should "be described as an array without items" in {
    typeOf(schema(List.empty[Tag])) should equal(Some("array"))
  }

  it should "terminate rather than recurse through Nil" in {
    noException should be thrownBy JSONFactory1_4_0.translateEntity(Nil, false)
  }

  // A Map is an Iterable too, and is not a JSON array - it must keep whatever it did before rather
  // than be reinterpreted as an array of its first entry.
  "a Map" should "not be described as an array" in {
    typeOf(schema(Map("a" -> 1))) should not equal Some("array")
  }
}
