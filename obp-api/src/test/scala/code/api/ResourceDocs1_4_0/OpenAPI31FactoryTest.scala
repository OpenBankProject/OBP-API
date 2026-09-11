package code.api.ResourceDocs1_4_0

import code.api.v1_4_0.JSONFactory1_4_0
import org.json4s.JsonAST.{JNothing, JValue}
import org.json4s.native.JsonMethods.parse
import org.json4s.jvalue2monadic
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Covers OpenAPI31JSONFactory, which had no test of any kind.
 *
 * It serves a public endpoint - GET /obp/vX/resource-docs/API_VERSION/openapi - and its input is
 * the typed_success_response_body that ResourceDocJson carries. This branch changed that field for
 * 62 (version, endpoint) pairs while correcting how collections are described, so the one consumer
 * that reads those schemas structurally was being handed new shapes with nothing asserting what it
 * did with them. It turned out to handle them correctly; that is now pinned rather than assumed.
 *
 * These are unit tests over the factory, not the endpoint: no server, no resource-docs fetch.
 */
class OpenAPI31FactoryTest extends AnyFlatSpec with Matchers {

  private def doc(operationId: String, typedBody: JValue): JSONFactory1_4_0.ResourceDocJson =
    JSONFactory1_4_0.ResourceDocJson(
      operation_id = operationId,
      implemented_by = JSONFactory1_4_0.ImplementedByJson("4.0.0", operationId),
      request_verb = "GET",
      request_url = "/test",
      summary = "Test",
      description = "Test desc",
      description_markdown = "Test desc",
      example_request_body = null,
      success_response_body = null,
      error_response_bodies = List("OBP-10000"),
      tags = List("Test"),
      typed_request_body = JNothing,
      typed_success_response_body = typedBody,
      roles = Some(List()),
      is_featured = false,
      special_instructions = "",
      specified_url = "/obp/v4.0.0/test",
      connector_methods = List(),
      created_by_bank_id = None
    )

  // DefaultFormats, not CustomJsonFormats: the latter reaches APIUtil, whose static initialiser
  // wants props that a unit test has not set up, and the suite dies before its first assertion.
  private implicit val formats: org.json4s.Formats = org.json4s.DefaultFormats

  private def responseSchema(typedBody: JValue): JValue = {
    val openApi = OpenAPI31JSONFactory.createOpenAPI31Json(List(doc("testOp", typedBody)), "v4.0.0", "http://localhost:8080")
    val rendered = parse(org.json4s.native.Serialization.write(openApi))
    // The one and only path, found rather than spelled: createOpenAPI31Json runs the url through
    // convertPathToOpenAPI, and hard-coding the result would test that transform rather than the
    // schema conversion this suite is about.
    val paths = (rendered \ "paths") match {
      case org.json4s.JObject(fields) => fields
      case other => fail(s"paths was not an object: $other")
    }
    withClue(s"expected exactly one path, got ${paths.map(_._1)}: ") { paths.size should equal(1) }
    (paths.head._2 \ "get" \ "responses" \ "200" \ "content" \ "application/json" \ "schema")
  }

  "an object typed body" should "become an object schema carrying its properties" in {
    val schema = responseSchema(parse("""{"type":"object","properties":{"bank_id":{"type":"string"}}}"""))

    (schema \ "type") should equal(org.json4s.JString("object"))
    (schema \ "properties" \ "bank_id" \ "type") should equal(org.json4s.JString("string"))
  }

  // The shape this branch introduced for a bare-List response. It reaches the factory as a root
  // array, which is the one case the object-shaped conversion path could have dropped.
  "an array typed body" should "become an array schema that keeps its items" in {
    val schema = responseSchema(parse(
      """{"type":"array","items":{"type":"object","properties":{"tag_name":{"type":"string"}}}}"""))

    (schema \ "type") should equal(org.json4s.JString("array"))
    (schema \ "items" \ "type") should equal(org.json4s.JString("object"))
    (schema \ "items" \ "properties" \ "tag_name" \ "type") should equal(org.json4s.JString("string"))
  }

  "a nested array field" should "keep its items through the conversion" in {
    val schema = responseSchema(parse(
      """{"type":"object","properties":{"views":{"type":"array","items":{"type":"object","properties":{"id":{"type":"string"}}}}}}"""))

    (schema \ "properties" \ "views" \ "type") should equal(org.json4s.JString("array"))
    (schema \ "properties" \ "views" \ "items" \ "properties" \ "id" \ "type") should equal(org.json4s.JString("string"))
  }

  "the document" should "declare the OpenAPI version it claims to be" in {
    val openApi = OpenAPI31JSONFactory.createOpenAPI31Json(List(doc("testOp", JNothing)), "v4.0.0", "http://localhost:8080")

    openApi.openapi should equal("3.1.0")
  }

  it should "survive a typed body that is absent" in {
    noException should be thrownBy
      OpenAPI31JSONFactory.createOpenAPI31Json(List(doc("testOp", JNothing)), "v4.0.0", "http://localhost:8080")
  }
}
