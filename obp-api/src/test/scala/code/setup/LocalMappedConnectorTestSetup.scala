package code.setup

import java.util.Date

import bootstrap.liftweb.ToSchemify
import code.api.util.APIUtil
import code.api.util.ErrorMessages._
import code.entitlement.Entitlement
import code.metadata.counterparties.Counterparties
import code.model._
import code.model.dataAccess._
import code.transaction.MappedTransaction
import code.transactionrequests.MappedTransactionRequest
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import net.liftweb.common.Box
import net.liftweb.mapper.{By, MetaMapper}
import net.liftweb.util.Helpers._
import org.iban4j

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

  override protected def createCounterparty(bankId: String, accountId: String, counterpartyObpRoutingAddress: String, isBeneficiary: Boolean, createdByUserId:String): CounterpartyTrait = {
    Counterparties.counterparties.vend.createCounterparty(
      createdByUserId = createdByUserId,
      thisBankId = bankId,
      thisAccountId = accountId,
      thisViewId = "",
      name = APIUtil.generateUUID(),
      otherAccountRoutingScheme = "OBP",
      otherAccountRoutingAddress = counterpartyObpRoutingAddress,
      otherBankRoutingScheme = "OBP",
      otherBankRoutingAddress = bankId,
      otherBranchRoutingScheme ="OBP",
      otherBranchRoutingAddress ="Berlin",
      isBeneficiary = isBeneficiary,
      otherAccountSecondaryRoutingScheme ="IBAN",
      otherAccountSecondaryRoutingAddress ="DE89 3704 0044 0532 0130 00",
      description = "String",
      currency = "String",
      bespoke = Nil
    ).openOrThrowException(attemptedToOpenAnEmptyBox)
  }

  override protected def createAccount(bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
    BankAccountRouting.create
      .BankId(bankId.value)
      .AccountId(accountId.value)
      .AccountRoutingScheme(AccountRoutingScheme.IBAN.toString)
      .AccountRoutingAddress(iban4j.Iban.random().toString())
      .saveMe
    BankAccountRouting.create
      .BankId(bankId.value)
      .AccountId(accountId.value)
      .AccountRoutingScheme(randomString(4))
      .AccountRoutingAddress(randomString(4))
      .saveMe
    MappedBankAccount.create
      .bank(bankId.value)
      .theAccountId(accountId.value)
      .accountCurrency(currency.toUpperCase)
      .accountBalance(900000000)
      .holder(randomString(4))
      .accountLastUpdate(now)
      .accountName(randomString(4))
      .accountNumber(randomString(4))
      .accountLabel(randomString(4))
      .mBranchId(randomString(4))   
      .saveMe
  }

  override protected def updateAccountCurrency(bankId: BankId, accountId : AccountId, currency : String) : BankAccount = {
     MappedBankAccount.find(By(MappedBankAccount.bank, bankId.value), By(MappedBankAccount.theAccountId, accountId.value)).openOrThrowException(attemptedToOpenAnEmptyBox).accountCurrency(currency.toUpperCase).saveMe()
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

  override protected def createTransactionRequest(account: BankAccount): List[MappedTransactionRequest] = {
  
    val firstRequest = MappedTransactionRequest.create
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
  
    val secondRequest = MappedTransactionRequest.create
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
    
    List(firstRequest, secondRequest)
  }
  
  override protected def wipeTestData() = {
    //returns true if the model should not be wiped after each test
    def exclusion(m : MetaMapper[_]) = {
      m == Nonce || m == Token || m == Consumer || m == AuthUser || m == ResourceUser
    }

    //empty the relational db tables after each test
    ToSchemify.models.filterNot(exclusion).foreach(_.bulkDelete_!!())
    ToSchemify.modelsRemotedata.filterNot(exclusion).foreach(_.bulkDelete_!!())
  }
}
