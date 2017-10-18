package code.bankconnectors.vJuneYellow2017

/*
Open Bank Project - API
Copyright (C) 2011-2017, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see http://www.gnu.org/licenses/.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany
*/

import java.text.SimpleDateFormat
import java.util.{Locale, UUID}

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.MessageDoc
import code.api.util.ErrorMessages
import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.api.v3_0_0.custom.{TransactionRequestBodyTransferToAccount, TransactionRequestBodyTransferToAtmJson, TransactionRequestBodyTransferToPhoneJson}
import code.bankconnectors.vJune2017._
import code.bankconnectors.vMar2017.InboundStatusMessage
import code.kafka.KafkaHelper
import code.metadata.counterparties.{CounterpartyTrait, MappedCounterparty}
import code.model._
import code.model.dataAccess.MappedBankAccount
import code.transactionChallenge.ExpectedChallengeAnswer
import code.transactionrequests.TransactionRequests
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{TRANSFER_TO_ACCOUNT, TRANSFER_TO_ATM, TRANSFER_TO_PHONE}
import code.transactionrequests.TransactionRequests.{TransactionRequest, TransactionRequestChallenge, TransactionRequestCharge, TransactionRequestTypes}
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers.{now, tryo}
import net.liftweb.util.{BCrypt, Props}

import scala.language.postfixOps

object KafkaMappedConnector_vJuneYellow2017 extends KafkaMappedConnector_vJune2017 with KafkaHelper with MdcLoggable {
  
  messageDocs += MessageDoc(
    process = "obp.makePaymentImpl",
    messageFormat = messageFormat,
    description = "saveTransaction from kafka",
    exampleOutboundMessage = decompose(
      OutboundCreateTransaction(
        AuthInfo("userId","usename","cbsToken"),
        // fromAccount
        fromAccountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        fromAccountBankId = "gh.29.uk",
        
        // transaction details
        transactionRequestType = "SANDBOX_TAN",
        transactionChargePolicy = "RECEIVER",
        transactionRequestCommonBody=SwaggerDefinitionsJSON.transactionRequestBodyCounterpartyJSON,
        // toAccount or toCounterparty
        toCounterpartyId = "1234",
        toCounterpartyName = "obp",
        toCounterpartyCurrency = "EUR",
        toCounterpartyRoutingAddress = "1234",
        toCounterpartyRoutingScheme = "OBP",
        toCounterpartyBankRoutingAddress = "12345",
        toCounterpartyBankRoutingScheme = "OBP"
      )
    ),
    exampleInboundMessage = decompose(
      InboundCreateTransactionId(
        AuthInfo("userId","usename","cbsToken"),
        InternalTransactionId(
          errorCode = "OBP-6001: ...",
          List(InboundStatusMessage("ESB", "Success", "0", "OK")), 
          "")
      )
    )
  )
  
  override def makePaymentv300(
    initiator: User,
    fromAccount: BankAccount,
    toAccount: BankAccount,
    toCounterparty: CounterpartyTrait,
    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
    transactionRequestType: TransactionRequestType,
    chargePolicy: String
  ): Box[TransactionId] = {
    
    val postedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).format(now)
    
    val req = OutboundCreateTransaction(
      authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, cbsToken),
      
      // fromAccount
      fromAccountId = fromAccount.accountId.value,
      fromAccountBankId = fromAccount.bankId.value,
      
      // transaction details
      transactionRequestType = transactionRequestType.value,
      transactionChargePolicy = chargePolicy,
      transactionRequestCommonBody = transactionRequestCommonBody,
      
      // toAccount or toCounterparty
      toCounterpartyId = toAccount.accountId.value,
      toCounterpartyName = toAccount.name,
      toCounterpartyCurrency = toAccount.currency,
      toCounterpartyRoutingAddress = toAccount.accountId.value,
      toCounterpartyRoutingScheme = "OBP",
      toCounterpartyBankRoutingAddress = toAccount.bankId.value,
      toCounterpartyBankRoutingScheme = "OBP"
    )
    
    // Since result is single account, we need only first list entry
    val box= processToBox[OutboundCreateTransaction](req).map(_.extract[InboundCreateTransactionId].data)
    
    val res = box match {
      case Full(r) if (r.errorCode=="") =>
        Full(TransactionId(r.id))
      case Full(r) if (r.errorCode!="") =>
        Failure("INTERNAL-OBP-ADAPTER-xxx:"+ r.errorCode+". + CoreBank-Error:"+ r.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    
    res
  }
  
  override def createTransactionAfterChallengev300(
    initiator: User,
    fromAccount: BankAccount,
    transReqId: TransactionRequestId,
    transactionRequestType: TransactionRequestType
  ): Box[TransactionRequest] = {
    for {
      tr <- getTransactionRequestImpl(transReqId) ?~ s"${ErrorMessages.InvalidTransactionRequestId} : $transReqId"
      
      details: JValue = tr.details
      
      //Note, it should be four different type of details in mappedtransactionrequest.
      //But when we design "createTransactionRequest", we try to make it the same as SandBoxTan. There is still some different now.
      // Take a look at TransactionRequestDetailsMapperJSON, TransactionRequestDetailsMapperCounterpartyJSON, TransactionRequestDetailsMapperSEPAJSON and TransactionRequestDetailsMapperFreeFormJSON
      transactionRequestCommonBody <-TransactionRequestTypes.withName(transactionRequestType.value) match {
        case TRANSFER_TO_PHONE =>
          Full(details.extract[TransactionRequestBodyTransferToPhoneJson])
        case TRANSFER_TO_ATM =>
          Full(details.extract[TransactionRequestBodyTransferToAtmJson])
        case TRANSFER_TO_ACCOUNT =>
          Full(details.extract[TransactionRequestBodyTransferToAccount])
        case _ =>
          Full(details.extract[TransactionRequestBodyTransferToPhoneJson])
      }
      
      transId <- makePaymentv300(
        initiator,
        fromAccount,
        new MappedBankAccount(),
        new MappedCounterparty(),
        transactionRequestCommonBody,
        transactionRequestType,
        tr.charge_policy
      ) ?~ "Couldn't create Transaction"
      
      didSaveTransId <- saveTransactionRequestTransaction(transReqId, transId)
      
      didSaveStatus <- saveTransactionRequestStatusImpl(transReqId, TransactionRequests.STATUS_COMPLETED)
      
    } yield {
      var tr = getTransactionRequestImpl(transReqId).openOrThrowException("Exception: Couldn't create transaction")
      //update the return value, getTransactionRequestImpl is not in real-time. need update the data manually.
      tr=tr.copy(transaction_ids =transId.value)
      tr=tr.copy(status =TransactionRequests.STATUS_COMPLETED)
      tr
    }
  }
  
  override def createTransactionRequestv300(
    initiator: User,
    viewId: ViewId,
    fromAccount: BankAccount,
    toAccount: BankAccount,
    toCounterparty: CounterpartyTrait,
    transactionRequestType: TransactionRequestType,
    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
    detailsPlain: String,
    chargePolicy: String
  ): Box[TransactionRequest] = {
    // Set initial status
    def getStatus(challengeThresholdAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal): Box[String] =
      Full(
        if (transactionRequestCommonBodyAmount < challengeThresholdAmount) {
          // For any connector != mapped we should probably assume that transaction_status_scheduler_delay will be > 0
          // so that getTransactionRequestStatusesImpl needs to be implemented for all connectors except mapped.
          // i.e. if we are certain that saveTransaction will be honored immediately by the backend, then transaction_status_scheduler_delay
          // can be empty in the props file. Otherwise, the status will be set to STATUS_PENDING
          // and getTransactionRequestStatusesImpl needs to be run periodically to update the transaction request status.
          if (Props.getLong("transaction_status_scheduler_delay").isEmpty )
            TransactionRequests.STATUS_COMPLETED
          else
            TransactionRequests.STATUS_PENDING
        } else {
          TransactionRequests.STATUS_INITIATED
        }
      )
    
    // Get the charge level value
    def getChargeValue(chargeLevelAmount: BigDecimal, transactionRequestCommonBodyAmount: BigDecimal): Box[String] =
      Full(
        transactionRequestCommonBodyAmount* chargeLevelAmount match {
          //Set the mininal cost (2 euros)for transaction request
          case value if (value < 2) => "2.0"
          //Set the largest cost (50 euros)for transaction request
          case value if (value > 50) => "50"
          //Set the cost according to the charge level
          case value => value.setScale(10, BigDecimal.RoundingMode.HALF_UP).toString()
        }
      )
    
    for {
    // Get the threshold for a challenge. i.e. over what value do we require an out of bounds security challenge to be sent?
      challengeThreshold <- getChallengeThreshold(
        fromAccount.bankId.value,
        fromAccount.accountId.value,
        viewId.value,
        transactionRequestType.value,
        transactionRequestCommonBody.value.currency,
        initiator.userId,
        initiator.name
      )
      
      challengeThresholdAmount <- tryo(BigDecimal(challengeThreshold.amount)) ?~! s"challengeThreshold amount ${challengeThreshold.amount} not convertible to number"
      transactionRequestCommonBodyAmount <- tryo(BigDecimal(transactionRequestCommonBody.value.amount)) ?~! s"transactionRequestCommonBody amount ${transactionRequestCommonBody.value.amount} not convertible to number"
      
      status <- getStatus(challengeThresholdAmount,transactionRequestCommonBodyAmount) ?~! s"createTransactionRequestv300.getStatus exception !"
      
      chargeLevel <- getChargeLevel(
        BankId(fromAccount.bankId.value),
        AccountId(fromAccount.accountId.value),
        viewId,
        initiator.userId,
        initiator.name,
        transactionRequestType.value,
        fromAccount.currency
      ) ?~! s"createTransactionRequestv300.getChargeLevel exception !"
      
      chargeLevelAmount <- tryo(BigDecimal(chargeLevel.amount)) ?~! s"chargeLevel.amount: ${chargeLevel.amount} can not be transferred to decimal !"
      
      chargeValue <- getChargeValue(chargeLevelAmount,transactionRequestCommonBodyAmount)
      
      charge <- Full(TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(transactionRequestCommonBody.value.currency, chargeValue)))
      
      // Always create a new Transaction Request
      transactionRequest <- createTransactionRequestImpl210(
        TransactionRequestId(java.util.UUID.randomUUID().toString),
        transactionRequestType,
        fromAccount,
        toAccount,
        toCounterparty,
        transactionRequestCommonBody,
        detailsPlain,
        status,
        charge,
        chargePolicy
      ) ?~! "createTransactionRequestv300.createTransactionRequestImpl210, Exception: Couldn't create transactionRequest"
      
      // If no challenge necessary, create Transaction immediately and put in data store and object to return
      newTransactionRequest: TransactionRequest <-
      if(status == TransactionRequests.STATUS_COMPLETED) {
        for {
          createdTransactionId <- makePaymentv300(
            initiator,
            fromAccount,
            toAccount,
            toCounterparty,
            transactionRequestCommonBody,
            transactionRequestType,
            chargePolicy
          ) ?~! "createTransactionRequestv300.makePaymentv300 exception"
          //set challenge to null, otherwise it have the default value "challenge": {"id": "","allowed_attempts": 0,"challenge_type": ""}
          transactionRequest <- Full(transactionRequest.copy(challenge = null))
          //save transaction_id into database
          _ <- Full(saveTransactionRequestTransaction(transactionRequest.id, createdTransactionId))
          //update transaction_id filed for varibale 'transactionRequest' 
          transactionRequest <- Full(transactionRequest.copy(transaction_ids = createdTransactionId.value))
        } yield {
          logger.debug(s"createTransactionRequestv300.createdTransactionId return: $transactionRequest")
          transactionRequest
        }
      }else if (status == TransactionRequests.STATUS_INITIATED ) {
        for {
        //if challenge necessary, create a new one
          challengeAnswer <- createChallenge(fromAccount.bankId,
            fromAccount.accountId, initiator.userId,
            transactionRequestType: TransactionRequestType,
            transactionRequest.id.value
          ) ?~! "createTransactionRequestv300.createChallenge exception !"
          
          challengeId = UUID.randomUUID().toString
          salt = BCrypt.gensalt()
          challengeAnswerHashed = BCrypt.hashpw(challengeAnswer, salt).substring(0, 44)
          
          //Save the challengeAnswer in OBP side, will check it in `Answer Transaction Request` endpoint.
          _<- ExpectedChallengeAnswer.expectedChallengeAnswerProvider.vend.saveExpectedChallengeAnswer(challengeId, salt, challengeAnswerHashed)
          
          // TODO: challenge_type should not be hard coded here. Rather it should be sent as a parameter to this function createTransactionRequestv300
          newChallenge = TransactionRequestChallenge(challengeId, allowed_attempts = 3, challenge_type = TransactionRequests.CHALLENGE_SANDBOX_TAN)
          _<- Full(saveTransactionRequestChallenge(transactionRequest.id, newChallenge))
          transactionRequest<- Full(transactionRequest.copy(challenge = newChallenge))
        } yield {
          transactionRequest
        }
      }else{
        Full(transactionRequest)
      }
    } yield {
      logger.debug(newTransactionRequest)
      newTransactionRequest
    }
  }

}
