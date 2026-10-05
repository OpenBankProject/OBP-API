package code.api.ResourceDocs1_4_0

import code.telemetry.Telemetry
import com.openbankproject.commons.util.JsonAliases.compactRender
import org.scalatest.Tag

/**
 * This suite checks that the resource-docs `tags` and `functions` filters are normalised: the
 * order of the values and repeated values do not change the response, and every spelling of the
 * same filter shares one cached document. Before, each spelling built its own cache key, so a
 * scanner varying the order or repeating values forced a fresh render every time.
 */
class ResourceDocsFilterNormalisationTest extends ResourceDocsV140ServerSetup {

  object FilterNormalisation extends Tag("ResourceDocsFilterNormalisation")

  private def staticDocsReads(result: String): Double =
    Option(Telemetry.registry.find("obp.api.redis_cache.gets").tags("cache", "static_resource_docs", "result", result).counter())
      .map(_.count()).getOrElse(0.0)

  private def docs(tags: String, functions: Option[String] = None) = {
    val params = List("content" -> "static", "tags" -> tags) ++ functions.map("functions" -> _)
    makeGetRequest((ResourceDocsV5_1Request / "resource-docs" / "v5.1.0" / "obp").GET <<? params)
  }

  feature("Resource-docs filters are normalised") {

    scenario("the order and repeats of tags do not change the response", FilterNormalisation) {
      val first = docs("Bank,Account")
      val second = docs("Account,Bank,Account")
      first.code should equal(200)
      second.code should equal(200)
      compactRender(second.body) should equal(compactRender(first.body))
    }

    scenario("every spelling of the same filter reads the same cached document", FilterNormalisation) {
      docs("Customer,Card").code should equal(200)
      val hitsBefore = staticDocsReads("hit")
      val errorsBefore = staticDocsReads("error")
      docs("Card,Customer,Card").code should equal(200)
      // With Redis reachable the reordered request is a hit; without it every read is an error.
      if (staticDocsReads("error") == errorsBefore) staticDocsReads("hit") - hitsBefore shouldBe 1.0
    }

    scenario("a tag no ResourceDoc carries does not change the response", FilterNormalisation) {
      val known = docs("Bank")
      val withJunk = docs("Bank,not-a-tag-4c1d")
      withJunk.code should equal(200)
      compactRender(withJunk.body) should equal(compactRender(known.body))
    }

    scenario("filters made only of unknown tags all share one cached empty document", FilterNormalisation) {
      val first = docs("junk-a91")
      first.code should equal(200)
      (first.body \ "resource_docs").children shouldBe empty
      val hitsBefore = staticDocsReads("hit")
      val errorsBefore = staticDocsReads("error")
      docs("junk-b52,junk-c63").code should equal(200)
      if (staticDocsReads("error") == errorsBefore) staticDocsReads("hit") - hitsBefore shouldBe 1.0
    }

    scenario("the order of functions does not change the response", FilterNormalisation) {
      val first = docs("Bank", Some("getBanks,getBank"))
      val second = docs("Bank", Some("getBank,getBanks,getBank"))
      compactRender(second.body) should equal(compactRender(first.body))
    }
  }
}
