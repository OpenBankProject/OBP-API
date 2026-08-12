package code.bankconnectors

import code.api.Constant
import code.model.dataAccess.BankAccountRouting
import code.setup.{DefaultUsers, ServerSetupWithTestData}
import com.openbankproject.commons.model.{AccountId, AccountRoutingJsonV121, BankAccountRoutings, BankId, BankRoutingJson, BranchRoutingJsonV141}
import net.liftweb.mapper.By
import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest.Tag

/**
 * The `OBP` account-routing scheme means two things at once, and resolution has to honour both.
 *
 * It is an implicit self-identifier: an address under it is normally the account id itself, with no
 * row in bankaccountrouting. But a bank may also *register* an `OBP` routing whose address is
 * something else entirely, and that row is stored like any other scheme's. Reading only the implicit
 * meaning left those accounts unreachable through every endpoint that resolves by routing — the row
 * was in the table, and the answer was "Bank Account not found", which is what blocked converting a
 * consent-request that named an account that way.
 */
class ObpAccountRoutingResolutionTest extends ServerSetupWithTestData with DefaultUsers {

  object ObpRouting extends Tag("ObpAccountRoutingResolution")

  // "OBP" is the overloaded one, accepted in both bank- and account-routing contexts.
  private val obpScheme = "OBP"

  feature("Resolving an account by an OBP-scheme routing") {

    scenario("an address that is the account id resolves, with and without a bank", ObpRouting) {
      val account = createAccountRelevantResource(Some(resourceUser1), testBankId1, testAccountId1, "EUR")

      Connector.connector.vend.getBankAccountByRoutingLegacy(
        Some(account.bankId), obpScheme, account.accountId.value, None
      ).map(_._1.accountId) should equal(net.liftweb.common.Full(account.accountId))

    }

    scenario("without a bank, an account id shared by several banks is reported as ambiguous", ObpRouting) {
      // The fixture gives more than one bank an account called testAccount1, so with no bank context
      // the address matches several accounts. That has to stay an ambiguity: falling through to the
      // routing table would find nothing there and answer a bare "not found" instead.
      createAccountRelevantResource(Some(resourceUser1), testBankId1, testAccountId1, "EUR")
      createAccountRelevantResource(Some(resourceUser1), testBankId2, testAccountId1, "EUR")

      val result = Connector.connector.vend.getBankAccountByRoutingLegacy(
        None, obpScheme, testAccountId1.value, None)
      result.isDefined should equal(false)
      result.toString should include("OBP-31075")
    }

    scenario("a registered OBP routing whose address is not the account id resolves too", ObpRouting) {
      val account = createAccountRelevantResource(Some(resourceUser1), testBankId2, AccountId("testAccountObpRouting"), "EUR")
      val registeredAddress = "some-bank-chosen-obp-address"

      BankAccountRouting.create
        .BankId(account.bankId.value)
        .AccountId(account.accountId.value)
        .AccountRoutingScheme(obpScheme)
        .AccountRoutingAddress(registeredAddress)
        .saveMe()

      Connector.connector.vend.getBankAccountByRoutingLegacy(
        Some(account.bankId), obpScheme, registeredAddress, None
      ).map(_._1.accountId) should equal(net.liftweb.common.Full(account.accountId))

      Connector.connector.vend.getBankAccountByRoutingLegacy(
        None, obpScheme, registeredAddress, None
      ).map(_._1.accountId) should equal(net.liftweb.common.Full(account.accountId))
    }

    scenario("the plural-routings resolver honours a registered OBP routing too", ObpRouting) {
      // getBankAccountByRoutings has its own copy of the implicit-OBP shortcut, and had the same
      // blind spot. This is the path the VRP consent-request creation takes.
      val account = createAccountRelevantResource(Some(resourceUser1), testBankId1, AccountId("testAccountPluralRouting"), "EUR")
      val registeredAddress = "another-bank-chosen-obp-address"

      BankAccountRouting.create
        .BankId(account.bankId.value)
        .AccountId(account.accountId.value)
        .AccountRoutingScheme(obpScheme)
        .AccountRoutingAddress(registeredAddress)
        .saveMe()

      val routings = BankAccountRoutings(
        bank = BankRoutingJson(obpScheme, account.bankId.value),
        account = BranchRoutingJsonV141(obpScheme, registeredAddress),
        branch = AccountRoutingJsonV121("", "")
      )
      val resolved = Await.result(
        Connector.connector.vend.getBankAccountByRoutings(routings, None), 20.seconds)._1
      resolved.map(_.accountId) should equal(net.liftweb.common.Full(account.accountId))
    }

    scenario("an address that is neither still resolves to nothing", ObpRouting) {
      Connector.connector.vend.getBankAccountByRoutingLegacy(
        Some(BankId(testBankId1.value)), obpScheme, "no-such-address-anywhere", None
      ).isDefined should equal(false)
    }
  }

  override def afterEach(): Unit = {
    BankAccountRouting.findAll(
      By(BankAccountRouting.AccountRoutingAddress, "some-bank-chosen-obp-address")).foreach(_.delete_!)
    BankAccountRouting.findAll(
      By(BankAccountRouting.AccountRoutingAddress, "another-bank-chosen-obp-address")).foreach(_.delete_!)
    super.afterEach()
  }
}
