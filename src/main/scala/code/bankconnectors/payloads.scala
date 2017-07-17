package code.bankconnectors

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
case class GetAccounts(authInfo: AuthInfo) extends TopicCaseClass
case class GetAccountbyAccountID(authInfo: AuthInfo, bankId: String, accountId: String)
case class GetAccountbyAccountNumber(authInfo: AuthInfo, bankId: String, accountNumber: String)
case class GetTransactions(authInfo: AuthInfo,bankId: String, accountId: String, queryParams: String)
/**
  * case classes used as payloads
  */
case class Banks(authInfo: AuthInfo, data: List[InboundBank])
case class BankWrapper(authInfo: AuthInfo, data: InboundBank)
case class AdapterInfo(data: InboundAdapterInfo)
case class InboundBankAccounts(authInfo: AuthInfo, data: List[InboundAccountJune2017])
case class InboundBankAccount(authInfo: AuthInfo, data: InboundAccountJune2017)
case class InboundTransactions(authInfo: AuthInfo, data: List[InternalTransaction])