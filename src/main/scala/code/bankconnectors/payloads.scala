package code.bankconnectors

import java.util.Date

/**
  * Created by slavisa on 6/5/17.
  */
case class AuthInfo(userId: String, username: String)

/**
  * case classes used to define topics
  */
sealed trait TopicCaseClass
case class GetBanks(authInfo: AuthInfo, criteria: String) extends TopicCaseClass
case class GetBank(authInfo: AuthInfo, bankId: String) extends TopicCaseClass
case class GetUserByUsernamePassword(username: String, password: String) extends TopicCaseClass
case class UpdateUserAccountViews(username: String, password: String) extends TopicCaseClass
case class GetAdapterInfo(date: String) extends TopicCaseClass


/**
  * case classes used as payloads
  */
case class Banks(authInfo: AuthInfo, data: List[InboundBank])
case class BankWrapper(authInfo: AuthInfo, data: InboundBank)
case class AdapterInfo(data: InboundAdapterInfo)