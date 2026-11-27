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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, RoutingRules}
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.bankconnectors.Connector
import code.entitlement.Entitlement
import code.model.dataAccess.MappedBankAccount
import code.setup.DefaultUsers
import com.openbankproject.commons.model.{AccountRoutingJsonV121, AmountOfMoneyJsonV121}
import net.liftweb.common.Full
import net.liftweb.mapper.By
import org.json4s._
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * One account, read through every API version, shows one account number.
 *
 * Each version shows the number in its `number` field and, where it has them, in its account
 * routings. Versions before v6.0.0 show the routings as they were stored: a number the caller gave
 * keeps the caller's spelling of the scheme (ACCOUNT_NUMBER, AccountNumber or NUMBER), and a number
 * the bank assigned is not shown as a routing. From v6.0.0, every account shows exactly one
 * ACCOUNT_NUMBER routing. In every version, every value shown is the same number.
 */
class AccountNumberAcrossVersionsTest extends V600ServerSetup with DefaultUsers {

  object AcrossVersions extends Tag("AccountNumberAcrossVersions")

  private val versionsBeforeV600 = List("v1.2.1", "v1.3.0", "v1.4.0", "v2.0.0", "v2.1.0", "v2.2.0", "v3.0.0", "v3.1.0", "v4.0.0", "v5.0.0", "v5.1.0")
  private val versionsFromV600 = List("v6.0.0", "v7.0.0")
  // Get Account by Id (Core) exists from v2.0.0.
  private val coreVersions = (versionsBeforeV600 ++ versionsFromV600).filterNot(v => Set("v1.2.1", "v1.3.0", "v1.4.0").contains(v))

  private def newNumber(): String = "8" + (1 to 9).map(_ => scala.util.Random.nextInt(10)).mkString

  // Creates an account through v4.0.0 as user1, who becomes its holder and owner.
  private def createAccount(routings: List[AccountRoutingJsonV121]): String = {
    val canCreate = Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canCreateAccount.toString)
    val response = try {
      makePostRequest((v4_0_0_Request / "banks" / testBankId1.value / "accounts").POST <@ (user1), write(
        SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(
          user_id = resourceUser1.userId,
          balance = AmountOfMoneyJsonV121("EUR", "0"),
          account_routings = routings)))
    } finally Entitlement.entitlement.vend.deleteEntitlement(canCreate)
    response.code should equal(201)
    response.body.extract[CreateAccountResponseJsonV310].account_id
  }

  private def numberFieldOf(accountId: String): String =
    MappedBankAccount.find(By(MappedBankAccount.bank, testBankId1.value), By(MappedBankAccount.theAccountId, accountId))
      .map(_.accountNumber.get).openOr("")

  /** What one response shows: its number field, and its account number routings. */
  case class Shown(where: String, numberField: Option[String], numberRoutings: List[AccountRoutingJsonV121])

  private def shownIn(where: String, json: JValue): Shown = {
    val numberField = (json \ "number") match {
      case JString(value) => Some(value)
      case _ => None
    }
    val routings = (json \ "account_routings") match {
      case JArray(items) => items.map(_.extract[AccountRoutingJsonV121])
      case _ => Nil
    }
    Shown(where, numberField, routings.filter(r => RoutingRules.isAccountNumberScheme(r.scheme)))
  }

  private def read(version: String, path: List[String]): Shown = {
    val request = path.foldLeft(baseRequest / "obp" / version)(_ / _).GET <@ (user1)
    val response = makeGetRequest(request)
    withClue(s"GET /obp/$version/${path.mkString("/")}: ") { response.code should equal(200) }
    shownIn(s"$version /${path.mkString("/")}", response.body)
  }

  private def everyResponse(accountId: String): List[Shown] = {
    val full = (versionsBeforeV600 ++ versionsFromV600).map(v => read(v, List("banks", testBankId1.value, "accounts", accountId, "owner", "account")))
    val core = coreVersions.map(v => read(v, List("my", "banks", testBankId1.value, "accounts", accountId, "account")))
    full ++ core
  }

  private def isFromV600(shown: Shown) = versionsFromV600.exists(v => shown.where.startsWith(v + " "))

  /**
   * The checks that hold for every account: each version shows the same number everywhere, from
   * v6.0.0 there is exactly one ACCOUNT_NUMBER routing, and versions before v6.0.0 show exactly
   * `routingsBeforeV600`.
   */
  private def checkEveryVersion(accountId: String, number: String, routingsBeforeV600: List[AccountRoutingJsonV121]): Unit = {
    val responses = everyResponse(accountId)
    responses.foreach { shown =>
      withClue(s"${shown.where}: ") {
        shown.numberField.foreach(_ should equal(number))
        shown.numberRoutings.foreach(_.address should equal(number))
        if (isFromV600(shown)) shown.numberRoutings should equal(List(AccountRoutingJsonV121("ACCOUNT_NUMBER", number)))
        else if (shown.numberRoutings.nonEmpty || routingsBeforeV600.isEmpty) shown.numberRoutings should equal(routingsBeforeV600)
      }
    }
    withClue("at least one response shows the number field: ") { responses.exists(_.numberField.isDefined) should equal(true) }
    withClue("at least one version before v6.0.0 shows account routings: ") {
      if (routingsBeforeV600.nonEmpty) responses.filterNot(isFromV600).exists(_.numberRoutings.nonEmpty) should equal(true)
    }
  }

  private def checkEveryLookup(accountId: String, number: String): Unit =
    List("ACCOUNT_NUMBER", "AccountNumber", "accountNumber", "ACCOUNT_NO", "NUMBER", "number").foreach { scheme =>
      withClue(s"lookup by $scheme: ") {
        Connector.connector.vend.getBankAccountByRoutingLegacy(Some(testBankId1), scheme, number, None)
          .map(_._1.accountId.value) should equal(Full(accountId))
      }
    }

  feature("One account number, the same in every version") {

    scenario("A number the bank assigned", AcrossVersions) {
      val accountId = createAccount(Nil)
      val number = numberFieldOf(accountId)
      number should fullyMatch regex "[0-9]{10}"
      checkEveryVersion(accountId, number, routingsBeforeV600 = Nil)
      checkEveryLookup(accountId, number)
    }

    List("ACCOUNT_NUMBER", "AccountNumber", "NUMBER").foreach { spelling =>
      scenario(s"A number given as $spelling on a sandbox", AcrossVersions) {
        setPropsValues("allow_sandbox_account_creation" -> "true")
        val number = newNumber()
        val accountId = createAccount(List(AccountRoutingJsonV121(spelling, number)))
        numberFieldOf(accountId) should equal(number)
        checkEveryVersion(accountId, number, routingsBeforeV600 = List(AccountRoutingJsonV121(spelling, number)))
        checkEveryLookup(accountId, number)
      }
    }
  }
}
