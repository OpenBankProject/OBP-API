package code.api.v1_3_0

import code.api.DefaultUsers
import code.api.test.ServerSetup
import code.api.util.APIUtil
import code.bankconnectors.{OBPQueryParam, Connector}
import net.liftweb.common.{Empty, Box}
import code.model._
import dispatch._
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import APIUtil.OAuth.{Token, Consumer}
import code.model.dataAccess.APIUser
import code.model.TokenType._
import APIUtil.OAuth.Consumer
import code.model.PhysicalCard
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import java.util.Date
import APIUtil.OAuth._

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

  object MockedCardConnector extends Connector {
    //these methods aren't required by our test
    def getBank(permalink : String) : Box[Bank] = Empty
    def getBanks : List[Bank] = Nil
    def getBankAccount(bankPermalink : String, accountId : String) : Box[BankAccount] = Empty
    def getModeratedOtherBankAccount(bankID: String, accountID : String, otherAccountID : String)
                                    (moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]) : Box[ModeratedOtherBankAccount] =
      Empty
    def getModeratedOtherBankAccounts(bankID: String, accountID : String)
                                     (moderate: OtherBankAccount => Option[ModeratedOtherBankAccount]): Box[List[ModeratedOtherBankAccount]] =
      Empty
    def getTransactions(bankID: String, accountID: String, queryParams: OBPQueryParam*): Box[List[Transaction]] =
      Empty
    def getTransaction(bankID : String, accountID : String, transactionID : String): Box[Transaction] =
      Empty

    //these methods are required
    def getPhysicalCards(user : User) : Set[PhysicalCard] = {
      if(user == obpuser1) {
        user1AllCards
      } else if (user == obpuser2) {
        user2AllCards
      } else {
        Set.empty
      }
    }

    def getPhysicalCardsForBank(bankID : String, user : User) : Set[PhysicalCard] = {
      if(user == obpuser1) {
        user1CardsForOneBank
      } else if (user == obpuser2) {
        user2CardsForOneBank
      } else {
        Set.empty
      }
    }

    def getAccountHolders(bankID: String, accountID: String) : Set[User] = Set.empty
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
