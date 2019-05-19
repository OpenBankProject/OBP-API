package code.kafka

import java.util.Date

import code.api.util.{APIUtil, CallContext, ExampleValue}
import code.bankconnectors.vMar2017.InboundBank
import code.bankconnectors.vSept2018._
import code.setup.KafkaSetup
import com.openbankproject.commons.dto.{InBoundGetKycChecks, InBoundGetKycMedias, InBoundGetKycStatuses}
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Full}

import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer

class KafkaTest extends KafkaSetup {

  feature("Send and retrieve message") {
    scenario("1st test `getObpApiLoopback` method, there no need Adapter message for this method!") {
      //This method is only used for `kafka` connector, should first set `connector=kafka_vSept2018` in test.default.props. 
      //and also need to set up `api_instance_id` and `remotedata.timeout` field for it.
      val PropsConnectorVersion = APIUtil.getPropsValue("connector").openOrThrowException("connector props filed `connector` not set")
      val propsApiInstanceId = APIUtil.getPropsValue("api_instance_id").openOrThrowException("connector props filed `api_instance_id` not set")
      val propsRemotedataTimeout = APIUtil.getPropsValue("remotedata.timeout").openOrThrowException("connector props filed `remotedata.timeout` not set")
      
      PropsConnectorVersion contains ("kafka") should be (true)
      propsApiInstanceId should be ("1")
      propsRemotedataTimeout should be ("10")

      When("We call this method, and get the response. ")
      val future = KafkaMappedConnector_vSept2018.getObpApiLoopback(Some(CallContext()))
      val result: (Box[ObpApiLoopback], Option[CallContext]) =  future.getContent

      Then("If it return value successfully, that mean api <--> kafka is working well. We only need check one filed of response.")
      val connectorVersion= result._1.map(_.connectorVersion)
      connectorVersion should be (Full(PropsConnectorVersion))
    }
    
    scenario("Send and retrieve message directly to and from kafka") {
      val emptyStatusMessage = InboundStatusMessage("", "", "", "")
      val inBound = InboundGetBanks(InboundAuthInfo("", ""), Status("", List(emptyStatusMessage)), List(InboundBank("1", "2", "3", "4")))
      When("send a OutboundGetBanks message")

      dispathResponse(inBound)
      val req = OutboundGetBanks(AuthInfo())

      val future = processRequest[InboundGetBanks](req)
      val result: Box[InboundGetBanks] = future.getContent

      result should be (Full(inBound))
    }

    scenario("test `getKycStatuses` method") {
      When("send a OutboundGetKycStatuses api message")
      val emptyStatusMessage = InboundStatusMessage("", "", "", "")
      val kycStatusCommons = KycStatusCommons(bankId = "hello_bank_id", customerId = "hello_customer_id", customerNumber = "hello_customer_number", ok = true, date = new Date())
      val singleInboundBank = List(kycStatusCommons)
      val inboundAdapterCallContext = InboundAdapterCallContext(correlationId="some_correlationId")
      val inBound = InBoundGetKycStatuses(inboundAdapterCallContext, Status("", List(emptyStatusMessage)), singleInboundBank)

      dispathResponse(inBound)
      val future = KafkaMappedConnector_vSept2018.getKycStatuses(kycStatusCommons.customerId, Some(CallContext()))

      val result: (Box[List[KycStatus]], Option[CallContext]) =  future.getContent
      val expectResult = Full(singleInboundBank)
      result._1.toString should be (expectResult.toString)
    }

    scenario("test `getKycChecks` method") {
      When("send a OutboundGetKycChecks api message")
      val inBound = KafkaMappedConnector_vSept2018.messageDocs.filter(_.process =="obp.getKycChecks").map(_.exampleInboundMessage).head.asInstanceOf[InBoundGetKycChecks]

      dispathResponse(inBound)

      val future = KafkaMappedConnector_vSept2018.getKycChecks(inBound.data.head.customerId, Some(CallContext()))
      val result: (Box[List[KycCheck]], Option[CallContext]) =  future.getContent
      val expectResult = Full(inBound.data)
      result._1.toString should be (expectResult.toString)
    }

    scenario("test `getKycMedias` method") {
      When("send a OutboundetKycMedias api message")
      val inBound = KafkaMappedConnector_vSept2018.messageDocs.filter(_.process =="obp.getKycMedias").map(_.exampleInboundMessage).head.asInstanceOf[InBoundGetKycMedias]

      dispathResponse(inBound)
      val future = KafkaMappedConnector_vSept2018.getKycMedias(inBound.data.head.customerId, Some(CallContext()))

      val result: (Box[List[KycMedia]], Option[CallContext]) =  future.getContent
      val expectResult = Full(inBound.data)
      result._1.toString should be (expectResult.toString)
    }

    scenario(s"test getAdapterInfo method") {
      When("send a getAdapterInfo api message")
      val inBound = KafkaMappedConnector_vSept2018.messageDocs.filter(_.process.toString.contains("getAdapterInfo")).map(_.exampleInboundMessage).head.asInstanceOf[InboundAdapterInfo]

      dispathResponse(inBound)
      val future = KafkaMappedConnector_vSept2018.getAdapterInfo(None)

      val result: Box[(InboundAdapterInfoInternal, Option[CallContext])] =  future.getContent
      result.map(_._1) should be (Full(inBound.data))
    }

    scenario(s"test getUser method") {
      When("send a getUser api message")
      val inBound = KafkaMappedConnector_vSept2018.messageDocs.filter(_.process.toString.contains("getUser")).map(_.exampleInboundMessage).head.asInstanceOf[InboundGetUserByUsernamePassword]

      dispathResponse(inBound)
      val box = KafkaMappedConnector_vSept2018.getUser("username","password")

      box.map(_.displayName) should be (Full(inBound.data.displayName))
    }
  }
}
