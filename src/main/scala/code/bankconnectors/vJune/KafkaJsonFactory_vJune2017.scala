package code.bankconnectors.vJune

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import code.api.util.APIUtil.InboundMessageBase
import code.api.v1_2_1.AmountOfMoneyJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.CustomerFaceImageJson
import code.api.v2_1_0.CustomerCreditRatingJSON
import code.bankconnectors.Topics._
import code.bankconnectors._
import code.branches.{InboundAdapterInfo, InboundBank, InboundValidatedUser, InternalTransaction}
import code.customer.Customer
import code.model.dataAccess.MappedBankAccountData
import code.model.{AccountId, BankAccount, BankId}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.today

case class AuthInfo(userId: String, username: String, cbsToken: String)

/**
  * case classes used to define topics
  */

case class GetAdapterInfo(date: String) extends GetAdapterInfoTopic
case class GetBanks(authInfo: AuthInfo, criteria: String) extends GetBanksTopic
case class GetBank(authInfo: AuthInfo, bankId: String) extends GetBankTopic
case class GetUserByUsernamePassword(authInfo: AuthInfo, password: String) extends GetUserByUsernamePasswordTopic
case class OutboundGetAccounts(authInfo: AuthInfo, customers:InternalCustomers ) extends GetAccountsTopic
case class GetAccountbyAccountID(authInfo: AuthInfo, bankId: String, accountId: String)extends GetAccountbyAccountIDTopic
case class GetAccountbyAccountNumber(authInfo: AuthInfo, bankId: String, accountNumber: String)extends GetAccountbyAccountNumberTopic
case class GetTransactions(authInfo: AuthInfo,bankId: String, accountId: String, limit: Int, fromDate: String, toDate: String) extends GetTransactionsTopic
case class GetTransaction(authInfo: AuthInfo, bankId: String, accountId: String, transactionId: String) extends GetTransactionTopic
case class CreateCBSAuthToken(authInfo: AuthInfo) extends CreateCBSAuthTokenTopic

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

case class InternalCustomer(
  customer_id: String,
  customer_number: String,
  legal_name: String,
  mobile_phone_number: String,
  email: String,
  face_image: CustomerFaceImageJson,
  date_of_birth: Date,
  relationship_status: String,
  dependants: Int,
  dob_of_dependants: List[Date],
  credit_rating: Option[CustomerCreditRatingJSON],
  credit_limit: Option[AmountOfMoneyJsonV121],
  highest_education_attained: String,
  employment_status: String,
  kyc_status: Boolean,
  last_ok_date: Date
)

case class InternalCustomers(customers: List[InternalCustomer])

object JsonFactory_vJune2017 {
  def createCustomerJson(cInfo : Customer) : InternalCustomer = {
    InternalCustomer(
      customer_id = cInfo.customerId,
      customer_number = cInfo.number,
      legal_name = cInfo.legalName,
      mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email,
      face_image = CustomerFaceImageJson(url = cInfo.faceImage.url,
        date = cInfo.faceImage.date),
      date_of_birth = cInfo.dateOfBirth,
      relationship_status = cInfo.relationshipStatus,
      dependants = cInfo.dependents,
      dob_of_dependants = cInfo.dobOfDependents,
      credit_rating = Option(CustomerCreditRatingJSON(rating = cInfo.creditRating.rating, source = cInfo.creditRating.source)),
      credit_limit = Option(AmountOfMoneyJsonV121(currency = cInfo.creditLimit.currency, amount = cInfo.creditLimit.amount)),
      highest_education_attained = cInfo.highestEducationAttained,
      employment_status = cInfo.employmentStatus,
      kyc_status = cInfo.kycStatus,
      last_ok_date = cInfo.lastOkDate
    )
  }
  
  def createCustomersJson(customers : List[Customer]) : InternalCustomers = {
    InternalCustomers(customers.map(createCustomerJson))
  }
  
  
}