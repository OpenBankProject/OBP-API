package code.bankconnectors

/**
  * Created by slavisa on 6/5/17.
  */
case class AuthInfo(userId: String, username: String, cbsToken: String)

/**
  * case classes used to define topics
  */
sealed trait TopicCaseClass
//This is special, it is only internal, will not call any JONI call,so no AuthInfo inside
case class GetAdapterInfo(date: String) extends TopicCaseClass
case class GetBanks(authInfo: AuthInfo, criteria: String) extends TopicCaseClass
case class GetBank(authInfo: AuthInfo, bankId: String) extends TopicCaseClass
case class GetUserByUsernamePassword(authInfo: AuthInfo, password: String) extends TopicCaseClass
case class GetAccounts(authInfo: AuthInfo) extends TopicCaseClass
case class GetAccountbyAccountID(authInfo: AuthInfo, bankId: String, accountId: String)extends TopicCaseClass
case class GetAccountbyAccountNumber(authInfo: AuthInfo, bankId: String, accountNumber: String)extends TopicCaseClass
case class GetTransactions(authInfo: AuthInfo,bankId: String, accountId: String, queryParams: String) extends TopicCaseClass
case class GetTransaction(authInfo: AuthInfo, bankId: String, accountId: String, transactionId: String) extends TopicCaseClass
case class CreateCBSAuthToken(authInfo: AuthInfo) extends TopicCaseClass
/**
  * case classes used as payloads
  */
case class AdapterInfo(data: InboundAdapterInfo)
case class UserWrapper(data: Option[InboundValidatedUser])
case class Banks(authInfo: AuthInfo, data: List[InboundBank])
case class BankWrapper(authInfo: AuthInfo, data: InboundBank)
case class InboundBankAccounts(authInfo: AuthInfo, data: List[InboundAccountJune2017])
case class InboundBankAccount(authInfo: AuthInfo, data: InboundAccountJune2017)
case class InboundTransactions(authInfo: AuthInfo, data: List[InternalTransaction])
case class InboundTransaction(authInfo: AuthInfo, data: InternalTransaction)