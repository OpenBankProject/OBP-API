package code.connector

import code.api.util.{CallContext, CustomJsonFormats}
import code.bankconnectors._
import code.bankconnectors.vJune2017.InboundAccountJune2017
import code.setup.{DefaultConnectorTestSetup, DefaultUsers, ServerSetup}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, InboundAccountCommons}
import net.liftweb.common.{Box, Full}

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.json.Formats

import scala.concurrent.Future

/**
  * Created by zhanghongwei on 14/07/2017.
  */
object MockedJune2017Connector extends ServerSetup  
  with Connector with DefaultUsers  
  with DefaultConnectorTestSetup with MdcLoggable {
  override implicit val formats: Formats = CustomJsonFormats.nullTolerateFormats
  implicit override val nameOfConnector = "MockedCardConnector"
  
  //These bank id and account ids are real data over adapter  
  val bankIdAccountId1 = BankIdAccountId(BankId("obp-bank-id1"),AccountId("KOa4M8UfjUuWPIXwPXYPpy5FoFcTUwpfH-accountId1"))
  val bankIdAccountId2 = BankIdAccountId(BankId("obp-bank-id2"),AccountId("KOa4M8UfjUuWPIXwPXYPpy5FoFcTUwpfH-accountId2"))
  

  override def getBankAccountsForUser(username: String, callContext: Option[CallContext])= Future{
    Full(
      (InboundAccountCommons(
        bankId = bankIdAccountId1.bankId.value,
        accountId = bankIdAccountId1.accountId.value,
        viewsToGenerate = "Owner" :: "_Public" :: "Accountant" :: "Auditor" :: Nil,
        branchId = "",
        accountNumber = "",
        accountType = "",
        balanceAmount = "",
        balanceCurrency = "",
        owners = List(""),
        bankRoutingScheme = "",
        bankRoutingAddress = "",
        branchRoutingScheme = "",
        branchRoutingAddress = "",
        accountRoutingScheme = "",
        accountRoutingAddress = ""
      ) :: InboundAccountCommons(
        bankId = bankIdAccountId2.bankId.value,
        accountId = bankIdAccountId2.accountId.value,
        viewsToGenerate = "Owner" :: "_Public" :: "Accountant" :: "Auditor" :: Nil,
        branchId = "",
        accountNumber = "",
        accountType = "",
        balanceAmount = "",
        balanceCurrency = "",
        owners = List(""),
        bankRoutingScheme = "",
        bankRoutingAddress = "",
        branchRoutingScheme = "",
        branchRoutingAddress = "",
        accountRoutingScheme = "",
        accountRoutingAddress = ""
      ) :: Nil,callContext)
    )
  }
}

