package code.connector

import java.util.Date

import code.api.v2_1_0.{BranchJsonPostV210, TransactionRequestCommonBodyJSON}
import code.atms.Atms.AtmId
import code.atms.MappedAtm
import code.bankconnectors._
import code.branches.Branches.{Branch, BranchId, BranchT}
import code.branches.MappedBranch
import code.fx.FXRate
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.model.dataAccess.ResourceUser
import code.products.Products.{Product, ProductCode}
import code.setup.{DefaultConnectorTestSetup, DefaultUsers, ServerSetup}
import code.transactionrequests.TransactionRequestTypeCharge
import code.transactionrequests.TransactionRequests.{TransactionRequest, TransactionRequestBody, TransactionRequestChallenge, TransactionRequestCharge}
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Failure, Full}

/**
  * Created by zhanghongwei on 14/07/2017.
  */
object MockedCardConnector extends ServerSetup  
  with Connector with DefaultUsers  
  with DefaultConnectorTestSetup with MdcLoggable {
  
  type AccountType = BankAccount
  
  implicit override val nameOfConnector = "MockedCardConnector"
  
  override def getAdapterInfo: Box[InboundAdapterInfo] = Empty
  
  override def getTransactionRequestStatusesImpl() : Box[TransactionRequestStatus] = Empty
  override def getUser(name: String, password: String): Box[InboundUser] = ???
  
  //these methods aren't required by our test
  // override def getChallengeThreshold(userId: String, accountId: String, transactionRequestType: String, currency: String): (BigDecimal, String) = (0, "EUR")
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, phoneNumber: String): Box[String] = ???
  
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, userName: String): AmountOfMoney = AmountOfMoney("EUR", "0")
  // parameters in non ideal order override def createChallenge(transactionRequestType: code.model.TransactionRequestType,userID: String,transactionRequestId: String, bankId: BankId, accountId: AccountId): Box[String] = ???
  override def getChargeLevel(bankId: BankId,
    accountId: AccountId,
    viewId: ViewId,
    userId: String,
    userName: String,
    transactionRequestType: String,
    currency: String): Box[AmountOfMoney] = Empty
  override def validateChallengeAnswer(challengeId: String,hashOfSuppliedAnswer: String): Box[Boolean] = ???
  override def getBank(bankId : BankId) : Box[Bank] = Empty
  override def getBanks(): Box[List[Bank]] = Empty
  override def getBankAccount(bankId : BankId, accountId : AccountId) : Box[BankAccount] = Empty
  override def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = Empty
  override def getCounterpartyFromTransaction(bankId: BankId, accountID : AccountId, counterpartyID : String) : Box[Counterparty] =
    Empty
  override def getCounterpartiesFromTransaction(bankId: BankId, accountID : AccountId): List[Counterparty] =
    Nil
  override def getTransactions(bankId: BankId, accountID: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]] =
    Empty
  override def getTransaction(bankId : BankId, accountID : AccountId, transactionID : TransactionId): Box[Transaction] =
    Empty
  def AddPhysicalCard(bankCardNumber: String,
    nameOnCard: String,
    issueNumber: String,
    serialNumber: String,
    validFrom: Date,
    expires: Date,
    enabled: Boolean,
    cancelled: Boolean,
    onHotList: Boolean,
    technology: String,
    networks: List[String],
    allows: List[String],
    accountId: String,
    bankId: String,
    replacement: Option[CardReplacementInfo],
    pinResets: List[PinResetInfo],
    collected: Option[CardCollectionInfo],
    posted: Option[CardPostedInfo]
  ) : Box[PhysicalCard] = {
    Empty
  }
  
  override def getAccountHolders(bankId: BankId, accountID: AccountId) : Set[User] = Set.empty
  
  protected override def makePaymentImpl(fromAccount:AccountType, toAccount: AccountType, toCounterparty: CounterpartyTrait, amt: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String): Box[TransactionId] =
    Failure("not supported")
  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
    account : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
    status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = {
    Failure("not supported")
  }
  protected override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
    transactionRequestType: TransactionRequestType,
    account: BankAccount, toAccount: BankAccount,
    toCounterparty: CounterpartyTrait,
    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
    details: String, status: String,
    charge: TransactionRequestCharge,
    chargePolicy: String): Box[TransactionRequest] = {
    Failure("not supported")
  }
  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId) = ???
  override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) = ???
  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = ???
  
  override def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]] = ???
  override def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest]] = ???
  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId) : Box[TransactionRequest] = ???
  override def getTransactionRequestTypesImpl(fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
    Failure("not supported")
  }
  
  override def createBankAndAccount(
    bankName: String,
    bankNationalIdentifier: String,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String
  ): (Bank, BankAccount) = ???
  
  //sets a user as an account owner/holder
  override def setAccountHolder(bankAccountUID: BankIdAccountId, user: User): Unit = ???
  
  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String): Boolean = ???
  
  override def removeAccount(bankId: BankId, accountId: AccountId) : Boolean = ???
  
  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
  override def createSandboxBankAccount(bankId: BankId,
    accountId: AccountId,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    initialBalance: BigDecimal,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String
  ): Box[AccountType] = ???
  
  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, otherAccountHolder: String): Int = ???
  //used by transaction import api
  override def createImportedTransaction(transaction: ImporterTransaction): Box[Transaction] = ???
  override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal): Boolean = ???
  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) : Boolean = ???
  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String): Boolean = ???
  
  override def getProducts(bankId: BankId): Box[List[Product]] = Empty
  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = Empty
  
  override  def createOrUpdateBranch(branch: Branch): Box[BranchT] = Empty
  override def getBranch(bankId: BankId, branchId: BranchId): Box[MappedBranch]= Empty
  override def getAtm(bankId: BankId, atmId: AtmId): Box[MappedAtm] = Empty // TODO Return Not Implemented
  
  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId): Box[CounterpartyTrait] = ???
  
  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] = Empty
  
  override def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = Empty
  
  override def getTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType): Box[TransactionRequestTypeCharge] = Empty
  
  override def getTransactionRequestTypeCharges(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestTypes: List[TransactionRequestType]): Box[List[TransactionRequestTypeCharge]] = Empty
  
  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId,viewId :ViewId): Box[List[CounterpartyTrait]] = Empty
  
  override def getEmptyBankAccount(): Box[AccountType] = Empty
  
  override def createOrUpdateBank(
    bankId: String,
    fullBankName: String,
    shortBankName: String,
    logoURL: String,
    websiteURL: String,
    swiftBIC: String,
    national_identifier: String,
    bankRoutingScheme: String,
    bankRoutingAddress: String
  ): Box[Bank] = Empty
  
  //these methods are required in this test, there is no need to extends connector.
  override def getPhysicalCards(user : User) : List[PhysicalCard] = Nil
  
  override def getPhysicalCardsForBank(bank : Bank, user : User) : List[PhysicalCard] = Nil
  
  //These bank id and account ids are real data over adapter  
  val bankIdAccountId = BankIdAccountId(BankId("obp-bank-x-gh"),AccountId("KOa4M8UfjUuWPIXwPXYPpy5FoFcTUwpfHgXC1qpSluc"))
  val bankIdAccountId2 = BankIdAccountId(BankId("obp-bank-x-gh"),AccountId("tKWSUBy6sha3Vhxc/vw9OK96a0RprtoxUuObMYR29TI"))
  
  override def getBankAccounts(username: String): Box[List[InboundAccountJune2017]] = {
    Full(
      InboundAccountJune2017(
        errorCode = "OBP-6001: ...",
        cbsToken = "cbsToken",
        bankId = bankIdAccountId.bankId.value,
        branchId = "222", 
        accountId = bankIdAccountId.accountId.value,
        accountNumber = "123", 
        accountType = "AC", 
        balanceAmount = "50",
        balanceCurrency = "EUR", 
        owners = Nil,
        viewsToGenerate = "Owner" :: "Public" :: "Accountant" :: "Auditor" :: Nil,
        bankRoutingScheme = "iban", 
        bankRoutingAddress = "bankRoutingAddress",
        branchRoutingScheme = "branchRoutingScheme",
        branchRoutingAddress = " branchRoutingAddress",
        accountRoutingScheme = "accountRoutingScheme",
        accountRoutingAddress = "accountRoutingAddress"
      ) :: InboundAccountJune2017(
        errorCode = "OBP-6001: ...",
        cbsToken = "cbsToken",
        bankId = bankIdAccountId2.bankId.value, 
        branchId = "222",
        accountId = bankIdAccountId2.accountId.value, 
        accountNumber = "123",
        accountType = "AC", 
        balanceAmount = "50", 
        balanceCurrency = "EUR",
        owners = Nil,
        viewsToGenerate = "Owner" :: "Public" :: "Accountant" :: "Auditor" :: Nil,
        bankRoutingScheme = "iban", 
        bankRoutingAddress = "bankRoutingAddress",
        branchRoutingScheme = "branchRoutingScheme",
        branchRoutingAddress = " branchRoutingAddress",
        accountRoutingScheme = "accountRoutingScheme",
        accountRoutingAddress = "accountRoutingAddress"
      ) :: Nil
    )
  }
}

