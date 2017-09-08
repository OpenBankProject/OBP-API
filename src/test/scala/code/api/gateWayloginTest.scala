package code.api

import code.bankconnectors.vJune2017.InboundAccountJune2017
import code.bankconnectors.vMar2017.InboundStatusMessage
import code.util.Helper.MdcLoggable
import net.liftweb.common.Full
import net.liftweb.json.{Extraction, compact, render}
import org.scalatest._

class gateWayloginTest extends FeatureSpec 
  with BeforeAndAfterEach 
  with GivenWhenThen 
  with BeforeAndAfterAll 
  with ShouldMatchers 
  with MdcLoggable {
  
  implicit val formats = net.liftweb.json.DefaultFormats
  
  //fake this: Connector.connector.vend.getBankAccounts(username)
  val fakeResultFromAdapter =  Full(InboundAccountJune2017(
    errorCode = "",
    List(InboundStatusMessage("ESB", "Success", "0", "OK")),
    cbsToken ="cbsToken1",
    bankId = "gh.29.uk",
    branchId = "222",
    accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    accountNumber = "123",
    accountType = "AC",
    balanceAmount = "50",
    balanceCurrency = "EUR",
    owners = "Susan" :: " Frank" :: Nil,
    viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
    bankRoutingScheme = "iban",
    bankRoutingAddress = "bankRoutingAddress",
    branchRoutingScheme = "branchRoutingScheme",
    branchRoutingAddress = " branchRoutingAddress",
    accountRoutingScheme = "accountRoutingScheme",
    accountRoutingAddress = "accountRoutingAddress"
  ) :: InboundAccountJune2017(
    errorCode = "123",
    List(InboundStatusMessage("ESB", "Success", "0", "OK")),
    cbsToken ="cbsToken2",
    bankId = "gh.29.uk",
    branchId = "222",
    accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    accountNumber = "123",
    accountType = "AC",
    balanceAmount = "50",
    balanceCurrency = "EUR",
    owners = "Susan" :: " Frank" :: Nil,
    viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
    bankRoutingScheme = "iban",
    bankRoutingAddress = "bankRoutingAddress",
    branchRoutingScheme = "branchRoutingScheme",
    branchRoutingAddress = " branchRoutingAddress",
    accountRoutingScheme = "accountRoutingScheme",
    accountRoutingAddress = "accountRoutingAddress"
  ) ::Nil)
  
  
  feature("Unit Tests for two getCbsToken and getErrors: ") {
    scenario("test the getErrors") {
      val reply: List[String] =  GatewayLogin.getErrors(compact(render(Extraction.decompose(fakeResultFromAdapter.get))))
      reply.forall(_.equalsIgnoreCase("")) should equal(true)
    }
  
    scenario("test the getCbsToken") {
      val reply: List[String] =  GatewayLogin.getCbsTokens(compact(render(Extraction.decompose(fakeResultFromAdapter.get))))
      reply(0) should equal("cbsToken1")
      reply(1) should equal("cbsToken2")
  
      reply.exists(_.equalsIgnoreCase("")==false) should equal(true)
    }
  }
  
    
 
}