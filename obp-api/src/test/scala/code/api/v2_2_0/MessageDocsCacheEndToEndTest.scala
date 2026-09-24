package code.api.v2_2_0

import code.setup.DefaultUsers
import com.openbankproject.commons.util.JsonAliases.compactRender

/**
 * The message-docs response is cached in process and in the shared store. A second replica, or
 * this one after a restart, serves the shared copy, so that copy must be identical to what the
 * first request returned.
 */
class MessageDocsCacheEndToEndTest extends V220ServerSetup with DefaultUsers {

  feature("GET /obp/v2.2.0/message-docs/CONNECTOR is stable across the cache levels") {
    scenario("the response is identical whether it was generated or read back from the shared level") {
      MessageDocsJsonCache.invalidateAll()
      val request = (v2_2Request / "message-docs" / "rest_vMar2019").GET

      val generated = makeGetRequest(request)
      generated.code should equal(200)

      // Drop the in-process level so the next request goes through the shared level.
      MessageDocsJsonCache.invalidateAll()
      val again = makeGetRequest(request)
      again.code should equal(200)

      compactRender(again.body) should equal(compactRender(generated.body))
    }
  }
}
