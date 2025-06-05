package code.bankconnectors.rabbitmq.Adapter

import bootstrap.liftweb.ToSchemify
import code.api.util.APIUtil
import code.bankconnectors.rabbitmq.RabbitMQUtils
import code.bankconnectors.rabbitmq.RabbitMQUtils._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client._
import net.liftweb.db.DB
import net.liftweb.json
import net.liftweb.json.Serialization.write
import net.liftweb.mapper.Schemifier

import java.util.Date
import scala.concurrent.Future

class ServerCallback(val ch: Channel) extends DeliverCallback with MdcLoggable{

  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  override def handle(consumerTag: String, delivery: Delivery): Unit = {
    var response: Future[String] = Future {
      ""
    }
    val obpMessageId = delivery.getProperties.getMessageId
    val replyProps = new BasicProperties.Builder()
      .correlationId(delivery.getProperties.getCorrelationId)
      .contentType("application/json")
      .expiration("60000")
      .messageId(obpMessageId)
      .build
    val message = new String(delivery.getBody, "UTF-8")
     logger.debug(s"Request: OutBound message from OBP: methodId($obpMessageId) : message is $message ")

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
//---------------- dynamic start -------------------please don't modify this line
// ---------- created on 2025-05-27T08:15:58Z

      } else if (obpMessageId.contains("get_adapter_info")) {
        val outBound = json.parse(message).extract[OutBoundGetAdapterInfo]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getAdapterInfo(None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetAdapterInfo(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = InboundAdapterInfoInternal(
            errorCode = "",
            backendMessages = Nil,
            name ="RABBITMQ",
            version ="rabbitmq_vOct2024",
            git_commit ="",
            date = (new Date()).toString
        ))).recoverWith {
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
      } else if (obpMessageId.contains("get_accounts_held")) {
        val outBound = json.parse(message).extract[OutBoundGetAccountsHeld]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getAccountsHeld(outBound.bankId,outBound.user,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetAccountsHeld(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetAccountsHeld(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_accounts_held_by_user")) {
        val outBound = json.parse(message).extract[OutBoundGetAccountsHeldByUser]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getAccountsHeldByUser(outBound.user,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetAccountsHeldByUser(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetAccountsHeldByUser(          
          
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
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetPhysicalCardsForUser(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
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
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetPhysicalCardForBank(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("delete_physical_card_for_bank")) {
        val outBound = json.parse(message).extract[OutBoundDeletePhysicalCardForBank]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.deletePhysicalCardForBank(outBound.bankId,outBound.cardId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundDeletePhysicalCardForBank(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundDeletePhysicalCardForBank(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("get_physical_cards_for_bank")) {
        val outBound = json.parse(message).extract[OutBoundGetPhysicalCardsForBank]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getPhysicalCardsForBank(outBound.bank,outBound.user,Nil,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetPhysicalCardsForBank(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetPhysicalCardsForBank(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_physical_card")) {
        val outBound = json.parse(message).extract[OutBoundCreatePhysicalCard]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createPhysicalCard(outBound.bankCardNumber,outBound.nameOnCard,outBound.cardType,outBound.issueNumber,outBound.serialNumber,outBound.validFrom,outBound.expires,outBound.enabled,outBound.cancelled,outBound.onHotList,outBound.technology,outBound.networks,outBound.allows,outBound.accountId,outBound.bankId,outBound.replacement,outBound.pinResets,outBound.collected,outBound.posted,outBound.customerId,outBound.cvv,outBound.brand,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreatePhysicalCard(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreatePhysicalCard(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("update_physical_card")) {
        val outBound = json.parse(message).extract[OutBoundUpdatePhysicalCard]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.updatePhysicalCard(outBound.cardId,outBound.bankCardNumber,outBound.nameOnCard,outBound.cardType,outBound.issueNumber,outBound.serialNumber,outBound.validFrom,outBound.expires,outBound.enabled,outBound.cancelled,outBound.onHotList,outBound.technology,outBound.networks,outBound.allows,outBound.accountId,outBound.bankId,outBound.replacement,outBound.pinResets,outBound.collected,outBound.posted,outBound.customerId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundUpdatePhysicalCard(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundUpdatePhysicalCard(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("make_paymentv210")) {
        val outBound = json.parse(message).extract[OutBoundMakePaymentv210]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.makePaymentv210(outBound.fromAccount,outBound.toAccount,outBound.transactionRequestId,outBound.transactionRequestCommonBody,outBound.amount,outBound.description,outBound.transactionRequestType,outBound.chargePolicy,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundMakePaymentv210(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundMakePaymentv210(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_charge_value")) {
        val outBound = json.parse(message).extract[OutBoundGetChargeValue]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getChargeValue(outBound.chargeLevelAmount,outBound.transactionRequestCommonBodyAmount,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetChargeValue(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetChargeValue(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_transaction_requestv210")) {
        val outBound = json.parse(message).extract[OutBoundCreateTransactionRequestv210]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createTransactionRequestv210(outBound.initiator,outBound.viewId,outBound.fromAccount,outBound.toAccount,outBound.transactionRequestType,outBound.transactionRequestCommonBody,outBound.detailsPlain,outBound.chargePolicy,outBound.challengeType,outBound.scaMethod,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateTransactionRequestv210(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateTransactionRequestv210(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_transaction_requestv400")) {
        val outBound = json.parse(message).extract[OutBoundCreateTransactionRequestv400]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createTransactionRequestv400(outBound.initiator,outBound.viewId,outBound.fromAccount,outBound.toAccount,outBound.transactionRequestType,outBound.transactionRequestCommonBody,outBound.detailsPlain,outBound.chargePolicy,outBound.challengeType,outBound.scaMethod,outBound.reasons,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateTransactionRequestv400(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateTransactionRequestv400(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_transaction_request_sepa_credit_transfers_bgv1")) {
        val outBound = json.parse(message).extract[OutBoundCreateTransactionRequestSepaCreditTransfersBGV1]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createTransactionRequestSepaCreditTransfersBGV1(outBound.initiator,outBound.paymentServiceType,outBound.transactionRequestType,outBound.transactionRequestBody,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateTransactionRequestSepaCreditTransfersBGV1(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateTransactionRequestSepaCreditTransfersBGV1(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_transaction_request_periodic_sepa_credit_transfers_bgv1")) {
        val outBound = json.parse(message).extract[OutBoundCreateTransactionRequestPeriodicSepaCreditTransfersBGV1]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createTransactionRequestPeriodicSepaCreditTransfersBGV1(outBound.initiator,outBound.paymentServiceType,outBound.transactionRequestType,outBound.transactionRequestBody,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateTransactionRequestPeriodicSepaCreditTransfersBGV1(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateTransactionRequestPeriodicSepaCreditTransfersBGV1(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("save_transaction_request_transaction")) {
        val outBound = json.parse(message).extract[OutBoundSaveTransactionRequestTransaction]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.saveTransactionRequestTransaction(outBound.transactionRequestId,outBound.transactionId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundSaveTransactionRequestTransaction(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundSaveTransactionRequestTransaction(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("save_transaction_request_challenge")) {
        val outBound = json.parse(message).extract[OutBoundSaveTransactionRequestChallenge]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.saveTransactionRequestChallenge(outBound.transactionRequestId,outBound.challenge,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundSaveTransactionRequestChallenge(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundSaveTransactionRequestChallenge(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("save_transaction_request_status_impl")) {
        val outBound = json.parse(message).extract[OutBoundSaveTransactionRequestStatusImpl]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.saveTransactionRequestStatusImpl(outBound.transactionRequestId,outBound.status,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundSaveTransactionRequestStatusImpl(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundSaveTransactionRequestStatusImpl(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("get_transaction_requests210")) {
        val outBound = json.parse(message).extract[OutBoundGetTransactionRequests210]
        val obpMappedResponse = Future{code.bankconnectors.LocalMappedConnector.getTransactionRequests210(outBound.initiator,outBound.fromAccount,None).map(_._1).head}
        
        obpMappedResponse.map(response => InBoundGetTransactionRequests210(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTransactionRequests210(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_transaction_request_impl")) {
        val outBound = json.parse(message).extract[OutBoundGetTransactionRequestImpl]
        val obpMappedResponse = Future{code.bankconnectors.LocalMappedConnector.getTransactionRequestImpl(outBound.transactionRequestId,None).map(_._1).head}
        
        obpMappedResponse.map(response => InBoundGetTransactionRequestImpl(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTransactionRequestImpl(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_transaction_request_types")) {
        val outBound = json.parse(message).extract[OutBoundGetTransactionRequestTypes]
        val obpMappedResponse = Future{code.bankconnectors.LocalMappedConnector.getTransactionRequestTypes(outBound.initiator,outBound.fromAccount,None).map(_._1).head}
        
        obpMappedResponse.map(response => InBoundGetTransactionRequestTypes(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTransactionRequestTypes(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_transaction_after_challenge_v210")) {
        val outBound = json.parse(message).extract[OutBoundCreateTransactionAfterChallengeV210]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createTransactionAfterChallengeV210(outBound.fromAccount,outBound.transactionRequest,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateTransactionAfterChallengeV210(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateTransactionAfterChallengeV210(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("update_bank_account")) {
        val outBound = json.parse(message).extract[OutBoundUpdateBankAccount]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.updateBankAccount(outBound.bankId,outBound.accountId,outBound.accountType,outBound.accountLabel,outBound.branchId,outBound.accountRoutings,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundUpdateBankAccount(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundUpdateBankAccount(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_bank_account")) {
        val outBound = json.parse(message).extract[OutBoundCreateBankAccount]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createBankAccount(outBound.bankId,outBound.accountId,outBound.accountType,outBound.accountLabel,outBound.currency,outBound.initialBalance,outBound.accountHolderName,outBound.branchId,outBound.accountRoutings,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateBankAccount(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateBankAccount(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("update_account_label")) {
        val outBound = json.parse(message).extract[OutBoundUpdateAccountLabel]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.updateAccountLabel(outBound.bankId,outBound.accountId,outBound.label,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundUpdateAccountLabel(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundUpdateAccountLabel(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("get_products")) {
        val outBound = json.parse(message).extract[OutBoundGetProducts]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getProducts(outBound.bankId,outBound.params,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetProducts(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetProducts(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_product")) {
        val outBound = json.parse(message).extract[OutBoundGetProduct]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getProduct(outBound.bankId,outBound.productCode,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetProduct(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetProduct(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_branch")) {
        val outBound = json.parse(message).extract[OutBoundGetBranch]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBranch(outBound.bankId,outBound.branchId,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetBranch(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBranch(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_branches")) {
        val outBound = json.parse(message).extract[OutBoundGetBranches]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBranches(outBound.bankId,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetBranches(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBranches(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_atm")) {
        val outBound = json.parse(message).extract[OutBoundGetAtm]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getAtm(outBound.bankId,outBound.atmId,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetAtm(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetAtm(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_atms")) {
        val outBound = json.parse(message).extract[OutBoundGetAtms]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getAtms(outBound.bankId,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetAtms(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetAtms(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_transaction_after_challengev300")) {
        val outBound = json.parse(message).extract[OutBoundCreateTransactionAfterChallengev300]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createTransactionAfterChallengev300(outBound.initiator,outBound.fromAccount,outBound.transReqId,outBound.transactionRequestType,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateTransactionAfterChallengev300(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateTransactionAfterChallengev300(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("make_paymentv300")) {
        val outBound = json.parse(message).extract[OutBoundMakePaymentv300]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.makePaymentv300(outBound.initiator,outBound.fromAccount,outBound.toAccount,outBound.toCounterparty,outBound.transactionRequestCommonBody,outBound.transactionRequestType,outBound.chargePolicy,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundMakePaymentv300(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundMakePaymentv300(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_transaction_requestv300")) {
        val outBound = json.parse(message).extract[OutBoundCreateTransactionRequestv300]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createTransactionRequestv300(outBound.initiator,outBound.viewId,outBound.fromAccount,outBound.toAccount,outBound.toCounterparty,outBound.transactionRequestType,outBound.transactionRequestCommonBody,outBound.detailsPlain,outBound.chargePolicy,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundCreateTransactionRequestv300(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateTransactionRequestv300(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("make_payment_v400")) {
        val outBound = json.parse(message).extract[OutBoundMakePaymentV400]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.makePaymentV400(outBound.transactionRequest,outBound.reasons,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundMakePaymentV400(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundMakePaymentV400(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("cancel_payment_v400")) {
        val outBound = json.parse(message).extract[OutBoundCancelPaymentV400]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.cancelPaymentV400(outBound.transactionId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCancelPaymentV400(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCancelPaymentV400(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_transaction_request_type_charges")) {
        val outBound = json.parse(message).extract[OutBoundGetTransactionRequestTypeCharges]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getTransactionRequestTypeCharges(outBound.bankId,outBound.accountId,outBound.viewId,outBound.transactionRequestTypes,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetTransactionRequestTypeCharges(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTransactionRequestTypeCharges(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_counterparty")) {
        val outBound = json.parse(message).extract[OutBoundCreateCounterparty]
        val obpMappedResponse = Future{code.bankconnectors.LocalMappedConnector.createCounterparty(outBound.name,outBound.description,outBound.currency,outBound.createdByUserId,outBound.thisBankId,outBound.thisAccountId,outBound.thisViewId,outBound.otherAccountRoutingScheme,outBound.otherAccountRoutingAddress,outBound.otherAccountSecondaryRoutingScheme,outBound.otherAccountSecondaryRoutingAddress,outBound.otherBankRoutingScheme,outBound.otherBankRoutingAddress,outBound.otherBranchRoutingScheme,outBound.otherBranchRoutingAddress,outBound.isBeneficiary,outBound.bespoke,None).map(_._1).head}
        
        obpMappedResponse.map(response => InBoundCreateCounterparty(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateCounterparty(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("check_customer_number_available")) {
        val outBound = json.parse(message).extract[OutBoundCheckCustomerNumberAvailable]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.checkCustomerNumberAvailable(outBound.bankId,outBound.customerNumber,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCheckCustomerNumberAvailable(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCheckCustomerNumberAvailable(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("create_customer")) {
        val outBound = json.parse(message).extract[OutBoundCreateCustomer]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createCustomer(outBound.bankId,outBound.legalName,outBound.mobileNumber,outBound.email,outBound.faceImage,outBound.dateOfBirth,outBound.relationshipStatus,outBound.dependents,outBound.dobOfDependents,outBound.highestEducationAttained,outBound.employmentStatus,outBound.kycStatus,outBound.lastOkDate,outBound.creditRating,outBound.creditLimit,outBound.title,outBound.branchId,outBound.nameSuffix,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateCustomer(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateCustomer(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("update_customer_sca_data")) {
        val outBound = json.parse(message).extract[OutBoundUpdateCustomerScaData]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.updateCustomerScaData(outBound.customerId,outBound.mobileNumber,outBound.email,outBound.customerNumber,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundUpdateCustomerScaData(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundUpdateCustomerScaData(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("update_customer_credit_data")) {
        val outBound = json.parse(message).extract[OutBoundUpdateCustomerCreditData]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.updateCustomerCreditData(outBound.customerId,outBound.creditRating,outBound.creditSource,outBound.creditLimit,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundUpdateCustomerCreditData(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundUpdateCustomerCreditData(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("update_customer_general_data")) {
        val outBound = json.parse(message).extract[OutBoundUpdateCustomerGeneralData]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.updateCustomerGeneralData(outBound.customerId,outBound.legalName,outBound.faceImage,outBound.dateOfBirth,outBound.relationshipStatus,outBound.dependents,outBound.highestEducationAttained,outBound.employmentStatus,outBound.title,outBound.branchId,outBound.nameSuffix,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundUpdateCustomerGeneralData(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundUpdateCustomerGeneralData(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_customers_by_user_id")) {
        val outBound = json.parse(message).extract[OutBoundGetCustomersByUserId]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCustomersByUserId(outBound.userId,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetCustomersByUserId(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCustomersByUserId(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_customer_by_customer_id")) {
        val outBound = json.parse(message).extract[OutBoundGetCustomerByCustomerId]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCustomerByCustomerId(outBound.customerId,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetCustomerByCustomerId(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCustomerByCustomerId(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_customer_by_customer_number")) {
        val outBound = json.parse(message).extract[OutBoundGetCustomerByCustomerNumber]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCustomerByCustomerNumber(outBound.customerNumber,outBound.bankId,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetCustomerByCustomerNumber(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCustomerByCustomerNumber(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_customer_address")) {
        val outBound = json.parse(message).extract[OutBoundGetCustomerAddress]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCustomerAddress(outBound.customerId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCustomerAddress(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCustomerAddress(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_customer_address")) {
        val outBound = json.parse(message).extract[OutBoundCreateCustomerAddress]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createCustomerAddress(outBound.customerId,outBound.line1,outBound.line2,outBound.line3,outBound.city,outBound.county,outBound.state,outBound.postcode,outBound.countryCode,outBound.tags,outBound.status,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateCustomerAddress(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateCustomerAddress(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("update_customer_address")) {
        val outBound = json.parse(message).extract[OutBoundUpdateCustomerAddress]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.updateCustomerAddress(outBound.customerAddressId,outBound.line1,outBound.line2,outBound.line3,outBound.city,outBound.county,outBound.state,outBound.postcode,outBound.countryCode,outBound.tags,outBound.status,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundUpdateCustomerAddress(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundUpdateCustomerAddress(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("delete_customer_address")) {
        val outBound = json.parse(message).extract[OutBoundDeleteCustomerAddress]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.deleteCustomerAddress(outBound.customerAddressId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundDeleteCustomerAddress(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundDeleteCustomerAddress(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("create_tax_residence")) {
        val outBound = json.parse(message).extract[OutBoundCreateTaxResidence]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createTaxResidence(outBound.customerId,outBound.domain,outBound.taxNumber,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateTaxResidence(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateTaxResidence(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_tax_residence")) {
        val outBound = json.parse(message).extract[OutBoundGetTaxResidence]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getTaxResidence(outBound.customerId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetTaxResidence(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTaxResidence(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("delete_tax_residence")) {
        val outBound = json.parse(message).extract[OutBoundDeleteTaxResidence]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.deleteTaxResidence(outBound.taxResourceId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundDeleteTaxResidence(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundDeleteTaxResidence(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("get_customers")) {
        val outBound = json.parse(message).extract[OutBoundGetCustomers]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCustomers(outBound.bankId,None).map(_.head)
        
        obpMappedResponse.map(response => InBoundGetCustomers(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCustomers(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_customers_by_customer_phone_number")) {
        val outBound = json.parse(message).extract[OutBoundGetCustomersByCustomerPhoneNumber]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCustomersByCustomerPhoneNumber(outBound.bankId,outBound.phoneNumber,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCustomersByCustomerPhoneNumber(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCustomersByCustomerPhoneNumber(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_checkbook_orders")) {
        val outBound = json.parse(message).extract[OutBoundGetCheckbookOrders]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCheckbookOrders(outBound.bankId,outBound.accountId,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetCheckbookOrders(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCheckbookOrders(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_status_of_credit_card_order")) {
        val outBound = json.parse(message).extract[OutBoundGetStatusOfCreditCardOrder]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getStatusOfCreditCardOrder(outBound.bankId,outBound.accountId,None).map(_.map(_._1).head)
        
        obpMappedResponse.map(response => InBoundGetStatusOfCreditCardOrder(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetStatusOfCreditCardOrder(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_user_auth_context")) {
        val outBound = json.parse(message).extract[OutBoundCreateUserAuthContext]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createUserAuthContext(outBound.userId,outBound.key,outBound.value,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateUserAuthContext(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateUserAuthContext(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_user_auth_context_update")) {
        val outBound = json.parse(message).extract[OutBoundCreateUserAuthContextUpdate]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createUserAuthContextUpdate(outBound.userId,outBound.key,outBound.value,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateUserAuthContextUpdate(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateUserAuthContextUpdate(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("delete_user_auth_contexts")) {
        val outBound = json.parse(message).extract[OutBoundDeleteUserAuthContexts]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.deleteUserAuthContexts(outBound.userId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundDeleteUserAuthContexts(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundDeleteUserAuthContexts(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("delete_user_auth_context_by_id")) {
        val outBound = json.parse(message).extract[OutBoundDeleteUserAuthContextById]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.deleteUserAuthContextById(outBound.userAuthContextId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundDeleteUserAuthContextById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundDeleteUserAuthContextById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("get_user_auth_contexts")) {
        val outBound = json.parse(message).extract[OutBoundGetUserAuthContexts]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getUserAuthContexts(outBound.userId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetUserAuthContexts(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetUserAuthContexts(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_or_update_product_attribute")) {
        val outBound = json.parse(message).extract[OutBoundCreateOrUpdateProductAttribute]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createOrUpdateProductAttribute(outBound.bankId,outBound.productCode,outBound.productAttributeId,outBound.name,outBound.productAttributeType,outBound.value,outBound.isActive,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateOrUpdateProductAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateOrUpdateProductAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_attributes_by_bank")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAttributesByBank]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAttributesByBank(outBound.bankId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetBankAttributesByBank(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAttributesByBank(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_product_attribute_by_id")) {
        val outBound = json.parse(message).extract[OutBoundGetProductAttributeById]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getProductAttributeById(outBound.productAttributeId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetProductAttributeById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetProductAttributeById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_product_attributes_by_bank_and_code")) {
        val outBound = json.parse(message).extract[OutBoundGetProductAttributesByBankAndCode]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getProductAttributesByBankAndCode(outBound.bank,outBound.productCode,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetProductAttributesByBankAndCode(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetProductAttributesByBankAndCode(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("delete_product_attribute")) {
        val outBound = json.parse(message).extract[OutBoundDeleteProductAttribute]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.deleteProductAttribute(outBound.productAttributeId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundDeleteProductAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundDeleteProductAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("get_account_attribute_by_id")) {
        val outBound = json.parse(message).extract[OutBoundGetAccountAttributeById]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getAccountAttributeById(outBound.accountAttributeId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetAccountAttributeById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetAccountAttributeById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_transaction_attribute_by_id")) {
        val outBound = json.parse(message).extract[OutBoundGetTransactionAttributeById]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getTransactionAttributeById(outBound.transactionAttributeId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetTransactionAttributeById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTransactionAttributeById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_or_update_account_attribute")) {
        val outBound = json.parse(message).extract[OutBoundCreateOrUpdateAccountAttribute]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createOrUpdateAccountAttribute(outBound.bankId,outBound.accountId,outBound.productCode,outBound.productAttributeId,outBound.name,outBound.accountAttributeType,outBound.value,outBound.productInstanceCode,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateOrUpdateAccountAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateOrUpdateAccountAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_or_update_customer_attribute")) {
        val outBound = json.parse(message).extract[OutBoundCreateOrUpdateCustomerAttribute]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createOrUpdateCustomerAttribute(outBound.bankId,outBound.customerId,outBound.customerAttributeId,outBound.name,outBound.attributeType,outBound.value,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateOrUpdateCustomerAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateOrUpdateCustomerAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_or_update_transaction_attribute")) {
        val outBound = json.parse(message).extract[OutBoundCreateOrUpdateTransactionAttribute]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createOrUpdateTransactionAttribute(outBound.bankId,outBound.transactionId,outBound.transactionAttributeId,outBound.name,outBound.attributeType,outBound.value,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateOrUpdateTransactionAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateOrUpdateTransactionAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_account_attributes")) {
        val outBound = json.parse(message).extract[OutBoundCreateAccountAttributes]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createAccountAttributes(outBound.bankId,outBound.accountId,outBound.productCode,outBound.accountAttributes,outBound.productInstanceCode,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateAccountAttributes(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateAccountAttributes(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_account_attributes_by_account")) {
        val outBound = json.parse(message).extract[OutBoundGetAccountAttributesByAccount]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getAccountAttributesByAccount(outBound.bankId,outBound.accountId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetAccountAttributesByAccount(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetAccountAttributesByAccount(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_customer_attributes")) {
        val outBound = json.parse(message).extract[OutBoundGetCustomerAttributes]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCustomerAttributes(outBound.bankId,outBound.customerId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCustomerAttributes(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCustomerAttributes(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_customer_ids_by_attribute_name_values")) {
        val outBound = json.parse(message).extract[OutBoundGetCustomerIdsByAttributeNameValues]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCustomerIdsByAttributeNameValues(outBound.bankId,outBound.nameValues,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCustomerIdsByAttributeNameValues(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCustomerIdsByAttributeNameValues(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_customer_attributes_for_customers")) {
        val outBound = json.parse(message).extract[OutBoundGetCustomerAttributesForCustomers]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCustomerAttributesForCustomers(outBound.customers,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCustomerAttributesForCustomers(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCustomerAttributesForCustomers(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_transaction_ids_by_attribute_name_values")) {
        val outBound = json.parse(message).extract[OutBoundGetTransactionIdsByAttributeNameValues]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getTransactionIdsByAttributeNameValues(outBound.bankId,outBound.nameValues,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetTransactionIdsByAttributeNameValues(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTransactionIdsByAttributeNameValues(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_transaction_attributes")) {
        val outBound = json.parse(message).extract[OutBoundGetTransactionAttributes]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getTransactionAttributes(outBound.bankId,outBound.transactionId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetTransactionAttributes(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetTransactionAttributes(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_customer_attribute_by_id")) {
        val outBound = json.parse(message).extract[OutBoundGetCustomerAttributeById]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCustomerAttributeById(outBound.customerAttributeId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCustomerAttributeById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCustomerAttributeById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_or_update_card_attribute")) {
        val outBound = json.parse(message).extract[OutBoundCreateOrUpdateCardAttribute]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createOrUpdateCardAttribute(outBound.bankId,outBound.cardId,outBound.cardAttributeId,outBound.name,outBound.cardAttributeType,outBound.value,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateOrUpdateCardAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateOrUpdateCardAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_card_attribute_by_id")) {
        val outBound = json.parse(message).extract[OutBoundGetCardAttributeById]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCardAttributeById(outBound.cardAttributeId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCardAttributeById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCardAttributeById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_card_attributes_from_provider")) {
        val outBound = json.parse(message).extract[OutBoundGetCardAttributesFromProvider]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getCardAttributesFromProvider(outBound.cardId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetCardAttributesFromProvider(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetCardAttributesFromProvider(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_account_application")) {
        val outBound = json.parse(message).extract[OutBoundCreateAccountApplication]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createAccountApplication(outBound.productCode,outBound.userId,outBound.customerId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateAccountApplication(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateAccountApplication(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_all_account_application")) {
        val outBound = json.parse(message).extract[OutBoundGetAllAccountApplication]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getAllAccountApplication(None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetAllAccountApplication(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetAllAccountApplication(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_account_application_by_id")) {
        val outBound = json.parse(message).extract[OutBoundGetAccountApplicationById]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getAccountApplicationById(outBound.accountApplicationId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetAccountApplicationById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetAccountApplicationById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("update_account_application_status")) {
        val outBound = json.parse(message).extract[OutBoundUpdateAccountApplicationStatus]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.updateAccountApplicationStatus(outBound.accountApplicationId,outBound.status,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundUpdateAccountApplicationStatus(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundUpdateAccountApplicationStatus(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_or_create_product_collection")) {
        val outBound = json.parse(message).extract[OutBoundGetOrCreateProductCollection]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getOrCreateProductCollection(outBound.collectionCode,outBound.productCodes,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetOrCreateProductCollection(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetOrCreateProductCollection(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_product_collection")) {
        val outBound = json.parse(message).extract[OutBoundGetProductCollection]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getProductCollection(outBound.collectionCode,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetProductCollection(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetProductCollection(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_or_create_product_collection_item")) {
        val outBound = json.parse(message).extract[OutBoundGetOrCreateProductCollectionItem]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getOrCreateProductCollectionItem(outBound.collectionCode,outBound.memberProductCodes,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetOrCreateProductCollectionItem(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetOrCreateProductCollectionItem(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_product_collection_item")) {
        val outBound = json.parse(message).extract[OutBoundGetProductCollectionItem]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getProductCollectionItem(outBound.collectionCode,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetProductCollectionItem(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetProductCollectionItem(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_product_collection_items_tree")) {
        val outBound = json.parse(message).extract[OutBoundGetProductCollectionItemsTree]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getProductCollectionItemsTree(outBound.collectionCode,outBound.bankId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetProductCollectionItemsTree(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetProductCollectionItemsTree(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_meeting")) {
        val outBound = json.parse(message).extract[OutBoundCreateMeeting]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createMeeting(outBound.bankId,outBound.staffUser,outBound.customerUser,outBound.providerId,outBound.purposeId,outBound.when,outBound.sessionId,outBound.customerToken,outBound.staffToken,outBound.creator,outBound.invitees,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateMeeting(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateMeeting(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_meetings")) {
        val outBound = json.parse(message).extract[OutBoundGetMeetings]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getMeetings(outBound.bankId,outBound.user,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetMeetings(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetMeetings(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_meeting")) {
        val outBound = json.parse(message).extract[OutBoundGetMeeting]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getMeeting(outBound.bankId,outBound.user,outBound.meetingId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetMeeting(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetMeeting(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_or_update_kyc_check")) {
        val outBound = json.parse(message).extract[OutBoundCreateOrUpdateKycCheck]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createOrUpdateKycCheck(outBound.bankId,outBound.customerId,outBound.id,outBound.customerNumber,outBound.date,outBound.how,outBound.staffUserId,outBound.mStaffName,outBound.mSatisfied,outBound.comments,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateOrUpdateKycCheck(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateOrUpdateKycCheck(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_or_update_kyc_document")) {
        val outBound = json.parse(message).extract[OutBoundCreateOrUpdateKycDocument]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createOrUpdateKycDocument(outBound.bankId,outBound.customerId,outBound.id,outBound.customerNumber,outBound.`type`,outBound.number,outBound.issueDate,outBound.issuePlace,outBound.expiryDate,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateOrUpdateKycDocument(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateOrUpdateKycDocument(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_or_update_kyc_media")) {
        val outBound = json.parse(message).extract[OutBoundCreateOrUpdateKycMedia]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createOrUpdateKycMedia(outBound.bankId,outBound.customerId,outBound.id,outBound.customerNumber,outBound.`type`,outBound.url,outBound.date,outBound.relatesToKycDocumentId,outBound.relatesToKycCheckId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateOrUpdateKycMedia(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateOrUpdateKycMedia(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_or_update_kyc_status")) {
        val outBound = json.parse(message).extract[OutBoundCreateOrUpdateKycStatus]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createOrUpdateKycStatus(outBound.bankId,outBound.customerId,outBound.customerNumber,outBound.ok,outBound.date,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateOrUpdateKycStatus(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateOrUpdateKycStatus(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_kyc_checks")) {
        val outBound = json.parse(message).extract[OutBoundGetKycChecks]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getKycChecks(outBound.customerId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetKycChecks(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetKycChecks(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_kyc_documents")) {
        val outBound = json.parse(message).extract[OutBoundGetKycDocuments]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getKycDocuments(outBound.customerId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetKycDocuments(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetKycDocuments(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_kyc_medias")) {
        val outBound = json.parse(message).extract[OutBoundGetKycMedias]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getKycMedias(outBound.customerId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetKycMedias(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetKycMedias(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_kyc_statuses")) {
        val outBound = json.parse(message).extract[OutBoundGetKycStatuses]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getKycStatuses(outBound.customerId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetKycStatuses(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetKycStatuses(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_message")) {
        val outBound = json.parse(message).extract[OutBoundCreateMessage]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createMessage(outBound.user,outBound.bankId,outBound.message,outBound.fromDepartment,outBound.fromPerson,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateMessage(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateMessage(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("make_historical_payment")) {
        val outBound = json.parse(message).extract[OutBoundMakeHistoricalPayment]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.makeHistoricalPayment(outBound.fromAccount,outBound.toAccount,outBound.posted,outBound.completed,outBound.amount,outBound.currency,outBound.description,outBound.transactionRequestType,outBound.chargePolicy,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundMakeHistoricalPayment(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundMakeHistoricalPayment(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_direct_debit")) {
        val outBound = json.parse(message).extract[OutBoundCreateDirectDebit]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createDirectDebit(outBound.bankId,outBound.accountId,outBound.customerId,outBound.userId,outBound.counterpartyId,outBound.dateSigned,outBound.dateStarts,outBound.dateExpires,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateDirectDebit(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateDirectDebit(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("delete_customer_attribute")) {
        val outBound = json.parse(message).extract[OutBoundDeleteCustomerAttribute]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.deleteCustomerAttribute(outBound.customerAttributeId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundDeleteCustomerAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundDeleteCustomerAttribute(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("get_regulated_entities")) {
        val outBound = json.parse(message).extract[OutBoundGetRegulatedEntities]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getRegulatedEntities(None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetRegulatedEntities(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetRegulatedEntities(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_regulated_entity_by_entity_id")) {
        val outBound = json.parse(message).extract[OutBoundGetRegulatedEntityByEntityId]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getRegulatedEntityByEntityId(outBound.regulatedEntityId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetRegulatedEntityByEntityId(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetRegulatedEntityByEntityId(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_account_balances_by_account_id")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAccountBalancesByAccountId]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAccountBalancesByAccountId(outBound.accountId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetBankAccountBalancesByAccountId(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAccountBalancesByAccountId(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_accounts_balances_by_account_ids")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAccountsBalancesByAccountIds]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAccountsBalancesByAccountIds(outBound.accountIds,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetBankAccountsBalancesByAccountIds(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAccountsBalancesByAccountIds(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("get_bank_account_balance_by_id")) {
        val outBound = json.parse(message).extract[OutBoundGetBankAccountBalanceById]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.getBankAccountBalanceById(outBound.balanceId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundGetBankAccountBalanceById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundGetBankAccountBalanceById(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("create_or_update_bank_account_balance")) {
        val outBound = json.parse(message).extract[OutBoundCreateOrUpdateBankAccountBalance]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.createOrUpdateBankAccountBalance(outBound.bankId,outBound.accountId,outBound.balanceId,outBound.balanceType,outBound.balanceAmount,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundCreateOrUpdateBankAccountBalance(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundCreateOrUpdateBankAccountBalance(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
      } else if (obpMessageId.contains("delete_bank_account_balance")) {
        val outBound = json.parse(message).extract[OutBoundDeleteBankAccountBalance]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.deleteBankAccountBalance(outBound.balanceId,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundDeleteBankAccountBalance(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundDeleteBankAccountBalance(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = false
          ))
        }
      } else if (obpMessageId.contains("dynamic_entity_process")) {
        val outBound = json.parse(message).extract[OutBoundDynamicEntityProcess]
        val obpMappedResponse = code.bankconnectors.LocalMappedConnector.dynamicEntityProcess(outBound.operation,outBound.entityName,outBound.requestBody,outBound.entityId,None,None,None,false,None).map(_._1.head)
        
        obpMappedResponse.map(response => InBoundDynamicEntityProcess(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
          status = Status("", Nil),
          data = response
        )).recoverWith {
          case e: Exception => Future(InBoundDynamicEntityProcess(          
          
          inboundAdapterCallContext = InboundAdapterCallContext(
            correlationId = outBound.outboundAdapterCallContext.correlationId
          ),
            status = Status(e.getMessage, Nil),
            data = null
          ))
        }
// ---------- created on 2025-05-27T08:15:58Z
//---------------- dynamic end ---------------------please don't modify this line                        
      } else {  
        Future {
          1
        }
      }
      
      response = responseToOBP.map(a => write(a)).map("" + _)
      response.map(res =>  logger.debug(s"Response: inBound message to OBP: process($obpMessageId) : message is $res "))
      response
    } catch {
      case e: Throwable =>  logger.error("Unknown exception: " + e.toString)

    } finally {
      response.map(res => ch.basicPublish("", delivery.getProperties.getReplyTo, replyProps, res.getBytes("UTF-8")))
      ch.basicAck(delivery.getEnvelope.getDeliveryTag, false)
    }
  }

}

/**
 * This is only for testing, not ready for production.
 * use mapped connector as the bank CBS. 
 * Work in progress
 */
object MockedRabbitMqAdapter extends App with MdcLoggable{
  private val RPC_QUEUE_NAME = "obp_rpc_queue"
  
  DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier, APIUtil.vendor)
  Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.models: _*)
  
  
  var connection: Connection = null
  var channel: Channel = null
  try {
    val factory = new ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername(username)
    factory.setPassword(password)
    factory.setVirtualHost(virtualHost)
    if (APIUtil.getPropsAsBoolValue("rabbitmq.use.ssl", false)){
      factory.useSslProtocol(RabbitMQUtils.createSSLContext(
        keystorePath,
        keystorePassword,
        truststorePath,
        truststorePassword
      ))
    }

    connection = factory.newConnection()
    channel = connection.createChannel()

    channel.basicQos(1)
    // stop after one consumed message since this is example code
    val serverCallback = new ServerCallback(channel)
    channel basicConsume(RPC_QUEUE_NAME, false, serverCallback, _ => {})
    logger.info("Start awaiting OBP Connector Requests:")
  } catch {
    case e: Exception => e.printStackTrace()
  } finally {
    if (connection != null) {
      try {
        //          connection.close() //this is a temporary solution, we keep this connection open to wait for messages
      } catch {
        case e: Exception =>  logger.error(s"unknown Exception:$e")
      }
    }
  }

}

/**
 * This adapter is only for testing, not ready for the production
 */
object startRabbitMqAdapter {
  def main(args: Array[String]): Unit = {
    val thread = new Thread(new Runnable {
      override def run(): Unit = {
        MockedRabbitMqAdapter.main(Array.empty)  
      }
    })
    thread.start()
    thread.join()
  }
}
