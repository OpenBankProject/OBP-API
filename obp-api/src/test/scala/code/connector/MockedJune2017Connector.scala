package code.connector

import code.api.util.{CallContext, CustomJsonFormats}
import code.bankconnectors._
import code.bankconnectors.vJune2017.InboundAccountJune2017
import code.model._
import code.setup.{DefaultConnectorTestSetup, DefaultUsers, ServerSetup}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId}
import net.liftweb.common.{Box, Full}

import scala.collection.immutable.List
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by zhanghongwei on 14/07/2017.
  */
object MockedJune2017Connector extends ServerSetup  
  with Connector with DefaultUsers  
  with DefaultConnectorTestSetup with MdcLoggable {
  
  implicit override val nameOfConnector = "MockedCardConnector"
  
  //These bank id and account ids are real data over adapter  
  val bankIdAccountId = BankIdAccountId(BankId("obp-bank-x-gh"),AccountId("KOa4M8UfjUuWPIXwPXYPpy5FoFcTUwpfHgXC1qpSluc"))
  val bankIdAccountId2 = BankIdAccountId(BankId("obp-bank-x-gh"),AccountId("tKWSUBy6sha3Vhxc/vw9OK96a0RprtoxUuObMYR29TI"))
  
  override def getBankAccountsForUser(username: String, callContext: Option[CallContext]): Box[(List[InboundAccountJune2017], Option[CallContext])] = {
    Full(
      InboundAccountJune2017("", cbsToken = "cbsToken", bankId = bankIdAccountId.bankId.value, branchId = "222", accountId = bankIdAccountId.accountId.value, accountNumber = "123", accountType = "AC", balanceAmount = "50", balanceCurrency = "EUR", owners = Nil, viewsToGenerate = "Owner" :: "Public" :: "Accountant" :: "Auditor" :: Nil, bankRoutingScheme = "iban", bankRoutingAddress = "bankRoutingAddress", branchRoutingScheme = "branchRoutingScheme", branchRoutingAddress = " branchRoutingAddress", accountRoutingScheme = "accountRoutingScheme", accountRoutingAddress = "accountRoutingAddress", accountRouting = Nil, accountRules = Nil) :: InboundAccountJune2017("", cbsToken = "cbsToken", bankId = bankIdAccountId2.bankId.value, branchId = "222", accountId = bankIdAccountId2.accountId.value, accountNumber = "123", accountType = "AC", balanceAmount = "50", balanceCurrency = "EUR", owners = Nil, viewsToGenerate = "Owner" :: "Public" :: "Accountant" :: "Auditor" :: Nil, bankRoutingScheme = "iban", bankRoutingAddress = "bankRoutingAddress", branchRoutingScheme = "branchRoutingScheme", branchRoutingAddress = " branchRoutingAddress", accountRoutingScheme = "accountRoutingScheme", accountRoutingAddress = "accountRoutingAddress", accountRouting = Nil, accountRules = Nil) :: Nil,
      callContext
    )
  }

  override def getBankAccountsForUserFuture(username: String, callContext: Option[CallContext]):  Future[Box[(List[InboundAccountJune2017], Option[CallContext])]] = Future{
    getBankAccountsForUser(username,callContext)
  }
}

