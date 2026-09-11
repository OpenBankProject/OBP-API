package code.connectormethod

import code.api.util.APIUtil
import code.setup.ServerSetup
import net.liftweb.common.Failure

/**
 * Two edges of the provenance work that arrived with origin/develop, both on paths the endpoint
 * tests do not reach.
 *
 * The method body arrives URL-encoded and every provider hashes `decodedMethodBody`, so the decode
 * runs on the create path for every caller. `URLDecoder.decode` throws IllegalArgumentException on
 * a malformed escape, and the caller controls the body - `%` is an ordinary character in Scala
 * source. The Mapper implementation this replaced computed the hash inside its `tryo`, so such a
 * body came back as a Failure the endpoint could report; computing it outside turns the same input
 * into an exception that escapes create.
 *
 * The update path's provenance arguments carry defaults and the SET clause writes them
 * unconditionally, so omitting them does not leave the stored values alone - it nulls them. The
 * hash is what makes tampering with a runtime-compiled endpoint detectable, so clearing it defeats
 * the feature silently. Asserted here rather than left for a future caller to discover.
 */
class ConnectorMethodProvenanceEdgeTest extends ServerSetup {

  private def cleanup(): Unit =
    DoobieConnectorMethodProvider.getAll().foreach(m =>
      m.connectorMethodId.foreach(id => DoobieConnectorMethodProvider.deleteById(id)))

  override def beforeEach(): Unit = { super.beforeEach(); cleanup() }

  Feature("provenance on the connector-method store") {

    Scenario("a method body with a malformed percent escape is refused, not thrown out of") {
      // A bare '%' is legal Scala and legal in a request body; it is not a legal URL escape.
      val malformed = "() => { val pct = 100 % 7; pct }"
      val entity = JsonConnectorMethod(None, "getBankMalformed", malformed, "Scala")

      val result = DoobieConnectorMethodProvider.create(entity, Some("user-x"))

      withClue("the decode failure must be captured as a Failure box, the way the Mapper " +
        "implementation did, rather than escaping create as an exception: ") {
        result shouldBe a[Failure]
      }
    }

    Scenario("update leaves the creator alone and moves the hash to the new body") {
      val body = java.net.URLEncoder.encode("() => 1", "UTF-8")
      val created = DoobieConnectorMethodProvider
        .create(JsonConnectorMethod(None, "getBankProvenance", body, "Scala"), Some("creator-1"))
        .openOrThrowException("the connector method under test must be created")
      val id = created.connectorMethodId.getOrElse("")
      DoobieConnectorMethodProvider.getByIdWithProvenance(id)
        .openOrThrowException("just created").methodBodyHash shouldBe
        Some(APIUtil.sha256Hex("() => 1"))

      val newBody = java.net.URLEncoder.encode("() => 2", "UTF-8")
      DoobieConnectorMethodProvider.update(id, newBody, "Scala", Some("updater-1"))

      val after = DoobieConnectorMethodProvider.getByIdWithProvenance(id)
        .openOrThrowException("the updated connector method must be readable")

      withClue("the creator is not the updater and must survive an update: ") {
        after.createdByUserId shouldBe Some("creator-1")
      }
      withClue("the hash must track the new body, not stay on the old one or go null: ") {
        after.methodBodyHash shouldBe Some(APIUtil.sha256Hex("() => 2"))
      }
      withClue("the updater must be recorded: ") {
        after.updatedByUserId shouldBe Some("updater-1")
      }
    }
  }
}
