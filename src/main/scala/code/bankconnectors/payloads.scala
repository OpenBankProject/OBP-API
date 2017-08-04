package code.bankconnectors

import code.api.v2_1_0.TransactionRequestCommonBodyJSON


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
case class GetTransactions(authInfo: AuthInfo,bankId: String, accountId: String, limit: Int, fromDate: String, toDate: String) extends TopicCaseClass
case class GetTransaction(authInfo: AuthInfo, bankId: String, accountId: String, transactionId: String) extends TopicCaseClass
case class CreateCBSAuthToken(authInfo: AuthInfo) extends TopicCaseClass
case class CreateTransaction(
  authInfo: AuthInfo,
  
  // fromAccount
  fromAccountBankId : String,
  fromAccountId : String,
  
  // transaction details
  transactionRequestType: String,
  transactionChargePolicy: String,
  transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
  
  // toAccount or toCounterparty
  toCounterpartyId: String,
  toCounterpartyName: String,
  toCounterpartyCurrency: String,
  toCounterpartyRoutingAddress: String,
  toCounterpartyRoutingScheme: String,
  toCounterpartyBankRoutingAddress: String,
  toCounterpartyBankRoutingScheme: String
  
) extends TopicCaseClass

case class OutboundCreateChallengeJune2017(
  authInfo: AuthInfo,
  bankId: String,
  accountId: String,
  userId: String,
  username: String,
  transactionRequestType: String,
  transactionRequestId: String,
  phoneNumber: String
) extends TopicCaseClass

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
case class InboundCreateTransactionId(authInfo: AuthInfo, data: InternalTransactionId)
case class InboundCreateChallengeJune2017(authInfo: AuthInfo, data: InternalCreateChallengeJune2017)
