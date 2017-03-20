package code.api

import java.util.{Date, UUID}
import java.beans.Introspector

import bootstrap.liftweb.ToSchemify
import code.accountholder.AccountHolders
import code.model._
import code.model.dataAccess._
import net.liftweb.common.Box
import net.liftweb.mapper.{By, MetaMapper}
import net.liftweb.util.Props
import net.liftweb.util.Helpers._
import code.entitlement.{Entitlement, MappedEntitlement}
import code.metadata.counterparties.{Counterparties, CounterpartyTrait}
import code.transaction.MappedTransaction
import code.views.Views

import scala.util.Random

trait LocalMappedConnectorTestSetup extends TestConnectorSetupWithStandardPermissions {
  //TODO: replace all these helpers with connector agnostic methods like createRandomBank
  // that call Connector.createBank etc.
  // (same in LocalConnectorTestSetup)
  // Tests should simply use the currently selected connector
  override protected def createBank(id : String) : Bank = {
        MappedBank.create
          .fullBankName(randomString(5))
          .shortBankName(randomString(5))
          .permalink(id)
          .national_identifier(randomString(5)).saveMe
  }

  override protected def createCounterparty(bankId: String, accountId: String, accountRoutingAddress: String, otherAccountRoutingScheme: String, isBeneficiary: Boolean, createdByUserId: String): CounterpartyTrait = {
    Counterparties.counterparties.vend.createCounterparty(createdByUserId = createdByUserId,
      thisBankId = bankId,
      thisAccountId = accountId,
      thisViewId = "",
      name = UUID.randomUUID().toString,
      otherAccountRoutingAddress = accountId,
      otherAccountRoutingScheme = otherAccountRoutingScheme,
      otherBankRoutingScheme = "OBP",
      otherBankRoutingAddress = bankId,
      otherBranchRoutingScheme ="OBP",
      otherBranchRoutingAddress ="Berlin",
      isBeneficiary = isBeneficiary
    ).get
  }

// TODO: Should return an option or box so can test if the insert succeeded
// or if it failed due to unique exception etc. However, we'll need to modify / lift callers so they can handle an Option
//  override protected def createBank(id : String) : Option[Bank] = {
//    val newBankOpt : Option[Bank] = try {
//      Some(MappedBank.create
//        .fullBankName(randomString(5))
//        .shortBankName(randomString(5))
//        .permalink(id)
//        .national_identifier(randomString(5)).saveMe)
//    } catch {
//      case se : SQLException => None
//    }
//    newBankOpt
//  }




  override protected def createAccount(bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
    MappedBankAccount.create
      .bank(bankId.value)
      .theAccountId(accountId.value)
      .accountCurrency(currency)
      .accountBalance(900000000)
      .holder(randomString(4))
      .accountNumber(randomString(4))
      .accountLabel(randomString(4)).saveMe
  }

  override protected def updateAccountCurrency(bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
     MappedBankAccount.find(By(MappedBankAccount.bank, bankId.value), By(MappedBankAccount.theAccountId, accountId.value)).get.accountCurrency(currency).saveMe()
  }

  def addEntitlement(bankId: String, userId: String, roleName: String): Box[Entitlement] = {
    // Return a Box so we can handle errors later.
    val addEntitlement = MappedEntitlement.create
      .mBankId(bankId)
      .mUserId(userId)
      .mRoleName(roleName)
      .saveMe()
    Some(addEntitlement)
  }

  override protected def createTransaction(account: BankAccount, startDate: Date, finishDate: Date) = {
    //ugly
    val mappedBankAccount = account.asInstanceOf[MappedBankAccount]

    val accountBalanceBefore = mappedBankAccount.accountBalance.get
    val transactionAmount = Random.nextInt(1000).toLong
    val accountBalanceAfter = accountBalanceBefore + transactionAmount

    mappedBankAccount.accountBalance(accountBalanceAfter).save

    MappedTransaction.create
      .bank(account.bankId.value)
      .account(account.accountId.value)
      .transactionType(randomString(5))
      .tStartDate(startDate)
      .tFinishDate(finishDate)
      .currency(account.currency)
      .amount(transactionAmount)
      .newAccountBalance(accountBalanceAfter)
      .description(randomString(5))
      .counterpartyAccountHolder(randomString(5))
      .counterpartyAccountKind(randomString(5))
      .counterpartyAccountNumber(randomString(5))
      .counterpartyBankName(randomString(5))
      .counterpartyIban(randomString(5))
      .counterpartyNationalId(randomString(5))
      .saveMe
      .toTransaction.orNull
  }

  override protected def wipeTestData() = {
    //returns true if the model should not be wiped after each test
    def exclusion(m : MetaMapper[_]) = {
      m == Nonce || m == Token || m == Consumer || m == AuthUser || m == ResourceUser
    }

    //empty the relational db tables after each test
    ToSchemify.models.filterNot(exclusion).foreach(_.bulkDelete_!!())
    if (!Props.getBool("remotedata.enable", false)) {
      ToSchemify.modelsRemotedata.filterNot(exclusion).foreach(_.bulkDelete_!!())
    } else {
      Views.views.vend.bulkDeleteAllPermissionsAndViews()
      AccountHolders.accountHolders.vend.bulkDeleteAllAccountHolders()
    }
  }
}
