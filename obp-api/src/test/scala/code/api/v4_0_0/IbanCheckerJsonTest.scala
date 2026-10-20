package code.api.v4_0_0

import com.openbankproject.commons.model.{IbanChecker, IbanDetails}
import org.scalatest.{FlatSpec, Matchers}

/**
 * This class tests that the response of POST /obp/v4.0.0/account/check/scheme/iban gives each SEPA
 * attribute the value of its own field, so a bank's credit transfer support is not shown as its
 * direct debit support.
 */
class IbanCheckerJsonTest extends FlatSpec with Matchers {

  // every SEPA field has a different value, so a shifted mapping shows up
  private val details = IbanDetails("BIC1", "bank", "branch", "address", "city", "zip", "phone", "country", "DE",
    sepaCreditTransfer = "credit-transfer", sepaDirectDebit = "direct-debit", sepaSddCore = "sdd-core",
    sepaB2b = "b2b", sepaCardClearing = "card-clearing")

  "createIbanCheckerJson" should "map each SEPA attribute from its own field" in {
    val attributes = JSONFactory400.createIbanCheckerJson(IbanChecker(isValid = true, Some(details)))
      .details.toList.flatMap(_.attributes).map(a => a.name -> a.value).toMap
    attributes("sepa_credit_transfer") shouldBe "credit-transfer"
    attributes("sepa_direct_debit") shouldBe "direct-debit"
    attributes("sepa_sdd_core") shouldBe "sdd-core"
    attributes("sepa_b2b") shouldBe "b2b"
    attributes("sepa_card_clearing") shouldBe "card-clearing"
  }
}
