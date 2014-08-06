package code.api.v1_3_1

import code.api.test.ServerSetup
import code.bankconnectors.{OBPQueryParam, Connector}
import net.liftweb.common.{Empty, Box}
import code.model._
import dispatch._
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import code.util.APIUtil.OAuth.{Token, Consumer}
import code.model.dataAccess.APIUser
import code.model.TokenType._
import code.util.APIUtil.OAuth.Consumer
import code.model.PhysicalCard
import code.model.{Consumer => OBPConsumer, Token => OBPToken}
import java.util.Date

class PhysicalCardsTest extends ServerSetup {

  def v1_3Request = baseRequest / "obp" / "v1.3.0"

  //create the application
  lazy val testConsumer =
    OBPConsumer.create.
      name("test application").
      isActive(true).
      key(randomString(40).toLowerCase).
      secret(randomString(40).toLowerCase).
      saveMe

  val defaultProvider = Props.get("hostname","")

  lazy val consumer = new Consumer (testConsumer.key,testConsumer.secret)
  // create the access token
  lazy val tokenDuration = weeks(4)

  val obpuser1 =
    APIUser.create.provider_(defaultProvider).
      saveMe

  val obpuser2 =
    APIUser.create.provider_(defaultProvider).
      saveMe

  lazy val testToken =
    OBPToken.create.
      tokenType(Access).
      consumerId(testConsumer.id).
      userForeignKey(obpuser1.id.toLong).
      key(randomString(40).toLowerCase).
      secret(randomString(40).toLowerCase).
      duration(tokenDuration).
      expirationDate({(now : TimeSpan) + tokenDuration}).
      insertDate(now).
      saveMe

  lazy val token = new Token(testToken.key, testToken.secret)

  lazy val testToken2 =
    OBPToken.create.
      tokenType(Access).
      consumerId(testConsumer.id).
      userForeignKey(obpuser2.id.toLong).
      key(randomString(40).toLowerCase).
      secret(randomString(40).toLowerCase).
      duration(tokenDuration).
      expirationDate({(now : TimeSpan) + tokenDuration}).
      insertDate(now).
      saveMe

  lazy val token2 = new Token(testToken2.key, testToken2.secret)

  val user1 = Some((consumer, token))
  val user2 = Some((consumer, token2))

  val user1CardAtBank1 = new PhysicalCard(
  val bankCardNumber : String,
  val nameOnCard = "",
  val issueNumber = "",
  val serialNumber = "",
  val validFrom : Date,
  val expires : Date,
  val enabled = true,
  val cancelled = false,
  val technology = "",
  val networks = Set.empty,
  val allows = Set.empty,
  val account : BankAccount,
  val replacement = None,
  val pinResets = Nil,
  val collected = None,
  val posted = None
  )

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
    def getModeratedTransactions(bankID: String, accountID: String, queryParams: OBPQueryParam*)
                                (moderate: Transaction => ModeratedTransaction): Box[List[ModeratedTransaction]] =
      Empty
    def getModeratedTransaction(id : String, bankID : String, accountID : String)
                               (moderate: Transaction => ModeratedTransaction) : Box[ModeratedTransaction] =
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
  }

  feature("Getting details of physical cards") {

    scenario("A user wants to get details of all their cards across all banks") {

    }

    scenario("A user wants to get details of all their cards issued by a single bank") {

    }
  }

}
