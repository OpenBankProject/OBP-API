package code.api.v1_3_0

import java.util.Date

import code.api.util.APIUtil.OAuth._
import code.api.util.CallContext
import code.bankconnectors.Connector
import code.model.{PhysicalCard, _}
import code.setup.{DefaultConnectorTestSetup, DefaultUsers, ServerSetup}
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Full}

class PhysicalCardsTest extends ServerSetup with DefaultUsers  with DefaultConnectorTestSetup {

  def v1_3Request = baseRequest / "obp" / "v1.3.0"

  lazy val bank = createBank("a-bank")
  lazy val accId = "a-account"
  lazy val accountCurrency = "EUR"
  lazy val account = createAccount(bank.bankId, AccountId(accId), accountCurrency)

  def createCard(number : String) = PhysicalCard(
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

    implicit override val nameOfConnector = "MockedCardConnector"

    override def getBank(bankId : BankId, callContext: Option[CallContext])  = Full(bank, callContext)
  
    //these methods are required in this test, there is no need to extends connector.
    override def getPhysicalCards(user : User) = {
      val cardList = if(user == resourceUser1) {
        user1AllCards
      } else if (user == resourceUser2) {
        user2AllCards
      } else {
        List()
      }
      Full(cardList)
    }
  
    override def getPhysicalCardsForBank(bank : Bank, user : User) = {
      val cardList = if(user == resourceUser1) {
        user1CardsForOneBank
      } else if (user == resourceUser2) {
        user2CardsForOneBank
      } else {
        List()
      }
      Full(cardList)
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
