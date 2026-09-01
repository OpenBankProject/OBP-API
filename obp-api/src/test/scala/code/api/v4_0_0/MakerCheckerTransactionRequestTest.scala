package code.api.v4_0_0

import org.json4s._
import code.api.Constant._
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.api.util.APIUtil.extractErrorMessageCode
import code.api.util.ErrorMessages._
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.TransactionRequestBodyJsonV200
import code.api.v2_1_0._
import code.api.v4_0_0.Http4s400.Implementations4_0_0
import code.model.BankAccountX
import code.setup.DefaultUsers
import code.views.system.ViewPermission
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestStatus
import com.openbankproject.commons.model.enums.TransactionRequestTypes._
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.json4s.native.Serialization.write
import org.scalatest.Tag

class MakerCheckerTransactionRequestTest extends V400ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.answerTransactionRequestChallenge))

  /**
    * Helper to remove the can_bypass_maker_checker_separation permission
    * from the owner system view, forcing maker != checker.
    */
  def removeMakerCheckerPermissionFromOwnerView(): Unit = {
    val viewId = ViewId(SYSTEM_OWNER_VIEW_ID)
    ViewPermission.findSystemViewPermission(viewId, CAN_BYPASS_MAKER_CHECKER_SEPARATION)
      .foreach(_.delete_!)
  }

  /**
    * Helper to restore the permission after a test.
    */
  def addMakerCheckerPermissionToOwnerView(): Unit = {
    val viewId = ViewId(SYSTEM_OWNER_VIEW_ID)
    // Only add if not already present
    if (ViewPermission.findSystemViewPermission(viewId, CAN_BYPASS_MAKER_CHECKER_SEPARATION).isEmpty) {
      ViewPermission.createSystemViewPermission(viewId, CAN_BYPASS_MAKER_CHECKER_SEPARATION, None)
    }
  }

  /**
    * Create a transaction request with a high amount to trigger a challenge,
    * then return (transRequestId, challengeId, helper).
    */
  def createTransactionRequestWithChallenge(consumerAndToken: Option[(Consumer, Token)] = user1) = {
    val transactionRequestType = ACCOUNT.toString
    val testBank = createBank("__mc-test-bank")
    val bankId = testBank.bankId
    val accountId1 = AccountId("__mc_acc1__")
    val accountId2 = AccountId("__mc_acc2__")
    val fromCurrency = "AED"
    val toCurrency = "AED"

    createAccountRelevantResource(Some(resourceUser1), bankId, accountId1, fromCurrency)
    createAccountRelevantResource(Some(resourceUser1), bankId, accountId2, toCurrency)

    val fromAccount = BankAccountX(bankId, accountId1).getOrElse(fail("couldn't get from account"))
    val toAccount = BankAccountX(bankId, accountId2).getOrElse(fail("couldn't get to account"))

    val amt = "30000.00"
    val toAccountJson = TransactionRequestAccountJsonV140(toAccount.bankId.value, toAccount.accountId.value)
    val bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt)
    val transactionRequestBody = TransactionRequestBodyJsonV200(toAccountJson, bodyValue, "Maker-Checker test")

    val createTransReqRequest = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromAccount.accountId.value /
      SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@(consumerAndToken)

    val createResponse = makePostRequest(createTransReqRequest, write(transactionRequestBody))
    createResponse.code should equal(201)

    val transRequestId = (createResponse.body \ "id").values.toString
    transRequestId should not equal ("")

    (createResponse.body \ "status").values.toString should equal(TransactionRequestStatus.INITIATED.toString)

    val challengeId = (createResponse.body \ "challenges").extract[List[JValue]].headOption
      .map(c => (c \ "id").values.toString).getOrElse("")
    challengeId should not equal ("")

    (bankId, fromAccount, transactionRequestType, transRequestId, challengeId)
  }

  feature("Maker-Checker enforcement on answerTransactionRequestChallenge") {

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("Same maker and checker WITH can_have_same_maker_checker permission should SUCCEED", ApiEndpoint1) {}
    } else {
      scenario("Same maker and checker WITH can_have_same_maker_checker permission should SUCCEED", ApiEndpoint1) {
        // Default: owner view has the permission, so same user can make and check
        addMakerCheckerPermissionToOwnerView()

        val (bankId, fromAccount, transactionRequestType, transRequestId, challengeId) =
          createTransactionRequestWithChallenge(user1)

        // Same user (user1) answers the challenge
        val answerJson = ChallengeAnswerJson400(id = challengeId, answer = "123")
        val answerRequest = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromAccount.accountId.value /
          SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@(user1)

        val answerResponse = makePostRequest(answerRequest, write(answerJson))

        Then("we should get a 202 code - same maker/checker allowed")
        answerResponse.code should equal(202)
        (answerResponse.body \ "status").values.toString should equal(TransactionRequestStatus.COMPLETED.toString)
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("Same maker and checker WITHOUT can_have_same_maker_checker permission should FAIL", ApiEndpoint1) {}
    } else {
      scenario("Same maker and checker WITHOUT can_have_same_maker_checker permission should FAIL", ApiEndpoint1) {
        val (bankId, fromAccount, transactionRequestType, transRequestId, challengeId) =
          createTransactionRequestWithChallenge(user1)

        // Remove the permission to enforce maker != checker
        removeMakerCheckerPermissionFromOwnerView()

        try {
          // Same user (user1) tries to answer the challenge
          val answerJson = ChallengeAnswerJson400(id = challengeId, answer = "123")
          val answerRequest = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromAccount.accountId.value /
            SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@(user1)

          val answerResponse = makePostRequest(answerRequest, write(answerJson))

          Then("we should get a 400 code - same maker/checker NOT allowed")
          answerResponse.code should equal(400)

          And("the error message should indicate maker/checker separation")
          val errorMessage = (answerResponse.body \ "message").values.toString
          errorMessage should include("OBP-30279")
        } finally {
          // Restore the permission for other tests
          addMakerCheckerPermissionToOwnerView()
        }
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("Different maker and checker WITHOUT can_have_same_maker_checker permission should SUCCEED", ApiEndpoint1) {}
    } else {
      scenario("Different maker and checker WITHOUT can_have_same_maker_checker permission should SUCCEED", ApiEndpoint1) {
        val (bankId, fromAccount, transactionRequestType, transRequestId, challengeId) =
          createTransactionRequestWithChallenge(user1)

        // Remove the permission to enforce maker != checker
        removeMakerCheckerPermissionFromOwnerView()

        try {
          // Grant user2 access to the owner view on this account
          grantUserAccessToViewViaEndpoint(
            bankId.value,
            fromAccount.accountId.value,
            resourceUser2.userId,
            user1,
            PostViewJsonV400(view_id = SYSTEM_OWNER_VIEW_ID, is_system = true)
          )

          // Different user (user2) answers the challenge
          val answerJson = ChallengeAnswerJson400(id = challengeId, answer = "123")
          val answerRequest = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromAccount.accountId.value /
            SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@(user2)

          val answerResponse = makePostRequest(answerRequest, write(answerJson))

          Then("we should get a 202 code - different maker and checker is allowed")
          answerResponse.code should equal(202)
          (answerResponse.body \ "status").values.toString should equal(TransactionRequestStatus.COMPLETED.toString)
        } finally {
          // Restore the permission for other tests
          addMakerCheckerPermissionToOwnerView()
        }
      }
    }

    if (APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false) == false) {
      ignore("Multiple challenges with maker-checker: different users answer their own challenges", ApiEndpoint1) {}
    } else {
      scenario("Multiple challenges with maker-checker: different users answer their own challenges", ApiEndpoint1) {
        val transactionRequestType = COUNTERPARTY.toString
        val testBank = createBank("__mc-test-bank-multi")
        val bankId = testBank.bankId
        val accountId1 = AccountId("__mc_multi_acc1__")
        val accountId2 = AccountId("__mc_multi_acc2__")
        val fromCurrency = "AED"
        val toCurrency = "INR"

        createAccountRelevantResource(Some(resourceUser1), bankId, accountId1, fromCurrency)
        createAccountRelevantResource(Some(resourceUser1), bankId, accountId2, toCurrency)
        updateAccountCurrency(bankId, accountId2, toCurrency)

        val fromAccount = BankAccountX(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        val toAccount = BankAccountX(bankId, accountId2).getOrElse(fail("couldn't get to account"))

        val counterparty = createCounterparty(bankId.value, accountId1.value, accountId2.value, true, java.util.UUID.randomUUID.toString)

        // Set REQUIRED_CHALLENGE_ANSWERS to 2
        createAccountAttributeViaEndpoint(
          bankId.value,
          accountId1.value,
          "REQUIRED_CHALLENGE_ANSWERS",
          "2",
          "INTEGER",
          Some("LKJL98769G")
        )

        // Grant user2 access to the owner view
        grantUserAccessToViewViaEndpoint(
          bankId.value,
          accountId1.value,
          resourceUser2.userId,
          user1,
          PostViewJsonV400(view_id = SYSTEM_OWNER_VIEW_ID, is_system = true)
        )

        // Remove the permission to enforce maker != checker
        removeMakerCheckerPermissionFromOwnerView()

        try {
          val amt = "30000.00"
          val bodyValue = AmountOfMoneyJsonV121(fromCurrency, amt)
          val transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(
            CounterpartyIdJson(counterparty.counterpartyId), bodyValue, "Multi-challenge MC test", "SHARED"
          )

          val createTransReqRequest = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromAccount.accountId.value /
            SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@(user1)

          val createResponse = makePostRequest(createTransReqRequest, write(transactionRequestBodyCounterparty))
          createResponse.code should equal(201)

          val createResponseJson = createResponse.body.extract[TransactionRequestWithChargeJSON400]
          createResponseJson.status should equal(TransactionRequestStatus.INITIATED.toString)

          val transRequestId = createResponseJson.id
          val challengeOfUser1: Option[ChallengeJsonV400] = createResponseJson.challenges.find(_.user_id == resourceUser1.userId)
          val challengeOfUser2: Option[ChallengeJsonV400] = createResponseJson.challenges.find(_.user_id == resourceUser2.userId)

          challengeOfUser1 should not be (None)
          challengeOfUser2 should not be (None)

          Then("User1 answers their own challenge (user1 is maker, but this is user1's own challenge)")
          val answerJson1 = ChallengeAnswerJson400(id = challengeOfUser1.map(_.id).getOrElse(""), answer = "123")
          val answerRequest1 = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromAccount.accountId.value /
            SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@(user1)
          val ansReqResponseUser1 = makePostRequest(answerRequest1, write(answerJson1))

          And("User1's answer should indicate next challenge is pending (maker-checker check passes because user1 is maker answering their own challenge)")
          ansReqResponseUser1.body.extract[ErrorMessage].message contains extractErrorMessageCode(NextChallengePending) should be(true)

          Then("User2 answers their own challenge (user2 is different from maker user1)")
          val answerJson2 = ChallengeAnswerJson400(id = challengeOfUser2.map(_.id).getOrElse(""), answer = "123")
          val answerRequest2 = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromAccount.accountId.value /
            SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests" / transRequestId / "challenge").POST <@(user2)
          val ansReqResponseUser2 = makePostRequest(answerRequest2, write(answerJson2))

          And("The transaction request should be completed")
          ansReqResponseUser2.body.extract[TransactionRequestWithChargeJSON400].status should equal(TransactionRequestStatus.COMPLETED.toString)
        } finally {
          addMakerCheckerPermissionToOwnerView()
        }
      }

      // Regression guard for the request-scoped-connection TTL race (formerly ~40% flaky;
      // resolved by the RequestScopeConnection hardening — childValue→null override +
      // stale-proxy isClosed() guard). Each multi-challenge create writes 2
      // MappedExpectedChallengeAnswer rows on the request-scoped proxy connection (autocommit
      // off) and then reads them back into the 201 response while building `challenges`. If the
      // proxy fails to propagate to the read Future's worker, the read lands on a fresh pool
      // connection and sees 0 uncommitted rows → a challenge goes missing. Firing the create
      // many times in one warm JVM maximises ForkJoinPool scheduling pressure on that
      // write→read surface, so a regression in the connection-propagation logic shows up here.
      scenario("Stress: repeated multi-challenge creates must always read back both challenges (RequestScopeConnection regression guard)", ApiEndpoint1) {
        val iterations = 20
        val transactionRequestType = COUNTERPARTY.toString
        val testBank = createBank("__mc-stress-bank")
        val bankId = testBank.bankId
        val accountId1 = AccountId("__mc_stress_acc1__")
        val accountId2 = AccountId("__mc_stress_acc2__")
        val fromCurrency = "AED"
        val toCurrency = "INR"

        createAccountRelevantResource(Some(resourceUser1), bankId, accountId1, fromCurrency)
        createAccountRelevantResource(Some(resourceUser1), bankId, accountId2, toCurrency)
        updateAccountCurrency(bankId, accountId2, toCurrency)

        val fromAccount = BankAccountX(bankId, accountId1).getOrElse(fail("couldn't get from account"))
        val counterparty = createCounterparty(bankId.value, accountId1.value, accountId2.value, true, java.util.UUID.randomUUID.toString)

        // REQUIRED_CHALLENGE_ANSWERS = 2 forces the multi-user (quorum > 1) path that exercises the race.
        createAccountAttributeViaEndpoint(bankId.value, accountId1.value, "REQUIRED_CHALLENGE_ANSWERS", "2", "INTEGER", Some("LKJL98769G"))
        grantUserAccessToViewViaEndpoint(bankId.value, accountId1.value, resourceUser2.userId, user1,
          PostViewJsonV400(view_id = SYSTEM_OWNER_VIEW_ID, is_system = true))
        removeMakerCheckerPermissionFromOwnerView()

        try {
          val bodyValue = AmountOfMoneyJsonV121(fromCurrency, "30000.00")
          val transactionRequestBodyCounterparty = TransactionRequestBodyCounterpartyJSON(
            CounterpartyIdJson(counterparty.counterpartyId), bodyValue, "Multi-challenge MC stress", "SHARED")
          val createTransReqRequest = (v4_0_0_Request / "banks" / bankId.value / "accounts" / fromAccount.accountId.value /
            SYSTEM_OWNER_VIEW_ID / "transaction-request-types" / transactionRequestType / "transaction-requests").POST <@(user1)

          // INITIATED only — no money moves, so we can repeat freely.
          (1 to iterations).foreach { iter =>
            withClue(s"iteration $iter of $iterations: ") {
              val createResponse = makePostRequest(createTransReqRequest, write(transactionRequestBodyCounterparty))
              createResponse.code should equal(201)
              val json = createResponse.body.extract[TransactionRequestWithChargeJSON400]
              json.status should equal(TransactionRequestStatus.INITIATED.toString)
              // The race manifests as a missing challenge (read saw 0 uncommitted rows).
              json.challenges.find(_.user_id == resourceUser1.userId) should not be (None)
              json.challenges.find(_.user_id == resourceUser2.userId) should not be (None)
            }
          }
        } finally {
          addMakerCheckerPermissionToOwnerView()
        }
      }
    }

  }

}
