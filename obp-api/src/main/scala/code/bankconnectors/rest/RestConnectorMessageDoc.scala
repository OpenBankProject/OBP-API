package code.bankconnectors.rest

import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions._
import code.api.util.APIUtil.MessageDoc
import code.bankconnectors.akka.AkkaConnector_vDec2018
import com.openbankproject.commons.dto.{InboundGetBanks, OutboundGetBanks}

import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer

object RestConnector {
  val messageDocs = ArrayBuffer[MessageDoc]()
  val messageFormat: String = "March2019"
  
  messageDocs += MessageDoc(
    process = "obp.get.Banks",
    messageFormat = messageFormat,
    description = "Gets the banks list on this OBP installation.",
    outboundTopic = Some(OutboundGetBanks.getClass.getSimpleName.replace("$", "")),
    inboundTopic = Some(InboundGetBanks.getClass.getSimpleName.replace("$", "")),
    exampleOutboundMessage = (
      OutboundGetBanks(Some(callContextAkka))
    ),
    exampleInboundMessage = (
      InboundGetBanks(
        Some(List(inboundBank)),
        Some(callContextAkka))
    ),
    adapterImplementation = Some(adapterImplementation)
  )
  
  AkkaConnector_vDec2018.messageDocs.map(messageDoc => messageDocs += messageDoc)
}