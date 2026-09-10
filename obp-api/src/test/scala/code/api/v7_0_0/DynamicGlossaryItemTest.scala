package code.api.v7_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages._
import code.api.v3_0_0.GlossaryItemsJsonV300
import code.api.v7_0_0.JSONFactory700.{ApiGlossaryJsonV700, GlossaryItemJsonV700, GlossaryItemsJsonV700, PostGlossaryItemJsonV700, PutGlossaryItemJsonV700}
import code.api.v7_0_0.Http4s700.Implementations7_0_0
import code.entitlement.Entitlement
import code.setup.ServerSetupWithTestData
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.util.UUID

/**
 * Dynamic Glossary Items: role protected CRUD in v7.0.0, and the union they form with the static
 * Glossary in GET /obp/v3.0.0/api/glossary.
 *
 * The roles are system level, so entitlements are granted with an empty bank id. Entitlements
 * accumulate on a user across scenarios, so each "without the role" check calls as a user that no
 * earlier scenario granted that role to.
 */
class DynamicGlossaryItemTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations7_0_0.createDynamicGlossaryItem))
  object ApiEndpoint2 extends Tag(nameOf(Implementations7_0_0.getDynamicGlossaryItems))
  object ApiEndpoint3 extends Tag(nameOf(Implementations7_0_0.getDynamicGlossaryItem))
  object ApiEndpoint4 extends Tag(nameOf(Implementations7_0_0.updateDynamicGlossaryItem))
  object ApiEndpoint5 extends Tag(nameOf(Implementations7_0_0.deleteDynamicGlossaryItem))

  def v3 = baseRequest / "obp" / "v3.0.0"
  def v4 = baseRequest / "obp" / "v4.0.0"
  def v7 = baseRequest / "obp" / "v7.0.0"

  // A title that also exists in the static Glossary, used for the override scenario.
  val staticTitle = "Bank.bank_id"

  def newTitle(): String = "Test.glossary_item_" + UUID.randomUUID().toString.take(8)

  // Mirrors the placeholder Glossary.getGlossaryItem and friends emit into Resource Doc descriptions.
  val GlossaryPlaceholderInDoc = """<!--OBP-GLOSSARY:(FULL|SIMPLE|LINK):(.*?)-->""".r

  def grantSystemRole(userId: String, role: String): Unit =
    Entitlement.entitlement.vend.addEntitlement("", userId, role)

  def errorOf(response: code.setup.APIResponse): String = response.body.extract[ErrorMessage].message

  def post(title: String, description: String, as: Option[(Consumer, Token)], overrides: Option[Boolean] = None) =
    makePostRequest((v7 / "glossary-items").POST <@ (as),
      write(PostGlossaryItemJsonV700(title = title, description = description, overrides_static_item = overrides)))

  def put(title: String, description: String, as: Option[(Consumer, Token)], overrides: Option[Boolean] = None) =
    makePutRequest((v7 / "glossary-items" / title).PUT <@ (as),
      write(PutGlossaryItemJsonV700(description = description, overrides_static_item = overrides)))

  def delete(title: String, as: Option[(Consumer, Token)]) =
    makeDeleteRequest((v7 / "glossary-items" / title).DELETE <@ (as))

  def created(title: String, description: String, as: Option[(Consumer, Token)],
              overrides: Option[Boolean] = None): GlossaryItemJsonV700 = {
    val response = post(title, description, as, overrides)
    response.code should equal(201)
    response.body.extract[GlossaryItemJsonV700]
  }

  def glossaryTitled(title: String): List[String] = {
    val response = makeGetRequest((v3 / "api" / "glossary").GET)
    response.code should equal(200)
    response.body.extract[GlossaryItemsJsonV300].glossary_items
      .filter(_.title.equalsIgnoreCase(title))
      .map(_.description.markdown)
  }

  feature("Create Dynamic Glossary Item") {

    scenario("Authentication and the role are both required", ApiEndpoint1, VersionOfApi) {
      When("no user is given")
      val anonymous = makePostRequest((v7 / "glossary-items").POST,
        write(PostGlossaryItemJsonV700(title = newTitle(), description = "x", overrides_static_item = None)))
      Then("the call is unauthorised")
      anonymous.code should equal(401)

      When("a user without CanCreateGlossaryItem calls")
      val forbidden = post(newTitle(), "x", user2)
      Then("the call is forbidden and names the missing role")
      forbidden.code should equal(403)
      errorOf(forbidden) should include(CanCreateGlossaryItem.toString)
    }

    scenario("Create, then read it back", ApiEndpoint1, ApiEndpoint2, ApiEndpoint3, VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      val title = newTitle()

      When("the item is created")
      val item = created(title, "A **bold** description.", user1)
      Then("the response carries the markdown, the rendered html and the authorship")
      item.title should equal(title)
      item.description.markdown should equal("A **bold** description.")
      item.description.html should include("<strong>bold</strong>")
      item.overrides_static_item should equal(false)
      item.shadows_static_glossary_item should equal(false)
      item.created_by_user_id should equal(resourceUser1.userId)
      item.glossary_item_id should not be empty

      And("it is listed, and readable by title")
      val listed = makeGetRequest((v7 / "glossary-items").GET <@ (user1))
      listed.code should equal(200)
      listed.body.extract[GlossaryItemsJsonV700].glossary_items.map(_.title) should contain(title)

      val single = makeGetRequest((v7 / "glossary-items" / title).GET <@ (user1))
      single.code should equal(200)
      single.body.extract[GlossaryItemJsonV700].glossary_item_id should equal(item.glossary_item_id)

      And("the title is matched case insensitively")
      makeGetRequest((v7 / "glossary-items" / title.toUpperCase).GET <@ (user1)).code should equal(200)

      And("an unknown title is 404")
      val missing = makeGetRequest((v7 / "glossary-items" / newTitle()).GET <@ (user1))
      missing.code should equal(404)
      errorOf(missing) should startWith(GlossaryItemNotFound)
    }

    scenario("Titles are unique case insensitively, and must be non empty", ApiEndpoint1, VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      val title = newTitle()
      created(title, "first", user1)

      When("the same title is created again")
      val duplicate = post(title, "second", user1)
      Then("it is refused with 409")
      duplicate.code should equal(409)
      errorOf(duplicate) should startWith(GlossaryItemAlreadyExists)

      And("so is the same title in a different case")
      post(title.toUpperCase, "third", user1).code should equal(409)

      And("a blank title is refused with 400")
      val blank = post("   ", "x", user1)
      blank.code should equal(400)
      errorOf(blank) should startWith(InvalidGlossaryItemTitle)
    }
  }

  feature("Update and delete Dynamic Glossary Items") {

    scenario("Update replaces the description, and needs its own role", ApiEndpoint1, ApiEndpoint4, VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      val title = newTitle()
      val item = created(title, "before", user1)

      When("a user without CanUpdateGlossaryItem calls")
      val forbidden = put(title, "after", user3)
      Then("the call is forbidden")
      forbidden.code should equal(403)
      errorOf(forbidden) should include(CanUpdateGlossaryItem.toString)

      When("the role is granted")
      grantSystemRole(resourceUser1.userId, CanUpdateGlossaryItem.toString)
      val updated = put(title, "after", user1)
      Then("the description is replaced and the id kept")
      updated.code should equal(200)
      val updatedItem = updated.body.extract[GlossaryItemJsonV700]
      updatedItem.description.markdown should equal("after")
      updatedItem.glossary_item_id should equal(item.glossary_item_id)

      And("updating an unknown title is 404")
      val missing = put(newTitle(), "x", user1)
      missing.code should equal(404)
      errorOf(missing) should startWith(GlossaryItemNotFound)
    }

    scenario("Delete removes the item, and needs its own role", ApiEndpoint1, ApiEndpoint5, VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      val title = newTitle()
      created(title, "doomed", user1)

      When("a user without CanDeleteGlossaryItem calls")
      val forbidden = delete(title, user3)
      Then("the call is forbidden")
      forbidden.code should equal(403)
      errorOf(forbidden) should include(CanDeleteGlossaryItem.toString)

      When("the role is granted")
      grantSystemRole(resourceUser1.userId, CanDeleteGlossaryItem.toString)
      Then("the item is deleted and gone")
      delete(title, user1).code should equal(204)
      makeGetRequest((v7 / "glossary-items" / title).GET <@ (user1)).code should equal(404)

      And("deleting it again is 404")
      val again = delete(title, user1)
      again.code should equal(404)
      errorOf(again) should startWith(GlossaryItemNotFound)
    }
  }

  feature("GET /api/glossary returns the union of static and Dynamic Glossary Items") {

    scenario("A new Dynamic Glossary Item appears in the Glossary", ApiEndpoint1, ApiEndpoint5, VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      grantSystemRole(resourceUser1.userId, CanDeleteGlossaryItem.toString)
      val title = newTitle()

      Given("the title is not in the Glossary to start with")
      glossaryTitled(title) should equal(Nil)

      When("a Dynamic Glossary Item is created")
      created(title, "Only in the database.", user1)
      Then("the Glossary picks it up straight away, without a redeploy")
      glossaryTitled(title) should equal(List("Only in the database."))

      When("it is deleted")
      delete(title, user1).code should equal(204)
      Then("it leaves the Glossary again")
      glossaryTitled(title) should equal(Nil)
    }

    scenario("A Dynamic Glossary Item replaces the static one of the same title", ApiEndpoint1, ApiEndpoint5, VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      grantSystemRole(resourceUser1.userId, CanDeleteGlossaryItem.toString)

      Given("the static Glossary carries this title")
      // Note Bank.bank_id is defined twice in the static Glossary, in Glossary.scala and again in
      // ExampleValue.scala, so this is a list rather than a single entry. Overriding it replaces
      // every static item with that title, which is what makes the count check below meaningful.
      val staticDescriptions = glossaryTitled(staticTitle)
      staticDescriptions should not be empty

      When("a Dynamic Glossary Item with that title is created without declaring the override")
      val refused = post(staticTitle, "Overridden by the operator.", user1)
      Then("it is refused, so shipped documentation is never displaced by accident")
      refused.code should equal(409)
      errorOf(refused) should startWith(GlossaryItemShadowsStaticItem)

      When("the override is declared")
      val item = created(staticTitle, "Overridden by the operator.", user1, overrides = Some(true))
      Then("the response reports both the declared intent and the actual shadowing")
      item.overrides_static_item should equal(true)
      item.shadows_static_glossary_item should equal(true)

      And("the Glossary now carries exactly one item with that title, and it is the dynamic text")
      glossaryTitled(staticTitle) should equal(List("Overridden by the operator."))

      When("the Dynamic Glossary Item is deleted")
      delete(staticTitle, user1).code should equal(204)
      Then("the static text is served again")
      glossaryTitled(staticTitle) should equal(staticDescriptions)
    }
  }

  feature("Overriding a static Glossary Item has to be declared") {

    scenario("A collision with a static title is refused unless the override is declared", ApiEndpoint1, VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      grantSystemRole(resourceUser1.userId, CanDeleteGlossaryItem.toString)

      When("the override is not mentioned at all")
      val silent = post(staticTitle, "text", user1)
      Then("the request is refused and says what to do about it")
      silent.code should equal(409)
      errorOf(silent) should startWith(GlossaryItemShadowsStaticItem)

      When("the override is explicitly declined")
      post(staticTitle, "text", user1, overrides = Some(false)).code should equal(409)

      When("the override is declared")
      Then("the item is created")
      val item = created(staticTitle, "text", user1, overrides = Some(true))
      item.overrides_static_item should equal(true)

      delete(staticTitle, user1).code should equal(204)
    }

    scenario("A title with no static counterpart needs no declaration", ApiEndpoint1, VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      val item = created(newTitle(), "text", user1)
      item.overrides_static_item should equal(false)
      item.shadows_static_glossary_item should equal(false)
    }

    scenario("The Glossary marks Dynamic Items and the ones displacing static text", ApiEndpoint1, ApiEndpoint5, VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      grantSystemRole(resourceUser1.userId, CanDeleteGlossaryItem.toString)

      // The provenance flags are a v7.0.0 addition: v3.0.0 is STABLE and its JSON must not change.
      def itemInGlossary(title: String) = {
        val response = makeGetRequest((v7 / "api" / "glossary").GET)
        response.code should equal(200)
        response.body.extract[ApiGlossaryJsonV700].glossary_items.find(_.title.equalsIgnoreCase(title))
      }

      Given("a static Glossary Item is reported as neither dynamic nor overriding")
      itemInGlossary(staticTitle).map(_.is_dynamic) should equal(Some(false))

      When("a Dynamic Item is added that does not displace anything")
      val plainTitle = newTitle()
      created(plainTitle, "standalone", user1)
      Then("it is marked dynamic but not as an override")
      val plain = itemInGlossary(plainTitle)
      plain.map(_.is_dynamic) should equal(Some(true))
      plain.map(_.overrides_static_item) should equal(Some(false))

      When("a Dynamic Item is added that declares an override of a static one")
      created(staticTitle, "overriding text", user1, overrides = Some(true))
      Then("the Glossary marks it as both dynamic and overriding")
      val overriding = itemInGlossary(staticTitle)
      overriding.map(_.is_dynamic) should equal(Some(true))
      overriding.map(_.overrides_static_item) should equal(Some(true))

      delete(staticTitle, user1).code should equal(204)
      delete(plainTitle, user1).code should equal(204)
    }
  }

  feature("No Glossary placeholder ever reaches a client") {

    // Glossary Items cross-reference each other, so their own descriptions carry placeholders too.
    // Missing that is exactly how 60 raw tokens once reached GET /api/glossary.
    scenario("The Glossary itself carries expanded links, not placeholders", VersionOfApi) {
      for ((label, url) <- List("v3.0.0" -> (v3 / "api" / "glossary"), "v7.0.0" -> (v7 / "api" / "glossary"))) {
        val response = makeGetRequest(url.GET)
        response.code should equal(200)
        val body = response.body.toString
        withClue(s"$label Glossary leaked an unexpanded placeholder: ") {
          body should not include "OBP-GLOSSARY"
        }
        And(s"$label carries the links those placeholders stand for")
        body should include("/glossary#")
      }
    }
  }

  feature("Every Glossary title appears once") {

    scenario("The Glossary has no duplicate titles", VersionOfApi) {
      // A duplicate title breaks any client that keys a list by it, and only one of the two can own
      // the /glossary#Title anchor. Five pairs once shipped this way.
      val response = makeGetRequest((v3 / "api" / "glossary").GET)
      response.code should equal(200)
      // Exact titles: anchors are case sensitive, so Account and account are distinct entries to a
      // client and both are served. Only an identical title breaks a keyed list.
      val titles = response.body.extract[GlossaryItemsJsonV300].glossary_items.map(_.title)
      val duplicated = titles.groupBy(identity).collect { case (t, ts) if ts.size > 1 => t }.toList.sorted
      withClue("titles defined more than once: ") { duplicated should equal(Nil) }
    }
  }

  feature("The STABLE v3.0.0 Glossary keeps its shape") {

    scenario("v3.0.0 serves the same merged Glossary, without the v7 provenance fields", VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      grantSystemRole(resourceUser1.userId, CanDeleteGlossaryItem.toString)
      val title = newTitle()
      created(title, "Only in the database.", user1)

      When("the STABLE v3.0.0 Glossary is fetched")
      val response = makeGetRequest((v3 / "api" / "glossary").GET)
      response.code should equal(200)
      Then("it carries the Dynamic Item, since the content is the same union")
      val item = (response.body \\ "glossary_items").children
        .find(i => (i \\ "title").extractOpt[String].contains(title))
      item should not be empty

      And("each entry has exactly the two fields v3.0.0 has always had")
      item.get.children should have size 2
      (item.get \\ "is_dynamic").extractOpt[Boolean] should equal(None)
      (item.get \\ "overrides_static_item").extractOpt[Boolean] should equal(None)

      delete(title, user1).code should equal(204)
    }
  }

  feature("Glossary text embedded in endpoint descriptions honours Dynamic Glossary Items") {

    // createMyApiCollectionEndpoint embeds the "API Collections" Glossary Item in its description
    // with Glossary.getGlossaryItem, which is the placeholder that gets expanded when docs are served.
    val embeddedTitle = "API Collection"
    val embeddingFunction = "createMyApiCollectionEndpoint"

    def descriptionOfEmbeddingEndpoint(): String = {
      val response = makeGetRequest(
        (v4 / "resource-docs" / "v4.0.0" / "obp") <<? List(("functions", embeddingFunction)))
      response.code should equal(200)
      val descriptions = (response.body \ "resource_docs").children
        .filter(doc => (doc \ "operation_id").extractOpt[String].exists(_.contains(embeddingFunction)))
        .flatMap(doc => (doc \ "description").extractOpt[String])
      withClue(s"no resource doc found for $embeddingFunction in: ${response.body}") {
        descriptions should not be empty
      }
      descriptions.mkString("\n")
    }

    scenario("Every title embedded in a description resolves to a Glossary Item", VersionOfApi) {
      // A typo in a title is silent otherwise: the description just renders the literal text
      // "glossary-item-not-found". That is how "API Collections" (the item is "API Collection")
      // went unnoticed across six endpoint descriptions.
      val response = makeGetRequest((v3 / "api" / "glossary").GET)
      response.code should equal(200)
      val definedTitles =
        response.body.extract[GlossaryItemsJsonV300].glossary_items.map(_.title.toLowerCase).toSet

      val embeddedTitles = code.api.util.APIUtil.allStaticResourceDocs
        .flatMap(doc => GlossaryPlaceholderInDoc.findAllMatchIn(doc.description).map(_.group(2)))
        .distinct

      withClue("Resource Doc descriptions embed Glossary titles that do not exist: ") {
        embeddedTitles.filterNot(title => definedTitles.contains(title.toLowerCase)) should equal(Nil)
      }
    }

    scenario("Placeholders never leak into a served description", VersionOfApi) {
      val description = descriptionOfEmbeddingEndpoint()
      Then("the description carries the Glossary text, not the unexpanded placeholder")
      description should not include "OBP-GLOSSARY"
      description should include(embeddedTitle)

      And("no field links to an empty Glossary anchor")
      // "[field](/glossary#)" lands the reader at the top of the Glossary rather than a definition.
      description should not include "(/glossary#)"
    }

    scenario("A Dynamic Glossary Item replaces the embedded text", ApiEndpoint1, ApiEndpoint5, VersionOfApi) {
      grantSystemRole(resourceUser1.userId, CanCreateGlossaryItem.toString)
      grantSystemRole(resourceUser1.userId, CanDeleteGlossaryItem.toString)

      Given("the endpoint description embeds the static Glossary text")
      val before = descriptionOfEmbeddingEndpoint()
      before should not include "Overridden in the endpoint description."

      When("a Dynamic Glossary Item with that title is created")
      created(embeddedTitle, "Overridden in the endpoint description.", user1, overrides = Some(true))
      Then("the endpoint description carries the dynamic text, despite the Resource Doc cache")
      val during = descriptionOfEmbeddingEndpoint()
      during should include("Overridden in the endpoint description.")
      during should not include "OBP-GLOSSARY"

      When("the Dynamic Glossary Item is deleted")
      delete(embeddedTitle, user1).code should equal(204)
      Then("the endpoint description goes back to the static text")
      val after = descriptionOfEmbeddingEndpoint()
      after should not include "Overridden in the endpoint description."
      after should equal(before)
    }
  }
}
