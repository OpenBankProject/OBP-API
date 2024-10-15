package code.bankconnectors.rabbitmq.Adapter

import bootstrap.liftweb.ToSchemify
import code.api.util.{APIUtil}
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._
import net.liftweb.db.DB
import net.liftweb.json
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.{Schemifier}
import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

class ServerCallback(val ch: Channel) extends DeliverCallback {

  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  override def handle(consumerTag: String, delivery: Delivery): Unit = {
    var response: Future[String] = Future {
      ""
    }
    val obpMessageId = delivery.getProperties.getMessageId
    val replyProps = new BasicProperties.Builder()
      .correlationId(delivery.getProperties.getCorrelationId)
      .contentType("application/json")
      .messageId(obpMessageId)
      .build
    val message = new String(delivery.getBody, "UTF-8")
    println(s"Request: OutBound message from OBP: methodId($obpMessageId) : message is $message ")

    try {
      val responseToOBP = if (obpMessageId.contains("obp_get_banks")) {
        val outBound = json.parse(message).extract[OutBoundGetBanks]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBanks(None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetBanks(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBanks(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("obp_get_bank")) {
        val outBound = json.parse(message).extract[OutBoundGetBank]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBank(outBound.bankId, None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetBank(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBanks(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
// ---------- created on 2024-10-15T22:43:44Z

      } else if (obpMessageId.contains("get_adapter_info")) {
        val outBound = json.parse(message).extract[OutBoundGetAdapterInfo]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getAdapterInfo(None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetAdapterInfo(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetAdapterInfo(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("validate_and_check_iban_number")) {
        val outBound = json.parse(message).extract[OutBoundValidateAndCheckIbanNumber]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.validateAndCheckIbanNumber(outBound.iban,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundValidateAndCheckIbanNumber(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundValidateAndCheckIbanNumber(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_challenge_threshold")) {
        val outBound = json.parse(message).extract[OutBoundGetChallengeThreshold]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getChallengeThreshold(outBound.bankId,outBound.accountId,outBound.viewId,outBound.transactionRequestType,outBound.currency,outBound.userId,outBound.username,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetChallengeThreshold(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetChallengeThreshold(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_charge_level")) {
        val outBound = json.parse(message).extract[OutBoundGetChargeLevel]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getChargeLevel(outBound.bankId,outBound.accountId,outBound.viewId,outBound.userId,outBound.username,outBound.transactionRequestType,outBound.currency,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetChargeLevel(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetChargeLevel(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_charge_level_c2")) {
        val outBound = json.parse(message).extract[OutBoundGetChargeLevelC2]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getChargeLevelC2(outBound.bankId,outBound.accountId,outBound.viewId,outBound.userId,outBound.username,outBound.transactionRequestType,outBound.currency,outBound.amount,outBound.toAccountRoutings,outBound.customAttributes,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetChargeLevelC2(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetChargeLevelC2(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_challenge")) {
        val outBound = json.parse(message).extract[OutBoundCreateChallenge]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createChallenge(outBound.bankId,outBound.accountId,outBound.userId,outBound.transactionRequestType,outBound.transactionRequestId,outBound.scaMethod,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateChallenge(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateChallenge(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_challenges")) {
        val outBound = json.parse(message).extract[OutBoundCreateChallenges]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createChallenges(outBound.bankId,outBound.accountId,outBound.userIds,outBound.transactionRequestType,outBound.transactionRequestId,outBound.scaMethod,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateChallenges(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateChallenges(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_challenges_c2")) {
        val outBound = json.parse(message).extract[OutBoundCreateChallengesC2]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createChallengesC2(outBound.userIds,outBound.challengeType,outBound.transactionRequestId,outBound.scaMethod,outBound.scaStatus,outBound.consentId,outBound.authenticationMethodId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateChallengesC2(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateChallengesC2(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_challenges_c3")) {
        val outBound = json.parse(message).extract[OutBoundCreateChallengesC3]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createChallengesC3(outBound.userIds,outBound.challengeType,outBound.transactionRequestId,outBound.scaMethod,outBound.scaStatus,outBound.consentId,outBound.basketId,outBound.authenticationMethodId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateChallengesC3(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateChallengesC3(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("validate_challenge_answer")) {
        val outBound = json.parse(message).extract[OutBoundValidateChallengeAnswer]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.validateChallengeAnswer(outBound.challengeId,outBound.hashOfSuppliedAnswer,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundValidateChallengeAnswer(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundValidateChallengeAnswer(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("validate_challenge_answer_v2")) {
        val outBound = json.parse(message).extract[OutBoundValidateChallengeAnswerV2]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.validateChallengeAnswerV2(outBound.challengeId,outBound.suppliedAnswer,outBound.suppliedAnswerType,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundValidateChallengeAnswerV2(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundValidateChallengeAnswerV2(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("validate_challenge_answer_c2")) {
        val outBound = json.parse(message).extract[OutBoundValidateChallengeAnswerC2]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.validateChallengeAnswerC2(outBound.transactionRequestId,outBound.consentId,outBound.challengeId,outBound.hashOfSuppliedAnswer,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundValidateChallengeAnswerC2(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundValidateChallengeAnswerC2(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("validate_challenge_answer_c3")) {
        val outBound = json.parse(message).extract[OutBoundValidateChallengeAnswerC3]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.validateChallengeAnswerC3(outBound.transactionRequestId,outBound.consentId,outBound.basketId,outBound.challengeId,outBound.hashOfSuppliedAnswer,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundValidateChallengeAnswerC3(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundValidateChallengeAnswerC3(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("validate_challenge_answer_c4")) {
        val outBound = json.parse(message).extract[OutBoundValidateChallengeAnswerC4]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.validateChallengeAnswerC4(outBound.transactionRequestId,outBound.consentId,outBound.challengeId,outBound.suppliedAnswer,outBound.suppliedAnswerType,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundValidateChallengeAnswerC4(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundValidateChallengeAnswerC4(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("validate_challenge_answer_c5")) {
        val outBound = json.parse(message).extract[OutBoundValidateChallengeAnswerC5]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.validateChallengeAnswerC5(outBound.transactionRequestId,outBound.consentId,outBound.basketId,outBound.challengeId,outBound.suppliedAnswer,outBound.suppliedAnswerType,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundValidateChallengeAnswerC5(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundValidateChallengeAnswerC5(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_challenges_by_transaction_request_id")) {
        val outBound = json.parse(message).extract[OutBoundGetChallengesByTransactionRequestId]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getChallengesByTransactionRequestId(outBound.transactionRequestId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetChallengesByTransactionRequestId(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetChallengesByTransactionRequestId(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_challenges_by_consent_id")) {
        val outBound = json.parse(message).extract[OutBoundGetChallengesByConsentId]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getChallengesByConsentId(outBound.consentId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetChallengesByConsentId(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetChallengesByConsentId(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_challenges_by_basket_id")) {
        val outBound = json.parse(message).extract[OutBoundGetChallengesByBasketId]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getChallengesByBasketId(outBound.basketId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetChallengesByBasketId(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetChallengesByBasketId(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_challenge")) {
        val outBound = json.parse(message).extract[OutBoundGetChallenge]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getChallenge(outBound.challengeId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetChallenge(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetChallenge(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank")) {
        val outBound = json.parse(message).extract[OutBoundGetBank]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBank(outBound.bankId,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetBank(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBank(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_banks")) {
        val outBound = json.parse(message).extract[OutBoundGetBanks]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBanks(None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetBanks(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBanks(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_accounts_for_user")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAccountsForUser]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAccountsForUser(outBound.provider,outBound.username,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetBankAccountsForUser(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAccountsForUser(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_account_by_iban")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAccountByIban]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAccountByIban(outBound.iban,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetBankAccountByIban(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAccountByIban(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_account_by_routing")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAccountByRouting]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAccountByRouting(outBound.bankId,outBound.scheme,outBound.address,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetBankAccountByRouting(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAccountByRouting(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_accounts")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAccounts]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAccounts(outBound.bankIdAccountIds,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetBankAccounts(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAccounts(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_accounts_balances")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAccountsBalances]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAccountsBalances(outBound.bankIdAccountIds,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetBankAccountsBalances(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAccountsBalances(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_account_balances")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAccountBalances]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAccountBalances(outBound.bankIdAccountId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetBankAccountBalances(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAccountBalances(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_core_bank_accounts")) {
        val outBound = json.parse(message).extract[OutBoundGetCoreBankAccounts]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCoreBankAccounts(outBound.bankIdAccountIds,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetCoreBankAccounts(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCoreBankAccounts(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_accounts_held")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAccountsHeld]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAccountsHeld(outBound.bankIdAccountIds,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetBankAccountsHeld(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAccountsHeld(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("check_bank_account_exists")) {
        val outBound = json.parse(message).extract[OutBoundCheckBankAccountExists]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.checkBankAccountExists(outBound.bankId,outBound.accountId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCheckBankAccountExists(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCheckBankAccountExists(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_counterparty_trait")) {
        val outBound = json.parse(message).extract[OutBoundGetCounterpartyTrait]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCounterpartyTrait(outBound.bankId,outBound.accountId,outBound.couterpartyId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCounterpartyTrait(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCounterpartyTrait(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_counterparty_by_counterparty_id")) {
        val outBound = json.parse(message).extract[OutBoundGetCounterpartyByCounterpartyId]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCounterpartyByCounterpartyId(outBound.counterpartyId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCounterpartyByCounterpartyId(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCounterpartyByCounterpartyId(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_counterparty_by_iban")) {
        val outBound = json.parse(message).extract[OutBoundGetCounterpartyByIban]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCounterpartyByIban(outBound.iban,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCounterpartyByIban(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCounterpartyByIban(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_counterparty_by_iban_and_bank_account_id")) {
        val outBound = json.parse(message).extract[OutBoundGetCounterpartyByIbanAndBankAccountId]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCounterpartyByIbanAndBankAccountId(outBound.iban,outBound.bankId,outBound.accountId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCounterpartyByIbanAndBankAccountId(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCounterpartyByIbanAndBankAccountId(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_counterparties")) {
        val outBound = json.parse(message).extract[OutBoundGetCounterparties]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCounterparties(outBound.thisBankId,outBound.thisAccountId,outBound.viewId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCounterparties(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCounterparties(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_transactions")) {
        val outBound = json.parse(message).extract[OutBoundGetTransactions]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getTransactions(outBound.bankId,outBound.accountId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetTransactions(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTransactions(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_transactions_core")) {
        val outBound = json.parse(message).extract[OutBoundGetTransactionsCore]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getTransactionsCore(outBound.bankId,outBound.accountId,Nil,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetTransactionsCore(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTransactionsCore(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_transaction")) {
        val outBound = json.parse(message).extract[OutBoundGetTransaction]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getTransaction(outBound.bankId,outBound.accountId,outBound.transactionId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetTransaction(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTransaction(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_physical_cards_for_user")) {
        val outBound = json.parse(message).extract[OutBoundGetPhysicalCardsForUser]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getPhysicalCardsForUser(outBound.user,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetPhysicalCardsForUser(
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetPhysicalCardsForUser(
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_physical_card_for_bank")) {
        val outBound = json.parse(message).extract[OutBoundGetPhysicalCardForBank]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getPhysicalCardForBank(outBound.bankId,outBound.cardId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetPhysicalCardForBank(
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response.asInstanceOf[PhysicalCard]
        )).recoverWith {
          case e: Exception => Future(InBoundGetPhysicalCardForBank(
            inboundAdapterCallContext = InboundAdapterCallContext(
              correlationId = outBound.outboundAdapterCallContext.correlationId
            ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
// ---------- created on 2024-10-15T22:43:44Z
//---------------- dynamic start -------------------please don't modify this line
//---------------- dynamic end ---------------------please don't modify this line 
      } else {  
        Future {
          1
        }
      }
      
      response = responseToOBP.map(a => write(a)).map("" + _)
      response.map(res => println(s"Response: inBound message to OBP: process($obpMessageId) : message is $res "))
      response
    } catch {
      case e: Throwable => println("Unknown exception: " + e.toString)

    } finally {
      response.map(res => ch.basicPublish("", delivery.getProperties.getReplyTo, replyProps, res.getBytes("UTF-8")))
      ch.basicAck(delivery.getEnvelope.getDeliveryTag, false)
    }
  }

}

object RPCServer extends App {
  private val RPC_QUEUE_NAME = "obp_rpc_queue"
  // lazy initial RabbitMQ connection
  val host = APIUtil.getPropsValue("rabbitmq_connector.host").openOrThrowException("mandatory property rabbitmq_connector.host is missing!")
  val port = APIUtil.getPropsAsIntValue("rabbitmq_connector.port").openOrThrowException("mandatory property rabbitmq_connector.port is missing!")
//  val username = APIUtil.getPropsValue("rabbitmq_connector.username").openOrThrowException("mandatory property rabbitmq_connector.username is missing!")
//  val password = APIUtil.getPropsValue("rabbitmq_connector.password").openOrThrowException("mandatory property rabbitmq_connector.password is missing!")

  DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier, APIUtil.vendor)
  Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.models: _*)
  
  
  var connection: Connection = null
  var channel: Channel = null
  try {
    val factory = new ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername("server")
    factory.setPassword("server")
    connection = factory.newConnection()
    channel = connection.createChannel()
    channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null)
    channel.basicQos(1)
    // stop after one consumed message since this is example code
    val serverCallback = new ServerCallback(channel)
    channel basicConsume(RPC_QUEUE_NAME, false, serverCallback, _ => {})
    println("Start awaiting OBP Connector Requests:")
  } catch {
    case e: Exception => e.printStackTrace()
  } finally {
    if (connection != null) {
      try {
        //          connection.close()
      } catch {
        case e: Exception => println(s"unknown Exception:$e")
      }
    }
  }

}
