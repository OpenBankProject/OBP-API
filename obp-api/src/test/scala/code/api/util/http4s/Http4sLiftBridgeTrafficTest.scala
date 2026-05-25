package code.api.util.http4s

import org.scalatest.{FlatSpec, Matchers}

class Http4sLiftBridgeTrafficTest extends FlatSpec with Matchers {

  "bucket" should "keep API version segments verbatim" in {
    Http4sLiftBridgeTraffic.bucket("/obp/v6.0.0/banks") shouldBe "/obp/v6.0.0/banks"
    Http4sLiftBridgeTraffic.bucket("/obp/v1_2_1/banks") shouldBe "/obp/v1_2_1/banks"
  }

  it should "collapse UUIDs" in {
    Http4sLiftBridgeTraffic.bucket("/obp/v6.0.0/consents/9ca8a7e4-6d02-40e3-a129-0b2bf89de9b1") shouldBe
      "/obp/v6.0.0/consents/{uuid}"
  }

  it should "collapse purely numeric segments" in {
    Http4sLiftBridgeTraffic.bucket("/obp/v6.0.0/transactions/12345") shouldBe
      "/obp/v6.0.0/transactions/{n}"
  }

  it should "collapse long opaque token-like segments (with a digit/dot/dash/underscore)" in {
    Http4sLiftBridgeTraffic.bucket("/obp/v6.0.0/banks/gh.29.uk/accounts") shouldBe
      "/obp/v6.0.0/banks/{id}/accounts"
    Http4sLiftBridgeTraffic.bucket("/obp/v6.0.0/accounts/8ca8a7e4_6d02_40e3_a129_0b2bf89de9f0/x") shouldBe
      "/obp/v6.0.0/accounts/{id}/x"
  }

  it should "leave short literal segments alone" in {
    Http4sLiftBridgeTraffic.bucket("/obp/v6.0.0/banks/BANK_ID/accounts/views") shouldBe
      "/obp/v6.0.0/banks/BANK_ID/accounts/views"
  }

  it should "normalise the OIDC callback path the way operators expect" in {
    Http4sLiftBridgeTraffic.bucket("/auth/openid-connect/callback") shouldBe
      "/auth/openid-connect/callback"
    Http4sLiftBridgeTraffic.bucket("/auth/openid-connect/callback-1") shouldBe
      "/auth/openid-connect/callback-1"
  }

  it should "handle the root path" in {
    Http4sLiftBridgeTraffic.bucket("/") shouldBe "/"
    Http4sLiftBridgeTraffic.bucket("") shouldBe "/"
  }

  "observe" should "increment on repeated hits and snapshot the totals" in {
    Http4sLiftBridgeTraffic.reset()
    Http4sLiftBridgeTraffic.observe("GET",  "/obp/v6.0.0/banks/gh.29.uk", 200)
    Http4sLiftBridgeTraffic.observe("GET",  "/obp/v6.0.0/banks/gh.29.uk", 200)
    Http4sLiftBridgeTraffic.observe("GET",  "/obp/v6.0.0/banks/some.other.bank", 200)
    Http4sLiftBridgeTraffic.observe("POST", "/auth/openid-connect/callback", 200)
    val snap = Http4sLiftBridgeTraffic.snapshot()
    snap("GET /obp/v6.0.0/banks/{id} 200") shouldBe 3L
    snap("POST /auth/openid-connect/callback 200") shouldBe 1L
    snap.size shouldBe 2
    Http4sLiftBridgeTraffic.reset()
    Http4sLiftBridgeTraffic.snapshot() shouldBe empty
  }

  it should "key separately on status — 200 and 404 don't collide" in {
    Http4sLiftBridgeTraffic.reset()
    // Same method + bucket but different statuses → two separate entries.
    // This is what surfaces test-probe 404s vs real Lift work in the audit.
    Http4sLiftBridgeTraffic.observe("DELETE", "/obp/v4.0.0/banks/gh.29.uk/accounts", 200)
    Http4sLiftBridgeTraffic.observe("DELETE", "/obp/v4.0.0/banks/gh.29.uk/accounts", 404)
    Http4sLiftBridgeTraffic.observe("DELETE", "/obp/v4.0.0/banks/de.bank.io/accounts", 404)
    val snap = Http4sLiftBridgeTraffic.snapshot()
    snap("DELETE /obp/v4.0.0/banks/{id}/accounts 200") shouldBe 1L
    snap("DELETE /obp/v4.0.0/banks/{id}/accounts 404") shouldBe 2L
    snap.size shouldBe 2
  }
}
