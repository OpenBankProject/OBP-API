package code.bankconnectors.akka

import code.api.util.CallContext


/**
  * 
  * case classes used to define outbound Akka messages
  * 
  */
case class OutboundGetAdapterInfo(date: String, callContext: Option[CallContext])
case class OutboundGetBanks(callContext: Option[CallContext])
case class OutboundGetBank(bankId: String, callContext: Option[CallContext])
