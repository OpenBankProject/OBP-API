package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil
import code.api.v1_4_0.JSONFactory1_4_0
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.{FlatSpec, Matchers}
import code.api.util.http4s.Http4sResourceDocAggregation

/**
 * Two claims the Scala 2.13 migration made about generated documentation, neither of which any
 * existing test could confirm.
 *
 * The first is ordering. createSwaggerResourceDoc used to build its paths map with breakOut,
 * consuming an already-sorted sequence directly; it now collects the pairs and hands them to
 * ListMap, and the comment left behind asserts the order is unaffected. ListMap has a history of
 * iterating in reverse insertion order, the paths map goes into a published artifact, and
 * SwaggerDocsTest only checks status codes and extraction - so the claim was never tested.
 *
 * The second is the array-shaped bodies. Six ResourceDocs passed a bare List, which was a Product
 * on 2.12, so getAllFields walked it with productIterator and documented `head` and `tl` as if they
 * were fields of the entity. What replaced it is the root-collection branch in getAllFields, which
 * documents what the collection holds instead of the collection itself. (A jArrayBodyOf helper was
 * tried first and reverted - it described json4s internals rather than the entity - so it is not
 * the fix, and the name survives nowhere else.) The question here is not whether the old leak is
 * gone - it is - but whether anything still describes what the array contains.
 */
class SwaggerPathOrderAndArrayBodyTest extends FlatSpec with Matchers {

  /** Real docs rather than synthetic ones: the ordering only matters for what actually ships. */
  private lazy val swagger: SwaggerJSONFactory.SwaggerResourceDoc = {
    val docs = JSONFactory1_4_0
      .createResourceDocsJson(Http4sResourceDocAggregation.v400.toList, isVersion4OrHigher = true, None)
      .resource_docs
    SwaggerJSONFactory.createSwaggerResourceDoc(docs, ApiVersion.v4_0_0)
  }

  "createSwaggerResourceDoc" should "emit paths in ascending url order" in {
    val urls = swagger.paths.keys.toList

    urls should not be empty
    urls should equal(urls.sorted)
  }

  it should "emit one entry per distinct url" in {
    val urls = swagger.paths.keys.toList

    urls.distinct.size should equal(urls.size)
  }

  "a list used as a response body" should "not leak a List's product elements as API fields" in {
    // The defect this replaced: head and tl documented as if they belonged to the entity.
    val body = List(SwaggerDefinitionsJSON.bankLevelEndpointTagResponseJson400)

    val fieldNames = JSONFactory1_4_0.getAllFields(body).map(_.getName)

    fieldNames should not contain "tl"
    fieldNames should not contain "head"
  }

  it should "still describe the element type's own fields" in {
    // The claim under test. An array-shaped body whose field table is empty documents nothing about
    // what the array holds, which is the whole point of the field table.
    val entity = SwaggerDefinitionsJSON.bankLevelEndpointTagResponseJson400
    val entityFields = JSONFactory1_4_0.getAllFields(entity).map(_.getName)
    entityFields should not be empty

    val bodyFields = JSONFactory1_4_0.getAllFields(List(entity)).map(_.getName)

    withClue(s"entity declares $entityFields but the array body describes $bodyFields: ") {
      entityFields.foreach(name => bodyFields should contain(name))
    }
  }
}
