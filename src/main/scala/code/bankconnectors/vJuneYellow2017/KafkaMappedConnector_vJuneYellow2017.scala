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
import java.util.Locale

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.MessageDoc
import code.api.util.ErrorMessages
import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.bankconnectors.vJune2017._
import code.bankconnectors.vMar2017.InboundStatusMessage
import code.kafka.KafkaHelper
import code.metadata.counterparties.CounterpartyTrait
import code.model._
import code.util.Helper.MdcLoggable
import net.liftweb.common._
import net.liftweb.json.Extraction._
import net.liftweb.util.Helpers.now

import scala.language.postfixOps

object KafkaMappedConnector_vJuneYellow2017 extends KafkaMappedConnector_vJune2017 with KafkaHelper with MdcLoggable {
  
  messageDocs += MessageDoc(
    process = "obp.makePaymentImpl",
    messageFormat = messageFormat,
    description = "saveTransaction from kafka",
    exampleOutboundMessage = decompose(
      CreateTransaction(
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
    
    val req = CreateTransaction(
      authInfo = AuthInfo(currentResourceUserId, currentResourceUsername, ""),
      
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
    val box= processToBox[CreateTransaction](req).map(_.extract[InboundCreateTransactionId].data)
    
    val res = box match {
      case Full(r) if (r.errorCode=="") =>
        Full(TransactionId(r.id))
      case Full(r) if (r.errorCode!="") =>
        Failure("OBP-Error:"+ r.errorCode+". + CoreBank-Error:"+ r.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    
    res
  }
  

}
