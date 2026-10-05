package code.api.v4_0_0

import code.DynamicData.DynamicDataProvider
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.dynamic.endpoint.helper.DynamicEndpoints
import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages.DynamicResourceDocUrlAmbiguous
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import code.dynamicResourceDoc.{DynamicResourceDoc, DynamicResourceDocProvider}
import net.liftweb.common.Full
import net.liftweb.mapper.By
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import net.liftweb.util.StringHelpers
import org.json4s._

import java.net.URLEncoder

/**
 * This suite checks that a Dynamic Resource Doc is served under the space it belongs to:
 * /obp/dynamic-endpoint/banks/BANK_ID/dynamic-resource-doc/REQUEST_URL, with SYS for the system space,
 * so docs of different spaces at the same URL never answer for one another. The URL without the space
 * still works while exactly one doc answers it, and is refused, naming the spaces, when several do.
 *
 * Each space gets an entity of the same name holding one record that names the space, and a Dynamic
 * Query at the same URL reading it, so an answer shows which space's doc ran.
 */
class DynamicResourceDocSpaceUrlTest extends V400ServerSetup {

  private val owner = "space-url-owner"
  private val sfx = java.util.UUID.randomUUID().toString.take(8).replace("-", "")
  private val Echo = s"Echo$sfx"
  private val bankA = testBankId1.value
  private val bankB = testBankId2.value
  private val requestUrl = s"/space_url_$sfx/names"

  /** Define the entity in a space (None = system), save one record naming the space, and let user1 read it there. */
  private def spaceWithRecord(space: Option[String], name: String): Unit = {
    DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(DynamicEntityCommons(
      Echo, s"""{"$Echo":{"properties":{"${StringHelpers.snakify(Echo)}_id":{"type":"string"},"name":{"type":"string"}}}}""",
      None, owner, space, hasPersonalEntity = false)).openOrThrowException("definition")
    DynamicDataProvider.connectorMethodProvider.vend.save(space, Echo,
      JObject(JField(StringHelpers.snakify(Echo) + "_id", JString(java.util.UUID.randomUUID().toString)), JField("name", JString(name))),
      Some(owner), false).openOrThrowException("record")
    Entitlement.entitlement.vend.addEntitlement(space.getOrElse("SYS"), resourceUser1.userId, s"CanGetDynamicEntityRecord_$Echo")
  }

  /** A Dynamic Query in a space (None = system) at the shared URL, reading that space's entity. */
  private def queryDoc(space: Option[String]): Unit = queryDocAt(space, requestUrl)

  private def queryDocAt(space: Option[String], url: String): Unit =
    DynamicResourceDocProvider.provider.vend.create(space, SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
      dynamicResourceDocId = None, bankId = space, roles = "", partialFunctionName = s"spaceUrl${space.getOrElse("System").capitalize}",
      requestVerb = "GET", requestUrl = url, exampleRequestBody = None, errorResponseBodies = "OBP-50000: Unknown Error.",
      methodBody = URLEncoder.encode(s"""{ "from": "$Echo", "select": ["name"], "envelope": { "rows": "names" } }""", "UTF-8"),
      programmingLang = "Query"), Some(owner)).openOrThrowException(s"doc in $space")

  private def underSpace(space: String) = dynamicEndpoint_Request / "banks" / space / "dynamic-resource-doc" / s"space_url_$sfx" / "names"
  private def withoutSpace = dynamicEndpoint_Request / "dynamic-resource-doc" / s"space_url_$sfx" / "names"
  private def names(response: code.setup.APIResponse): List[JValue] =
    withClue(s"response: ${response.body}") {
      response.code should equal(200)
      (response.body \ "names" \ "name") match { case JArray(values) => values; case single => List(single) }
    }

  feature("A Dynamic Resource Doc is served under the space it belongs to") {
    scenario("system and bank docs at the same URL answer under their own space; the URL without a space works only while it is unambiguous") {
      spaceWithRecord(None, "system space")
      spaceWithRecord(Some(bankA), "bank A")
      spaceWithRecord(Some(bankB), "bank B")

      Given("a system-level doc only")
      queryDoc(None)
      Then("it answers under /banks/SYS, and under the URL without a space")
      names(makeGetRequest(underSpace("SYS") <@ (user1))) shouldBe List(JString("system space"))
      names(makeGetRequest(withoutSpace <@ (user1))) shouldBe List(JString("system space"))
      And("not under a bank")
      makeGetRequest(underSpace(bankA) <@ (user1)).code should equal(404)

      And("the registry shows the URL that names its space")
      DynamicEndpoints.dynamicResourceDocs.map(_.requestUrl) should contain(s"/banks/SYS/dynamic-resource-doc$requestUrl")

      When("bank A gets a doc at the same URL")
      queryDoc(Some(bankA))
      Then("each answers under its own space, with its own space's data")
      names(makeGetRequest(underSpace(bankA) <@ (user1))) shouldBe List(JString("bank A"))
      names(makeGetRequest(underSpace("SYS") <@ (user1))) shouldBe List(JString("system space"))
      And("the URL without a space no longer says which, so it is refused, naming both spaces")
      val ambiguous = makeGetRequest(withoutSpace <@ (user1))
      ambiguous.code should equal(409)
      ambiguous.body.extract[ErrorMessage].message shouldBe s"$DynamicResourceDocUrlAmbiguous${List("SYS", bankA).sorted.mkString(", ")}."

      When("bank B gets one too")
      queryDoc(Some(bankB))
      names(makeGetRequest(underSpace(bankB) <@ (user1))) shouldBe List(JString("bank B"))
      names(makeGetRequest(underSpace(bankA) <@ (user1))) shouldBe List(JString("bank A"))
      makeGetRequest(withoutSpace <@ (user1)).body.extract[ErrorMessage].message shouldBe
        s"$DynamicResourceDocUrlAmbiguous${List("SYS", bankA, bankB).sorted.mkString(", ")}."
    }

    scenario("a URL is unique within a space: the same verb and URL cannot be created twice in one space") {
      val url = s"/space_url_unique_$sfx"
      def create(space: Option[String]) = DynamicResourceDocProvider.provider.vend.getByVerbAndUrl(space, "GET", url).isEmpty
      queryDocAt(None, url)
      queryDocAt(Some(bankA), url)
      create(None) shouldBe false
      create(Some(bankA)) shouldBe false
      create(Some(bankB)) shouldBe true

      Then("a system level doc is stored with the SYS bank id, not NULL, and reads back as the system space")
      val stored = DynamicResourceDoc.findAll(By(DynamicResourceDoc.RequestUrl, url), By(DynamicResourceDoc.RequestVerb, "GET"))
      stored.map(_.BankId.get).sorted shouldBe List(bankA, "SYS").sorted
      DynamicResourceDocProvider.provider.vend.getByVerbAndUrl(None, "GET", url).map(_.bankId) shouldBe Full(None)

      And("so the database itself refuses a second system level doc at the same verb and URL")
      DynamicResourceDocProvider.provider.vend.create(None, SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None, bankId = None, roles = "", partialFunctionName = "spaceUrlSecondSystem",
        requestVerb = "GET", requestUrl = url, exampleRequestBody = None, errorResponseBodies = "OBP-50000: Unknown Error.",
        methodBody = URLEncoder.encode(s"""{ "from": "$Echo", "select": ["name"] }""", "UTF-8"),
        programmingLang = "Query"), Some(owner)).isDefined shouldBe false
    }

    scenario("a bank-level doc alone still answers at the URL without a space, as it did before") {
      val onlyBank = s"/space_url_only_bank_$sfx/names"
      spaceWithRecord(Some(bankB), "bank B")
      DynamicResourceDocProvider.provider.vend.create(Some(bankB), SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None, bankId = Some(bankB), roles = "", partialFunctionName = "spaceUrlOnlyBank",
        requestVerb = "GET", requestUrl = onlyBank, exampleRequestBody = None, errorResponseBodies = "OBP-50000: Unknown Error.",
        methodBody = URLEncoder.encode(s"""{ "from": "$Echo", "select": ["name"], "envelope": { "rows": "names" } }""", "UTF-8"),
        programmingLang = "Query"), Some(owner)).openOrThrowException("doc")
      names(makeGetRequest(dynamicEndpoint_Request / "dynamic-resource-doc" / s"space_url_only_bank_$sfx" / "names" <@ (user1))) shouldBe List(JString("bank B"))
      names(makeGetRequest(dynamicEndpoint_Request / "banks" / bankB / "dynamic-resource-doc" / s"space_url_only_bank_$sfx" / "names" <@ (user1))) shouldBe List(JString("bank B"))
    }
  }
}
