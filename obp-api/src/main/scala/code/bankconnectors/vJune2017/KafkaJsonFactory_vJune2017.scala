package code.bankconnectors.vJune2017

import java.lang
import java.util.Date

import code.api.util.APIUtil
import com.openbankproject.commons.model.CheckbookOrdersJson
import code.bankconnectors.vMar2017._
import code.branches.Branches.{DriveUpString, LobbyString}

import code.model.dataAccess.MappedBankAccountData
import com.openbankproject.commons.model.TransactionRequest
import com.openbankproject.commons.model.{CounterpartyTrait, CreditLimit, _}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.today

import scala.collection.immutable.List

/**
  * case classes used to define topics, these are outbound kafka messages
  */

case class OutboundGetAdapterInfo(authInfo: AuthInfo, date: String) extends TopicTrait
case class OutboundGetBanks(authInfo: AuthInfo) extends TopicTrait
case class OutboundGetBank(authInfo: AuthInfo, bankId: String) extends TopicTrait
case class OutboundGetAccounts(authInfo: AuthInfo, customers:InternalBasicCustomers) extends TopicTrait
case class OutboundGetAccountbyAccountID(authInfo: AuthInfo, bankId: String, accountId: String)extends TopicTrait
case class OutboundCheckBankAccountExists(authInfo: AuthInfo, bankId: String, accountId: String)extends TopicTrait
case class OutboundGetCoreBankAccounts(authInfo: AuthInfo, bankIdAccountIds: List[BankIdAccountId])extends TopicTrait
case class OutboundGetTransactions(authInfo: AuthInfo,bankId: String, accountId: String, limit: Int, fromDate: String, toDate: String) extends TopicTrait
case class OutboundGetTransaction(authInfo: AuthInfo, bankId: String, accountId: String, transactionId: String) extends TopicTrait
case class OutboundGetBranches(authInfo: AuthInfo,bankId: String) extends TopicTrait
case class OutboundGetBranch(authInfo: AuthInfo, bankId: String, branchId: String)extends TopicTrait
case class OutboundGetAtms(authInfo: AuthInfo,bankId: String) extends TopicTrait
case class OutboundGetAtm(authInfo: AuthInfo,bankId: String, atmId: String) extends TopicTrait

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

case class OutboundGetCounterpartyByCounterpartyId(
  authInfo: AuthInfo,
  counterparty: OutboundGetCounterpartyById
) extends TopicTrait
case class OutboundGetCounterparty(authInfo: AuthInfo, thisBankId: String, thisAccountId: String, counterpartyId: String) extends TopicTrait

case class OutboundGetCustomersByUserId(
  authInfo: AuthInfo
) extends TopicTrait

case class OutboundGetCheckbookOrderStatus(
  authInfo: AuthInfo, 
  bankId: String, 
  accountId: String, 
  originatorApplication: String, 
  originatorStationIP: String, 
  primaryAccount: String
)extends TopicTrait

case class OutboundGetCreditCardOrderStatus(
  authInfo: AuthInfo, 
  bankId: String, 
  accountId: String, 
  originatorApplication: String, 
  originatorStationIP: String, 
  primaryAccount: String
)extends TopicTrait



/**
  * case classes used in Kafka message, these are InBound Kafka messages
  */

case class InboundAdapterInfo(authInfo: AuthInfo, data: InboundAdapterInfoInternal)
case class InboundGetUserByUsernamePassword(authInfo: AuthInfo, data: InboundValidatedUser)
case class InboundGetBanks(authInfo: AuthInfo, status: Status,data: List[InboundBank])
case class InboundGetBank(authInfo: AuthInfo, status: Status, data: InboundBank)
case class InboundGetAccounts(authInfo: AuthInfo, status: Status, data: List[InboundAccountJune2017])
case class InboundGetAccountbyAccountID(authInfo: AuthInfo, status: Status, data: Option[InboundAccountJune2017])
case class InboundCheckBankAccountExists(authInfo: AuthInfo, status: Status, data: Option[InboundAccountJune2017])
case class InboundGetCoreBankAccounts(authInfo: AuthInfo, data: List[InternalInboundCoreAccount])
case class InboundGetTransactions(authInfo: AuthInfo, status: Status, data: List[InternalTransaction_vJune2017])
case class InboundGetTransaction(authInfo: AuthInfo, status: Status, data: Option[InternalTransaction_vJune2017])
case class InboundCreateChallengeJune2017(authInfo: AuthInfo, data: InternalCreateChallengeJune2017)
case class InboundCreateCounterparty(authInfo: AuthInfo, status: Status, data: Option[InternalCounterparty])
case class InboundGetTransactionRequests210(authInfo: AuthInfo, status: Status, data: List[TransactionRequest])
case class InboundGetCounterparties(authInfo: AuthInfo, status: Status, data: List[InternalCounterparty])
case class InboundGetCounterparty(authInfo: AuthInfo, status: Status, data: Option[InternalCounterparty])
case class InboundGetCustomersByUserId(authInfo: AuthInfo, status: Status, data: List[InternalCustomer])
case class InboundGetBranches(authInfo: AuthInfo,status: Status,data: List[InboundBranchVJune2017])
case class InboundGetBranch(authInfo: AuthInfo,status: Status, data: Option[InboundBranchVJune2017])
case class InboundGetAtms(authInfo: AuthInfo, status: Status, data: List[InboundAtmJune2017])
case class InboundGetAtm(authInfo: AuthInfo, status: Status, data: Option[InboundAtmJune2017])
case class InboundGetChecksOrderStatus(authInfo: AuthInfo, status: Status, data: CheckbookOrdersJson)
case class InboundGetCreditCardOrderStatus(authInfo: AuthInfo, status: Status, data: List[InboundCardDetails])


////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////
// These are case classes, used in internal message mapping
case class InternalInboundCoreAccount(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  id : String,
  label : String,
  bankId : String,
  accountType: String, 
  accountRoutings: List[AccountRouting]
)
case class Status(
                   errorCode: String,
                   backendMessages: List[InboundStatusMessage]
                 )
case class AuthInfo(userId: String = "", username: String ="", cbsToken: String ="", isFirst: Boolean = true, correlationId: String="", sessionId :String = "")

case class InboundAccountJune2017(
  errorCode: String,
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
  accountRoutingAddress: String,
  accountRouting: List[AccountRouting],
  accountRules: List[AccountRule]
) extends InboundMessageBase with InboundAccount

case class BankAccountJune2017(r: InboundAccountJune2017) extends BankAccount {

  def accountId: AccountId = AccountId(r.accountId)
  def accountType: String = r.accountType
  def balance: BigDecimal = BigDecimal(r.balanceAmount)
  def currency: String = r.balanceCurrency
  def name: String = r.owners.head
  // Note: deprecated, extends from BankAccount
  def iban: Option[String] = Some("iban")
  def number: String = r.accountNumber
  def bankId: BankId = BankId(r.bankId)
  def lastUpdate: Date = APIUtil.DateWithMsFormat.parse(today.getTime.toString)
  def accountHolder: String = r.owners.head

  // Fields modifiable from OBP are stored in mapper
  def label: String = (for {
    d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, r.accountId))
  } yield {
    d.getLabel
  }).getOrElse(r.accountNumber)

  def accountRoutingScheme: String = r.accountRoutingScheme
  def accountRoutingAddress: String = r.accountRoutingAddress
  def accountRoutings: List[AccountRouting] = List()
  def branchId: String = r.branchId

  def accountRules: List[AccountRule] = r.accountRules
  
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
  bespoke: List[CounterpartyBespoke]
)

case class InternalOutboundGetCounterparties(
  thisBankId: String, 
  thisAccountId: String,
  viewId :String
)

case class OutboundGetCounterpartyById(
  counterpartyId : String
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
                                 bespoke: List[CounterpartyBespoke]) extends CounterpartyTrait


case class InternalCustomer(
  customerId: String,
  bankId: String,
  number: String,
  legalName: String,
  mobileNumber: String,
  email: String,
  faceImage: CustomerFaceImage,
  dateOfBirth: Date,
  relationshipStatus: String,
  dependents: Integer,
  dobOfDependents: List[Date],
  highestEducationAttained: String,
  employmentStatus: String,
  creditRating: CreditRating,
  creditLimit: CreditLimit,
  kycStatus: lang.Boolean,
  lastOkDate: Date
)

case class ObpCustomer(
  customerId: String,
  bankId: String,
  number: String,
  legalName: String,
  mobileNumber: String,
  email: String,
  faceImage: CustomerFaceImage,
  dateOfBirth: Date,
  relationshipStatus: String,
  dependents: Integer,
  dobOfDependents: List[Date],
  highestEducationAttained: String,
  employmentStatus: String,
  creditRating: CreditRating,
  creditLimit: CreditLimit,
  kycStatus: lang.Boolean,
  lastOkDate: Date,
  title: String = "", //These new fields for V310, not from Connector for now. 
  branchId: String = "", //These new fields for V310, not from Connector for now. 
  nameSuffix: String = "", //These new fields for V310, not from Connector for now. 
) extends Customer

case class  InboundBranchVJune2017(
                           branchId: BranchId,
                           bankId: BankId,
                           name: String,
                           address: Address,
                           location: Location,
                           lobbyString: Option[LobbyString],
                           driveUpString: Option[DriveUpString],
                           meta: Meta,
                           branchRouting: Option[Routing],
                           lobby: Option[Lobby],
                           driveUp: Option[DriveUp],
                           // Easy access for people who use wheelchairs etc.
                           isAccessible : Option[Boolean],
                           accessibleFeatures: Option[String],
                           branchType : Option[String],
                           moreInfo : Option[String],
                           phoneNumber : Option[String],
                           isDeleted : Option[Boolean]
                         ) extends BranchT

case class InboundAtmJune2017(
                               atmId : AtmId,
                               bankId : BankId,
                               name : String,
                               address : Address,
                               location : Location,
                               meta : Meta,

                               OpeningTimeOnMonday : Option[String],
                               ClosingTimeOnMonday : Option[String],

                               OpeningTimeOnTuesday : Option[String],
                               ClosingTimeOnTuesday : Option[String],

                               OpeningTimeOnWednesday : Option[String],
                               ClosingTimeOnWednesday : Option[String],

                               OpeningTimeOnThursday : Option[String],
                               ClosingTimeOnThursday: Option[String],

                               OpeningTimeOnFriday : Option[String],
                               ClosingTimeOnFriday : Option[String],

                               OpeningTimeOnSaturday : Option[String],
                               ClosingTimeOnSaturday : Option[String],

                               OpeningTimeOnSunday: Option[String],
                               ClosingTimeOnSunday : Option[String],

                               isAccessible : Option[Boolean],

                               locatedAt : Option[String],
                               moreInfo : Option[String],
                               hasDepositCapability : Option[Boolean]
                             ) extends AtmT

case class InternalTransaction_vJune2017(
                                transactionId: String,
                                accountId: String,
                                amount: String,
                                bankId: String,
                                completedDate: String,
                                counterpartyId: String,
                                counterpartyName: String,
                                currency: String,
                                description: String,
                                newBalanceAmount: String,
                                newBalanceCurrency: String,
                                postedDate: String,
                                `type`: String,
                                userId: String
                              )

case class InboundCardDetails(
  orderId: String,
  creditCardType: String,
  cardDescription: String,
  useType: String,
  orderDate: String,
  deliveryStatus: String,
  statusDate: String,
  branch: String
)

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
  def createObpCustomer(customer : InternalCustomer) : Customer = {
    ObpCustomer(
      customerId = customer.customerId,
      bankId = customer.bankId,
      number = customer.number,
      legalName = customer.legalName,
      mobileNumber = customer.mobileNumber,
      email = customer.email,
      faceImage = customer.faceImage,
      dateOfBirth = customer.dateOfBirth,
      relationshipStatus = customer.relationshipStatus,
      dependents = customer.dependents,
      dobOfDependents = customer.dobOfDependents,
      highestEducationAttained = customer.highestEducationAttained,
      employmentStatus = customer.employmentStatus,
      creditRating = customer.creditRating,
      creditLimit = customer.creditLimit,
      kycStatus = customer.kycStatus,
      lastOkDate = customer.lastOkDate,
      )
  }
  
  def createCustomersJson(customers : List[Customer]) : InternalBasicCustomers = {
    InternalBasicCustomers(customers.map(createCustomerJson))
  }
  
  def createObpCustomers(customers : List[InternalCustomer]) : List[Customer] = {
    customers.map(createObpCustomer)
  }
}