package code.bankconnectors.akka


/**
  * 
  * case classes used to define outbound Akka messages
  * 
  */
case class OutboundGetBanks(authInfo: AuthInfo)


/**
  * 
  * These are case classes, used in internal message mapping
  * 
  */
case class AuthInfo(userId: String = "", 
                    username: String = "", 
                    cbsToken: String = "", 
                    isFirst: Boolean = true, 
                    correlationId: String = "", 
                    sessionId: String = ""
                   )
