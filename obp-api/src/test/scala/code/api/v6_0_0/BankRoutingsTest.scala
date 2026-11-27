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

package code.api.v6_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateBank
import code.api.util.ErrorMessages._
import code.api.util.RoutingRules
import code.bankconnectors.Connector
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import code.api.v1_2_1.BankRoutingJsonV121
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import net.liftweb.util.Helpers.randomString
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Creating and updating a bank with its bank routings, and reading them back.
 *
 * A bank can have any number of bank routings: a BIC and national schemes such as BLZ or a Nigerian
 * CBN bank code, side by side. Its BIC is kept as a bank routing. Its deprecated swift_bic field is
 * never needed to find it.
 */
class BankRoutingsTest extends V600ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v6_0_0.toString)
  object BankRoutingsTag extends Tag("BankRoutings")

  // A BIC no real bank can have: ZZ is not assigned to any country. Each test gets its own.
  private def newBic(): String = ("T" + randomString(3) + "ZZ22").toUpperCase

  private def newBankId(): String = "bank." + randomString(8).toLowerCase

  private def body(bankId: String, routings: List[BankRoutingJsonV121]): String =
    write(PostBankJson600(
      bank_id = bankId,
      bank_code = "rtest",
      full_name = Some("Bank Routings Test"),
      logo = Some("https://example.com/logo.png"),
      website = Some("https://example.com"),
      bank_routings = Some(routings)))

  // Runs `f` while user1 may create banks, and takes the role away afterwards.
  private def asBankCreator[T](f: => T): T = {
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateBank.toString)
    try f finally Entitlement.entitlement.vend.deleteEntitlement(entitlement)
  }

  private def createBank(bankId: String, routings: List[BankRoutingJsonV121]) =
    makePostRequest((v6_0_0_Request / "banks").POST <@ (user1), body(bankId, routings))

  // Update Bank is defined in v5.0.0 and is called through v5.1.0.
  // Its body is PostBankJson500, which names the bank `id`.
  private def updateBank(bankId: String, routings: List[BankRoutingJsonV121]) =
    makePutRequest((v5_1_0_Request / "banks").PUT <@ (user1), write(code.api.v5_0_0.PostBankJson500(
      id = Some(bankId),
      bank_code = "rtest",
      full_name = Some("Bank Routings Test"),
      logo = Some("https://example.com/logo.png"),
      website = Some("https://example.com"),
      bank_routings = Some(routings))))

  private def routingsIn(json: JValue): List[BankRoutingJsonV121] =
    (json \ "bank_routings").extract[List[BankRoutingJsonV121]]

  private def readBank(bankId: String): List[BankRoutingJsonV121] = {
    val response = makeGetRequest((v6_0_0_Request / "banks" / bankId).GET)
    response.code should equal(200)
    routingsIn(response.body)
  }

  private def findBankId(scheme: String, address: String) = {
    val banks = Await.result(Connector.connector.vend.getBanks(None), 20.seconds).map(_._1).openOrThrowException("getBanks failed")
    RoutingRules.findBank(banks, scheme, address).map(_.bankId.value)
  }

  feature(s"Creating a bank with bank routings - $VersionOfApi") {

    scenario("With a BIC: it is shown once, read back once, and finds the bank", VersionOfApi, BankRoutingsTag) {
      val bankId = newBankId(); val bic = newBic()
      val response = asBankCreator(createBank(bankId, List(BankRoutingJsonV121("BIC", bic))))
      response.code should equal(201)
      routingsIn(response.body).filter(_.scheme == "BIC") should equal(List(BankRoutingJsonV121("BIC", bic)))
      readBank(bankId).filter(_.scheme == "BIC") should equal(List(BankRoutingJsonV121("BIC", bic)))
      findBankId("BIC", bic) should equal(Full(bankId))
    }

    scenario("With a BIC and two national schemes: all are kept, and each finds the bank", VersionOfApi, BankRoutingsTag) {
      val bankId = newBankId(); val bic = newBic()
      val routings = List(
        BankRoutingJsonV121("BIC", bic),
        BankRoutingJsonV121("BLZ", randomString(8)),
        BankRoutingJsonV121("CBN_BANK_CODE", randomString(6)))
      val response = asBankCreator(createBank(bankId, routings))
      response.code should equal(201)
      routingsIn(response.body) should equal(BankRoutingJsonV121("OBP", bankId) :: routings)
      readBank(bankId) should equal(BankRoutingJsonV121("OBP", bankId) :: routings)
      routings.foreach { r =>
        withClue(s"routing ${r.scheme} ${r.address}: ") { findBankId(r.scheme, r.address) should equal(Full(bankId)) }
      }
    }

    scenario("With no bank routings: only the OBP entry is shown, and no empty BIC", VersionOfApi, BankRoutingsTag) {
      val bankId = newBankId()
      val response = asBankCreator(createBank(bankId, Nil))
      response.code should equal(201)
      routingsIn(response.body) should equal(List(BankRoutingJsonV121("OBP", bankId)))
      readBank(bankId) should equal(List(BankRoutingJsonV121("OBP", bankId)))
    }

    scenario("With the BIC scheme written in lower case: it is kept as BIC", VersionOfApi, BankRoutingsTag) {
      val bankId = newBankId(); val bic = newBic()
      val response = asBankCreator(createBank(bankId, List(BankRoutingJsonV121("bic", bic))))
      response.code should equal(201)
      readBank(bankId).filter(_.scheme == "BIC") should equal(List(BankRoutingJsonV121("BIC", bic)))
    }

    scenario("With the same BIC twice: it is kept once", VersionOfApi, BankRoutingsTag) {
      val bankId = newBankId(); val bic = newBic()
      val response = asBankCreator(createBank(bankId, List(BankRoutingJsonV121("BIC", bic), BankRoutingJsonV121("BIC", bic))))
      response.code should equal(201)
      readBank(bankId).filter(_.scheme == "BIC") should equal(List(BankRoutingJsonV121("BIC", bic)))
    }

    scenario("With two different BICs: refused with 400", VersionOfApi, BankRoutingsTag) {
      val bankId = newBankId()
      val response = asBankCreator(createBank(bankId, List(BankRoutingJsonV121("BIC", newBic()), BankRoutingJsonV121("BIC", newBic()))))
      response.code should equal(400)
      response.body.extract[ErrorMessage].message should include("BIC")
    }

    scenario("With a BIC another bank already has: refused with 409", VersionOfApi, BankRoutingsTag) {
      val bic = newBic()
      asBankCreator(createBank(newBankId(), List(BankRoutingJsonV121("BIC", bic)))).code should equal(201)
      val response = asBankCreator(createBank(newBankId(), List(BankRoutingJsonV121("BIC", bic))))
      response.code should equal(409)
      response.body.extract[ErrorMessage].message should startWith(BankRoutingAlreadyExists.split(":").head)
    }
  }

  feature(s"Updating a bank's bank routings - $VersionOfApi") {

    scenario("Adding a BIC to a bank that had none", VersionOfApi, BankRoutingsTag) {
      val bankId = newBankId(); val bic = newBic()
      asBankCreator(createBank(bankId, Nil)).code should equal(201)
      val response = asBankCreator(updateBank(bankId, List(BankRoutingJsonV121("BIC", bic))))
      response.code should equal(200)
      readBank(bankId).filter(_.scheme == "BIC") should equal(List(BankRoutingJsonV121("BIC", bic)))
      findBankId("BIC", bic) should equal(Full(bankId))
    }

    scenario("Replacing a bank's BIC: the old BIC no longer finds it", VersionOfApi, BankRoutingsTag) {
      val bankId = newBankId(); val oldBic = newBic(); val newBicValue = newBic()
      asBankCreator(createBank(bankId, List(BankRoutingJsonV121("BIC", oldBic)))).code should equal(201)
      asBankCreator(updateBank(bankId, List(BankRoutingJsonV121("BIC", newBicValue)))).code should equal(200)
      readBank(bankId).filter(_.scheme == "BIC") should equal(List(BankRoutingJsonV121("BIC", newBicValue)))
      findBankId("BIC", newBicValue) should equal(Full(bankId))
      findBankId("BIC", oldBic).isDefined should equal(false)
    }

    scenario("Giving a bank a BIC another bank already has: refused with 409", VersionOfApi, BankRoutingsTag) {
      val bic = newBic(); val bankId = newBankId()
      asBankCreator(createBank(newBankId(), List(BankRoutingJsonV121("BIC", bic)))).code should equal(201)
      asBankCreator(createBank(bankId, Nil)).code should equal(201)
      val response = asBankCreator(updateBank(bankId, List(BankRoutingJsonV121("BIC", bic))))
      response.code should equal(409)
      response.body.extract[ErrorMessage].message should startWith(BankRoutingAlreadyExists.split(":").head)
    }
  }
}
