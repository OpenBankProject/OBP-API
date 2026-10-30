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
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.attemptedToOpenAnEmptyBox
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.counterpartyattribute.{CounterpartyAttribute, CounterpartyAttributeX}
import code.counterpartylimit.{CounterpartyLimit, CounterpartyLimitProvider}
import code.entitlement.Entitlement
import code.metadata.counterparties.{Counterparties, MappedCounterparty, MappedCounterpartyBespoke, MappedCounterpartyMetadata, MappedCounterpartyWhereTag}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.CounterpartyAttributeType
import com.openbankproject.commons.model.{AccountId, BankId, CounterpartyBespoke, CounterpartyId, CounterpartyTrait}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.mapper.{By, ByList}
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration._

class DeleteCascadeCounterpartiesTest extends V400ServerSetup {

  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.deleteAccountCascade))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.deleteBankCascade))

  private val ownerView = Constant.SYSTEM_OWNER_VIEW_ID

  private case class Seeded(bankId: BankId, accountId: AccountId, counterparty: CounterpartyTrait, locationIds: List[Long])

  /** A counterparty on the given account, with rows in every table that hangs off it. */
  private def createCounterpartyWithChildren(bankId: BankId, accountId: AccountId): Seeded = {
    val counterparty = Counterparties.counterparties.vend.createCounterparty(
      createdByUserId = resourceUser1.userId,
      thisBankId = bankId.value,
      thisAccountId = accountId.value,
      thisViewId = ownerView,
      name = randomString(20),
      otherAccountRoutingScheme = "IBAN",
      otherAccountRoutingAddress = "DE89370400440532013000",
      otherBankRoutingScheme = "BIC",
      otherBankRoutingAddress = "COBADEFFXXX",
      otherBranchRoutingScheme = "OBP",
      otherBranchRoutingAddress = "Berlin",
      isBeneficiary = true,
      otherAccountSecondaryRoutingScheme = "IBAN",
      otherAccountSecondaryRoutingAddress = "DE89370400440532013000",
      description = "cascade test payee",
      currency = "EUR",
      bespoke = List(CounterpartyBespoke("key", "value"))
    ).openOrThrowException(attemptedToOpenAnEmptyBox)

    val metadata = Counterparties.counterparties.vend
      .getOrCreateMetadata(bankId, accountId, counterparty.counterpartyId, counterparty.name)
      .openOrThrowException(attemptedToOpenAnEmptyBox)
    metadata.addPhysicalLocation(resourceUser1.userPrimaryKey, new Date(), 13.4, 52.5) should be(true)
    metadata.addCorporateLocation(resourceUser1.userPrimaryKey, new Date(), 11.6, 48.1) should be(true)
    Await.result(
      CounterpartyAttributeX.counterpartyAttributeProvider.vend.createOrUpdateCounterpartyAttribute(
        CounterpartyId(counterparty.counterpartyId), None, "TAX_NUMBER", CounterpartyAttributeType.STRING, "123", Some(true)),
      10.seconds
    ).openOrThrowException(attemptedToOpenAnEmptyBox)
    Await.result(
      CounterpartyLimitProvider.counterpartyLimit.vend.createOrUpdateCounterpartyLimit(
        bankId.value, accountId.value, ownerView, counterparty.counterpartyId,
        "EUR", BigDecimal(100), BigDecimal(1000), 10, BigDecimal(10000), 100, BigDecimal(100000), 1000),
      10.seconds
    ).openOrThrowException(attemptedToOpenAnEmptyBox)

    val storedMetadata = MappedCounterpartyMetadata.find(By(MappedCounterpartyMetadata.counterpartyId, counterparty.counterpartyId))
      .openOrThrowException(attemptedToOpenAnEmptyBox)
    Seeded(bankId, accountId, counterparty, List(storedMetadata.physicalLocation.get, storedMetadata.corporateLocation.get))
  }

  /** Rows left in each counterparty table for one seeded counterparty. */
  private def remainingRows(seeded: Seeded): Map[String, Long] = {
    val Seeded(bankId, accountId, counterparty, locationIds) = seeded
    val counterpartyId = counterparty.counterpartyId
    val primaryKey = counterparty.asInstanceOf[MappedCounterparty].id.get
    Map(
      "counterparty" -> MappedCounterparty.count(By(MappedCounterparty.mCounterPartyId, counterpartyId)),
      "bespoke" -> MappedCounterpartyBespoke.count(By(MappedCounterpartyBespoke.mCounterparty, primaryKey)),
      "metadata" -> MappedCounterpartyMetadata.count(
        By(MappedCounterpartyMetadata.thisBankId, bankId.value),
        By(MappedCounterpartyMetadata.thisAccountId, accountId.value),
        By(MappedCounterpartyMetadata.counterpartyId, counterpartyId)),
      "attribute" -> CounterpartyAttribute.count(By(CounterpartyAttribute.CounterpartyId_, counterpartyId)),
      "limit" -> CounterpartyLimit.count(By(CounterpartyLimit.CounterpartyId, counterpartyId)),
      "location" -> MappedCounterpartyWhereTag.count(ByList(MappedCounterpartyWhereTag.id, locationIds))
    )
  }

  private val nothingLeft =
    Map("counterparty" -> 0L, "bespoke" -> 0L, "metadata" -> 0L, "attribute" -> 0L, "limit" -> 0L, "location" -> 0L)
  private val untouched =
    Map("counterparty" -> 1L, "bespoke" -> 1L, "metadata" -> 1L, "attribute" -> 1L, "limit" -> 1L, "location" -> 2L)

  feature(s"test $ApiEndpoint1 version $VersionOfApi - counterparties") {
    scenario("Deleting an account deletes its own counterparties and leaves other accounts' counterparties alone", ApiEndpoint1, VersionOfApi) {
      Given("Two banks that each have an account with the same ACCOUNT_ID, plus a second account in the first bank")
      val bankId = BankId(randomString(10))
      val otherBankId = BankId(randomString(10))
      createBank(bankId.value)
      createBank(otherBankId.value)
      val accountId = AccountId(randomString(10))
      val siblingAccountId = AccountId(randomString(10))
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")
      createAccountRelevantResource(Some(resourceUser1), otherBankId, accountId, "EUR")
      createAccountRelevantResource(Some(resourceUser1), bankId, siblingAccountId, "EUR")

      And("A counterparty with bespoke fields, metadata with two locations, an attribute and a limit on each account")
      val target = createCounterpartyWithChildren(bankId, accountId)
      val sameIdOtherBank = createCounterpartyWithChildren(otherBankId, accountId)
      val sibling = createCounterpartyWithChildren(bankId, siblingAccountId)
      remainingRows(target) should equal(untouched)

      When("We delete the first account with the cascading endpoint")
      Entitlement.entitlement.vend.addEntitlement(bankId.value, resourceUser1.userId, ApiRole.canDeleteAccountCascade.toString)
      val request = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId.value /
        "accounts" / accountId.value).DELETE <@ (user1)
      val response = makeDeleteRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("The account's counterparty and every row that hangs off it are gone")
      remainingRows(target) should equal(nothingLeft)

      And("The counterparty of the same ACCOUNT_ID at another bank is untouched")
      remainingRows(sameIdOtherBank) should equal(untouched)

      And("The counterparty of the other account at the same bank is untouched")
      remainingRows(sibling) should equal(untouched)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - counterparties") {
    scenario("Deleting a bank deletes the counterparties of all its accounts and leaves other banks alone", ApiEndpoint2, VersionOfApi) {
      Given("A bank with two accounts, and another bank with an account that has the same ACCOUNT_ID")
      val bankId = BankId(randomString(10))
      val otherBankId = BankId(randomString(10))
      createBank(bankId.value)
      createBank(otherBankId.value)
      val accountId = AccountId(randomString(10))
      val secondAccountId = AccountId(randomString(10))
      createAccountRelevantResource(Some(resourceUser1), bankId, accountId, "EUR")
      createAccountRelevantResource(Some(resourceUser1), bankId, secondAccountId, "EUR")
      createAccountRelevantResource(Some(resourceUser1), otherBankId, accountId, "EUR")

      val first = createCounterpartyWithChildren(bankId, accountId)
      val second = createCounterpartyWithChildren(bankId, secondAccountId)
      val otherBank = createCounterpartyWithChildren(otherBankId, accountId)

      When("We delete the bank with the cascading endpoint")
      Entitlement.entitlement.vend.addEntitlement(bankId.value, resourceUser1.userId, ApiRole.canDeleteBankCascade.toString)
      val request = (v4_0_0_Request / "management" / "cascading" / "banks" / bankId.value).DELETE <@ (user1)
      val response = makeDeleteRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("The counterparties of both accounts and their child rows are gone")
      remainingRows(first) should equal(nothingLeft)
      remainingRows(second) should equal(nothingLeft)

      And("The counterparty at the other bank is untouched")
      remainingRows(otherBank) should equal(untouched)
    }
  }

}
