package code.api

import code.bankconnectors.InboundAccountJun2017
import net.liftweb.json.{Extraction, compact, render}
import code.util.Helper.MdcLoggable
import net.liftweb.common.{ Full}
import org.scalatest._

class gateWayloginTest extends FeatureSpec 
  with BeforeAndAfterEach 
  with GivenWhenThen 
  with BeforeAndAfterAll 
  with ShouldMatchers 
  with MdcLoggable {
  
  implicit val formats = net.liftweb.json.DefaultFormats
  
  //fake this: Connector.connector.vend.getBankAccounts(username)
  val fakeResultFromAdapter =  Full(InboundAccountJun2017(
    errorCode = "",
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
  ) :: InboundAccountJun2017(
    errorCode = "",
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
      println(reply)
      reply.length should equal(0)
    }
  
    scenario("test the getCbsToken") {
      val reply: List[String] =  GatewayLogin.getCbsToken(compact(render(Extraction.decompose(fakeResultFromAdapter.get))))
      reply(0) should equal(""""cbsToken1"""")
      reply(1) should equal(""""cbsToken2"""")
      println(reply)
    }
  }
  
    
 
}