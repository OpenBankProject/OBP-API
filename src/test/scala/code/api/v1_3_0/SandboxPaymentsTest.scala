package code.api.v1_3_0

import code.api.DefaultUsers
import code.api.test.{APIResponse, ServerSetup}
import code.model.BankAccount
import code.util.APIUtil.OAuth.{Token, Consumer}
import net.liftweb.json._
import dispatch._
import code.util.APIUtil.OAuth._
import net.liftweb.util.TimeHelpers._
import net.liftweb.json.Serialization.{write}

case class MakePaymentJson(bank_id : String, account_id : String, amount : String)

case class BankAccountDetails(bankId: String, accountId : String)

class SandboxPaymentsTest extends ServerSetup with DefaultUsers {

  def v1_3_0_Request = baseRequest / "obp" / "v1.3.0"

  implicit val dateFormats = net.liftweb.json.DefaultFormats

  private val testTransactionAmount = BigDecimal("13.50")

  /**
   * @param desiredTransactionStatus See SandboxPaymentProcessor for how to specify the type of payment response you would like
   */
  private def postTransaction(bankId: String, accountId: String, viewId: String, paymentJson: MakePaymentJson,
                      consumerAndToken: Option[(Consumer, Token)], desiredTransactionStatus : Option[String]): APIResponse = {
    //TODO: add obp_desired_transaction_status header
    val desiredTransactionStatusHeader = desiredTransactionStatus match {
      case Some(value) => List(("obp_desired_transaction_status", value))
      case None => Nil
    }

    val request = (v1_3_0_Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions").POST <:< desiredTransactionStatusHeader <@(consumerAndToken)
    makePostRequest(request, compact(render(Extraction.decompose(paymentJson))))
  }

  /**
   * @return a tuple containing the bankId and accountId of the sending account (BankAccountDetails), and
   *         the response from the api to the payment request (APIResponse)
   */
  private def makeGoodPayment(desiredTransactionStatus : Option[String]) : (BankAccountDetails, APIResponse) = {
    val testBank = createPaymentTestBank()
    val bankId = testBank.permalink.get
    val acc1 = createAccountAndOwnerView(Some(obpuser1), testBank, "__acc1", "EUR")
    val acc2  = createAccountAndOwnerView(Some(obpuser1), testBank, "__acc2", "EUR")

    def getFromAccount : BankAccount = {
      BankAccount(bankId, acc1.permalink.get).getOrElse(fail("couldn't get from account"))
    }

    def getToAccount : BankAccount = {
      BankAccount(bankId, acc2.permalink.get).getOrElse(fail("couldn't get to account"))
    }

    val fromAccount = getFromAccount
    val toAccount = getToAccount

    val payJson = MakePaymentJson(toAccount.bankPermalink, toAccount.permalink, testTransactionAmount.toString)

    val apiResponse = postTransaction(fromAccount.bankPermalink, fromAccount.permalink, "owner", payJson, user1, desiredTransactionStatus)

    (BankAccountDetails(bankId, acc1.permalink.get), apiResponse)
  }

  feature("Sandbox payments with challenges/operations") {

    scenario("Payment without a security challenge") {
      val (bankAccountDetails, response) = makeGoodPayment(None)
      response.code should equal(201)
      response.locationHeader startsWith "/obp/v1.3.0/operations/" should be(true)

      //check response body is a transaction
      val responseTransaction = response.body.extract[TransactionJSON1_3_0]
      responseTransaction.id.isEmpty should be(false)
      //because this is the transaction for the one who sent the payment, the amount should be negative
      responseTransaction.details.value.amount should equal("-" + testTransactionAmount.toString)

      //check the transaction has really been created by getting it via GET
      val getTransaction = makeGetRequest(baseRequest / "obp" / "v1.3.0" / "banks" / bankAccountDetails.bankId / "accounts" / bankAccountDetails.accountId / "owner"/
        "transactions" / responseTransaction.id / "transaction" <@ (user1))
      getTransaction.code should equal(200)
      getTransaction.body.extract[TransactionJSON1_3_0].id should equal(responseTransaction.id)
      //the location header should also point to a valid operation resource
      val getOperation = baseRequest.setUrl(baseRequest.url + response.locationHeader).GET <@(user1)
      val operationResponse = makeGetRequest(getOperation)

      operationResponse.code should equal(200)

      val operation = operationResponse.body.extract[OperationJSON1_3_0]
      operation.id.isEmpty should be(false)
      operation.action should equal("POST_TRANSACTION")
      operation.status should equal("COMPLETED")
      operation.challenges.isEmpty should be(true)
      operation.start_date should equal(operation.end_date)

      val end = TimeSpan(operation.end_date.getTime)
      //check that the end date is at least in the last 24 hours
      val twentyFourHoursAgo = (now : TimeSpan) - (24 hours)
      end.after(twentyFourHoursAgo) should be(true)
    }

    scenario("Payment with a single security challenge answered correctly") {
      val (bankAccountDetails, response) = makeGoodPayment(Some("challenge_pending"))
      response.code should equal(202)
      response.locationHeader startsWith "/obp/v1.3.0/operations/" should be(true)
      write(response.body) should equal("{}") //TODO: Should the body of the response really be '{}'?

      //the location header should also point to a valid operation resource
      val getOperation = baseRequest.setUrl(baseRequest.url + response.locationHeader).GET <@(user1)
      val operationResponse = makeGetRequest(getOperation)

      operationResponse.code should equal(200)

      val operation = operationResponse.body.extract[OperationJSON1_3_0]
      operation.id.isEmpty should be(false)
      operation.action should equal("POST_TRANSACTION")
      operation.status should equal("CHALLENGE_PENDING")
      operation.challenges.length should equal(1) //sandbox payment processor currently only emits single challenges

      val challenge = operation.challenges(0)
      challenge.id.isEmpty should be(false)
      challenge.label.isEmpty should be(false) //TODO: label vs question, should question really be question_type, e.g. TAN?

      //answer the challenge correctly:

      //sandbox challenges answer is always 'Berlin'
      val answerChallengePost = (baseRequest / "obp" / "v1.3.0" / "challenges" / challenge.id / "answers").POST <@(user1)
      val challengeCorrectResponseJsonString = write(AnswerChallengeJson("Berlin"))
      val answerChallengeResponse = makePostRequest(answerChallengePost, challengeCorrectResponseJsonString)

      answerChallengeResponse.code should equal(204)
      write(answerChallengeResponse.body) should equal("{}") //TODO: Should the body of the response really be '{}'?

      //check transaction was created
      val createdTransaction = makeGetRequest(baseRequest.setUrl(baseRequest.url + answerChallengeResponse.locationHeader).GET <@(user1))
      createdTransaction.code should equal(200)
      //check that some of the transaction fields are correct
      val transaction = createdTransaction.body.extract[TransactionJSON1_3_0]
      transaction.id.isEmpty should equal(false)
      //because this is the transaction for the one who sent the payment, the amount should be negative
      transaction.details.value.amount should equal("-" + testTransactionAmount.toString)
    }

    scenario("Payment with a single security challenged answered incorrectly, and then correctly on the second attempt") {
      //TODO: lots of this is duplicate code
      val (bankAccountDetails, response) = makeGoodPayment(Some("challenge_pending"))
      response.code should equal(202)
      response.locationHeader startsWith "/obp/v1.3.0/operations/" should be(true)
      write(response.body) should equal("{}") //TODO: Should the body of the response really be '{}'?

      //the location header should also point to a valid operation resource
      val getOperation = baseRequest.setUrl(baseRequest.url + response.locationHeader).GET <@(user1)
      val operationResponse = makeGetRequest(getOperation)

      operationResponse.code should equal(200)

      val operation = operationResponse.body.extract[OperationJSON1_3_0]
      operation.id.isEmpty should be(false)
      operation.action should equal("POST_TRANSACTION")
      operation.status should equal("CHALLENGE_PENDING")
      operation.challenges.length should equal(1) //sandbox payment processor currently only emits single challenges

      val challenge = operation.challenges(0)
      challenge.id.isEmpty should be(false)
      challenge.label.isEmpty should be(false) //TODO: label vs question, should question really be question_type, e.g. TAN?

      //answer the challenge incorrectly:
      val incorrectAnswerChallengePost = (baseRequest / "obp" / "v1.3.0" / "challenges" / challenge.id / "answers").POST <@(user1)
      val challengeIncorrectResponseJsonString = write(AnswerChallengeJson("incorrect answer"))
      val badAnswerChallengeResponse = makePostRequest(incorrectAnswerChallengePost, challengeIncorrectResponseJsonString)

      badAnswerChallengeResponse.code should equal(400)
      write(badAnswerChallengeResponse.body) should equal("{}") //TODO: Should the body of the response really be '{}'?

      //answer the challenge correctly:
      val answerChallengePost = (baseRequest / "obp" / "v1.3.0" / "challenges" / challenge.id / "answers").POST <@(user1)
      val challengeCorrectResponseJsonString = write(AnswerChallengeJson("Berlin"))
      val answerChallengeResponse = makePostRequest(answerChallengePost, challengeCorrectResponseJsonString)

      answerChallengeResponse.code should equal(204)
      write(answerChallengeResponse.body) should equal("{}") //TODO: Should the body of the response really be '{}'?

      //check transaction was created
      val createdTransaction = makeGetRequest(baseRequest.setUrl(baseRequest.url + answerChallengeResponse.locationHeader).GET <@(user1))
      createdTransaction.code should equal(200)
      //check that some of the transaction fields are correct
      val transaction = createdTransaction.body.extract[TransactionJSON1_3_0]
      transaction.id.isEmpty should equal(false)
      //because this is the transaction for the one who sent the payment, the amount should be negative
      transaction.details.value.amount should equal("-" + testTransactionAmount.toString)
    }

    scenario("Payment fails due to too many incorrectly answered challenges") {
      //TODO
      1 should equal(2)
    }

    scenario("Payment with multiple security challenges") {
      //TODO: need to add this case to the sandbox payment processor
      1 should equal(2)
    }
  }


}
