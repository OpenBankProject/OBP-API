package code.bankconnectors.vJune2017

import java.lang
import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import code.api.util.APIUtil.InboundMessageBase
import code.api.v1_2_1.AccountRoutingJsonV121
import code.api.v2_1_0.PostCounterpartyBespoke
import code.bankconnectors._
import code.bankconnectors.vMar2017._
import code.customer.{Customer, MockCreditLimit, MockCreditRating, MockCustomerFaceImage}
import code.kafka.Topics._
import code.metadata.counterparties.CounterpartyTrait
import code.model.dataAccess.MappedBankAccountData
import code.model.{AmountOfMoney => _, _}
import code.transactionrequests.TransactionRequests.TransactionRequest
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.today

import scala.collection.immutable.List

/**
  * case classes used to define topics, these are outbound kafka messages
  */

case class OutboundGetAdapterInfo(date: String) extends TopicTrait
case class OutboundGetBanks(authInfo: AuthInfo) extends TopicTrait
case class OutboundGetBank(authInfo: AuthInfo, bankId: String) extends TopicTrait
case class OutboundGetUserByUsernamePassword(authInfo: AuthInfo, password: String) extends TopicTrait
case class OutboundGetAccounts(authInfo: AuthInfo, callMfFlag: Boolean, customers:InternalBasicCustomers) extends TopicTrait
case class OutboundGetAccountbyAccountID(authInfo: AuthInfo, bankId: String, accountId: String)extends TopicTrait
case class OutboundCheckBankAccountExists(authInfo: AuthInfo, bankId: String, accountId: String)extends TopicTrait
case class OutboundGetCoreBankAccounts(authInfo: AuthInfo, bankIdAccountIds: List[BankIdAccountId])extends TopicTrait
case class OutboundGetTransactions(authInfo: AuthInfo,bankId: String, accountId: String, limit: Int, fromDate: String, toDate: String) extends TopicTrait
case class OutboundGetTransaction(authInfo: AuthInfo, bankId: String, accountId: String, transactionId: String) extends TopicTrait
case class OutboundCreateChallengeJune2017(
  authInfo: AuthInfo,
  bankId: String,
  accountId: String,
  userId: String,
  username: String,
  transactionRequestType: String,
  transactionRequestId: String
) extends TopicTrait

case class OutboundCreateCounterparty(
  authInfo: AuthInfo,
  counterparty: OutboundCounterparty
) extends TopicTrait

case class OutboundGetTransactionRequests210(
  authInfo: AuthInfo,
  counterparty: OutboundTransactionRequests
) extends TopicTrait

case class OutboundGetCounterparties(
  authInfo: AuthInfo,
  counterparty: InternalOutboundGetCounterparties
) extends TopicTrait

case class OutboundGetCustomersByUserId(
  authInfo: AuthInfo
) extends TopicTrait

/**
  * case classes used in Kafka message, these are InBound Kafka messages
  */

//AdapterInfo has no AuthInfo, because it just get data from Adapter, no need for AuthInfo
case class InboundAdapterInfo(data: InboundAdapterInfoInternal)
case class InboundGetUserByUsernamePassword(authInfo: AuthInfo, data: InboundValidatedUser)
case class InboundGetBanks(authInfo: AuthInfo, data: List[InboundBank])
case class InboundGetBank(authInfo: AuthInfo, data: InboundBank)
case class InboundGetAccounts(authInfo: AuthInfo, data: List[InboundAccountJune2017])
case class InboundGetAccountbyAccountID(authInfo: AuthInfo, data: InboundAccountJune2017)
case class InboundCheckBankAccountExists(authInfo: AuthInfo, data: InboundAccountJune2017)
case class InboundGetCoreBankAccounts(authInfo: AuthInfo, data: List[InternalInboundCoreAccount])
case class InboundGetTransactions(authInfo: AuthInfo, data: List[InternalTransaction])
case class InboundGetTransaction(authInfo: AuthInfo, data: InternalTransaction)
case class InboundCreateChallengeJune2017(authInfo: AuthInfo, data: InternalCreateChallengeJune2017)
case class InboundCreateCounterparty(authInfo: AuthInfo, data: InternalCounterparty)
case class InboundGetTransactionRequests210(authInfo: AuthInfo, data: InternalGetTransactionRequests)
case class InboundGetCounterparties(authInfo: AuthInfo, data: List[InternalCounterparty])
case class InboundGetCustomersByUserId(authInfo: AuthInfo, data: List[InternalCustomer])








////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////
// These are case classes, used in internal message mapping
case class InternalInboundCoreAccount(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  id : String,
  label : String,
  bank_id : String,
  account_routing: AccountRouting
)

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

case class InternalGetTransactionRequests(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  transactionRequests:List[TransactionRequest]
)

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

case class InternalOutboundGetCounterparties(
  thisBankId: String, 
  thisAccountId: String,
  viewId :String
)

case class OutboundTransactionRequests(
  accountId: String,
  accountType: String,
  currency: String,
  iban: String,
  number: String,
  bankId: String,
  branchId: String,
  accountRoutingScheme: String,
  accountRoutingAddress: String
)
  

case class InternalCounterparty(
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


case class InternalCustomer(
  status: String,
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  customerId : String, 
  bankId : String,
  number : String,   // The Customer number i.e. the bank identifier for the customer.
  legalName : String,
  mobileNumber : String,
  email : String,
  faceImage : MockCustomerFaceImage,
  dateOfBirth: Date,
  relationshipStatus: String,
  dependents: Integer,
  dobOfDependents: List[Date],
  highestEducationAttained: String,
  employmentStatus: String,
  creditRating : MockCreditRating,
  creditLimit: MockCreditLimit,
  kycStatus: lang.Boolean,
  lastOkDate: Date
)extends Customer


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