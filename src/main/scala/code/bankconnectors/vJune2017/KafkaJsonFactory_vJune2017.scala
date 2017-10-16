package code.bankconnectors.vJune2017

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import code.api.util.APIUtil.InboundMessageBase
import code.api.v2_1_0.{PostCounterpartyBespoke, TransactionRequestCommonBodyJSON}
import code.bankconnectors._
import code.bankconnectors.vMar2017._
import code.customer.Customer
import code.kafka.Topics._
import code.metadata.counterparties.CounterpartyTrait
import code.model.dataAccess.MappedBankAccountData
import code.model.{AccountId, BankAccount, BankId}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.today

/**
  * case classes used to define topics, these are outbound kafka messages
  */

case class GetAdapterInfo(date: String) extends GetAdapterInfoTopic
case class GetBanks(authInfo: AuthInfo) extends GetBanksTopic
case class GetBank(authInfo: AuthInfo, bankId: String) extends GetBankTopic
case class GetUserByUsernamePassword(authInfo: AuthInfo, password: String) extends GetUserByUsernamePasswordTopic
case class OutboundGetAccounts(authInfo: AuthInfo, customers:InternalBasicCustomers ) extends GetAccountsTopic
case class GetAccountbyAccountID(authInfo: AuthInfo, bankId: String, accountId: String)extends GetAccountbyAccountIDTopic
case class GetAccountbyAccountNumber(authInfo: AuthInfo, bankId: String, accountNumber: String)extends GetAccountbyAccountNumberTopic
case class GetTransactions(authInfo: AuthInfo,bankId: String, accountId: String, limit: Int, fromDate: String, toDate: String) extends GetTransactionsTopic
case class GetTransaction(authInfo: AuthInfo, bankId: String, accountId: String, transactionId: String) extends GetTransactionTopic
case class CreateCBSAuthToken(authInfo: AuthInfo) extends CreateCBSAuthTokenTopic
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

) extends CreateTransactionTopic

case class OutboundCreateChallengeJune2017(
  authInfo: AuthInfo,
  bankId: String,
  accountId: String,
  userId: String,
  username: String,
  transactionRequestType: String,
  transactionRequestId: String,
  phoneNumber: String
) extends OutboundCreateChallengeJune2017Topic


case class OutboundCounterparty(
  name: String,
  description: String,
  createdByUserId: String,
  thisBankId: String,
  thisAccountId: String,
  thisViewId: String,
  otherAccountRoutingScheme: String,
  otherAccountRoutingAddress: String,
  otherAccountSecondaryRoutingScheme: String,
  otherAccountSecondaryRoutingAddress: String,
  otherBankRoutingScheme: String,
  otherBankRoutingAddress: String,
  otherBranchRoutingScheme: String,
  otherBranchRoutingAddress: String,
  isBeneficiary:Boolean,
  bespoke: List[PostCounterpartyBespoke]
)
case class OutboundCreateCounterparty(
  authInfo: AuthInfo,
  counterparty: OutboundCounterparty
) extends OutboundCreateCounterpartyTopic

/**
  * case classes used in Kafka message, these are InBound Kafka messages
  */

//AdapterInfo has no AuthInfo, because it just get data from Adapter, no need for AuthInfo
case class AdapterInfo(data: InboundAdapterInfo)
case class UserWrapper(authInfo: AuthInfo, data: InboundValidatedUser)
case class Banks(authInfo: AuthInfo, data: List[InboundBank])
case class BankWrapper(authInfo: AuthInfo, data: InboundBank)
case class InboundBankAccounts(authInfo: AuthInfo, data: List[InboundAccountJune2017])
case class InboundBankAccount(authInfo: AuthInfo, data: InboundAccountJune2017)
case class InboundTransactions(authInfo: AuthInfo, data: List[InternalTransaction])
case class InboundTransaction(authInfo: AuthInfo, data: InternalTransaction)
case class InboundCreateChallengeJune2017(authInfo: AuthInfo, data: InternalCreateChallengeJune2017)
case class InboundCreateCounterparty(authInfo: AuthInfo, data: InternalCreateCounterparty)








////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////
// These are case classes, used in internal message mapping
case class AuthInfo(userId: String, username: String, cbsToken: String)
case class InboundAccountJune2017(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  cbsToken: String, //TODO, this maybe move to AuthInfo, but it is used in GatewayLogin
  bankId: String,
  branchId: String,
  accountId: String,
  accountNumber: String,
  accountType: String,
  balanceAmount: String,
  balanceCurrency: String,
  owners: List[String],
  viewsToGenerate: List[String],
  bankRoutingScheme: String,
  bankRoutingAddress: String,
  branchRoutingScheme: String,
  branchRoutingAddress: String,
  accountRoutingScheme: String,
  accountRoutingAddress: String
) extends InboundMessageBase with InboundAccountCommon

case class BankAccountJune2017(r: InboundAccountJune2017) extends BankAccount {
  
  def accountId: AccountId = AccountId(r.accountId)
  def accountType: String = r.accountType
  def balance: BigDecimal = BigDecimal(r.balanceAmount)
  def currency: String = r.balanceCurrency
  def name: String = r.owners.head
  // Note: swift_bic--> swiftBic, but it extends from BankAccount
  def swift_bic: Option[String] = Some("swift_bic")
  // Note: deprecated, extends from BankAccount
  def iban: Option[String] = Some("iban")
  def number: String = r.accountNumber
  def bankId: BankId = BankId(r.bankId)
  def lastUpdate: Date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(today.getTime.toString)
  def accountHolder: String = r.owners.head
  
  // Fields modifiable from OBP are stored in mapper
  def label: String = (for {
    d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, r.accountId))
  } yield {
    d.getLabel
  }).getOrElse(r.accountNumber)
  
  def accountRoutingScheme: String = r.accountRoutingScheme
  def accountRoutingAddress: String = r.accountRoutingAddress
  def branchId: String = r.branchId
  
}

case class InternalBasicCustomer(
  bankId:String,
  customerId: String,
  customerNumber: String,
  legalName: String,
  dateOfBirth: Date
)

case class InternalBasicCustomers(customers: List[InternalBasicCustomer])

case class InternalCreateChallengeJune2017(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  answer : String
)

case class InternalCreateCounterparty(
  status: String,
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  createdByUserId: String,
  name: String,
  thisBankId: String,
  thisAccountId: String,
  thisViewId: String,
  counterpartyId: String,
  otherAccountRoutingScheme: String,
  otherAccountRoutingAddress: String,
  otherBankRoutingScheme: String,
  otherBankRoutingAddress: String,
  otherBranchRoutingScheme: String,
  otherBranchRoutingAddress: String,
  isBeneficiary: Boolean,
  description: String,
  otherAccountSecondaryRoutingScheme: String,
  otherAccountSecondaryRoutingAddress: String,
  bespoke: List[PostCounterpartyBespoke]
) extends CounterpartyTrait


object JsonFactory_vJune2017 {
  def createCustomerJson(customer : Customer) : InternalBasicCustomer = {
    InternalBasicCustomer(
      bankId=customer.bankId,
      customerId = customer.customerId,
      customerNumber = customer.number,
      legalName = customer.legalName,
      dateOfBirth = customer.dateOfBirth
    )
  }
  
  def createCustomersJson(customers : List[Customer]) : InternalBasicCustomers = {
    InternalBasicCustomers(customers.map(createCustomerJson))
  }
}