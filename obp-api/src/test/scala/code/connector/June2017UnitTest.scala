package code.connector


import code.api.util.APIUtil.MessageDoc
import code.api.util.CustomJsonFormats
import code.bankconnectors.vJune2017.{InboundGetTransactionRequests210, InternalGetTransactionRequests, KafkaMappedConnector_vJune2017, OutboundGetTransactionRequests210}
import com.openbankproject.commons.model.TransactionRequest
import code.util.Helper.MdcLoggable
import net.liftweb.json.Extraction
import net.liftweb.json.JsonAST.JValue
import org.scalatest._

import scala.collection.mutable.ArrayBuffer

class June2017UnitTest extends FunSuite
  with Matchers
  with MdcLoggable with CustomJsonFormats {
  
  
  test("getTransactionRequests210 kafka message") {
    val messageDocs: ArrayBuffer[MessageDoc] = KafkaMappedConnector_vJune2017.messageDocs.filter(_.process=="obp.get.transactionRequests210")
    val outboundMessage: JValue = Extraction.decompose(messageDocs.head.exampleOutboundMessage)
    val inboundMessage: JValue = Extraction.decompose(messageDocs.head.exampleInboundMessage)
  
  
    val outboundGetTransactionRequests210: OutboundGetTransactionRequests210 = outboundMessage.extract[OutboundGetTransactionRequests210]
    
    val inboundGetTransactionRequests210: InboundGetTransactionRequests210 = inboundMessage.extract[InboundGetTransactionRequests210]
    val internalGetTransactionRequests: List[TransactionRequest] = inboundGetTransactionRequests210.data
  
  
    outboundGetTransactionRequests210 shouldBe a [OutboundGetTransactionRequests210]
    internalGetTransactionRequests shouldBe a [List[_]]
    
  }

 
  
}
