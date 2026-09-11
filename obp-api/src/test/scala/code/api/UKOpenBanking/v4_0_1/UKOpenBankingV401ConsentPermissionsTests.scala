package code.api.UKOpenBanking.v4_0_1

import code.api.util.Consent
import org.json4s.jvalue2extractable
import org.json4s.jvalue2monadic
import code.api.util.ErrorMessages.InvalidUKConsentPermissions
import com.openbankproject.commons.model.ErrorMessage
import org.scalatest.Tag

// The Account and Transaction API profile lists permission combinations the ASPSP "must reject
// with a 400 response code". OBP accepted all of them, so a consent could be created that no
// AISP can ever use: every AIS endpoint other than /accounts is /accounts/{AccountId}/..., and a
// consent with no account-read permission can never discover the ids it would need. Such a
// consent authorises AND returns an empty account list forever, with no error to explain it.
//
// Both the pure rule function and the HTTP surface are covered: the function is where the rules
// live, the endpoint is where the 400 has to actually come out.
class UKOpenBankingV401ConsentPermissionsTests extends UKOpenBankingV401ServerSetup {

  object UKOpenBankingV401ConsentPermissions extends Tag("UKOpenBankingV401ConsentPermissions")

  private def body(permissions: String): String =
    s"""{
       |  "Data": {
       |    "Permissions": $permissions,
       |    "ExpirationDateTime": "2030-01-01",
       |    "TransactionFromDateTime": "2020-01-01",
       |    "TransactionToDateTime": "2030-01-01"
       |  },
       |  "Risk": {}
       |}""".stripMargin

  Feature("Consent.validateUKConsentPermissions") {

    Scenario("an empty array is refused", UKOpenBankingV401ConsentPermissions) {
      Consent.validateUKConsentPermissions(Nil).isDefined should equal(true)
    }

    Scenario("a code that is not a UK permission code is refused", UKOpenBankingV401ConsentPermissions) {
      val reason = Consent.validateUKConsentPermissions(List("ReadAccountsBasic", "ReadEverything"))
      reason.isDefined should equal(true)
      reason.get should include("ReadEverything")
    }

    Scenario("an array with no account-read permission is refused", UKOpenBankingV401ConsentPermissions) {
      // The combination that motivated this work: authorises fine, then /aisp/accounts is empty
      // forever because no account is readable.
      val reason = Consent.validateUKConsentPermissions(
        List("ReadBalances", "ReadTransactionsBasic", "ReadTransactionsDebits"))
      reason.isDefined should equal(true)
      reason.get should include("ReadAccountsBasic")
    }

    Scenario("either account-read permission satisfies the requirement", UKOpenBankingV401ConsentPermissions) {
      Consent.validateUKConsentPermissions(List("ReadAccountsBasic")) should equal(None)
      Consent.validateUKConsentPermissions(List("ReadAccountsDetail")) should equal(None)
    }

    Scenario("transaction depth without a direction is refused", UKOpenBankingV401ConsentPermissions) {
      Consent.validateUKConsentPermissions(
        List("ReadAccountsBasic", "ReadTransactionsBasic")).isDefined should equal(true)
      Consent.validateUKConsentPermissions(
        List("ReadAccountsBasic", "ReadTransactionsDetail")).isDefined should equal(true)
    }

    Scenario("a transaction direction without a depth is refused", UKOpenBankingV401ConsentPermissions) {
      Consent.validateUKConsentPermissions(
        List("ReadAccountsBasic", "ReadTransactionsCredits")).isDefined should equal(true)
      Consent.validateUKConsentPermissions(
        List("ReadAccountsBasic", "ReadTransactionsDebits")).isDefined should equal(true)
    }

    Scenario("depth paired with either direction is accepted", UKOpenBankingV401ConsentPermissions) {
      Consent.validateUKConsentPermissions(
        List("ReadAccountsBasic", "ReadTransactionsBasic", "ReadTransactionsCredits")) should equal(None)
      Consent.validateUKConsentPermissions(
        List("ReadAccountsBasic", "ReadTransactionsDetail", "ReadTransactionsDebits")) should equal(None)
    }

    Scenario("requesting both Basic and Detail is allowed, not rejected as duplication",
      UKOpenBankingV401ConsentPermissions) {
      // The profile calls this duplication but forbids rejecting on that basis alone.
      Consent.validateUKConsentPermissions(
        List("ReadAccountsBasic", "ReadAccountsDetail")) should equal(None)
      Consent.validateUKConsentPermissions(List(
        "ReadAccountsBasic", "ReadAccountsDetail",
        "ReadTransactionsBasic", "ReadTransactionsDetail",
        "ReadTransactionsCredits", "ReadTransactionsDebits")) should equal(None)
    }

    Scenario("permissions unrelated to the combination rules pass alongside a valid base",
      UKOpenBankingV401ConsentPermissions) {
      Consent.validateUKConsentPermissions(
        List("ReadAccountsBasic", "ReadBalances", "ReadProducts", "ReadPAN")) should equal(None)
    }
  }

  Feature("UKOB v4.0.1 POST /aisp/account-access-consents rejects invalid Permissions") {

    Scenario("no account-read permission -> 400 with the OBP error code",
      UKOpenBankingV401ConsentPermissions) {
      val response = postAuthed(
        body("""["ReadBalances", "ReadTransactionsBasic", "ReadTransactionsDebits"]"""),
        "aisp", "account-access-consents")
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(InvalidUKConsentPermissions)
    }

    Scenario("empty Permissions array -> 400", UKOpenBankingV401ConsentPermissions) {
      postAuthed(body("[]"), "aisp", "account-access-consents").code should equal(400)
    }

    Scenario("transaction depth without a direction -> 400", UKOpenBankingV401ConsentPermissions) {
      postAuthed(
        body("""["ReadAccountsBasic", "ReadTransactionsBasic"]"""),
        "aisp", "account-access-consents").code should equal(400)
    }

    Scenario("unknown permission code -> 400", UKOpenBankingV401ConsentPermissions) {
      postAuthed(
        body("""["ReadAccountsBasic", "ReadEverything"]"""),
        "aisp", "account-access-consents").code should equal(400)
    }

    Scenario("a valid combination is still created -> 201", UKOpenBankingV401ConsentPermissions) {
      val response = postAuthed(
        body("""["ReadAccountsBasic", "ReadBalances", "ReadTransactionsBasic", "ReadTransactionsDebits"]"""),
        "aisp", "account-access-consents")
      response.code should equal(201)
      (response.body \ "Data" \ "Permissions").extract[List[String]] should equal(
        List("ReadAccountsBasic", "ReadBalances", "ReadTransactionsBasic", "ReadTransactionsDebits"))
    }
  }
}
