package code.kafka

import java.util.{Date, UUID}

import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.util.{APIUtil, CallContext, CustomJsonFormats}
import code.api.v2_1_0.TransactionRequestBodyCommonJSON
import code.bankconnectors.Connector
import code.bankconnectors.vMar2017.InboundBank
import code.bankconnectors.vSept2018._
import code.setup.{KafkaSetup, ServerSetupWithTestData}
import com.openbankproject.commons.dto.{InBoundGetKycChecks, InBoundGetKycMedias, InBoundGetKycStatuses}
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Full}
import org.scalatest.Tag

import scala.collection.immutable.List

class KafkaTest extends KafkaSetup with ServerSetupWithTestData {

  override implicit val formats = CustomJsonFormats.formats
  
  object kafkaTest extends Tag("kafkaTest")
  
  val callContext = Some(
    CallContext(
      gatewayLoginRequestPayload = Some(PayloadOfJwtJSON(
        login_user_name = "",
        is_first = false,
        app_id = "",
        app_name = "",
        time_stamp = "",
        cbs_token = Some(""),
        cbs_id = "",
        session_id = Some(""))),
      user = Full(resourceUser1)
    )
  )

  val PropsConnectorVersion = APIUtil.getPropsValue("connector").openOrThrowException("connector props filed `connector` not set")
  
 
  feature("Send and retrieve message") {
    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getObpConnectorLoopback, if it is mapped connector", kafkaTest) {}
    } else
      scenario("1st test `getObpConnectorLoopback` method, there no need Adapter message for this method!", kafkaTest) {
        //This method is only used for `kafka` connector, should first set `connector=kafka_vSept2018` in test.default.props. 
        //and also need to set up `api_instance_id` and `remotedata.timeout` field for it.
        val propsApiInstanceId = APIUtil.getPropsValue("api_instance_id").openOrThrowException("connector props filed `api_instance_id` not set")
        val propsRemotedataTimeout = APIUtil.getPropsValue("remotedata.timeout").openOrThrowException("connector props filed `remotedata.timeout` not set")
  
        PropsConnectorVersion contains ("kafka") should be (true)
        propsApiInstanceId should be ("1")
        propsRemotedataTimeout should be ("10")
  
        When("We call this method, and get the response. ")
        val future = KafkaHelper.echoKafkaServer
        val result =  future.getContent
  
        Then("If it return value successfully, that mean api <--> kafka is working well. We only need check one filed of response.")
        val connectorVersion= result.connectorVersion
        connectorVersion should be (PropsConnectorVersion)
  
        Then("For KafkaMappedConnector_vSept2018 connector, we need to make these two methods work `getAuthInfoFirstCbsCall` and `getAuthInfo`")
        
        val firstAuthInfo: Box[AuthInfo] = for{
          firstGetAuthInfo <- KafkaMappedConnector_vSept2018.getAuthInfoFirstCbsCall("", callContext)
        } yield {
          (firstGetAuthInfo)
        }
        firstAuthInfo.openOrThrowException("firstAuthInfo Can not be empty here. ")

        val authInfo: Box[AuthInfo] =  for{
          getAuthInfo <- KafkaMappedConnector_vSept2018.getAuthInfo(callContext)
        } yield {
          getAuthInfo
        }
        authInfo.openOrThrowException("firstAuthInfo Can not be empty here. ")

    }

//    if (PropsConnectorVersion =="mapped") {
//      ignore("ignore test processRequest, if it is mapped connector", kafkaTest) {}
//    } else
//      scenario("Send and retrieve message directly to and from kafka", kafkaTest) {
//        val emptyStatusMessage = InboundStatusMessage("", "", "", "")
//        val inBound = InboundGetBanks(InboundAuthInfo("", ""), Status("", List(emptyStatusMessage)), List(InboundBank("1", "2", "3", "4")))
//        When("send a OutboundGetBanks message")
//
//        dispathResponse(inBound)
//        val req = OutboundGetBanks(AuthInfo())
//
//        val future = processRequest[InboundGetBanks](req)
//        val result: Box[InboundGetBanks] = future.getContent
//
//        result should be (Full(inBound))
//    }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getKycStatuses, if it is mapped connector", kafkaTest) {}
    } else
      scenario("test `getKycStatuses` method",kafkaTest) {
        When("send a OutboundGetKycStatuses api message")
        val emptyStatusMessage = InboundStatusMessage("", "", "", "")
        val kycStatusCommons = KycStatusCommons(bankId = "hello_bank_id", customerId = "hello_customer_id", customerNumber = "hello_customer_number", ok = true, date = new Date())
        val singleInboundBank = List(kycStatusCommons)
        val inboundAdapterCallContext = InboundAdapterCallContext(correlationId="some_correlationId")
        val inBound = InBoundGetKycStatuses(inboundAdapterCallContext, Status("", List(emptyStatusMessage)), singleInboundBank)

        dispathResponse(inBound)
        val future = Connector.connector.vend.getKycStatuses(kycStatusCommons.customerId, Some(CallContext()))

        val result: (Box[List[KycStatus]], Option[CallContext]) =  future.getContent
        val expectResult = Full(singleInboundBank)
        result._1.toString should be (expectResult.toString)
     }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getKycChecks, if it is mapped connector", kafkaTest) {}
    } else
      scenario("test `getKycChecks` method", kafkaTest) {
        When("send a OutboundGetKycChecks api message")
        val inBound = Connector.connector.vend.messageDocs.filter(_.process =="obp.getKycChecks").map(_.exampleInboundMessage).head.asInstanceOf[InBoundGetKycChecks]

        dispathResponse(inBound)

        val future = Connector.connector.vend.getKycChecks(inBound.data.head.customerId, Some(CallContext()))
        val result: (Box[List[KycCheck]], Option[CallContext]) =  future.getContent
        val expectResult = Full(inBound.data)
        result._1.toString should be (expectResult.toString)
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getKycMedias, if it is mapped connector", kafkaTest) {}
    } else
      scenario("test `getKycMedias` method",kafkaTest) {
        When("send a OutboundetKycMedias api message")
        val inBound = Connector.connector.vend.messageDocs.filter(_.process =="obp.getKycMedias").map(_.exampleInboundMessage).head.asInstanceOf[InBoundGetKycMedias]

        dispathResponse(inBound)
        val future = Connector.connector.vend.getKycMedias(inBound.data.head.customerId, Some(CallContext()))

        val result: (Box[List[KycMedia]], Option[CallContext]) =  future.getContent
        val expectResult = Full(inBound.data)
        result._1.toString should be (expectResult.toString)
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getAdapterInfo, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getAdapterInfo method",kafkaTest) {
        When("send a getAdapterInfo api message")
        val inBound = Connector.connector.vend.messageDocs.filter(_.process.toString.contains("getAdapterInfo")).map(_.exampleInboundMessage).head.asInstanceOf[InboundAdapterInfo]

        dispathResponse(inBound)
        val future = Connector.connector.vend.getAdapterInfo(None)

        val result: Box[(InboundAdapterInfoInternal, Option[CallContext])] =  future.getContent
        result.map(_._1) should be (Full(inBound.data))
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getUser, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getUser method",kafkaTest) {
        When("send a getUser api message")
        val inBound = Connector.connector.vend.messageDocs.filter(_.process.toString.contains("getUser")).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetUserByUsernamePassword]

        dispathResponse(inBound)
        val box = Connector.connector.vend.getUser("username","password")

        box.map(_.displayName) should be (Full(inBound.data.displayName))
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getBanksFuture, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getBanksFuture method", kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.process.toString.contains("getBanks")).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetBanks]

        dispathResponse(inBound)
        val future = Connector.connector.vend.getBanks(None)

        val result =  future.getContent
        result.map(_._1.head.bankId).toString should be (Full(inBound.data.head.bankId).toString)

      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getBanks, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getBanks method", kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.process.toString.contains("getBanks")).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetBanks]

        dispathResponse(inBound)
        val box = Connector.connector.vend.getBanksLegacy(None)

        box.map(_._1.head.bankId).toString should be (Full(inBound.data.head.bankId).toString)
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getBank, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getBank method", kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundGetBank]).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetBank]

        dispathResponse(inBound)
        val box = Connector.connector.vend.getBankLegacy(BankId(""), None)

        box.map(_._1.bankId).toString should be (Full(inBound.data.bankId).toString)
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getBankFuture, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getBankFuture method",kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundGetBank]).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetBank]

        dispathResponse(inBound)
        val future = Connector.connector.vend.getBank(BankId(""), None)
        val result = future.getContent

        result.map(_._1.bankId).toString should be (Full(inBound.data.bankId).toString)
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getBankAccountsForUserFuture, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getBankAccountsForUserFuture method",kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundGetAccounts]).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetAccounts]

        dispathResponse(inBound)
        val future = Connector.connector.vend.getBankAccountsForUser("", callContext)
        val result = future.getContent

        result.map(_._1.head).toString should be (Full(inBound.data.head).toString)
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getBankAccountsForUser, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getBankAccountsForUser method",kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundGetAccounts]).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetAccounts]
        dispathResponse(inBound)
        val box = Connector.connector.vend.getBankAccountsForUserLegacy("", callContext)

        box.map(_._1.head).toString should be (Full(inBound.data.head).toString)
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getBankAccount, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getBankAccount method",kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundGetAccountbyAccountID]).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetAccountbyAccountID]
        dispathResponse(inBound)
        val box = Connector.connector.vend.getBankAccountLegacy(BankId(""), AccountId(""), callContext)

        box.map(_._1.bankId).toString should be (Full(inBound.data.head.bankId).toString)
        box.map(_._1.accountId).toString should be (Full(inBound.data.head.accountId).toString)
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getBankAccountFuture, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getBankAccountFuture method",kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundGetAccountbyAccountID]).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetAccountbyAccountID]
        dispathResponse(inBound)
        val future = Connector.connector.vend.checkBankAccountExists(BankId(""), AccountId(""), callContext)

        val result = future.getContent

        result._1.map(_.accountId.value).toString should be (Full(inBound.data.head.accountId).toString)
        result._1.map(_.bankId.value).toString should be (Full(inBound.data.head.bankId).toString)
        
      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getChallengeThreshold, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getChallengeThreshold method",kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundGetChallengeThreshold]).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetChallengeThreshold]
        dispathResponse(inBound)
        val future = Connector.connector.vend.getChallengeThreshold("","","","","","","", callContext)

        val result = future.getContent

        result._1.map(_.amount).toString should be (Full(inBound.data.amount).toString)
        result._1.map(_.currency).toString should be (Full(inBound.data.currency).toString)

      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test makePaymentv210, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test makePaymentv210 method",kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundCreateTransactionId]).map(_.exampleInboundMessage).head.asInstanceOf[InboundCreateTransactionId]
        dispathResponse(inBound)
        
        val fromAccount = BankAccountSept2018(KafkaMappedConnector_vSept2018.inboundAccountSept2018Example)
        val toAccount = BankAccountSept2018(KafkaMappedConnector_vSept2018.inboundAccountSept2018Example)
        val transactionRequestId = TransactionRequestId(UUID.randomUUID().toString)
        val transactionRequestCommonBody = TransactionRequestBodyCommonJSON(AmountOfMoneyJsonV121("",""),"")
        val future = Connector.connector.vend.makePaymentv210(
          fromAccount,
          toAccount,
          transactionRequestId,
          transactionRequestCommonBody,
          10,
          "",
          TransactionRequestType("SANDBOX_TAN"),
          "", 
          callContext)

        val result = future.getContent

        result._1.map(_.value).toString should be (Full(inBound.data.id).toString)

      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test createChallenge, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test createChallenge method",kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundCreateChallengeSept2018]).map(_.exampleInboundMessage).head.asInstanceOf[InboundCreateChallengeSept2018]
        dispathResponse(inBound)

        val account = BankAccountSept2018(KafkaMappedConnector_vSept2018.inboundAccountSept2018Example)
        val transactionRequestCommonBody = TransactionRequestBodyCommonJSON(AmountOfMoneyJsonV121("",""),"")
        val future = Connector.connector.vend.createChallenge(
          account.bankId,
          account.accountId,
          "",
          TransactionRequestType("SANDBOX_TAN"),
          "",
          None,
          callContext)

        val result = future.getContent

        result._1.toString should be (Full(inBound.data.answer).toString)

      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test createCounterparty, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test createCounterparty method",kafkaTest) {
        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundCreateCounterparty]).map(_.exampleInboundMessage).head.asInstanceOf[InboundCreateCounterparty]
        val outBound = Connector.connector.vend.messageDocs.filter(_.exampleOutboundMessage.isInstanceOf[OutboundCreateCounterparty]).map(_.exampleOutboundMessage).head.asInstanceOf[OutboundCreateCounterparty]
        dispathResponse(inBound)

        val account = BankAccountSept2018(KafkaMappedConnector_vSept2018.inboundAccountSept2018Example)
        val transactionRequestCommonBody = TransactionRequestBodyCommonJSON(AmountOfMoneyJsonV121("",""),"")
        val box = Connector.connector.vend.createCounterparty(
          outBound.counterparty.name,
          outBound.counterparty.description,                       
          outBound.counterparty.currency,
          outBound.counterparty.createdByUserId,
          outBound.counterparty.thisBankId,                
          outBound.counterparty.thisAccountId,             
          outBound.counterparty.thisViewId,                
          outBound.counterparty.otherAccountRoutingScheme, 
          outBound.counterparty.otherAccountRoutingAddress,
          outBound.counterparty.otherAccountSecondaryRoutingScheme,
          outBound.counterparty.otherAccountSecondaryRoutingAddress,
          outBound.counterparty.otherBankRoutingScheme,    
          outBound.counterparty.otherBankRoutingAddress,   
          outBound.counterparty.otherBranchRoutingScheme,  
          outBound.counterparty.otherBranchRoutingAddress,  
          outBound.counterparty.isBeneficiary,  
          outBound.counterparty.bespoke,  
          callContext)


        box.map(_._1.counterpartyId) should be (Full(inBound.data.get.counterpartyId))
        box.map(_._1.createdByUserId) should be (Full(inBound.data.get.createdByUserId))

      }

    if (PropsConnectorVersion =="mapped") {
      ignore("ignore test getTransactionRequests210, if it is mapped connector", kafkaTest) {}
    } else
      scenario(s"test getTransactionRequests210 method",kafkaTest) {

        val inBound = Connector.connector.vend.messageDocs.filter(_.exampleInboundMessage.isInstanceOf[InboundGetTransactionRequests210]).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetTransactionRequests210]
        dispathResponse(inBound)

        val account = BankAccountSept2018(KafkaMappedConnector_vSept2018.inboundAccountSept2018Example)
        val transactionRequestCommonBody = TransactionRequestBodyCommonJSON(AmountOfMoneyJsonV121("",""),"")
        val box = Connector.connector.vend.getTransactionRequests210(
          resourceUser1,
          account,
          callContext)

       box.map(_._1.head.body) should be (inBound.data.head.body) 


      }

  }
}
