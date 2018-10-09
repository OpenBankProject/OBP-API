package code.setup

import java.util.{Date, UUID}

import code.api.util.ErrorMessages._
import bootstrap.liftweb.ToSchemify
import code.accountholder.AccountHolders
import code.api.util.APIUtil
import code.entitlement.Entitlement
import code.metadata.counterparties.{Counterparties, CounterpartyTrait, MappedCounterparty}
import code.model._
import code.model.dataAccess._
import code.transaction.MappedTransaction
import code.transactionrequests.{MappedTransactionRequest, TransactionRequests}
import code.views.Views
import net.liftweb.common.Box
import net.liftweb.mapper.{By, MetaMapper}
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

import scala.util.Random

trait LocalMappedConnectorTestSetup extends TestConnectorSetupWithStandardPermissions {
  //TODO: replace all these helpers with connector agnostic methods like createRandomBank
  // that call Connector.createBank etc.
  // (same in LocalRecordConnectorTestSetup)
  // Tests should simply use the currently selected connector
  override protected def createBank(id : String) : Bank = {
        MappedBank.create
          .fullBankName(randomString(5))
          .shortBankName(randomString(5))
          .permalink(id)
          .national_identifier(randomString(5))
          .mBankRoutingScheme(randomString(5))
          .mBankRoutingAddress(randomString(5))
          .saveMe
  }

  override protected def createCounterparty(bankId: String, accountId: String, accountRoutingAddress: String, otherAccountRoutingScheme: String, isBeneficiary: Boolean, createdByUserId:String): CounterpartyTrait = {
    Counterparties.counterparties.vend.createCounterparty(
      createdByUserId = createdByUserId,
      thisBankId = bankId,
      thisAccountId = accountId,
      thisViewId = "",
      name = APIUtil.generateUUID(),
      otherAccountRoutingScheme = otherAccountRoutingScheme,
      otherAccountRoutingAddress = accountId,
      otherBankRoutingScheme = "OBP",
      otherBankRoutingAddress = bankId,
      otherBranchRoutingScheme ="OBP",
      otherBranchRoutingAddress ="Berlin",
      isBeneficiary = isBeneficiary,
      otherAccountSecondaryRoutingScheme ="IBAN",
      otherAccountSecondaryRoutingAddress ="DE89 3704 0044 0532 0130 00",
      description = "String",
      bespoke = Nil
    ).openOrThrowException(attemptedToOpenAnEmptyBox)
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
      .accountLabel(randomString(4))
      .mAccountRoutingScheme(randomString(4))  
      .mAccountRoutingAddress(randomString(4))   
      .mBranchId(randomString(4))   
      .saveMe
  }

  override protected def updateAccountCurrency(bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
     MappedBankAccount.find(By(MappedBankAccount.bank, bankId.value), By(MappedBankAccount.theAccountId, accountId.value)).openOrThrowException(attemptedToOpenAnEmptyBox).accountCurrency(currency).saveMe()
  }

  def addEntitlement(bankId: String, userId: String, roleName: String): Box[Entitlement] = {
    // Return a Box so we can handle errors later.
    Entitlement.entitlement.vend.addEntitlement(bankId, userId, roleName)
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
      .CPOtherAccountRoutingScheme(randomString(5))
      .CPOtherAccountRoutingAddress(randomString(5))
      .CPOtherAccountSecondaryRoutingScheme(randomString(5))
      .CPOtherAccountSecondaryRoutingAddress(randomString(5))
      .CPOtherBankRoutingScheme(randomString(5))
      .CPOtherBankRoutingAddress(randomString(5))
      .saveMe
      .toTransaction.orNull
  }

  override protected def createTransactionRequest(account: BankAccount) = {
  
    MappedTransactionRequest.create
      .mTransactionRequestId(APIUtil.generateUUID())
      .mType("SANDBOX_TAN")
      .mFrom_BankId(account.bankId.value)
      .mFrom_AccountId(account.accountId.value)
      .mTo_BankId(randomString(5))
      .mTo_AccountId(randomString(5))
      .mBody_Value_Currency(account.currency)
      .mBody_Value_Amount("10")
      .mBody_Description("This is a description..")
      .mStatus("COMPLETED")
      .mStartDate(now)
      .mEndDate(now)
      .saveMe
  
    MappedTransactionRequest.create
      .mTransactionRequestId(APIUtil.generateUUID())
      .mType("SANDBOX_TAN")
      .mFrom_BankId(account.bankId.value)
      .mFrom_AccountId(account.accountId.value)
      .mTo_BankId(randomString(5))
      .mTo_AccountId(randomString(5))
      .mBody_Value_Currency(account.currency)
      .mBody_Value_Amount("1001")
      .mBody_Description("This is a description..")
      .mStatus("INITIATED")
      .mStartDate(now)
      .mEndDate(now)
      .saveMe
  }
  
  override protected def wipeTestData() = {
    //returns true if the model should not be wiped after each test
    def exclusion(m : MetaMapper[_]) = {
      m == Nonce || m == Token || m == Consumer || m == AuthUser || m == ResourceUser
    }

    //empty the relational db tables after each test
    ToSchemify.models.filterNot(exclusion).foreach(_.bulkDelete_!!())
    if (!APIUtil.getPropsAsBoolValue("remotedata.enable", false)) {
      ToSchemify.modelsRemotedata.filterNot(exclusion).foreach(_.bulkDelete_!!())
    } else {
      Views.views.vend.bulkDeleteAllPermissionsAndViews()
      AccountHolders.accountHolders.vend.bulkDeleteAllAccountHolders()
      TransactionRequests.transactionRequestProvider.vend.bulkDeleteTransactionRequests()
      MappedTransaction.bulkDelete_!!()
      MappedBank.bulkDelete_!!()
      MappedBankAccount.bulkDelete_!!()
      Counterparties.counterparties.vend.bulkDeleteAllCounterparties()
    }
  }
}
