package code.connector

import code.bankconnectors._
import code.bankconnectors.vJune2017.InboundAccountJune2017
import code.model._
import code.setup.{DefaultConnectorTestSetup, DefaultUsers, ServerSetup}
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Full}

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
}

