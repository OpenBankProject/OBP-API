package code.bankconnectors.akka

import code.api.util.CallContext


/**
  * 
  * case classes used to define outbound Akka messages
  * 
  */
case class OutboundGetAdapterInfo(callContext: Option[CallContext], date: String)
case class OutboundGetBanks(callContext: Option[CallContext])
case class OutboundGetBank(callContext: Option[CallContext], bankId: String)
