package code.bankconnectors.vSept2018

import java.util.Date

import code.api.util.APIUtil
import code.bankconnectors.vJune2017.InternalCustomer
import code.bankconnectors.vMar2017._
import code.branches.Branches.{DriveUpString, LobbyString}
import code.model.dataAccess.MappedBankAccountData
import com.openbankproject.commons.model.{CounterpartyTrait, Customer, UserAuthContext, _}
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
case class OutboundGetChallengeThreshold(
  authInfo: AuthInfo,
  bankId: String,
  accountId: String,
  viewId: String,
  transactionRequestType: String,
  currency: String,
  userId: String,
  userName: String
) extends TopicTrait
case class OutboundCreateTransaction(
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

) extends TopicTrait

case class OutboundCreateChallengeSept2018(
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

//AdapterInfo has no AuthInfo, because it just get data from Adapter, no need for AuthInfo
case class InboundAdapterInfo(data: InboundAdapterInfoInternal)
case class InboundGetUserByUsernamePassword(inboundAuthInfo: InboundAuthInfo, data: InboundValidatedUser)
case class InboundGetBanks(inboundAuthInfo: InboundAuthInfo, status: Status,data: List[InboundBank])
case class InboundGetBank(inboundAuthInfo: InboundAuthInfo, status: Status, data: InboundBank)
case class InboundGetAccounts(inboundAuthInfo: InboundAuthInfo, status: Status, data: List[InboundAccountSept2018])
case class InboundGetAccountbyAccountID(inboundAuthInfo: InboundAuthInfo, status: Status, data: Option[InboundAccountSept2018])
case class InboundCheckBankAccountExists(inboundAuthInfo: InboundAuthInfo, status: Status, data: Option[InboundAccountSept2018])
case class InboundGetCoreBankAccounts(inboundAuthInfo: InboundAuthInfo, data: List[InternalInboundCoreAccount])
case class InboundGetTransactions(inboundAuthInfo: InboundAuthInfo, status: Status, data: List[InternalTransaction_vSept2018])
case class InboundGetTransaction(inboundAuthInfo: InboundAuthInfo, status: Status, data: Option[InternalTransaction_vSept2018])
case class InboundCreateChallengeSept2018(inboundAuthInfo: InboundAuthInfo, data: InternalCreateChallengeSept2018)
case class InboundCreateCounterparty(inboundAuthInfo: InboundAuthInfo, status: Status, data: Option[InternalCounterparty])
case class InboundGetTransactionRequests210(inboundAuthInfo: InboundAuthInfo, status: Status, data: List[TransactionRequest])
case class InboundGetCounterparties(inboundAuthInfo: InboundAuthInfo, status: Status, data: List[InternalCounterparty])
case class InboundGetCounterparty(inboundAuthInfo: InboundAuthInfo, status: Status, data: Option[InternalCounterparty])
case class InboundGetCustomersByUserId(inboundAuthInfo: InboundAuthInfo, status: Status, data: List[InternalCustomer])
case class InboundGetBranches(inboundAuthInfo: InboundAuthInfo,status: Status,data: List[InboundBranchVSept2018])
case class InboundGetBranch(inboundAuthInfo: InboundAuthInfo,status: Status, data: Option[InboundBranchVSept2018])
case class InboundGetAtms(inboundAuthInfo: InboundAuthInfo, status: Status, data: List[InboundAtmSept2018])
case class InboundGetAtm(inboundAuthInfo: InboundAuthInfo, status: Status, data: Option[InboundAtmSept2018])
case class InboundGetChecksOrderStatus(inboundAuthInfo: InboundAuthInfo, status: Status, data: CheckbookOrdersJson)
case class InboundGetCreditCardOrderStatus(inboundAuthInfo: InboundAuthInfo, status: Status, data: List[InboundCardDetails])
case class InboundGetChallengeThreshold(inboundAuthInfo: InboundAuthInfo, status: Status, data: AmountOfMoney)


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
case class ViewBasic(
  id: String,
  short_name: String,
  description: String,
)

case class AuthView(
  view: ViewBasic,
  account:AccountBasic,
)

case class AuthInfo(
  userId: String = "", 
  username: String = "", 
  cbsToken: String = "", 
  isFirst: Boolean = true, 
  correlationId: String = "",
  sessionId: String = "", 
  linkedCustomers: List[BasicCustomer] = Nil,
  userAuthContexts: List[BasicUserAuthContext]= Nil,
  authViews: List[AuthView] = Nil,
)

case class InboundAuthInfo(
  cbsToken: String = "",
  sessionId: String = ""
)


case class InboundAccountSept2018(
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

case class BankAccountSept2018(r: InboundAccountSept2018) extends BankAccount {

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

case class BasicCustomer(
  customerId: String,
  customerNumber: String,
  legalName: String,
)

case class InternalCreateChallengeSept2018(
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


case class  InboundBranchVSept2018(
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

case class InboundAtmSept2018(
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

case class InternalTransaction_vSept2018(
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

case class InternalTransactionId(
  id : String
)
case class InboundCreateTransactionId(inboundAuthInfo: InboundAuthInfo, status: Status, data: InternalTransactionId)

object JsonFactory_vSept2018 {
  def createCustomerJson(customer : Customer) : InternalBasicCustomer = {
    InternalBasicCustomer(
      bankId=customer.bankId,
      customerId = customer.customerId,
      customerNumber = customer.number,
      legalName = customer.legalName,
      dateOfBirth = customer.dateOfBirth
    )
  }
  
  def createUserJson(user : User) : InternalBasicUser = {
    InternalBasicUser(
      user.userId,
      user.emailAddress,
      user.name,
    )
  }
  
  def createBasicCustomerJson(customer : Customer) : BasicCustomer = {
    BasicCustomer(
      customerId = customer.customerId,
      customerNumber = customer.number,
      legalName = customer.legalName,
    )
  }
  
  def createBasicUserAuthContext(userAuthContest : UserAuthContext) : BasicUserAuthContext = {
    BasicUserAuthContext(
      key = userAuthContest.key,
      value = userAuthContest.value
    )
  }
  
  def createCustomersJson(customers : List[Customer]) : InternalBasicCustomers = {
    InternalBasicCustomers(customers.map(createCustomerJson))
  }
  
  def createUsersJson(users : List[User]) : InternalBasicUsers = {
    InternalBasicUsers(users.map(createUserJson))
  }
  
  def createBasicCustomerJson(customers : List[Customer]) : List[BasicCustomer] = {
    customers.map(createBasicCustomerJson)
  }
  
  
  def createBasicUserAuthContextJson(userAuthContexts : List[UserAuthContext]) : List[BasicUserAuthContext] = {
    userAuthContexts.map(createBasicUserAuthContext)
  }
  
}