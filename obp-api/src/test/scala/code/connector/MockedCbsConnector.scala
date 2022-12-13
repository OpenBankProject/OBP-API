package code.connector

import code.api.Constant.SYSTEM_STANDARD_VIEW_ID
import code.api.util.{CallContext, CustomJsonFormats}
import code.bankconnectors._
import code.setup.{DefaultConnectorTestSetup, DefaultUsers, ServerSetup}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, InboundAccount, InboundAccountCommons}
import net.liftweb.common.{Box, Full}

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.json.Formats

import scala.concurrent.Future

/**
  * Created by zhanghongwei on 14/07/2017.
  */
object MockedCbsConnector extends ServerSetup  
  with Connector with DefaultUsers  
  with DefaultConnectorTestSetup with MdcLoggable {
  override implicit val formats: Formats = CustomJsonFormats.nullTolerateFormats
  implicit override val nameOfConnector = "MockedCardConnector"
  
  //These bank id and account ids are real data over adapter  
  val bankIdAccountId = BankIdAccountId(BankId("obp-bank-x-gh"),AccountId("KOa4M8UfjUuWPIXwPXYPpy5FoFcTUwpfHgXC1qpSluc"))
  val bankIdAccountId2 = BankIdAccountId(BankId("obp-bank-x-gh"),AccountId("tKWSUBy6sha3Vhxc/vw9OK96a0RprtoxUuObMYR29TI"))
  
  override def getBankAccountsForUserLegacy(provider: String, username:String, callContext: Option[CallContext]): Box[(List[InboundAccount], Option[CallContext])] = {
    Full(
      List(InboundAccountCommons(
        bankId = bankIdAccountId.bankId.value,
        branchId = "",
        accountId = bankIdAccountId.accountId.value,
        accountNumber = "",
        accountType = "",
        balanceAmount = "",
        balanceCurrency = "",
        owners = List(""),
        viewsToGenerate = SYSTEM_STANDARD_VIEW_ID :: "Owner" :: "_Public" :: "Accountant" :: "Auditor" :: Nil,
        bankRoutingScheme = "",
        bankRoutingAddress = "",
        branchRoutingScheme = "",
        branchRoutingAddress = "",
        accountRoutingScheme = "",
        accountRoutingAddress = "",
      ),InboundAccountCommons(
        bankId = bankIdAccountId2.bankId.value,
        branchId = "",
        accountId = bankIdAccountId2.accountId.value,
        accountNumber = "",
        accountType = "",
        balanceAmount = "",
        balanceCurrency = "",
        owners = List(""),
        viewsToGenerate = SYSTEM_STANDARD_VIEW_ID :: "Owner" :: "_Public" :: "Accountant" :: "Auditor" :: Nil,
        bankRoutingScheme = "",
        bankRoutingAddress = "",
        branchRoutingScheme = "",
        branchRoutingAddress = "",
        accountRoutingScheme = "",
        accountRoutingAddress = "",
      )),
      callContext
    )
  }

  override def getBankAccountsForUser(provider: String, username:String, callContext: Option[CallContext]):  Future[Box[(List[InboundAccount], Option[CallContext])]] = Future{
    getBankAccountsForUserLegacy(provider, username,callContext)
  }
}

