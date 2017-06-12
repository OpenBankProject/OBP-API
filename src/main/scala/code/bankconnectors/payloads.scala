package code.bankconnectors

/**
  * Created by slavisa on 6/5/17.
  */
case class AuthInfo(userId: String, username: String)
case class GetBanks(authInfo: AuthInfo, version: String)
case class Banks(authInfo: AuthInfo, data: List[InboundBank])