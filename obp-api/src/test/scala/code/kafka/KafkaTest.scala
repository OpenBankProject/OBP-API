package code.kafka

import java.util.Date

import code.api.util.{CallContext, ExampleValue}
import code.bankconnectors.vMar2017.{InboundBank}
import code.bankconnectors.vSept2018._
import code.setup.KafkaSetup
import com.openbankproject.commons.dto.InBoundGetKycStatuses
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Full}
import net.liftweb.json

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, _}

class KafkaTest extends KafkaSetup {
  val waitTime: Duration = (10 second)


  feature("Send and retrieve message") {
    scenario("Send and retrieve message directly to and from kafka") {
      val emptyStatusMessage = InboundStatusMessage("", "", "", "")
      val inBound = InboundGetBanks(InboundAuthInfo("", ""), Status("", List(emptyStatusMessage)), List(InboundBank("1", "2", "3", "4")))
      When("send a OutboundGetBanks message")

      dispathResponse(inBound)
      val req = OutboundGetBanks(AuthInfo())

      val future = processToFuture[OutboundGetBanks](req)
      val result:json.JValue =  Await.result(future, waitTime)

      val banks = result.extract[InboundGetBanks]
      banks should be equals (inBound)
    }

    /**
      * override val bankId: String,
      * override val customerId: String,
      * override val customerNumber : String,
      * override val ok : Boolean,
      * override val date : Date
      */
    scenario("Send and retrieve api message") {
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
    }
  }
}
