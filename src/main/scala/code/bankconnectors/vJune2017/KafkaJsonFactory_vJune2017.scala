package code.bankconnectors.vJune2017

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import code.api.util.APIUtil.InboundMessageBase
import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.bankconnectors._
import code.bankconnectors.vMar2017._
import code.customer.Customer
import code.kafka.Topics._
import code.model.dataAccess.MappedBankAccountData
import code.model.{AccountId, BankAccount, BankId}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.today

case class AuthInfo(userId: String, username: String, cbsToken: String)

/**
  * case classes used to define topics
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

case class InboundAccountJune2017(
  errorCode: String,
  cbsToken: String,
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

case class InternalTransactionId(id : String)

case class InternalCreateChallengeJune2017(answer : String)

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