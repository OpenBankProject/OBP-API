package code.api.v1_3_0

import java.util.Date

import code.api.util.APIUtil.OAuth._
import code.api.{DefaultUsers, ServerSetup}
import code.bankconnectors.{Connector, OBPQueryParam}
import code.management.ImporterAPI.ImporterTransaction
import code.model.{PhysicalCard, Consumer => OBPConsumer, Token => OBPToken, _}
import code.transactionrequests.TransactionRequests._
import net.liftweb.common.{Box, Empty, Failure, Loggable}

class PhysicalCardsTest extends ServerSetup with DefaultUsers {

  implicit val dateFormats = net.liftweb.json.DefaultFormats

  def v1_3Request = baseRequest / "obp" / "v1.3.0"

  def createCard(number : String) = new PhysicalCard(
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
    networks = Set.empty,
    allows = Set.empty,
    account = None,
    replacement = None,
    pinResets = Nil,
    collected = None,
    posted = None
  )

  val user1CardAtBank1 = createCard("1")
  val user1CardAtBank2 = createCard("2")
  val user2CardAtBank1 = createCard("a")
  val user2CardAtBank2 = createCard("b")

  val user1AllCards = Set(user1CardAtBank1, user1CardAtBank2)
  val user2AllCards = Set(user2CardAtBank1, user2CardAtBank2)

  val user1CardsForOneBank = Set(user1CardAtBank1)
  val user2CardsForOneBank = Set(user2CardAtBank1)

  object MockedCardConnector extends Connector with Loggable {

    type AccountType = BankAccount

    //these methods aren't required by our test
    override def getChallengeThreshold(userId: String, accountId: String, transactionRequestType: String, currency: String): (BigDecimal, String) = (0, "EUR")
    override def getBank(bankId : BankId) : Box[Bank] = Empty
    override def getBanks : List[Bank] = Nil
    override def getBankAccount(bankId : BankId, accountId : AccountId) : Box[BankAccount] = Empty
    override def getOtherBankAccount(bankId: BankId, accountID : AccountId, otherAccountID : String) : Box[OtherBankAccount] =
      Empty
    override def getOtherBankAccounts(bankId: BankId, accountID : AccountId): List[OtherBankAccount] =
      Nil
    override def getTransactions(bankId: BankId, accountID: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]] =
      Empty
    override def getTransaction(bankId : BankId, accountID : AccountId, transactionID : TransactionId): Box[Transaction] =
      Empty

    //these methods are required
    override def getPhysicalCards(user : User) : Set[PhysicalCard] = {
      if(user == obpuser1) {
        user1AllCards
      } else if (user == obpuser2) {
        user2AllCards
      } else {
        Set.empty
      }
    }

    override def getPhysicalCardsForBank(bankId : BankId, user : User) : Set[PhysicalCard] = {
      if(user == obpuser1) {
        user1CardsForOneBank
      } else if (user == obpuser2) {
        user2CardsForOneBank
      } else {
        Set.empty
      }
    }

    override def getAccountHolders(bankId: BankId, accountID: AccountId) : Set[User] = Set.empty

    override def makePaymentImpl(fromAccount : AccountType, toAccount : AccountType, amt : BigDecimal, description : String) : Box[TransactionId] =
      Failure("not supported")
    override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                              account : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                              status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = {
      Failure("not supported")
    }
    override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                              account : BankAccount, details: String,
                                              status: String, charge: TransactionRequestCharge) : Box[TransactionRequest210] = {
      Failure("not supported")
    }
    override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId) = ???
    override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge) = ???
    override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = ???

    override def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]] = ???
    override def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest210]] = ???
    override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId) : Box[TransactionRequest] = ???
    override def getTransactionRequestTypesImpl(fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
      Failure("not supported")
    }

    override def createBankAndAccount(bankName : String, bankNationalIdentifier : String, accountNumber : String,
                                      accountType: String, accountLabel: String, currency: String,
                                      accountHolderName : String): (Bank, BankAccount) = ???

    //sets a user as an account owner/holder
    override def setAccountHolder(bankAccountUID: BankAccountUID, user: User): Unit = ???

    //for sandbox use -> allows us to check if we can generate a new test account with the given number
    override def accountExists(bankId: BankId, accountNumber: String): Boolean = ???

    override def removeAccount(bankId: BankId, accountId: AccountId) : Boolean = ???

    //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
    override def createSandboxBankAccount(bankId: BankId, accountId: AccountId, accountNumber: String,
                                          accountType: String, accountLabel: String, currency: String,
                                          initialBalance: BigDecimal, accountHolderName: String): Box[AccountType] = ???

    //used by transaction import api call to check for duplicates
    override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, otherAccountHolder: String): Int = ???
    //used by transaction import api
    override def createImportedTransaction(transaction: ImporterTransaction): Box[Transaction] = ???
    override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal): Boolean = ???
    override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) : Boolean = ???
    override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String): Boolean = ???
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
      val returnedCardNumbers = json.cards.map(_.bank_card_number).toSet

      returnedCardNumbers should equal(expectedCardNumbers)
    }

    scenario("A user wants to get details of all their cards issued by a single bank") {
      When("A user requests their cards")

      //our dummy connector doesn't care about the value of the bank id, so we can just use "somebank"
      val request = (v1_3Request / "banks" / "somebank" / "cards").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      //dummy connector above tells us we should get back user1CardsForOneBank
      //we are just testing that the api calls the connector properly
      And("We should get the correct cards")

      val expectedCardNumbers = user1CardsForOneBank.map(_.bankCardNumber)
      val json = response.body.extract[PhysicalCardsJSON]
      val returnedCardNumbers = json.cards.map(_.bank_card_number).toSet

      returnedCardNumbers should equal(expectedCardNumbers)
    }

  }

}
