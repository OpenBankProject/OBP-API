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
package code.api.v4_0_0

import code.api.Constant
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.extractErrorMessageCode
import code.api.util.ApiRole
import code.api.util.ErrorMessages.CouldNotDeleteCascade
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.entitlement.Entitlement
import code.methodrouting.{MethodRouting, MethodRoutingCommons, MethodRoutingProvider}
import code.model.dataAccess.{MappedBank, MappedBankAccount}
import code.views.system.AccountAccess
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, AmountOfMoneyJsonV121, BankId, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier
import org.json4s.jvalue2extractable
import org.json4s.native.Serialization.write
import org.scalatest.Tag

/**
 * A cascading delete either removes everything it covers or nothing: one that fails part way must leave every row
 * it had already deleted in place.
 */
class CascadeDeleteAtomicityTest extends V400ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object DeleteAccountCascade extends Tag(nameOf(Implementations4_0_0.deleteAccountCascade))
  object DeleteBankCascade extends Tag(nameOf(Implementations4_0_0.deleteBankCascade))

  private def createAccountWithSecondViewer(bankId: String): String = {
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateAccount.toString)
    val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310
      .copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR", "0"))
    val response = makePostRequest((v4_0_0_Request / "banks" / bankId / "accounts").POST <@ (user1), write(addAccountJson))
    response.code should equal(201)
    val accountId = response.body.extract[CreateAccountResponseJsonV310].account_id
    grantUserAccessToViewViaEndpoint(bankId, accountId, resourceUser2.userId, user1,
      PostViewJsonV400(view_id = Constant.SYSTEM_OWNER_VIEW_ID, is_system = true))
    accountId
  }

  private def accountExists(bankId: String, accountId: String): Boolean =
    MappedBankAccount.find(bankId, accountId).isDefined

  private def accessRows(bankId: String, accountId: String): Int =
    AccountAccess.findAllByBankIdAccountId(BankId(bankId), AccountId(accountId)).size

  /** The account cascade lists the account's transactions through the connector; one that cannot fails the step. */
  private def withTransactionsUnreadable[T](block: => T): T = {
    val routing = MethodRoutingProvider.connectorMethodProvider.vend.createOrUpdate(MethodRoutingCommons(
      "getTransactionsLegacy", "cardano_vJun2025", isBankIdExactMatch = false, bankIdPattern = Some(MethodRouting.bankIdPatternMatchAny)
    )).openOrThrowException("expected the method routing to be created")
    try block
    finally MethodRoutingProvider.connectorMethodProvider.vend.delete(routing.methodRoutingId.get)
  }

  private def runSql(sql: String): Unit =
    DB.use(DefaultConnectionIdentifier) { conn => DB.prepareStatement(sql, conn)(_.executeUpdate()) }

  feature("A cascading delete that fails part way deletes nothing") {

    scenario("An account cascade whose transactions cannot be read keeps the account and its access", DeleteAccountCascade, VersionOfApi) {
      val bankId = randomBankId
      val accountId = createAccountWithSecondViewer(bankId)
      val accessBefore = accessRows(bankId, accountId)
      accessBefore should be >= 2
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)

      val response = withTransactionsUnreadable {
        makeDeleteRequest((v4_0_0_Request / "management" / "cascading" / "banks" / bankId / "accounts" / accountId).DELETE <@ (user1))
      }

      response.code should equal(400)
      response.body.extract[ErrorMessage].message should include(extractErrorMessageCode(CouldNotDeleteCascade))
      accountExists(bankId, accountId) should equal(true)
      accessRows(bankId, accountId) should equal(accessBefore)
    }

    scenario("A bank cascade whose account cannot be deleted keeps the bank and the account", DeleteBankCascade, VersionOfApi) {
      val bankId = createBank(code.api.util.APIUtil.generateUUID()).bankId.value
      val accountId = createAccountWithSecondViewer(bankId)
      val accessBefore = accessRows(bankId, accountId)
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteBankCascade.toString)

      val response = withTransactionsUnreadable {
        makeDeleteRequest((v4_0_0_Request / "management" / "cascading" / "banks" / bankId).DELETE <@ (user1))
      }

      response.code should equal(400)
      MappedBank.findByBankId(BankId(bankId)).isDefined should equal(true)
      accountExists(bankId, accountId) should equal(true)
      accessRows(bankId, accountId) should equal(accessBefore)
    }

    scenario("An account cascade that a database error stops keeps everything it had deleted", DeleteAccountCascade, VersionOfApi) {
      val bankId = randomBankId
      val accountId = createAccountWithSecondViewer(bankId)
      val accessBefore = accessRows(bankId, accountId)
      val accountPk = MappedBankAccount.find(bankId, accountId)
        .openOrThrowException("expected the account").accountPrimaryKey
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      // A row that still refers to the account, so deleting the account itself, the last step, fails. On H2 (and
      // MySQL, SQL Server, Oracle) the deletes before it stay in the transaction; Postgres aborts the whole one.
      val table = "mappedbankaccount"
      runSql(s"CREATE TABLE cascade_atomicity_blocker (account_pk BIGINT REFERENCES $table(id))")
      try {
        runSql(s"INSERT INTO cascade_atomicity_blocker (account_pk) VALUES ($accountPk)")

        val response = makeDeleteRequest(
          (v4_0_0_Request / "management" / "cascading" / "banks" / bankId / "accounts" / accountId).DELETE <@ (user1))

        response.code should not equal 200
        accountExists(bankId, accountId) should equal(true)
        accessRows(bankId, accountId) should equal(accessBefore)
      } finally runSql("DROP TABLE cascade_atomicity_blocker")
    }
  }
}
