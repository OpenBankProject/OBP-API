package code.kafka

import java.util.Date

import code.api.util.{APIUtil, CallContext, ExampleValue}
import code.bankconnectors.vMar2017.InboundBank
import code.bankconnectors.vSept2018._
import code.setup.KafkaSetup
import com.openbankproject.commons.dto.{InBoundGetKycChecks, InBoundGetKycStatuses}
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Full}
import net.liftweb.json

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, _}

class KafkaTest extends KafkaSetup {
  val waitTime: Duration = (10 second)


  feature("Send and retrieve message") {
    scenario("1st test `getObpApiLoopback` method, there no need Adapter message for this method!") {
      //This method is only used for `kafka` connector, should first set `connector=kafka_vSept2018` in test.default.props. 
      val expectedConnectorVersion = APIUtil.getPropsValue("connector").openOrThrowException("connector props filed not set")
      expectedConnectorVersion contains ("kafka") should be (true)

      When("We call this method, and get the response. ")
      val future = KafkaMappedConnector_vSept2018.getObpApiLoopback(Some(CallContext()))
      val result: (Box[ObpApiLoopback], Option[CallContext]) =  Await.result(future, waitTime)

      Then("If it return value successfully, that mean api <--> kafka is working well. We only need check one filed of response.")
      val connectorVersion= result._1.map(_.connectorVersion)
      connectorVersion should be (Full(expectedConnectorVersion))
    }
    
    scenario("Send and retrieve message directly to and from kafka") {
      val emptyStatusMessage = InboundStatusMessage("", "", "", "")
      val inBound = InboundGetBanks(InboundAuthInfo("", ""), Status("", List(emptyStatusMessage)), List(InboundBank("1", "2", "3", "4")))
      When("send a OutboundGetBanks message")

      dispathResponse(inBound)
      val req = OutboundGetBanks(AuthInfo())

      val future = processRequest[InboundGetBanks](req)
      val result: Box[InboundGetBanks] = Await.result(future, waitTime)

      result should be (Full(inBound))
    }

    /**
      * override val bankId: String,
      * override val customerId: String,
      * override val customerNumber : String,
      * override val ok : Boolean,
      * override val date : Date
      */
    scenario("test `getKycStatuses` method") {
      When("send a OutboundGetKycStatuses api message")
      val emptyStatusMessage = InboundStatusMessage("", "", "", "")
      val kycStatusCommons = KycStatusCommons(bankId = "hello_bank_id", customerId = "hello_customer_id", customerNumber = "hello_customer_number", ok = true, date = new Date())
      val singleInboundBank = List(kycStatusCommons)
      val inboundAdapterCallContext = InboundAdapterCallContext(correlationId="some_correlationId")
      val inBound = InBoundGetKycStatuses(inboundAdapterCallContext, Status("", List(emptyStatusMessage)), singleInboundBank)

      dispathResponse(inBound)

      val future = KafkaMappedConnector_vSept2018.getKycStatuses(kycStatusCommons.customerId, Some(CallContext()))
      val result: (Box[List[KycStatus]], Option[CallContext]) =  Await.result(future, waitTime)
      val expectResult = Full(singleInboundBank)
      result._1 should be equals(expectResult)
      result._1 should be equals(123)
      result._1.toString should be (expectResult.toString)
    }

    scenario("test `getKycChecks` method") {
      When("send a OutboundGetKycStatuses api message")
      val inBound = KafkaMappedConnector_vSept2018.messageDocs.filter(_.process =="obp.getKycChecks").map(_.exampleInboundMessage).head.asInstanceOf[InBoundGetKycChecks]

      dispathResponse(inBound)

      val future = KafkaMappedConnector_vSept2018.getKycChecks(inBound.data.head.customerId, Some(CallContext()))
      val result: (Box[List[KycCheck]], Option[CallContext]) =  Await.result(future, waitTime)
      val expectResult = Full(inBound.data)
      result._1.toString should be (expectResult.toString)
    }
  }
}
