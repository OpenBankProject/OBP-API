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

import org.json4s._
import code.accountholders.MapperAccountHolders
import code.api.Constant
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createViewJsonV300
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.util.ApiRole.CanDeleteAccountCascade
import code.api.util.ErrorMessages.{UserHasMissingRoles, AuthenticatedUserIsRequired}
import code.api.v3_1_0.CreateAccountResponseJsonV310
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.cards.MappedPhysicalCard
import code.consent.Consents
import code.entitlement.{Entitlement, MappedEntitlement}
import code.model.dataAccess.MappedBankAccount
import code.transactionChallenge.MappedExpectedChallengeAnswer
import code.views.system.AccountAccess
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, AmountOfMoneyJsonV121, BankId, ErrorMessage}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.randomString
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.util.concurrent.TimeUnit

class DeleteAccountCascadeTest extends V400ServerSetup {

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * mvn test -D tagsToInclude
    *
    * This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.deleteAccountCascade))

  lazy val bankId = randomBankId
  lazy val bankAccount = randomPrivateAccountViaEndpoint(bankId)
  lazy val addAccountJson = SwaggerDefinitionsJSON.createAccountRequestJsonV310.copy(user_id = resourceUser1.userId, balance = AmountOfMoneyJsonV121("EUR","0"))


  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId / 
        "accounts" / bankAccount.id).DELETE
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(AuthenticatedUserIsRequired)
    }
  }
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "accounts" / bankAccount.id).DELETE <@(user1)
      val response400 = makeDeleteRequest(request400)
      Then("We should get a 403")
      response400.code should equal(403)
      val errorMessage = response400.body.extract[ErrorMessage].message
      errorMessage contains (UserHasMissingRoles) should be (true)
      errorMessage contains (CanDeleteAccountCascade.toString()) should be (true)
    }
  }
  feature(s"test $ApiEndpoint1 - Authorized access") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We grant the role")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canCreateAccount.toString)
      And("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "accounts" ).POST <@(user1)
      val response400 = makePostRequest(request400, write(addAccountJson))
      Then("We should get a 201")
      response400.code should equal(201)
      val account = response400.body.extract[CreateAccountResponseJsonV310]
      account.account_id should not be empty

      val postBodyView = createViewJsonV300.copy(name = "_cascade_delete", metadata_view = "_cascade_delete", is_public = false).toCreateViewJson
      createViewViaEndpoint(bankId, account.account_id, postBodyView, user1)
      
      createAccountAttributeViaEndpoint(
        bankId,
        account.account_id,
        "REQUIRED_CHALLENGE_ANSWERS",
        "2",
        "INTEGER",
        Some("LKJL98769F")
      )

      grantUserAccessToViewViaEndpoint(
        bankId,
        account.account_id,
        resourceUser2.userId,
        user1,
        PostViewJsonV400(view_id = Constant.SYSTEM_OWNER_VIEW_ID, is_system = true)
      )

      createWebhookViaEndpoint(
        bankId,
        account.account_id,
        resourceUser1.userId,
        user1
      )

      When("We grant the role")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      And("We make a delete cascade request v4.0.0")
      val deleteRequest400 = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "accounts" / account.account_id).DELETE <@(user1)
      val deleteResponse400 = makeDeleteRequest(deleteRequest400)
      Then("We should get a 200")
      deleteResponse400.code should equal(200)

      When("We try to delete one more time")
      makeDeleteRequest(request400).code should equal(404)
    }
    scenario("We delete an account that has paid by transaction requests", ApiEndpoint1, VersionOfApi) {
      val paymentsBankId = createBank(APIUtil.generateUUID()).bankId.value
      val (fromAccountId, toAccountId, transactionRequestIds) = createPaymentsForDeleteCascade(paymentsBankId)
      doubleEntryBookTransactionsOfAccount(paymentsBankId, fromAccountId) should not be empty
      val (transactionRequestsBefore, challengesBefore) = transactionRequestsAndChallenges(transactionRequestIds)
      transactionRequestsBefore.size should equal(2)
      challengesBefore should not be empty

      When("We grant the role")
      Entitlement.entitlement.vend.addEntitlement(paymentsBankId, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      And("We make a delete cascade request v4.0.0")
      val deleteRequest400 = (v4_0_0_Request / "management" / "cascading" / "banks" / paymentsBankId /
        "accounts" / fromAccountId).DELETE <@(user1)
      makeDeleteRequest(deleteRequest400).code should equal(200)

      Then("The double-entry book rows, transaction requests and challenges of the account are deleted")
      doubleEntryBookTransactionsOfAccount(paymentsBankId, fromAccountId) shouldBe empty
      val (transactionRequestsAfter, challengesAfter) = transactionRequestsAndChallenges(transactionRequestIds)
      transactionRequestsAfter shouldBe empty
      challengesAfter shouldBe empty
      And("The double-entry book row is gone for the receiving account as well, since it was one row for both sides")
      doubleEntryBookTransactionsOfAccount(paymentsBankId, toAccountId) shouldBe empty
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - cards") {
    scenario("Deleting an account keeps the cards of an account with the same ACCOUNT_ID at another bank", ApiEndpoint1, VersionOfApi) {
      Given("Two banks that each have an account with the same ACCOUNT_ID, each with a card")
      val bankId = BankId(randomString(10))
      val otherBankId = BankId(randomString(10))
      createBank(bankId.value)
      createBank(otherBankId.value)
      val accountId = AccountId(randomString(10))
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")
      createAccountRelevantResource(Some(resourceUser1), otherBankId, accountId, "EUR")

      def addCard(bank: BankId): String = {
        val account = MappedBankAccount.find(
          By(MappedBankAccount.bank, bank.value),
          By(MappedBankAccount.theAccountId, accountId.value)
        ).openOrThrowException("test account not found")
        MappedPhysicalCard.create
          .mBankId(bank.value)
          .mAccount(account.id.get)
          .mBankCardNumber(randomString(16))
          .mSerialNumber(randomString(10))
          .saveMe()
          .mCardId.get
      }
      def cardExists(cardId: String): Boolean = MappedPhysicalCard.find(By(MappedPhysicalCard.mCardId, cardId)).isDefined
      val card = addCard(bankId)
      val otherBankCard = addCard(otherBankId)

      When("We delete the account at the first bank with the cascading endpoint")
      Entitlement.entitlement.vend.addEntitlement(bankId.value, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      val request = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId.value /
        "accounts" / accountId.value).DELETE <@ (user1)
      val response = makeDeleteRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("The deleted account's card is gone and the other bank's card is kept")
      cardExists(card) should equal(false)
      cardExists(otherBankCard) should equal(true)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - entitlements") {
    scenario("Deleting an account keeps the entitlements of users who only use an account with the same ACCOUNT_ID at another bank", ApiEndpoint1, VersionOfApi) {
      Given("Two banks that each have an account with the same ACCOUNT_ID, owned by different users")
      val bankId = BankId(randomString(10))
      val otherBankId = BankId(randomString(10))
      createBank(bankId.value)
      createBank(otherBankId.value)
      val accountId = AccountId(randomString(10))
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")
      createAccountRelevantResource(Some(resourceUser2), otherBankId, accountId, "EUR")

      And("The other bank's account owner has an entitlement at the first bank")
      Entitlement.entitlement.vend.addEntitlement(bankId.value, resourceUser2.userId, ApiRole.canCreateAccount.toString)
      def otherOwnerEntitlements: Long = MappedEntitlement.count(
        By(MappedEntitlement.mBankId, bankId.value),
        By(MappedEntitlement.mUserId, resourceUser2.userId)
      )
      otherOwnerEntitlements should equal(1L)

      When("We delete the account at the first bank with the cascading endpoint")
      Entitlement.entitlement.vend.addEntitlement(bankId.value, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      val request = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId.value /
        "accounts" / accountId.value).DELETE <@ (user1)
      val response = makeDeleteRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("The other bank's account owner keeps the entitlement")
      otherOwnerEntitlements should equal(1L)
    }
  }

  private def accountHolders(bankId: String, accountId: String): List[MapperAccountHolders] =
    MapperAccountHolders.findAll(
      By(MapperAccountHolders.accountBankPermalink, bankId),
      By(MapperAccountHolders.accountPermalink, accountId)
    )

  private def accountAccess(bankId: String, accountId: String): List[AccountAccess] =
    AccountAccess.findAll(
      By(AccountAccess.bank_id, bankId),
      By(AccountAccess.account_id, accountId)
    )

  feature(s"test $ApiEndpoint1 version $VersionOfApi - account holders") {
    scenario("Deleting an account removes its holders, so a later refresh of the former holder grants no views on it", ApiEndpoint1, VersionOfApi) {
      Given("An account created through the API, which makes the user its holder and grants the user its system views")
      val bankId = createBank(APIUtil.generateUUID()).bankId.value
      val deletedAccountId = createAccountViaEndpoint(bankId, addAccountJson, user1).account_id
      accountHolders(bankId, deletedAccountId) should not be empty
      accountAccess(bankId, deletedAccountId) should not be empty

      When("We delete the account with the cascading endpoint")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      val request = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId /
        "accounts" / deletedAccountId).DELETE <@ (user1)
      makeDeleteRequest(request).code should equal(200)

      Then("The account's holders and account access are gone")
      accountHolders(bankId, deletedAccountId) shouldBe empty
      accountAccess(bankId, deletedAccountId) shouldBe empty

      When("The former holder reads the deleted account")
      val getRequest = (v5_1_0_Request / "banks" / bankId / "accounts" / deletedAccountId /
        Constant.SYSTEM_OWNER_VIEW_ID / "account").GET <@ (user1)
      makeGetRequest(getRequest).code should equal(404)

      And("The former holder creates another account, which refreshes the views of every account the user holds")
      createAccountViaEndpoint(bankId, addAccountJson, user1)

      Then("No account access is granted on the deleted account again")
      accountAccess(bankId, deletedAccountId) shouldBe empty
    }

    scenario("Deleting an account keeps the holders of an account with the same ACCOUNT_ID at another bank", ApiEndpoint1, VersionOfApi) {
      Given("Two banks that each have an account with the same ACCOUNT_ID, held by the same user")
      val bankId = BankId(randomString(10))
      val otherBankId = BankId(randomString(10))
      createBank(bankId.value)
      createBank(otherBankId.value)
      val accountId = AccountId(randomString(10))
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")
      createAccountRelevantResource(Some(resourceUser1), otherBankId, accountId, "EUR")

      When("We delete the first bank's account, keeping the other bank's one")
      Entitlement.entitlement.vend.addEntitlement(bankId.value, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      val request = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId.value /
        "accounts" / accountId.value).DELETE <@ (user1)
      makeDeleteRequest(request).code should equal(200)

      Then("The deleted account's holder is gone and the other bank's holder is kept")
      accountHolders(bankId.value, accountId.value) shouldBe empty
      accountHolders(otherBankId.value, accountId.value).size should equal(1)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - consent challenges") {
    scenario("Deleting an account deletes the SCA challenges of the consents bound to it", ApiEndpoint1, VersionOfApi) {
      setPropsValues("suggested_default_sca_method" -> "DUMMY")
      Given("Two banks that each have an account with the same ACCOUNT_ID, both held by the same user")
      val bankId = BankId(randomString(10))
      val otherBankId = BankId(randomString(10))
      createBank(bankId.value)
      createBank(otherBankId.value)
      val accountId = AccountId(randomString(10))
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")
      createAccountRelevantResource(Some(resourceUser1), otherBankId, accountId, "EUR")

      // A UK consent is lodged without a PSU and bound to the accounts the PSU selects when it
      // answers the challenge, the way Portal authorises one.
      def authorisedUKConsent(bank: BankId): String = {
        val consentId = Consents.consentProvider.vend.saveUKConsent(
          user = None, bankId = None, accountIds = None, consumerId = None,
          permissions = List("ReadAccountsBasic"),
          expirationDateTime = Some(new java.util.Date(System.currentTimeMillis() + 3600000L)),
          transactionFromDateTime = None, transactionToDateTime = None,
          apiStandard = Some("UKOpenBanking"), apiVersion = Some("4.0.1")
        ).openOrThrowException("consent creation failed").consentId
        val consentPath = v5_1_0_Request / "banks" / bank.value / "consents" / consentId / "authorise"
        val challenge = makePostRequest((consentPath / "challenge").POST <@ (user1), "")
        challenge.code should equal(200)
        val challengeId = (challenge.body \ "challenge_id").extract[String]
        val authorised = makePostRequest(consentPath.POST <@ (user1),
          s"""{"account_ids":["${accountId.value}"],"challenge_id":"$challengeId","answer":"123"}""")
        authorised.code should equal(200)
        consentId
      }
      def challengesOf(consentId: String): Int =
        MappedExpectedChallengeAnswer.findAll(By(MappedExpectedChallengeAnswer.ConsentId, consentId)).size
      val consent = authorisedUKConsent(bankId)
      val otherBankConsent = authorisedUKConsent(otherBankId)
      challengesOf(consent) should equal(1)
      challengesOf(otherBankConsent) should equal(1)

      When("We delete the account at the first bank with the cascading endpoint")
      Entitlement.entitlement.vend.addEntitlement(bankId.value, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      val request = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId.value /
        "accounts" / accountId.value).DELETE <@ (user1)
      makeDeleteRequest(request).code should equal(200)

      Then("The challenge of the consent bound to the deleted account is gone")
      challengesOf(consent) should equal(0)
      And("The challenge of the consent bound to the other bank's account is kept")
      challengesOf(otherBankConsent) should equal(1)
    }
  }

}
