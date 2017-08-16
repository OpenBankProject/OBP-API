package code.api.v1_3_0

import java.util.Date

import code.api.util.APIUtil.OAuth._
import code.api.v2_1_0.{BranchJsonPostV210, TransactionRequestCommonBodyJSON}
import code.atms.Atms.AtmId
import code.atms.MappedAtm
import code.bankconnectors.{Connector, InboundUser, OBPQueryParam}
import code.branches.Branches.{Branch, BranchId, BranchT}
import code.branches.{InboundAdapterInfo, MappedBranch}
import code.fx.FXRate
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.counterparties.CounterpartyTrait
import code.model.dataAccess.ResourceUser
import code.model.{Consumer, PhysicalCard, _}
import code.products.Products.{Product, ProductCode}
import code.setup.{DefaultConnectorTestSetup, DefaultUsers, ServerSetup}
import code.transactionrequests.TransactionRequestTypeCharge
import code.transactionrequests.TransactionRequests._
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Failure, Full}

class PhysicalCardsTest extends ServerSetup with DefaultUsers  with DefaultConnectorTestSetup {

  def v1_3Request = baseRequest / "obp" / "v1.3.0"

  lazy val bank = createBank("a-bank")
  lazy val accId = "a-account"
  lazy val accountCurrency = "EUR"
  lazy val account = createAccount(bank.bankId, AccountId(accId), accountCurrency)

  def createCard(number : String) = new PhysicalCard(
    bankId= bank.bankId.value,
    bankCardNumber = number,
    nameOnCard = "",
    issueNumber = "",
    serialNumber = "",
    validFrom = new Date(),
    expires = new Date(),
    enabled = true,
    cancelled = false,
    onHotList = false,
    technology = "",
    networks = List(),
    allows = List(),
    account = account,
    replacement = None,
    pinResets = Nil,
    collected = None,
    posted = None
  )

  val user1CardAtBank1 = createCard("1")
  val user1CardAtBank2 = createCard("2")
  val user2CardAtBank1 = createCard("a")
  val user2CardAtBank2 = createCard("b")

  val user1AllCards = List(user1CardAtBank1, user1CardAtBank2)
  val user2AllCards = List(user2CardAtBank1, user2CardAtBank2)

  val user1CardsForOneBank = List(user1CardAtBank1)
  val user2CardsForOneBank = List(user2CardAtBank1)

  object MockedCardConnector extends Connector with MdcLoggable {

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
    override def getBank(bankId : BankId) : Box[Bank] = Full(bank)
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
    override def createOrUpdatePhysicalCard(bankCardNumber: String,
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

    override def createOrUpdateBranch(branch: Branch): Box[BranchT] = Empty
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
    override def getPhysicalCards(user : User) : List[PhysicalCard] = {
      if(user == resourceUser1) {
        user1AllCards
      } else if (user == resourceUser2) {
        user2AllCards
      } else {
        List()
      }
    }
  
    override def getPhysicalCardsForBank(bank : Bank, user : User) : List[PhysicalCard] = {
      if(user == resourceUser1) {
        user1CardsForOneBank
      } else if (user == resourceUser2) {
        user2CardsForOneBank
      } else {
        List()
      }
    }
  }

  override def beforeAll() {
    super.beforeAll()
    //use the mock connector
    Connector.connector.default.set(MockedCardConnector)
  }

  override def afterAll() {
    super.afterAll()
    //reset the default connector
    Connector.connector.default.set(Connector.buildOne)
  }

  feature("Getting details of physical cards") {

    scenario("A user wants to get details of all their cards across all banks") {
      When("A user requests their cards")

      val request = (v1_3Request / "cards").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      //dummy connector above tells us we should get back user1AllCards
      //we are just testing that the api calls the connector properly
      And("We should get the correct cards")
      val expectedCardNumbers = user1AllCards.map(_.bankCardNumber)
      val json = response.body.extract[PhysicalCardsJSON]
      val returnedCardNumbers = json.cards.map(_.bank_card_number)

      returnedCardNumbers should equal(expectedCardNumbers)
    }

    scenario("A user wants to get details of all their cards issued by a single bank") {
      When("A user requests their cards")

      //our dummy connector doesn't care about the value of the bank id, so we can just use "somebank"
      val request = (v1_3Request / "banks" / bank.bankId.value / "cards").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      //dummy connector above tells us we should get back user1CardsForOneBank
      //we are just testing that the api calls the connector properly
      And("We should get the correct cards")

      val expectedCardNumbers = user1CardsForOneBank.map(_.bankCardNumber)
      val json = response.body.extract[PhysicalCardsJSON]
      val returnedCardNumbers = json.cards.map(_.bank_card_number)

      returnedCardNumbers should equal(expectedCardNumbers)
    }

  }

}
