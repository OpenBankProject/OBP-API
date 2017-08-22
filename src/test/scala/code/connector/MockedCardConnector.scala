package code.connector

import java.util.Date

import code.api.v2_1_0.{BranchJsonPostV210, TransactionRequestCommonBodyJSON}
import code.atms.Atms.AtmId
import code.atms.MappedAtm
import code.bankconnectors._
import code.bankconnectors.vJune2017.InboundAccountJune2017
import code.branches.Branches.{Branch, BranchId, BranchT}
import code.branches.{InboundAdapterInfo, MappedBranch}
import code.fx.FXRate
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.model.dataAccess.ResourceUser
import code.products.Products.{Product, ProductCode}
import code.setup.{DefaultConnectorTestSetup, DefaultUsers, ServerSetup}
import code.transactionrequests.TransactionRequestTypeCharge
import code.transactionrequests.TransactionRequests.{TransactionRequest, TransactionRequestBody, TransactionRequestChallenge, TransactionRequestCharge}
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Failure, Full}

/**
  * Created by zhanghongwei on 14/07/2017.
  */
object MockedCardConnector extends ServerSetup  
  with Connector with DefaultUsers  
  with DefaultConnectorTestSetup with MdcLoggable {
  
  type AccountType = BankAccount
  
  implicit override val nameOfConnector = "MockedCardConnector"
  
  
  //These bank id and account ids are real data over adapter  
  val bankIdAccountId = BankIdAccountId(BankId("obp-bank-x-gh"),AccountId("KOa4M8UfjUuWPIXwPXYPpy5FoFcTUwpfHgXC1qpSluc"))
  val bankIdAccountId2 = BankIdAccountId(BankId("obp-bank-x-gh"),AccountId("tKWSUBy6sha3Vhxc/vw9OK96a0RprtoxUuObMYR29TI"))
  
  override def getBankAccounts(username: String): Box[List[InboundAccountJune2017]] = {
    Full(
      InboundAccountJune2017(
        errorCode = "OBP-6001: ...",
        cbsToken = "cbsToken",
        bankId = bankIdAccountId.bankId.value,
        branchId = "222", 
        accountId = bankIdAccountId.accountId.value,
        accountNumber = "123", 
        accountType = "AC", 
        balanceAmount = "50",
        balanceCurrency = "EUR", 
        owners = Nil,
        viewsToGenerate = "Owner" :: "Public" :: "Accountant" :: "Auditor" :: Nil,
        bankRoutingScheme = "iban", 
        bankRoutingAddress = "bankRoutingAddress",
        branchRoutingScheme = "branchRoutingScheme",
        branchRoutingAddress = " branchRoutingAddress",
        accountRoutingScheme = "accountRoutingScheme",
        accountRoutingAddress = "accountRoutingAddress"
      ) :: InboundAccountJune2017(
        errorCode = "OBP-6001: ...",
        cbsToken = "cbsToken",
        bankId = bankIdAccountId2.bankId.value, 
        branchId = "222",
        accountId = bankIdAccountId2.accountId.value, 
        accountNumber = "123",
        accountType = "AC", 
        balanceAmount = "50", 
        balanceCurrency = "EUR",
        owners = Nil,
        viewsToGenerate = "Owner" :: "Public" :: "Accountant" :: "Auditor" :: Nil,
        bankRoutingScheme = "iban", 
        bankRoutingAddress = "bankRoutingAddress",
        branchRoutingScheme = "branchRoutingScheme",
        branchRoutingAddress = " branchRoutingAddress",
        accountRoutingScheme = "accountRoutingScheme",
        accountRoutingAddress = "accountRoutingAddress"
      ) :: Nil
    )
  }
  override def accountExists(
    bankId: BankId,
    accountNumber: String
  ): Boolean = ???
  override def removeAccount(
    bankId: BankId,
    accountId: AccountId
  ): Boolean = ???
  override def getMatchingTransactionCount(
    bankNationalIdentifier: String,
    accountNumber: String,
    amount: String,
    completed: Date,
    otherAccountHolder: String
  ): Int = ???
  override def updateAccountBalance(
    bankId: BankId,
    accountId: AccountId,
    newBalance: BigDecimal
  ): Boolean = ???
  override def setBankAccountLastUpdated(
    bankNationalIdentifier: String,
    accountNumber: String,
    updateDate: Date
  ): Boolean = ???
  override def updateAccountLabel(
    bankId: BankId,
    accountId: AccountId,
    label: String
  ): Boolean = ???
}

