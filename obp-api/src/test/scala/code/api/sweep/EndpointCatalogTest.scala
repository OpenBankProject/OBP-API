package code.api.sweep

import code.setup.ServerSetupWithTestData
import org.scalatest.Tag

/**
 * EndpointCatalog.isPlaceholder decides which ALL_CAPS URL segments the sweeps substitute with a
 * concrete value before calling an endpoint. Getting this wrong in either direction breaks a
 * sweep's verdict: substituting a real literal sends a value the endpoint does not recognise, and
 * leaving a real placeholder unsubstituted does the same thing from the other side -- the sweep
 * calls a well-formed request that was never going to work, and reports the resulting 4xx/5xx as
 * the endpoint's own defect.
 *
 * This pins the second failure mode for the three segments EndpointCatalog's own comment named as
 * a known, accepted gap: PAYMENT_SERVICE, PAYMENT_PRODUCT and SCA_METHOD are enumerated values a
 * live endpoint validates inline, not literals -- Http4sBGv2PIS's payment-service branches guard
 * on `Set("payments", "bulk-payments", "periodic-payments").contains(paymentService)`, and
 * Http4s310's auth-context-updates branch guards on
 * `List(StrongCustomerAuthentication.SMS, EMAIL).contains(scaMethod)`. Left as the literal
 * strings "PAYMENT_SERVICE"/"SCA_METHOD", both guards fail and the sweep calls a URL that was
 * never going to route anywhere -- exactly the class of self-inflicted failure EndpointCatalog's
 * own docstring says the ID/_CODE/_NAME heuristic exists to avoid.
 */
class EndpointCatalogTest extends ServerSetupWithTestData {

  object EndpointCatalogPlaceholders extends Tag("EndpointCatalogPlaceholders")

  feature("EndpointCatalog substitutes every real path placeholder, not just the ones ending in ID/_CODE/_NAME") {

    scenario("PAYMENT_SERVICE is substituted with a value the endpoint's own guard would accept",
             EndpointCatalogPlaceholders) {
      // No ResourceDoc in the current EndpointCatalog carries a PAYMENT_SERVICE segment --
      // Berlin Group's route trees are not aggregated into Http4s700.allResourceDocs (only the
      // OBP v1.2.1..v7.0.0 lineage is), so this exercises concretePath/isPlaceholder directly via
      // .copy on a real doc rather than filtering the live catalog for one that is not there.
      // That pins the behaviour EndpointCatalog must have the day Berlin Group docs do join the
      // catalog, instead of waiting to notice the gap then.
      val doc = EndpointCatalog.all.head.copy(
        requestUrl = "/PAYMENT_SERVICE/PAYMENT_PRODUCT/PAYMENT_ID/status")
      val path = EndpointCatalog.concretePath(doc)
      withClue(s"($path) left PAYMENT_SERVICE unsubstituted -- Http4sBGv2PIS's payment-status " +
               s"branch guards on Set(\"payments\",\"bulk-payments\",\"periodic-payments\")" +
               s".contains(paymentService), so this literal fails it and the sweep would " +
               s"misreport a working endpoint as broken: ") {
        path should not include "PAYMENT_SERVICE"
      }
    }

    scenario("SCA_METHOD is substituted with a value the endpoint's own guard accepts",
             EndpointCatalogPlaceholders) {
      val docs = EndpointCatalog.all.filter(_.requestUrl.contains("SCA_METHOD"))
      withClue("no ResourceDoc in the catalog carries a SCA_METHOD segment any more -- this " +
               "test's premise no longer holds against the current catalog, update it: ") {
        docs should not be empty
      }
      docs.foreach { doc =>
        val path = EndpointCatalog.concretePath(doc)
        withClue(s"${doc.operationId} ($path) left SCA_METHOD unsubstituted -- the endpoint " +
                 s"only accepts SMS/EMAIL/IMPLICIT, so this literal fails validation and the " +
                 s"sweep misreports a working endpoint as broken: ") {
          path should not include "SCA_METHOD"
        }
      }
    }

    scenario("literal ALL_CAPS segments that are not placeholders stay untouched",
             EndpointCatalogPlaceholders) {
      // The fix must not turn every unrecognised ALL_CAPS segment into a placeholder -- only the
      // three named above. CARDANO/MOBILE_WALLET/ETH_SEND_TRANSACTION are genuine literals this
      // catalog must keep sending verbatim.
      val literalSegments = List("CARDANO", "MOBILE_WALLET", "ETH_SEND_TRANSACTION")
      val docs = EndpointCatalog.all.filter(doc => literalSegments.exists(doc.requestUrl.contains))
      withClue("no ResourceDoc in the catalog carries any of CARDANO/MOBILE_WALLET/" +
               "ETH_SEND_TRANSACTION any more -- this test's premise no longer holds, update it: ") {
        docs should not be empty
      }
      docs.foreach { doc =>
        val path = EndpointCatalog.concretePath(doc)
        val literalInDoc = literalSegments.find(doc.requestUrl.contains).get
        withClue(s"${doc.operationId} substituted over the literal $literalInDoc, which routes " +
                 s"nowhere -- these are not placeholders: ") {
          path should include(literalInDoc)
        }
      }
    }
  }
}
