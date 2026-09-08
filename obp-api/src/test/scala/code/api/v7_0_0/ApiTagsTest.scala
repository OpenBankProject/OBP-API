package code.api.v7_0_0

import code.api.v7_0_0.Http4s700.Implementations7_0_0
import code.api.v7_0_0.JSONFactory700.ApiTagsJsonV700
import code.setup.ServerSetupWithTestData
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.scalatest.Tag

/**
 * GET /obp/v7.0.0/api/tags — every API tag with the number of endpoints that carry it.
 *
 * The counts come from Http4s700.allResourceDocs (all versions, deduplicated), so the test
 * cross-checks one tag against that same source rather than against a hard-coded number.
 */
class ApiTagsTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object GetApiTags extends Tag(nameOf(Implementations7_0_0.getApiTags))

  def v7 = baseRequest / "obp" / "v7.0.0"

  feature(s"test ${GetApiTags}") {
    scenario("anonymous GET returns every tag with an endpoint count", GetApiTags, VersionOfApi) {
      When("we call the endpoint without authentication")
      val response = makeGetRequest(v7 / "api" / "tags")

      Then("it is public and returns 200")
      response.code should equal(200)
      val json = response.body.extract[ApiTagsJsonV700]

      And("there are tags and endpoints")
      json.tags should not be empty
      json.number_of_endpoints should be > 0
      json.number_of_endpoints should equal(Http4s700.allResourceDocs.size)

      And("well-known tags carry endpoints")
      val byTag = json.tags.map(t => t.tag -> t.number_of_endpoints).toMap
      byTag("Account") should be > 0
      byTag("Bank") should be > 0

      And("a tag's count matches the aggregated resource docs")
      val expectedAccountCount = Http4s700.allResourceDocs.count(_.tags.exists(_.displayTag == "Account"))
      byTag("Account") should equal(expectedAccountCount)

      And("no count is negative and tags are unique")
      json.tags.foreach(_.number_of_endpoints should be >= 0)
      json.tags.map(_.tag).distinct.size should equal(json.tags.size)

      And("tags are sorted by count descending then by name")
      val counts = json.tags.map(_.number_of_endpoints)
      counts should equal(counts.sorted.reverse)
      json.tags.sortBy(t => (-t.number_of_endpoints, t.tag)) should equal(json.tags)
    }

    scenario("the v5.1.0 GET /tags name list is a subset of the v7.0.0 tags", GetApiTags, VersionOfApi) {
      val v7Tags = makeGetRequest(v7 / "api" / "tags").body.extract[ApiTagsJsonV700].tags.map(_.tag).toSet
      val v51 = makeGetRequest(baseRequest / "obp" / "v5.1.0" / "tags")
      v51.code should equal(200)
      val v51Tags = v51.body.extract[code.api.v5_1_0.APITags].tags.toSet
      v51Tags.subsetOf(v7Tags) shouldBe true
    }
  }
}
