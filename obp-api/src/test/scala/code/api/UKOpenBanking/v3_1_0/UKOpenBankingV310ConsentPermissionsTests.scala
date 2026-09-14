/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.UKOpenBanking.v3_1_0

import code.api.util.ErrorMessages.InvalidUKConsentPermissions
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

  feature("UKOB v3.1 POST /account-access-consents rejects invalid Permissions") {

    scenario("no account-read permission -> 400 with the OBP error code",
      UKOpenBankingV310ConsentPermissions) {
      val response = postAuthed(
        body("""["ReadBalances", "ReadTransactionsBasic", "ReadTransactionsDebits"]"""),
        "account-access-consents")
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should startWith(InvalidUKConsentPermissions)
    }

    scenario("empty Permissions array -> 400", UKOpenBankingV310ConsentPermissions) {
      postAuthed(body("[]"), "account-access-consents").code should equal(400)
    }

    scenario("transaction depth without a direction -> 400", UKOpenBankingV310ConsentPermissions) {
      postAuthed(
        body("""["ReadAccountsBasic", "ReadTransactionsBasic"]"""),
        "account-access-consents").code should equal(400)
    }

    scenario("a transaction direction without a depth -> 400", UKOpenBankingV310ConsentPermissions) {
      postAuthed(
        body("""["ReadAccountsBasic", "ReadTransactionsCredits"]"""),
        "account-access-consents").code should equal(400)
    }

    scenario("unknown permission code -> 400", UKOpenBankingV310ConsentPermissions) {
      postAuthed(
        body("""["ReadAccountsBasic", "ReadEverything"]"""),
        "account-access-consents").code should equal(400)
    }

    scenario("a valid combination is still created -> 201", UKOpenBankingV310ConsentPermissions) {
      val response = postAuthed(
        body("""["ReadAccountsBasic", "ReadBalances", "ReadTransactionsBasic", "ReadTransactionsCredits"]"""),
        "account-access-consents")
      response.code should equal(201)
      (response.body \ "Data" \ "Permissions").extract[List[String]] should equal(
        List("ReadAccountsBasic", "ReadBalances", "ReadTransactionsBasic", "ReadTransactionsCredits"))
    }
  }
}
