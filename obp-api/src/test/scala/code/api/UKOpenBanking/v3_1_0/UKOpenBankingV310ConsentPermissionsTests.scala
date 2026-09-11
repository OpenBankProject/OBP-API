package code.api.UKOpenBanking.v3_1_0

import code.api.util.ErrorMessages.InvalidUKConsentPermissions
import org.json4s.jvalue2extractable
import org.json4s.jvalue2monadic
import com.openbankproject.commons.model.ErrorMessage
import org.scalatest.Tag

// v3.1 lodges account-access consents through its own handler, so the permission-combination rules
// have to be asserted here as well as on v4.0.1 -- the rule function being shared is not by itself
// evidence that this endpoint calls it. The rules themselves, and why an unusable consent is worth
// refusing outright, are covered in UKOpenBankingV401ConsentPermissionsTests.
class UKOpenBankingV310ConsentPermissionsTests extends UKOpenBankingV310ServerSetup {

  object UKOpenBankingV310ConsentPermissions extends Tag("UKOpenBankingV310ConsentPermissions")

  // v3.1's ConsentPostBodyUKV310 types Risk as a String, not an object.
  private def body(permissions: String): String =
    s"""{
       |  "Data": {
       |    "Permissions": $permissions,
       |    "ExpirationDateTime": "2030-01-01",
       |    "TransactionFromDateTime": "2020-01-01",
       |    "TransactionToDateTime": "2030-01-01"
       |  },
       |  "Risk": ""
       |}""".stripMargin

  Feature("UKOB v3.1 POST /account-access-consents rejects invalid Permissions") {

    Scenario("no account-read permission -> 400 with the OBP error code",
      UKOpenBankingV310ConsentPermissions) {
      val response = postAuthed(
        body("""["ReadBalances", "ReadTransactionsBasic", "ReadTransactionsDebits"]"""),
        "account-access-consents")
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(InvalidUKConsentPermissions)
    }

    Scenario("empty Permissions array -> 400", UKOpenBankingV310ConsentPermissions) {
      postAuthed(body("[]"), "account-access-consents").code should equal(400)
    }

    Scenario("transaction depth without a direction -> 400", UKOpenBankingV310ConsentPermissions) {
      postAuthed(
        body("""["ReadAccountsBasic", "ReadTransactionsBasic"]"""),
        "account-access-consents").code should equal(400)
    }

    Scenario("a transaction direction without a depth -> 400", UKOpenBankingV310ConsentPermissions) {
      postAuthed(
        body("""["ReadAccountsBasic", "ReadTransactionsCredits"]"""),
        "account-access-consents").code should equal(400)
    }

    Scenario("unknown permission code -> 400", UKOpenBankingV310ConsentPermissions) {
      postAuthed(
        body("""["ReadAccountsBasic", "ReadEverything"]"""),
        "account-access-consents").code should equal(400)
    }

    Scenario("a valid combination is still created -> 201", UKOpenBankingV310ConsentPermissions) {
      val response = postAuthed(
        body("""["ReadAccountsBasic", "ReadBalances", "ReadTransactionsBasic", "ReadTransactionsCredits"]"""),
        "account-access-consents")
      response.code should equal(201)
      (response.body \ "Data" \ "Permissions").extract[List[String]] should equal(
        List("ReadAccountsBasic", "ReadBalances", "ReadTransactionsBasic", "ReadTransactionsCredits"))
    }
  }
}
