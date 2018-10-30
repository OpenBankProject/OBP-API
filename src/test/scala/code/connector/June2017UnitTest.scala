package code.connector


import code.api.util.APIUtil.MessageDoc
import code.bankconnectors.vJune2017.{InboundGetTransactionRequests210, InternalGetTransactionRequests, KafkaMappedConnector_vJune2017, OutboundGetTransactionRequests210}
import code.transactionrequests.TransactionRequests.TransactionRequest
import code.util.Helper.MdcLoggable
import net.liftweb.json.JsonAST.JValue
import org.scalatest._

import scala.collection.mutable.ArrayBuffer

class June2017UnitTest extends FunSuite
  with Matchers
  with MdcLoggable{
  
  
  implicit val formats = net.liftweb.json.DefaultFormats
  
  
  test("getTransactionRequests210 kafka message") {
    val messageDocs: ArrayBuffer[MessageDoc] = KafkaMappedConnector_vJune2017.messageDocs.filter(_.process=="obp.get.transactionRequests210")
    val outboundMessage: JValue = messageDocs.head.exampleOutboundMessage
    val inboundMessage: JValue = messageDocs.head.exampleInboundMessage
  
  
    val outboundGetTransactionRequests210: OutboundGetTransactionRequests210 = outboundMessage.extract[OutboundGetTransactionRequests210]
    
    val inboundGetTransactionRequests210: InboundGetTransactionRequests210 = inboundMessage.extract[InboundGetTransactionRequests210]
    val internalGetTransactionRequests: List[TransactionRequest] = inboundGetTransactionRequests210.data
  
  
    outboundGetTransactionRequests210 shouldBe a [OutboundGetTransactionRequests210]
    internalGetTransactionRequests shouldBe a [List[_]]
    
  }

 
  
}
