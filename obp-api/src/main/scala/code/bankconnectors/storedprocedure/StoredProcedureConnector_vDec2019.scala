package code.bankconnectors.storedprocedure

/*
Open Bank Project - API
Copyright (C) 2011-2017, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany
*/

import java.util.Date

import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.getIbanAndBban
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, _}
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
import code.api.util.{APIUtil, CallContext, HashUtil, OBPQueryParam}
import code.bankconnectors._
import code.bankconnectors.vJune2017.AuthInfo
import code.customer.internalMapping.MappedCustomerIdMappingProvider
import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.{InBoundTrait, _}
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums._
import com.openbankproject.commons.model.{TopicTrait, _}
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.common.{Box, _}
import net.liftweb.json._
import net.liftweb.util.StringHelpers

import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.postfixOps
import scala.reflect.runtime.universe._

trait StoredProcedureConnector_vDec2019 extends Connector with MdcLoggable {
  //this one import is for implicit convert, don't delete
  import com.openbankproject.commons.model.{AmountOfMoney, CreditLimit, CreditRating, CustomerFaceImage}

  implicit override val nameOfConnector = StoredProcedureConnector_vDec2019.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "Dec2019"

  override val messageDocs = ArrayBuffer[MessageDoc]()

  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."

  val connectorName = "stored_procedure_vDec2019"

//---------------- dynamic start -------------------please don't modify this line
// ---------- created on 2020-12-14T15:30:08Z

  messageDocs += getAdapterInfoDoc
  def getAdapterInfoDoc = MessageDoc(
    process = "obp.getAdapterInfo",
    messageFormat = messageFormat,
    description = "Get Adapter Info",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
          OutBoundGetAdapterInfo(MessageDocsSwaggerDefinitions.outboundAdapterCallContext)
    ),
    exampleInboundMessage = (
     InBoundGetAdapterInfo(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= InboundAdapterInfoInternal(errorCode=inboundAdapterInfoInternalErrorCodeExample.value,
      backendMessages=List( InboundStatusMessage(source=sourceExample.value,
      status=inboundStatusMessageStatusExample.value,
      errorCode=inboundStatusMessageErrorCodeExample.value,
      text=inboundStatusMessageTextExample.value)),
      name=inboundAdapterInfoInternalNameExample.value,
      version=inboundAdapterInfoInternalVersionExample.value,
      git_commit=inboundAdapterInfoInternalGit_commitExample.value,
      date=inboundAdapterInfoInternalDateExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getAdapterInfo(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetAdapterInfo => InBound, OutBoundGetAdapterInfo => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_adapter_info", req, callContext)
        response.map(convertToTuple[InboundAdapterInfoInternal](callContext))        
  }
          
  messageDocs += validateAndCheckIbanNumberDoc
  def validateAndCheckIbanNumberDoc = MessageDoc(
    process = "obp.validateAndCheckIbanNumber",
    messageFormat = messageFormat,
    description = "Validate And Check Iban Number",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundValidateAndCheckIbanNumber(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      iban=ibanExample.value)
    ),
    exampleInboundMessage = (
     InBoundValidateAndCheckIbanNumber(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= IbanChecker(isValid=true,
      details=Some( IbanDetails(bic=bicExample.value,
      bank=bankExample.value,
      branch="string",
      address=addressExample.value,
      city=cityExample.value,
      zip="string",
      phone=phoneExample.value,
      country="string",
      countryIso="string",
      sepaCreditTransfer=sepaCreditTransferExample.value,
      sepaDirectDebit=sepaDirectDebitExample.value,
      sepaSddCore=sepaSddCoreExample.value,
      sepaB2b=sepaB2bExample.value,
      sepaCardClearing=sepaCardClearingExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def validateAndCheckIbanNumber(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[IbanChecker]] = {
        import com.openbankproject.commons.dto.{InBoundValidateAndCheckIbanNumber => InBound, OutBoundValidateAndCheckIbanNumber => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, iban)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_validate_and_check_iban_number", req, callContext)
        response.map(convertToTuple[IbanChecker](callContext))        
  }
          
  messageDocs += getChallengeThresholdDoc
  def getChallengeThresholdDoc = MessageDoc(
    process = "obp.getChallengeThreshold",
    messageFormat = messageFormat,
    description = "Get Challenge Threshold",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetChallengeThreshold(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=bankIdExample.value,
      accountId=accountIdExample.value,
      viewId=viewIdExample.value,
      transactionRequestType=transactionRequestTypeExample.value,
      currency=currencyExample.value,
      userId=userIdExample.value,
      username=usernameExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetChallengeThreshold(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, username: String, callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = {
        import com.openbankproject.commons.dto.{InBoundGetChallengeThreshold => InBound, OutBoundGetChallengeThreshold => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, viewId, transactionRequestType, currency, userId, username)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_challenge_threshold", req, callContext)
        response.map(convertToTuple[AmountOfMoney](callContext))        
  }
          
  messageDocs += getChargeLevelDoc
  def getChargeLevelDoc = MessageDoc(
    process = "obp.getChargeLevel",
    messageFormat = messageFormat,
    description = "Get Charge Level",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetChargeLevel(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      viewId=ViewId(viewIdExample.value),
      userId=userIdExample.value,
      username=usernameExample.value,
      transactionRequestType=transactionRequestTypeExample.value,
      currency=currencyExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetChargeLevel(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getChargeLevel(bankId: BankId, accountId: AccountId, viewId: ViewId, userId: String, username: String, transactionRequestType: String, currency: String, callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = {
        import com.openbankproject.commons.dto.{InBoundGetChargeLevel => InBound, OutBoundGetChargeLevel => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, viewId, userId, username, transactionRequestType, currency)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_charge_level", req, callContext)
        response.map(convertToTuple[AmountOfMoney](callContext))        
  }
          
  messageDocs += getChargeLevelC2Doc
  def getChargeLevelC2Doc = MessageDoc(
    process = "obp.getChargeLevelC2",
    messageFormat = messageFormat,
    description = "Get Charge Level C2",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetChargeLevelC2(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      viewId=ViewId(viewIdExample.value),
      userId=userIdExample.value,
      username=usernameExample.value,
      transactionRequestType=transactionRequestTypeExample.value,
      currency=currencyExample.value,
      amount=amountExample.value,
      toAccountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      customAttributes=List( CustomAttribute(name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.AttributeType.example,
      value=valueExample.value)))
    ),
    exampleInboundMessage = (
     InBoundGetChargeLevelC2(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getChargeLevelC2(bankId: BankId, accountId: AccountId, viewId: ViewId, userId: String, username: String, transactionRequestType: String, currency: String, amount: String, toAccountRoutings: List[AccountRouting], customAttributes: List[CustomAttribute], callContext: Option[CallContext]): OBPReturnType[Box[AmountOfMoney]] = {
        import com.openbankproject.commons.dto.{InBoundGetChargeLevelC2 => InBound, OutBoundGetChargeLevelC2 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, viewId, userId, username, transactionRequestType, currency, amount, toAccountRoutings, customAttributes)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_charge_level_c2", req, callContext)
        response.map(convertToTuple[AmountOfMoney](callContext))        
  }
          
  messageDocs += createChallengeDoc
  def createChallengeDoc = MessageDoc(
    process = "obp.createChallenge",
    messageFormat = messageFormat,
    description = "Create Challenge",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateChallenge(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      userId=userIdExample.value,
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      transactionRequestId=transactionRequestIdExample.value,
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS))
    ),
    exampleInboundMessage = (
     InBoundCreateChallenge(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data="string")
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, scaMethod: Option[StrongCustomerAuthentication.SCA], callContext: Option[CallContext]): OBPReturnType[Box[String]] = {
        import com.openbankproject.commons.dto.{InBoundCreateChallenge => InBound, OutBoundCreateChallenge => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, userId, transactionRequestType, transactionRequestId, scaMethod)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_challenge", req, callContext)
        response.map(convertToTuple[String](callContext))        
  }
          
  messageDocs += createChallengesDoc
  def createChallengesDoc = MessageDoc(
    process = "obp.createChallenges",
    messageFormat = messageFormat,
    description = "Create Challenges",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateChallenges(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      userIds=listExample.value.split("[,;]").toList,
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      transactionRequestId=transactionRequestIdExample.value,
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS))
    ),
    exampleInboundMessage = (
     InBoundCreateChallenges(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=listExample.value.split("[,;]").toList)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createChallenges(bankId: BankId, accountId: AccountId, userIds: List[String], transactionRequestType: TransactionRequestType, transactionRequestId: String, scaMethod: Option[StrongCustomerAuthentication.SCA], callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] = {
        import com.openbankproject.commons.dto.{InBoundCreateChallenges => InBound, OutBoundCreateChallenges => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, userIds, transactionRequestType, transactionRequestId, scaMethod)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_challenges", req, callContext)
        response.map(convertToTuple[List[String]](callContext))        
  }
          
  messageDocs += createChallengesC2Doc
  def createChallengesC2Doc = MessageDoc(
    process = "obp.createChallengesC2",
    messageFormat = messageFormat,
    description = "Create Challenges C2",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateChallengesC2(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      userIds=listExample.value.split("[,;]").toList,
      challengeType=com.openbankproject.commons.model.enums.ChallengeType.example,
      transactionRequestId=Some(transactionRequestIdExample.value),
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS),
      scaStatus=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.example),
      consentId=Some(consentIdExample.value),
      authenticationMethodId=Some("string"))
    ),
    exampleInboundMessage = (
     InBoundCreateChallengesC2(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( ChallengeCommons(challengeId=challengeIdExample.value,
      transactionRequestId=transactionRequestIdExample.value,
      expectedAnswer="string",
      expectedUserId="string",
      salt="string",
      successful=true,
      challengeType=challengeTypeExample.value,
      consentId=Some(consentIdExample.value),
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS),
      scaStatus=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.example),
      authenticationMethodId=Some("string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createChallengesC2(userIds: List[String], challengeType: ChallengeType.Value, transactionRequestId: Option[String], scaMethod: Option[StrongCustomerAuthentication.SCA], scaStatus: Option[SCAStatus], consentId: Option[String], authenticationMethodId: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] = {
        import com.openbankproject.commons.dto.{InBoundCreateChallengesC2 => InBound, OutBoundCreateChallengesC2 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, userIds, challengeType, transactionRequestId, scaMethod, scaStatus, consentId, authenticationMethodId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_challenges_c2", req, callContext)
        response.map(convertToTuple[List[ChallengeCommons]](callContext))        
  }
          
  messageDocs += validateChallengeAnswerDoc
  def validateChallengeAnswerDoc = MessageDoc(
    process = "obp.validateChallengeAnswer",
    messageFormat = messageFormat,
    description = "Validate Challenge Answer",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundValidateChallengeAnswer(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      challengeId=challengeIdExample.value,
      hashOfSuppliedAnswer=hashOfSuppliedAnswerExample.value)
    ),
    exampleInboundMessage = (
     InBoundValidateChallengeAnswer(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{InBoundValidateChallengeAnswer => InBound, OutBoundValidateChallengeAnswer => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, challengeId, hashOfSuppliedAnswer)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_validate_challenge_answer", req, callContext)
        response.map(convertToTuple[Boolean](callContext))        
  }
          
  messageDocs += validateChallengeAnswerC2Doc
  def validateChallengeAnswerC2Doc = MessageDoc(
    process = "obp.validateChallengeAnswerC2",
    messageFormat = messageFormat,
    description = "Validate Challenge Answer C2",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundValidateChallengeAnswerC2(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      transactionRequestId=Some(transactionRequestIdExample.value),
      consentId=Some(consentIdExample.value),
      challengeId=challengeIdExample.value,
      hashOfSuppliedAnswer=hashOfSuppliedAnswerExample.value)
    ),
    exampleInboundMessage = (
     InBoundValidateChallengeAnswerC2(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= ChallengeCommons(challengeId=challengeIdExample.value,
      transactionRequestId=transactionRequestIdExample.value,
      expectedAnswer="string",
      expectedUserId="string",
      salt="string",
      successful=true,
      challengeType=challengeTypeExample.value,
      consentId=Some(consentIdExample.value),
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS),
      scaStatus=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.example),
      authenticationMethodId=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def validateChallengeAnswerC2(transactionRequestId: Option[String], consentId: Option[String], challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Box[ChallengeTrait]] = {
        import com.openbankproject.commons.dto.{InBoundValidateChallengeAnswerC2 => InBound, OutBoundValidateChallengeAnswerC2 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, transactionRequestId, consentId, challengeId, hashOfSuppliedAnswer)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_validate_challenge_answer_c2", req, callContext)
        response.map(convertToTuple[ChallengeCommons](callContext))        
  }
          
  messageDocs += getChallengesByTransactionRequestIdDoc
  def getChallengesByTransactionRequestIdDoc = MessageDoc(
    process = "obp.getChallengesByTransactionRequestId",
    messageFormat = messageFormat,
    description = "Get Challenges By Transaction Request Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetChallengesByTransactionRequestId(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      transactionRequestId=transactionRequestIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetChallengesByTransactionRequestId(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( ChallengeCommons(challengeId=challengeIdExample.value,
      transactionRequestId=transactionRequestIdExample.value,
      expectedAnswer="string",
      expectedUserId="string",
      salt="string",
      successful=true,
      challengeType=challengeTypeExample.value,
      consentId=Some(consentIdExample.value),
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS),
      scaStatus=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.example),
      authenticationMethodId=Some("string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getChallengesByTransactionRequestId(transactionRequestId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] = {
        import com.openbankproject.commons.dto.{InBoundGetChallengesByTransactionRequestId => InBound, OutBoundGetChallengesByTransactionRequestId => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, transactionRequestId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_challenges_by_transaction_request_id", req, callContext)
        response.map(convertToTuple[List[ChallengeCommons]](callContext))        
  }
          
  messageDocs += getChallengesByConsentIdDoc
  def getChallengesByConsentIdDoc = MessageDoc(
    process = "obp.getChallengesByConsentId",
    messageFormat = messageFormat,
    description = "Get Challenges By Consent Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetChallengesByConsentId(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      consentId=consentIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetChallengesByConsentId(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( ChallengeCommons(challengeId=challengeIdExample.value,
      transactionRequestId=transactionRequestIdExample.value,
      expectedAnswer="string",
      expectedUserId="string",
      salt="string",
      successful=true,
      challengeType=challengeTypeExample.value,
      consentId=Some(consentIdExample.value),
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS),
      scaStatus=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.example),
      authenticationMethodId=Some("string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getChallengesByConsentId(consentId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ChallengeTrait]]] = {
        import com.openbankproject.commons.dto.{InBoundGetChallengesByConsentId => InBound, OutBoundGetChallengesByConsentId => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, consentId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_challenges_by_consent_id", req, callContext)
        response.map(convertToTuple[List[ChallengeCommons]](callContext))        
  }
          
  messageDocs += getChallengeDoc
  def getChallengeDoc = MessageDoc(
    process = "obp.getChallenge",
    messageFormat = messageFormat,
    description = "Get Challenge",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetChallenge(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      challengeId=challengeIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetChallenge(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= ChallengeCommons(challengeId=challengeIdExample.value,
      transactionRequestId=transactionRequestIdExample.value,
      expectedAnswer="string",
      expectedUserId="string",
      salt="string",
      successful=true,
      challengeType=challengeTypeExample.value,
      consentId=Some(consentIdExample.value),
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS),
      scaStatus=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.example),
      authenticationMethodId=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getChallenge(challengeId: String, callContext: Option[CallContext]): OBPReturnType[Box[ChallengeTrait]] = {
        import com.openbankproject.commons.dto.{InBoundGetChallenge => InBound, OutBoundGetChallenge => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, challengeId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_challenge", req, callContext)
        response.map(convertToTuple[ChallengeCommons](callContext))        
  }
          
  messageDocs += getBankDoc
  def getBankDoc = MessageDoc(
    process = "obp.getBank",
    messageFormat = messageFormat,
    description = "Get Bank",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBank(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetBank(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= BankCommons(bankId=BankId(bankIdExample.value),
      shortName=bankShortNameExample.value,
      fullName=bankFullNameExample.value,
      logoUrl=bankLogoUrlExample.value,
      websiteUrl=bankWebsiteUrlExample.value,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      swiftBic=bankSwiftBicExample.value,
      nationalIdentifier=bankNationalIdentifierExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBank(bankId: BankId, callContext: Option[CallContext]): Future[Box[(Bank, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetBank => InBound, OutBoundGetBank => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_bank", req, callContext)
        response.map(convertToTuple[BankCommons](callContext))        
  }
          
  messageDocs += getBanksDoc
  def getBanksDoc = MessageDoc(
    process = "obp.getBanks",
    messageFormat = messageFormat,
    description = "Get Banks",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
          OutBoundGetBanks(MessageDocsSwaggerDefinitions.outboundAdapterCallContext)
    ),
    exampleInboundMessage = (
     InBoundGetBanks(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( BankCommons(bankId=BankId(bankIdExample.value),
      shortName=bankShortNameExample.value,
      fullName=bankFullNameExample.value,
      logoUrl=bankLogoUrlExample.value,
      websiteUrl=bankWebsiteUrlExample.value,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      swiftBic=bankSwiftBicExample.value,
      nationalIdentifier=bankNationalIdentifierExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBanks(callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetBanks => InBound, OutBoundGetBanks => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_banks", req, callContext)
        response.map(convertToTuple[List[BankCommons]](callContext))        
  }
          
  messageDocs += getBankAccountsForUserDoc
  def getBankAccountsForUserDoc = MessageDoc(
    process = "obp.getBankAccountsForUser",
    messageFormat = messageFormat,
    description = "Get Bank Accounts For User",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountsForUser(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      username=usernameExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountsForUser(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( InboundAccountCommons(bankId=bankIdExample.value,
      branchId=branchIdExample.value,
      accountId=accountIdExample.value,
      accountNumber=accountNumberExample.value,
      accountType=accountTypeExample.value,
      balanceAmount=balanceAmountExample.value,
      balanceCurrency=balanceCurrencyExample.value,
      owners=inboundAccountOwnersExample.value.split("[,;]").toList,
      viewsToGenerate=inboundAccountViewsToGenerateExample.value.split("[,;]").toList,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      branchRoutingScheme=branchRoutingSchemeExample.value,
      branchRoutingAddress=branchRoutingAddressExample.value,
      accountRoutingScheme=accountRoutingSchemeExample.value,
      accountRoutingAddress=accountRoutingAddressExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBankAccountsForUser(username: String, callContext: Option[CallContext]): Future[Box[(List[InboundAccount], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetBankAccountsForUser => InBound, OutBoundGetBankAccountsForUser => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, username)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_bank_accounts_for_user", req, callContext)
        response.map(convertToTuple[List[InboundAccountCommons]](callContext))        
  }
          
  messageDocs += getUserDoc
  def getUserDoc = MessageDoc(
    process = "obp.getUser",
    messageFormat = messageFormat,
    description = "Get User",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetUser(name=userNameExample.value,
      password=passwordExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetUser(status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= InboundUser(email=emailExample.value,
      password=passwordExample.value,
      displayName=displayNameExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getUser(name: String, password: String): Box[InboundUser] = {
        import com.openbankproject.commons.dto.{InBoundGetUser => InBound, OutBoundGetUser => OutBound}  
        val callContext: Option[CallContext] = None
        val req = OutBound(name, password)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_user", req, callContext)
        response.map(convertToTuple[InboundUser](callContext))        
  }
          
  messageDocs += checkExternalUserCredentialsDoc
  def checkExternalUserCredentialsDoc = MessageDoc(
    process = "obp.checkExternalUserCredentials",
    messageFormat = messageFormat,
    description = "Check External User Credentials",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCheckExternalUserCredentials(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      username=usernameExample.value,
      password=passwordExample.value)
    ),
    exampleInboundMessage = (
     InBoundCheckExternalUserCredentials(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= InboundExternalUser(aud=audExample.value,
      exp=expExample.value,
      iat=iatExample.value,
      iss=issExample.value,
      sub=subExample.value,
      azp=Some("string"),
      email=Some(emailExample.value),
      emailVerified=Some(emailVerifiedExample.value),
      name=Some(userNameExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def checkExternalUserCredentials(username: String, password: String, callContext: Option[CallContext]): Box[InboundExternalUser] = {
        import com.openbankproject.commons.dto.{InBoundCheckExternalUserCredentials => InBound, OutBoundCheckExternalUserCredentials => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, username, password)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_check_external_user_credentials", req, callContext)
        response.map(convertToTuple[InboundExternalUser](callContext))        
  }
          
  messageDocs += checkExternalUserExistsDoc
  def checkExternalUserExistsDoc = MessageDoc(
    process = "obp.checkExternalUserExists",
    messageFormat = messageFormat,
    description = "Check External User Exists",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCheckExternalUserExists(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      username=usernameExample.value)
    ),
    exampleInboundMessage = (
     InBoundCheckExternalUserExists(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= InboundExternalUser(aud=audExample.value,
      exp=expExample.value,
      iat=iatExample.value,
      iss=issExample.value,
      sub=subExample.value,
      azp=Some("string"),
      email=Some(emailExample.value),
      emailVerified=Some(emailVerifiedExample.value),
      name=Some(userNameExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def checkExternalUserExists(username: String, callContext: Option[CallContext]): Box[InboundExternalUser] = {
        import com.openbankproject.commons.dto.{InBoundCheckExternalUserExists => InBound, OutBoundCheckExternalUserExists => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, username)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_check_external_user_exists", req, callContext)
        response.map(convertToTuple[InboundExternalUser](callContext))        
  }
          
  messageDocs += getBankAccountOldDoc
  def getBankAccountOldDoc = MessageDoc(
    process = "obp.getBankAccountOld",
    messageFormat = messageFormat,
    description = "Get Bank Account Old",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountOld(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountOld(status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBankAccountOld(bankId: BankId, accountId: AccountId): Box[BankAccount] = {
        import com.openbankproject.commons.dto.{InBoundGetBankAccountOld => InBound, OutBoundGetBankAccountOld => OutBound}  
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, accountId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_bank_account_old", req, callContext)
        response.map(convertToTuple[BankAccountCommons](callContext))        
  }
          
  messageDocs += getBankAccountByIbanDoc
  def getBankAccountByIbanDoc = MessageDoc(
    process = "obp.getBankAccountByIban",
    messageFormat = messageFormat,
    description = "Get Bank Account By Iban",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountByIban(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      iban=ibanExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountByIban(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBankAccountByIban(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{InBoundGetBankAccountByIban => InBound, OutBoundGetBankAccountByIban => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, iban)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_bank_account_by_iban", req, callContext)
        response.map(convertToTuple[BankAccountCommons](callContext))        
  }
          
  messageDocs += getBankAccountByRoutingDoc
  def getBankAccountByRoutingDoc = MessageDoc(
    process = "obp.getBankAccountByRouting",
    messageFormat = messageFormat,
    description = "Get Bank Account By Routing",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountByRouting(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=Some(BankId(bankIdExample.value)),
      scheme=schemeExample.value,
      address=addressExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountByRouting(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBankAccountByRouting(bankId: Option[BankId], scheme: String, address: String, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{InBoundGetBankAccountByRouting => InBound, OutBoundGetBankAccountByRouting => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, scheme, address)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_bank_account_by_routing", req, callContext)
        response.map(convertToTuple[BankAccountCommons](callContext))        
  }
          
  messageDocs += getBankAccountsDoc
  def getBankAccountsDoc = MessageDoc(
    process = "obp.getBankAccounts",
    messageFormat = messageFormat,
    description = "Get Bank Accounts",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccounts(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankIdAccountIds=List( BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccounts(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value))))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[List[BankAccount]]] = {
        import com.openbankproject.commons.dto.{InBoundGetBankAccounts => InBound, OutBoundGetBankAccounts => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankIdAccountIds)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_bank_accounts", req, callContext)
        response.map(convertToTuple[List[BankAccountCommons]](callContext))        
  }
          
  messageDocs += getBankAccountsBalancesDoc
  def getBankAccountsBalancesDoc = MessageDoc(
    process = "obp.getBankAccountsBalances",
    messageFormat = messageFormat,
    description = "Get Bank Accounts Balances",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountsBalances(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankIdAccountIds=List( BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountsBalances(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= AccountsBalances(accounts=List( AccountBalance(id=idExample.value,
      label=labelExample.value,
      bankId=bankIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      balance= AmountOfMoney(currency=balanceCurrencyExample.value,
      amount=balanceAmountExample.value))),
      overallBalance= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value),
      overallBalanceDate=toDate(overallBalanceDateExample)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[AccountsBalances]] = {
        import com.openbankproject.commons.dto.{InBoundGetBankAccountsBalances => InBound, OutBoundGetBankAccountsBalances => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankIdAccountIds)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_bank_accounts_balances", req, callContext)
        response.map(convertToTuple[AccountsBalances](callContext))        
  }
          
  messageDocs += getCoreBankAccountsDoc
  def getCoreBankAccountsDoc = MessageDoc(
    process = "obp.getCoreBankAccounts",
    messageFormat = messageFormat,
    description = "Get Core Bank Accounts",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCoreBankAccounts(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankIdAccountIds=List( BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))))
    ),
    exampleInboundMessage = (
     InBoundGetCoreBankAccounts(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( CoreAccount(id=accountIdExample.value,
      label=labelExample.value,
      bankId=bankIdExample.value,
      accountType=accountTypeExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): Future[Box[(List[CoreAccount], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetCoreBankAccounts => InBound, OutBoundGetCoreBankAccounts => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankIdAccountIds)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_core_bank_accounts", req, callContext)
        response.map(convertToTuple[List[CoreAccount]](callContext))        
  }
          
  messageDocs += getBankAccountsHeldDoc
  def getBankAccountsHeldDoc = MessageDoc(
    process = "obp.getBankAccountsHeld",
    messageFormat = messageFormat,
    description = "Get Bank Accounts Held",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBankAccountsHeld(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankIdAccountIds=List( BankIdAccountId(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))))
    ),
    exampleInboundMessage = (
     InBoundGetBankAccountsHeld(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( AccountHeld(id=idExample.value,
      label=labelExample.value,
      bankId=bankIdExample.value,
      number=numberExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[Box[List[AccountHeld]]] = {
        import com.openbankproject.commons.dto.{InBoundGetBankAccountsHeld => InBound, OutBoundGetBankAccountsHeld => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankIdAccountIds)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_bank_accounts_held", req, callContext)
        response.map(convertToTuple[List[AccountHeld]](callContext))        
  }
          
  messageDocs += checkBankAccountExistsDoc
  def checkBankAccountExistsDoc = MessageDoc(
    process = "obp.checkBankAccountExists",
    messageFormat = messageFormat,
    description = "Check Bank Account Exists",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCheckBankAccountExists(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundCheckBankAccountExists(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{InBoundCheckBankAccountExists => InBound, OutBoundCheckBankAccountExists => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_check_bank_account_exists", req, callContext)
        response.map(convertToTuple[BankAccountCommons](callContext))        
  }
          
  messageDocs += getCounterpartyTraitDoc
  def getCounterpartyTraitDoc = MessageDoc(
    process = "obp.getCounterpartyTrait",
    messageFormat = messageFormat,
    description = "Get Counterparty Trait",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterpartyTrait(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      couterpartyId="string")
    ),
    exampleInboundMessage = (
     InBoundGetCounterpartyTrait(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CounterpartyTraitCommons(createdByUserId=createdByUserIdExample.value,
      name=nameExample.value,
      description=descriptionExample.value,
      currency=currencyExample.value,
      thisBankId=thisBankIdExample.value,
      thisAccountId=thisAccountIdExample.value,
      thisViewId=thisViewIdExample.value,
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=otherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=otherAccountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme=otherAccountSecondaryRoutingSchemeExample.value,
      otherAccountSecondaryRoutingAddress=otherAccountSecondaryRoutingAddressExample.value,
      otherBankRoutingScheme=otherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=otherBankRoutingAddressExample.value,
      otherBranchRoutingScheme=otherBranchRoutingSchemeExample.value,
      otherBranchRoutingAddress=otherBranchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCounterpartyTrait(bankId: BankId, accountId: AccountId, couterpartyId: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
        import com.openbankproject.commons.dto.{InBoundGetCounterpartyTrait => InBound, OutBoundGetCounterpartyTrait => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, couterpartyId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_counterparty_trait", req, callContext)
        response.map(convertToTuple[CounterpartyTraitCommons](callContext))        
  }
          
  messageDocs += getCounterpartyByCounterpartyIdDoc
  def getCounterpartyByCounterpartyIdDoc = MessageDoc(
    process = "obp.getCounterpartyByCounterpartyId",
    messageFormat = messageFormat,
    description = "Get Counterparty By Counterparty Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterpartyByCounterpartyId(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      counterpartyId=CounterpartyId(counterpartyIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetCounterpartyByCounterpartyId(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CounterpartyTraitCommons(createdByUserId=createdByUserIdExample.value,
      name=nameExample.value,
      description=descriptionExample.value,
      currency=currencyExample.value,
      thisBankId=thisBankIdExample.value,
      thisAccountId=thisAccountIdExample.value,
      thisViewId=thisViewIdExample.value,
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=otherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=otherAccountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme=otherAccountSecondaryRoutingSchemeExample.value,
      otherAccountSecondaryRoutingAddress=otherAccountSecondaryRoutingAddressExample.value,
      otherBankRoutingScheme=otherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=otherBankRoutingAddressExample.value,
      otherBranchRoutingScheme=otherBranchRoutingSchemeExample.value,
      otherBranchRoutingAddress=otherBranchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
        import com.openbankproject.commons.dto.{InBoundGetCounterpartyByCounterpartyId => InBound, OutBoundGetCounterpartyByCounterpartyId => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, counterpartyId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_counterparty_by_counterparty_id", req, callContext)
        response.map(convertToTuple[CounterpartyTraitCommons](callContext))        
  }
          
  messageDocs += getCounterpartyByIbanDoc
  def getCounterpartyByIbanDoc = MessageDoc(
    process = "obp.getCounterpartyByIban",
    messageFormat = messageFormat,
    description = "Get Counterparty By Iban",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterpartyByIban(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      iban=ibanExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCounterpartyByIban(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CounterpartyTraitCommons(createdByUserId=createdByUserIdExample.value,
      name=nameExample.value,
      description=descriptionExample.value,
      currency=currencyExample.value,
      thisBankId=thisBankIdExample.value,
      thisAccountId=thisAccountIdExample.value,
      thisViewId=thisViewIdExample.value,
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=otherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=otherAccountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme=otherAccountSecondaryRoutingSchemeExample.value,
      otherAccountSecondaryRoutingAddress=otherAccountSecondaryRoutingAddressExample.value,
      otherBankRoutingScheme=otherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=otherBankRoutingAddressExample.value,
      otherBranchRoutingScheme=otherBranchRoutingSchemeExample.value,
      otherBranchRoutingAddress=otherBranchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCounterpartyByIban(iban: String, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
        import com.openbankproject.commons.dto.{InBoundGetCounterpartyByIban => InBound, OutBoundGetCounterpartyByIban => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, iban)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_counterparty_by_iban", req, callContext)
        response.map(convertToTuple[CounterpartyTraitCommons](callContext))        
  }
          
  messageDocs += getCounterpartyByIbanAndBankAccountIdDoc
  def getCounterpartyByIbanAndBankAccountIdDoc = MessageDoc(
    process = "obp.getCounterpartyByIbanAndBankAccountId",
    messageFormat = messageFormat,
    description = "Get Counterparty By Iban And Bank Account Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterpartyByIbanAndBankAccountId(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      iban=ibanExample.value,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetCounterpartyByIbanAndBankAccountId(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CounterpartyTraitCommons(createdByUserId=createdByUserIdExample.value,
      name=nameExample.value,
      description=descriptionExample.value,
      currency=currencyExample.value,
      thisBankId=thisBankIdExample.value,
      thisAccountId=thisAccountIdExample.value,
      thisViewId=thisViewIdExample.value,
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=otherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=otherAccountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme=otherAccountSecondaryRoutingSchemeExample.value,
      otherAccountSecondaryRoutingAddress=otherAccountSecondaryRoutingAddressExample.value,
      otherBankRoutingScheme=otherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=otherBankRoutingAddressExample.value,
      otherBranchRoutingScheme=otherBranchRoutingSchemeExample.value,
      otherBranchRoutingAddress=otherBranchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[CounterpartyTrait]] = {
        import com.openbankproject.commons.dto.{InBoundGetCounterpartyByIbanAndBankAccountId => InBound, OutBoundGetCounterpartyByIbanAndBankAccountId => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, iban, bankId, accountId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_counterparty_by_iban_and_bank_account_id", req, callContext)
        response.map(convertToTuple[CounterpartyTraitCommons](callContext))        
  }
          
  messageDocs += getCounterpartiesDoc
  def getCounterpartiesDoc = MessageDoc(
    process = "obp.getCounterparties",
    messageFormat = messageFormat,
    description = "Get Counterparties",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCounterparties(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      thisBankId=BankId(thisBankIdExample.value),
      thisAccountId=AccountId(thisAccountIdExample.value),
      viewId=ViewId(viewIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetCounterparties(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( CounterpartyTraitCommons(createdByUserId=createdByUserIdExample.value,
      name=nameExample.value,
      description=descriptionExample.value,
      currency=currencyExample.value,
      thisBankId=thisBankIdExample.value,
      thisAccountId=thisAccountIdExample.value,
      thisViewId=thisViewIdExample.value,
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=otherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=otherAccountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme=otherAccountSecondaryRoutingSchemeExample.value,
      otherAccountSecondaryRoutingAddress=otherAccountSecondaryRoutingAddressExample.value,
      otherBankRoutingScheme=otherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=otherBankRoutingAddressExample.value,
      otherBranchRoutingScheme=otherBranchRoutingSchemeExample.value,
      otherBranchRoutingAddress=otherBranchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext]): OBPReturnType[Box[List[CounterpartyTrait]]] = {
        import com.openbankproject.commons.dto.{InBoundGetCounterparties => InBound, OutBoundGetCounterparties => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, thisBankId, thisAccountId, viewId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_counterparties", req, callContext)
        response.map(convertToTuple[List[CounterpartyTraitCommons]](callContext))        
  }
          
  messageDocs += getTransactionsDoc
  def getTransactionsDoc = MessageDoc(
    process = "obp.getTransactions",
    messageFormat = messageFormat,
    description = "Get Transactions",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactions(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate=outBoundGetTransactionsFromDateExample.value,
      toDate=outBoundGetTransactionsToDateExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetTransactions(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( Transaction(uuid=transactionUuidExample.value,
      id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      otherAccount= Counterparty(nationalIdentifier=counterpartyNationalIdentifierExample.value,
      kind=counterpartyKindExample.value,
      counterpartyId=counterpartyIdExample.value,
      counterpartyName=counterpartyNameExample.value,
      thisBankId=BankId(thisBankIdExample.value),
      thisAccountId=AccountId(thisAccountIdExample.value),
      otherBankRoutingScheme=counterpartyOtherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=Some(counterpartyOtherBankRoutingAddressExample.value),
      otherAccountRoutingScheme=counterpartyOtherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=Some(counterpartyOtherAccountRoutingAddressExample.value),
      otherAccountProvider=counterpartyOtherAccountProviderExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean),
      transactionType=transactionTypeExample.value,
      amount=BigDecimal(transactionAmountExample.value),
      currency=currencyExample.value,
      description=Some(transactionDescriptionExample.value),
      startDate=toDate(transactionStartDateExample),
      finishDate=toDate(transactionFinishDateExample),
      balance=BigDecimal(balanceExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): OBPReturnType[Box[List[Transaction]]] = {
        import com.openbankproject.commons.dto.{InBoundGetTransactions => InBound, OutBoundGetTransactions => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_transactions", req, callContext)
        response.map(convertToTuple[List[Transaction]](callContext))        
  }
          
  messageDocs += getTransactionsCoreDoc
  def getTransactionsCoreDoc = MessageDoc(
    process = "obp.getTransactionsCore",
    messageFormat = messageFormat,
    description = "Get Transactions Core",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionsCore(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate=fromDateExample.value,
      toDate=toDateExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetTransactionsCore(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( TransactionCore(id=TransactionId(idExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      otherAccount= CounterpartyCore(kind=kindExample.value,
      counterpartyId=counterpartyIdExample.value,
      counterpartyName=counterpartyNameExample.value,
      thisBankId=BankId(thisBankIdExample.value),
      thisAccountId=AccountId(thisAccountIdExample.value),
      otherBankRoutingScheme=otherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=Some(otherBankRoutingAddressExample.value),
      otherAccountRoutingScheme=otherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=Some(otherAccountRoutingAddressExample.value),
      otherAccountProvider=otherAccountProviderExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean),
      transactionType=transactionTypeExample.value,
      amount=BigDecimal(amountExample.value),
      currency=currencyExample.value,
      description=Some(descriptionExample.value),
      startDate=toDate(startDateExample),
      finishDate=toDate(finishDateExample),
      balance=BigDecimal(balanceExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getTransactionsCore(bankId: BankId, accountId: AccountId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionCore]]] = {
        import com.openbankproject.commons.dto.{InBoundGetTransactionsCore => InBound, OutBoundGetTransactionsCore => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_transactions_core", req, callContext)
        response.map(convertToTuple[List[TransactionCore]](callContext))        
  }
          
  messageDocs += getTransactionDoc
  def getTransactionDoc = MessageDoc(
    process = "obp.getTransaction",
    messageFormat = messageFormat,
    description = "Get Transaction",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransaction(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      transactionId=TransactionId(transactionIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetTransaction(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= Transaction(uuid=transactionUuidExample.value,
      id=TransactionId(transactionIdExample.value),
      thisAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      otherAccount= Counterparty(nationalIdentifier=counterpartyNationalIdentifierExample.value,
      kind=counterpartyKindExample.value,
      counterpartyId=counterpartyIdExample.value,
      counterpartyName=counterpartyNameExample.value,
      thisBankId=BankId(thisBankIdExample.value),
      thisAccountId=AccountId(thisAccountIdExample.value),
      otherBankRoutingScheme=counterpartyOtherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=Some(counterpartyOtherBankRoutingAddressExample.value),
      otherAccountRoutingScheme=counterpartyOtherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=Some(counterpartyOtherAccountRoutingAddressExample.value),
      otherAccountProvider=counterpartyOtherAccountProviderExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean),
      transactionType=transactionTypeExample.value,
      amount=BigDecimal(transactionAmountExample.value),
      currency=currencyExample.value,
      description=Some(transactionDescriptionExample.value),
      startDate=toDate(transactionStartDateExample),
      finishDate=toDate(transactionFinishDateExample),
      balance=BigDecimal(balanceExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[Transaction]] = {
        import com.openbankproject.commons.dto.{InBoundGetTransaction => InBound, OutBoundGetTransaction => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, transactionId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_transaction", req, callContext)
        response.map(convertToTuple[Transaction](callContext))        
  }
          
  messageDocs += getPhysicalCardsForUserDoc
  def getPhysicalCardsForUserDoc = MessageDoc(
    process = "obp.getPhysicalCardsForUser",
    messageFormat = messageFormat,
    description = "Get Physical Cards For User",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
          OutBoundGetPhysicalCardsForUser( UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetPhysicalCardsForUser(status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=toDate(validFromExample),
      expires=toDate(expiresDateExample),
      enabled=enabledExample.value.toBoolean,
      cancelled=cancelledExample.value.toBoolean,
      onHotList=onHotListExample.value.toBoolean,
      technology=technologyExample.value,
      networks=networksExample.value.split("[,;]").toList,
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      replacement=Some( CardReplacementInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(toDate(collectedExample))),
      posted=Some(CardPostedInfo(toDate(postedExample))),
      customerId=customerIdExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getPhysicalCardsForUser(user: User): Box[List[PhysicalCard]] = {
        import com.openbankproject.commons.dto.{InBoundGetPhysicalCardsForUser => InBound, OutBoundGetPhysicalCardsForUser => OutBound}  
        val callContext: Option[CallContext] = None
        val req = OutBound(user)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_physical_cards_for_user", req, callContext)
        response.map(convertToTuple[List[PhysicalCard]](callContext))        
  }
          
  messageDocs += getPhysicalCardForBankDoc
  def getPhysicalCardForBankDoc = MessageDoc(
    process = "obp.getPhysicalCardForBank",
    messageFormat = messageFormat,
    description = "Get Physical Card For Bank",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetPhysicalCardForBank(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      cardId=cardIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetPhysicalCardForBank(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=toDate(validFromExample),
      expires=toDate(expiresDateExample),
      enabled=enabledExample.value.toBoolean,
      cancelled=cancelledExample.value.toBoolean,
      onHotList=onHotListExample.value.toBoolean,
      technology=technologyExample.value,
      networks=networksExample.value.split("[,;]").toList,
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      replacement=Some( CardReplacementInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(toDate(collectedExample))),
      posted=Some(CardPostedInfo(toDate(postedExample))),
      customerId=customerIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getPhysicalCardForBank(bankId: BankId, cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCardTrait]] = {
        import com.openbankproject.commons.dto.{InBoundGetPhysicalCardForBank => InBound, OutBoundGetPhysicalCardForBank => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, cardId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_physical_card_for_bank", req, callContext)
        response.map(convertToTuple[PhysicalCard](callContext))        
  }
          
  messageDocs += deletePhysicalCardForBankDoc
  def deletePhysicalCardForBankDoc = MessageDoc(
    process = "obp.deletePhysicalCardForBank",
    messageFormat = messageFormat,
    description = "Delete Physical Card For Bank",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeletePhysicalCardForBank(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      cardId=cardIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundDeletePhysicalCardForBank(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def deletePhysicalCardForBank(bankId: BankId, cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{InBoundDeletePhysicalCardForBank => InBound, OutBoundDeletePhysicalCardForBank => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, cardId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_delete_physical_card_for_bank", req, callContext)
        response.map(convertToTuple[Boolean](callContext))        
  }
          
  messageDocs += getPhysicalCardsForBankDoc
  def getPhysicalCardsForBankDoc = MessageDoc(
    process = "obp.getPhysicalCardsForBank",
    messageFormat = messageFormat,
    description = "Get Physical Cards For Bank",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetPhysicalCardsForBank(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bank= BankCommons(bankId=BankId(bankIdExample.value),
      shortName=bankShortNameExample.value,
      fullName=bankFullNameExample.value,
      logoUrl=bankLogoUrlExample.value,
      websiteUrl=bankWebsiteUrlExample.value,
      bankRoutingScheme=bankRoutingSchemeExample.value,
      bankRoutingAddress=bankRoutingAddressExample.value,
      swiftBic=bankSwiftBicExample.value,
      nationalIdentifier=bankNationalIdentifierExample.value),
      user= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate=fromDateExample.value,
      toDate=toDateExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetPhysicalCardsForBank(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=toDate(validFromExample),
      expires=toDate(expiresDateExample),
      enabled=enabledExample.value.toBoolean,
      cancelled=cancelledExample.value.toBoolean,
      onHotList=onHotListExample.value.toBoolean,
      technology=technologyExample.value,
      networks=networksExample.value.split("[,;]").toList,
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      replacement=Some( CardReplacementInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(toDate(collectedExample))),
      posted=Some(CardPostedInfo(toDate(postedExample))),
      customerId=customerIdExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getPhysicalCardsForBank(bank: Bank, user: User, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[PhysicalCard]]] = {
        import com.openbankproject.commons.dto.{InBoundGetPhysicalCardsForBank => InBound, OutBoundGetPhysicalCardsForBank => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bank, user, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_physical_cards_for_bank", req, callContext)
        response.map(convertToTuple[List[PhysicalCard]](callContext))        
  }
          
  messageDocs += createPhysicalCardDoc
  def createPhysicalCardDoc = MessageDoc(
    process = "obp.createPhysicalCard",
    messageFormat = messageFormat,
    description = "Create Physical Card",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreatePhysicalCard(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankCardNumber=bankCardNumberExample.value,
      nameOnCard=nameOnCardExample.value,
      cardType=cardTypeExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=toDate(validFromExample),
      expires=toDate(expiresDateExample),
      enabled=enabledExample.value.toBoolean,
      cancelled=cancelledExample.value.toBoolean,
      onHotList=onHotListExample.value.toBoolean,
      technology=technologyExample.value,
      networks=networksExample.value.split("[,;]").toList,
      allows=allowsExample.value.split("[,;]").toList,
      accountId=accountIdExample.value,
      bankId=bankIdExample.value,
      replacement=Some( CardReplacementInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(toDate(collectedExample))),
      posted=Some(CardPostedInfo(toDate(postedExample))),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreatePhysicalCard(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=toDate(validFromExample),
      expires=toDate(expiresDateExample),
      enabled=enabledExample.value.toBoolean,
      cancelled=cancelledExample.value.toBoolean,
      onHotList=onHotListExample.value.toBoolean,
      technology=technologyExample.value,
      networks=networksExample.value.split("[,;]").toList,
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      replacement=Some( CardReplacementInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(toDate(collectedExample))),
      posted=Some(CardPostedInfo(toDate(postedExample))),
      customerId=customerIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createPhysicalCard(bankCardNumber: String, nameOnCard: String, cardType: String, issueNumber: String, serialNumber: String, validFrom: Date, expires: Date, enabled: Boolean, cancelled: Boolean, onHotList: Boolean, technology: String, networks: List[String], allows: List[String], accountId: String, bankId: String, replacement: Option[CardReplacementInfo], pinResets: List[PinResetInfo], collected: Option[CardCollectionInfo], posted: Option[CardPostedInfo], customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCard]] = {
        import com.openbankproject.commons.dto.{InBoundCreatePhysicalCard => InBound, OutBoundCreatePhysicalCard => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankCardNumber, nameOnCard, cardType, issueNumber, serialNumber, validFrom, expires, enabled, cancelled, onHotList, technology, networks, allows, accountId, bankId, replacement, pinResets, collected, posted, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_physical_card", req, callContext)
        response.map(convertToTuple[PhysicalCard](callContext))        
  }
          
  messageDocs += updatePhysicalCardDoc
  def updatePhysicalCardDoc = MessageDoc(
    process = "obp.updatePhysicalCard",
    messageFormat = messageFormat,
    description = "Update Physical Card",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdatePhysicalCard(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      cardId=cardIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      nameOnCard=nameOnCardExample.value,
      cardType=cardTypeExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=toDate(validFromExample),
      expires=toDate(expiresDateExample),
      enabled=enabledExample.value.toBoolean,
      cancelled=cancelledExample.value.toBoolean,
      onHotList=onHotListExample.value.toBoolean,
      technology=technologyExample.value,
      networks=networksExample.value.split("[,;]").toList,
      allows=allowsExample.value.split("[,;]").toList,
      accountId=accountIdExample.value,
      bankId=bankIdExample.value,
      replacement=Some( CardReplacementInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(toDate(collectedExample))),
      posted=Some(CardPostedInfo(toDate(postedExample))),
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundUpdatePhysicalCard(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= PhysicalCard(cardId=cardIdExample.value,
      bankId=bankIdExample.value,
      bankCardNumber=bankCardNumberExample.value,
      cardType=cardTypeExample.value,
      nameOnCard=nameOnCardExample.value,
      issueNumber=issueNumberExample.value,
      serialNumber=serialNumberExample.value,
      validFrom=toDate(validFromExample),
      expires=toDate(expiresDateExample),
      enabled=enabledExample.value.toBoolean,
      cancelled=cancelledExample.value.toBoolean,
      onHotList=onHotListExample.value.toBoolean,
      technology=technologyExample.value,
      networks=networksExample.value.split("[,;]").toList,
      allows=List(com.openbankproject.commons.model.CardAction.DEBIT),
      account= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=accountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      replacement=Some( CardReplacementInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.CardReplacementReason.FIRST)),
      pinResets=List( PinResetInfo(requestedDate=toDate(requestedDateExample),
      reasonRequested=com.openbankproject.commons.model.PinResetReason.FORGOT)),
      collected=Some(CardCollectionInfo(toDate(collectedExample))),
      posted=Some(CardPostedInfo(toDate(postedExample))),
      customerId=customerIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def updatePhysicalCard(cardId: String, bankCardNumber: String, nameOnCard: String, cardType: String, issueNumber: String, serialNumber: String, validFrom: Date, expires: Date, enabled: Boolean, cancelled: Boolean, onHotList: Boolean, technology: String, networks: List[String], allows: List[String], accountId: String, bankId: String, replacement: Option[CardReplacementInfo], pinResets: List[PinResetInfo], collected: Option[CardCollectionInfo], posted: Option[CardPostedInfo], customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[PhysicalCardTrait]] = {
        import com.openbankproject.commons.dto.{InBoundUpdatePhysicalCard => InBound, OutBoundUpdatePhysicalCard => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, cardId, bankCardNumber, nameOnCard, cardType, issueNumber, serialNumber, validFrom, expires, enabled, cancelled, onHotList, technology, networks, allows, accountId, bankId, replacement, pinResets, collected, posted, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_update_physical_card", req, callContext)
        response.map(convertToTuple[PhysicalCard](callContext))        
  }
          
  messageDocs += makePaymentv210Doc
  def makePaymentv210Doc = MessageDoc(
    process = "obp.makePaymentv210",
    messageFormat = messageFormat,
    description = "Make Paymentv210",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundMakePaymentv210(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      transactionRequestId=TransactionRequestId(transactionRequestIdExample.value),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      amount=BigDecimal(amountExample.value),
      description=descriptionExample.value,
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      chargePolicy=chargePolicyExample.value)
    ),
    exampleInboundMessage = (
     InBoundMakePaymentv210(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=TransactionId(transactionIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def makePaymentv210(fromAccount: BankAccount, toAccount: BankAccount, transactionRequestId: TransactionRequestId, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, amount: BigDecimal, description: String, transactionRequestType: TransactionRequestType, chargePolicy: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] = {
        import com.openbankproject.commons.dto.{InBoundMakePaymentv210 => InBound, OutBoundMakePaymentv210 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, fromAccount, toAccount, transactionRequestId, transactionRequestCommonBody, amount, description, transactionRequestType, chargePolicy)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_make_paymentv210", req, callContext)
        response.map(convertToTuple[TransactionId](callContext))        
  }
          
  messageDocs += createTransactionRequestv210Doc
  def createTransactionRequestv210Doc = MessageDoc(
    process = "obp.createTransactionRequestv210",
    messageFormat = messageFormat,
    description = "Create Transaction Requestv210",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionRequestv210(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      viewId=ViewId(viewIdExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      detailsPlain="string",
      chargePolicy=chargePolicyExample.value,
      challengeType=Some(challengeTypeExample.value),
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionRequestv210(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= TransactionRequest(id=TransactionRequestId(transactionRequestIdExample.value),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value)),
      to_sepa=Some(TransactionRequestIban(transactionRequestIban.value)),
      to_counterparty=Some(TransactionRequestCounterpartyId(transactionRequestCounterpartyIdExample.value)),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to=ToAccountTransferToPhone(toExample.value))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`=typeExample.value,
      number=numberExample.value)))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name=nameExample.value,
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      transaction_ids="string",
      status=statusExample.value,
      start_date=toDate(transactionRequestStartDateExample),
      end_date=toDate(transactionRequestEndDateExample),
      challenge= TransactionRequestChallenge(id=challengeIdExample.value,
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary=summaryExample.value,
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value)),
      charge_policy="string",
      counterparty_id=CounterpartyId(transactionRequestCounterpartyIdExample.value),
      name=nameExample.value,
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createTransactionRequestv210(initiator: User, viewId: ViewId, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, detailsPlain: String, chargePolicy: String, challengeType: Option[String], scaMethod: Option[StrongCustomerAuthentication.SCA], callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{InBoundCreateTransactionRequestv210 => InBound, OutBoundCreateTransactionRequestv210 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, initiator, viewId, fromAccount, toAccount, transactionRequestType, transactionRequestCommonBody, detailsPlain, chargePolicy, challengeType, scaMethod)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_transaction_requestv210", req, callContext)
        response.map(convertToTuple[TransactionRequest](callContext))        
  }
          
  messageDocs += createTransactionRequestv400Doc
  def createTransactionRequestv400Doc = MessageDoc(
    process = "obp.createTransactionRequestv400",
    messageFormat = messageFormat,
    description = "Create Transaction Requestv400",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionRequestv400(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      viewId=ViewId(viewIdExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      detailsPlain="string",
      chargePolicy=chargePolicyExample.value,
      challengeType=Some(challengeTypeExample.value),
      scaMethod=Some(com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS),
      reasons=Some(List( TransactionRequestReason(code=codeExample.value,
      documentNumber=Some(documentNumberExample.value),
      amount=Some(amountExample.value),
      currency=Some(currencyExample.value),
      description=Some(descriptionExample.value)))),
      berlinGroupPayments=Some( SepaCreditTransfersBerlinGroupV13(endToEndIdentification=Some("string"),
      instructionIdentification=Some("string"),
      debtorName=Some("string"),
      debtorAccount=PaymentAccount("string"),
      debtorId=Some("string"),
      ultimateDebtor=Some("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      currencyOfTransfer=Some("string"),
      exchangeRateInformation=Some("string"),
      creditorAccount=PaymentAccount("string"),
      creditorAgent=Some("string"),
      creditorAgentName=Some("string"),
      creditorName="string",
      creditorId=Some("string"),
      creditorAddress=Some("string"),
      creditorNameAndAddress=Some("string"),
      ultimateCreditor=Some("string"),
      purposeCode=Some("string"),
      chargeBearer=Some("string"),
      serviceLevel=Some("string"),
      remittanceInformationUnstructured=Some("string"),
      remittanceInformationUnstructuredArray=Some("string"),
      remittanceInformationStructured=Some("string"),
      remittanceInformationStructuredArray=Some("string"),
      requestedExecutionDate=Some("string"),
      requestedExecutionTime=Some("string"))))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionRequestv400(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= TransactionRequest(id=TransactionRequestId(transactionRequestIdExample.value),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value)),
      to_sepa=Some(TransactionRequestIban(transactionRequestIban.value)),
      to_counterparty=Some(TransactionRequestCounterpartyId(transactionRequestCounterpartyIdExample.value)),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to=ToAccountTransferToPhone(toExample.value))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`=typeExample.value,
      number=numberExample.value)))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name=nameExample.value,
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      transaction_ids="string",
      status=statusExample.value,
      start_date=toDate(transactionRequestStartDateExample),
      end_date=toDate(transactionRequestEndDateExample),
      challenge= TransactionRequestChallenge(id=challengeIdExample.value,
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary=summaryExample.value,
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value)),
      charge_policy="string",
      counterparty_id=CounterpartyId(transactionRequestCounterpartyIdExample.value),
      name=nameExample.value,
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createTransactionRequestv400(initiator: User, viewId: ViewId, fromAccount: BankAccount, toAccount: BankAccount, transactionRequestType: TransactionRequestType, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, detailsPlain: String, chargePolicy: String, challengeType: Option[String], scaMethod: Option[StrongCustomerAuthentication.SCA], reasons: Option[List[TransactionRequestReason]], berlinGroupPayments: Option[SepaCreditTransfersBerlinGroupV13], callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{InBoundCreateTransactionRequestv400 => InBound, OutBoundCreateTransactionRequestv400 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, initiator, viewId, fromAccount, toAccount, transactionRequestType, transactionRequestCommonBody, detailsPlain, chargePolicy, challengeType, scaMethod, reasons, berlinGroupPayments)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_transaction_requestv400", req, callContext)
        response.map(convertToTuple[TransactionRequest](callContext))        
  }
          
  messageDocs += getTransactionRequests210Doc
  def getTransactionRequests210Doc = MessageDoc(
    process = "obp.getTransactionRequests210",
    messageFormat = messageFormat,
    description = "Get Transaction Requests210",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionRequests210(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionRequests210(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( TransactionRequest(id=TransactionRequestId(transactionRequestIdExample.value),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value)),
      to_sepa=Some(TransactionRequestIban(transactionRequestIban.value)),
      to_counterparty=Some(TransactionRequestCounterpartyId(transactionRequestCounterpartyIdExample.value)),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to=ToAccountTransferToPhone(toExample.value))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`=typeExample.value,
      number=numberExample.value)))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name=nameExample.value,
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      transaction_ids="string",
      status=statusExample.value,
      start_date=toDate(transactionRequestStartDateExample),
      end_date=toDate(transactionRequestEndDateExample),
      challenge= TransactionRequestChallenge(id=challengeIdExample.value,
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary=summaryExample.value,
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value)),
      charge_policy="string",
      counterparty_id=CounterpartyId(transactionRequestCounterpartyIdExample.value),
      name=nameExample.value,
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string"))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getTransactionRequests210(initiator: User, fromAccount: BankAccount, callContext: Option[CallContext]): Box[(List[TransactionRequest], Option[CallContext])] = {
        import com.openbankproject.commons.dto.{InBoundGetTransactionRequests210 => InBound, OutBoundGetTransactionRequests210 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, initiator, fromAccount)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_transaction_requests210", req, callContext)
        response.map(convertToTuple[List[TransactionRequest]](callContext))        
  }
          
  messageDocs += getTransactionRequestImplDoc
  def getTransactionRequestImplDoc = MessageDoc(
    process = "obp.getTransactionRequestImpl",
    messageFormat = messageFormat,
    description = "Get Transaction Request Impl",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionRequestImpl(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      transactionRequestId=TransactionRequestId(transactionRequestIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionRequestImpl(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= TransactionRequest(id=TransactionRequestId(transactionRequestIdExample.value),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value)),
      to_sepa=Some(TransactionRequestIban(transactionRequestIban.value)),
      to_counterparty=Some(TransactionRequestCounterpartyId(transactionRequestCounterpartyIdExample.value)),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to=ToAccountTransferToPhone(toExample.value))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`=typeExample.value,
      number=numberExample.value)))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name=nameExample.value,
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      transaction_ids="string",
      status=statusExample.value,
      start_date=toDate(transactionRequestStartDateExample),
      end_date=toDate(transactionRequestEndDateExample),
      challenge= TransactionRequestChallenge(id=challengeIdExample.value,
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary=summaryExample.value,
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value)),
      charge_policy="string",
      counterparty_id=CounterpartyId(transactionRequestCounterpartyIdExample.value),
      name=nameExample.value,
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId, callContext: Option[CallContext]): Box[(TransactionRequest, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{InBoundGetTransactionRequestImpl => InBound, OutBoundGetTransactionRequestImpl => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, transactionRequestId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_transaction_request_impl", req, callContext)
        response.map(convertToTuple[TransactionRequest](callContext))        
  }
          
  messageDocs += createTransactionAfterChallengeV210Doc
  def createTransactionAfterChallengeV210Doc = MessageDoc(
    process = "obp.createTransactionAfterChallengeV210",
    messageFormat = messageFormat,
    description = "Create Transaction After Challenge V210",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionAfterChallengeV210(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      transactionRequest= TransactionRequest(id=TransactionRequestId(transactionRequestIdExample.value),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value)),
      to_sepa=Some(TransactionRequestIban(transactionRequestIban.value)),
      to_counterparty=Some(TransactionRequestCounterpartyId(transactionRequestCounterpartyIdExample.value)),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to=ToAccountTransferToPhone(toExample.value))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`=typeExample.value,
      number=numberExample.value)))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name=nameExample.value,
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      transaction_ids="string",
      status=statusExample.value,
      start_date=toDate(transactionRequestStartDateExample),
      end_date=toDate(transactionRequestEndDateExample),
      challenge= TransactionRequestChallenge(id=challengeIdExample.value,
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary=summaryExample.value,
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value)),
      charge_policy="string",
      counterparty_id=CounterpartyId(transactionRequestCounterpartyIdExample.value),
      name=nameExample.value,
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionAfterChallengeV210(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= TransactionRequest(id=TransactionRequestId(transactionRequestIdExample.value),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value)),
      to_sepa=Some(TransactionRequestIban(transactionRequestIban.value)),
      to_counterparty=Some(TransactionRequestCounterpartyId(transactionRequestCounterpartyIdExample.value)),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to=ToAccountTransferToPhone(toExample.value))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`=typeExample.value,
      number=numberExample.value)))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name=nameExample.value,
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      transaction_ids="string",
      status=statusExample.value,
      start_date=toDate(transactionRequestStartDateExample),
      end_date=toDate(transactionRequestEndDateExample),
      challenge= TransactionRequestChallenge(id=challengeIdExample.value,
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary=summaryExample.value,
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value)),
      charge_policy="string",
      counterparty_id=CounterpartyId(transactionRequestCounterpartyIdExample.value),
      name=nameExample.value,
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{InBoundCreateTransactionAfterChallengeV210 => InBound, OutBoundCreateTransactionAfterChallengeV210 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, fromAccount, transactionRequest)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_transaction_after_challenge_v210", req, callContext)
        response.map(convertToTuple[TransactionRequest](callContext))        
  }
          
  messageDocs += updateBankAccountDoc
  def updateBankAccountDoc = MessageDoc(
    process = "obp.updateBankAccount",
    messageFormat = messageFormat,
    description = "Update Bank Account",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateBankAccount(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      accountLabel="string",
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)))
    ),
    exampleInboundMessage = (
     InBoundUpdateBankAccount(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def updateBankAccount(bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, branchId: String, accountRoutings: List[AccountRouting], callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{InBoundUpdateBankAccount => InBound, OutBoundUpdateBankAccount => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, accountType, accountLabel, branchId, accountRoutings)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_update_bank_account", req, callContext)
        response.map(convertToTuple[BankAccountCommons](callContext))        
  }
          
  messageDocs += createBankAccountDoc
  def createBankAccountDoc = MessageDoc(
    process = "obp.createBankAccount",
    messageFormat = messageFormat,
    description = "Create Bank Account",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateBankAccount(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      accountLabel="string",
      currency=currencyExample.value,
      initialBalance=BigDecimal("123.321"),
      accountHolderName="string",
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)))
    ),
    exampleInboundMessage = (
     InBoundCreateBankAccount(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createBankAccount(bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal, accountHolderName: String, branchId: String, accountRoutings: List[AccountRouting], callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
        import com.openbankproject.commons.dto.{InBoundCreateBankAccount => InBound, OutBoundCreateBankAccount => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, accountType, accountLabel, currency, initialBalance, accountHolderName, branchId, accountRoutings)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_bank_account", req, callContext)
        response.map(convertToTuple[BankAccountCommons](callContext))        
  }
          
  messageDocs += accountExistsDoc
  def accountExistsDoc = MessageDoc(
    process = "obp.accountExists",
    messageFormat = messageFormat,
    description = "Account Exists",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundAccountExists(bankId=BankId(bankIdExample.value),
      accountNumber=accountNumberExample.value)
    ),
    exampleInboundMessage = (
     InBoundAccountExists(status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def accountExists(bankId: BankId, accountNumber: String): Box[Boolean] = {
        import com.openbankproject.commons.dto.{InBoundAccountExists => InBound, OutBoundAccountExists => OutBound}  
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, accountNumber)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_account_exists", req, callContext)
        response.map(convertToTuple[Boolean](callContext))        
  }
          
  messageDocs += getProductsDoc
  def getProductsDoc = MessageDoc(
    process = "obp.getProducts",
    messageFormat = messageFormat,
    description = "Get Products",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProducts(bankId=BankId(bankIdExample.value),
      params=List( GetProductsParam(name=nameExample.value,
      value=valueExample.value.split("[,;]").toList)))
    ),
    exampleInboundMessage = (
     InBoundGetProducts(status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( ProductCommons(bankId=BankId(bankIdExample.value),
      code=ProductCode(productCodeExample.value),
      parentProductCode=ProductCode(parentProductCodeExample.value),
      name=nameExample.value,
      category=categoryExample.value,
      family=familyExample.value,
      superFamily=superFamilyExample.value,
      moreInfoUrl=moreInfoUrlExample.value,
      details=detailsExample.value,
      description=descriptionExample.value,
      meta=Meta( License(id=idExample.value,
      name=nameExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getProducts(bankId: BankId, params: List[GetProductsParam]): Box[List[Product]] = {
        import com.openbankproject.commons.dto.{InBoundGetProducts => InBound, OutBoundGetProducts => OutBound}  
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, params)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_products", req, callContext)
        response.map(convertToTuple[List[ProductCommons]](callContext))        
  }
          
  messageDocs += getProductDoc
  def getProductDoc = MessageDoc(
    process = "obp.getProduct",
    messageFormat = messageFormat,
    description = "Get Product",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProduct(bankId=BankId(bankIdExample.value),
      productCode=ProductCode(productCodeExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetProduct(status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= ProductCommons(bankId=BankId(bankIdExample.value),
      code=ProductCode(productCodeExample.value),
      parentProductCode=ProductCode(parentProductCodeExample.value),
      name=nameExample.value,
      category=categoryExample.value,
      family=familyExample.value,
      superFamily=superFamilyExample.value,
      moreInfoUrl=moreInfoUrlExample.value,
      details=detailsExample.value,
      description=descriptionExample.value,
      meta=Meta( License(id=idExample.value,
      name=nameExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = {
        import com.openbankproject.commons.dto.{InBoundGetProduct => InBound, OutBoundGetProduct => OutBound}  
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, productCode)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_product", req, callContext)
        response.map(convertToTuple[ProductCommons](callContext))        
  }
          
  messageDocs += getBranchDoc
  def getBranchDoc = MessageDoc(
    process = "obp.getBranch",
    messageFormat = messageFormat,
    description = "Get Branch",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBranch(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      branchId=BranchId(branchIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetBranch(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= BranchTCommons(branchId=BranchId(branchIdExample.value),
      bankId=BankId(bankIdExample.value),
      name=nameExample.value,
      address= Address(line1=line1Example.value,
      line2=line2Example.value,
      line3=line3Example.value,
      city=cityExample.value,
      county=Some(countyExample.value),
      state=stateExample.value,
      postCode=postCodeExample.value,
      countryCode=countryCodeExample.value),
      location= Location(latitude=latitudeExample.value.toDouble,
      longitude=longitudeExample.value.toDouble,
      date=Some(toDate(dateExample)),
      user=Some( BasicResourceUser(userId=userIdExample.value,
      provider=providerExample.value,
      username=usernameExample.value))),
      lobbyString=Some(LobbyString("string")),
      driveUpString=Some(DriveUpString("string")),
      meta=Meta( License(id=idExample.value,
      name=nameExample.value)),
      branchRouting=Some( Routing(scheme=branchRoutingSchemeExample.value,
      address=branchRoutingAddressExample.value)),
      lobby=Some( Lobby(monday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      tuesday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      wednesday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      thursday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      friday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      saturday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      sunday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)))),
      driveUp=Some( DriveUp(monday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      tuesday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      wednesday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      thursday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      friday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      saturday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      sunday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value))),
      isAccessible=Some(isAccessibleExample.value.toBoolean),
      accessibleFeatures=Some("string"),
      branchType=Some(branchTypeExample.value),
      moreInfo=Some(moreInfoExample.value),
      phoneNumber=Some(phoneNumberExample.value),
      isDeleted=Some(true)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBranch(bankId: BankId, branchId: BranchId, callContext: Option[CallContext]): Future[Box[(BranchT, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetBranch => InBound, OutBoundGetBranch => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, branchId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_branch", req, callContext)
        response.map(convertToTuple[BranchTCommons](callContext))        
  }
          
  messageDocs += getBranchesDoc
  def getBranchesDoc = MessageDoc(
    process = "obp.getBranches",
    messageFormat = messageFormat,
    description = "Get Branches",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetBranches(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate=fromDateExample.value,
      toDate=toDateExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetBranches(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( BranchTCommons(branchId=BranchId(branchIdExample.value),
      bankId=BankId(bankIdExample.value),
      name=nameExample.value,
      address= Address(line1=line1Example.value,
      line2=line2Example.value,
      line3=line3Example.value,
      city=cityExample.value,
      county=Some(countyExample.value),
      state=stateExample.value,
      postCode=postCodeExample.value,
      countryCode=countryCodeExample.value),
      location= Location(latitude=latitudeExample.value.toDouble,
      longitude=longitudeExample.value.toDouble,
      date=Some(toDate(dateExample)),
      user=Some( BasicResourceUser(userId=userIdExample.value,
      provider=providerExample.value,
      username=usernameExample.value))),
      lobbyString=Some(LobbyString("string")),
      driveUpString=Some(DriveUpString("string")),
      meta=Meta( License(id=idExample.value,
      name=nameExample.value)),
      branchRouting=Some( Routing(scheme=branchRoutingSchemeExample.value,
      address=branchRoutingAddressExample.value)),
      lobby=Some( Lobby(monday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      tuesday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      wednesday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      thursday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      friday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      saturday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)),
      sunday=List( OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value)))),
      driveUp=Some( DriveUp(monday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      tuesday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      wednesday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      thursday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      friday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      saturday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value),
      sunday= OpeningTimes(openingTime=openingTimeExample.value,
      closingTime=closingTimeExample.value))),
      isAccessible=Some(isAccessibleExample.value.toBoolean),
      accessibleFeatures=Some("string"),
      branchType=Some(branchTypeExample.value),
      moreInfo=Some(moreInfoExample.value),
      phoneNumber=Some(phoneNumberExample.value),
      isDeleted=Some(true))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBranches(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[BranchT], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetBranches => InBound, OutBoundGetBranches => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_branches", req, callContext)
        response.map(convertToTuple[List[BranchTCommons]](callContext))        
  }
          
  messageDocs += getAtmDoc
  def getAtmDoc = MessageDoc(
    process = "obp.getAtm",
    messageFormat = messageFormat,
    description = "Get Atm",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAtm(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      atmId=AtmId(atmIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetAtm(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= AtmTCommons(atmId=AtmId(atmIdExample.value),
      bankId=BankId(bankIdExample.value),
      name=nameExample.value,
      address= Address(line1=line1Example.value,
      line2=line2Example.value,
      line3=line3Example.value,
      city=cityExample.value,
      county=Some(countyExample.value),
      state=stateExample.value,
      postCode=postCodeExample.value,
      countryCode=countryCodeExample.value),
      location= Location(latitude=latitudeExample.value.toDouble,
      longitude=longitudeExample.value.toDouble,
      date=Some(toDate(dateExample)),
      user=Some( BasicResourceUser(userId=userIdExample.value,
      provider=providerExample.value,
      username=usernameExample.value))),
      meta=Meta( License(id=idExample.value,
      name=nameExample.value)),
      OpeningTimeOnMonday=Some("string"),
      ClosingTimeOnMonday=Some("string"),
      OpeningTimeOnTuesday=Some("string"),
      ClosingTimeOnTuesday=Some("string"),
      OpeningTimeOnWednesday=Some("string"),
      ClosingTimeOnWednesday=Some("string"),
      OpeningTimeOnThursday=Some("string"),
      ClosingTimeOnThursday=Some("string"),
      OpeningTimeOnFriday=Some("string"),
      ClosingTimeOnFriday=Some("string"),
      OpeningTimeOnSaturday=Some("string"),
      ClosingTimeOnSaturday=Some("string"),
      OpeningTimeOnSunday=Some("string"),
      ClosingTimeOnSunday=Some("string"),
      isAccessible=Some(isAccessibleExample.value.toBoolean),
      locatedAt=Some(locatedAtExample.value),
      moreInfo=Some(moreInfoExample.value),
      hasDepositCapability=Some(hasDepositCapabilityExample.value.toBoolean)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getAtm(bankId: BankId, atmId: AtmId, callContext: Option[CallContext]): Future[Box[(AtmT, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetAtm => InBound, OutBoundGetAtm => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, atmId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_atm", req, callContext)
        response.map(convertToTuple[AtmTCommons](callContext))        
  }
          
  messageDocs += getAtmsDoc
  def getAtmsDoc = MessageDoc(
    process = "obp.getAtms",
    messageFormat = messageFormat,
    description = "Get Atms",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAtms(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate=fromDateExample.value,
      toDate=toDateExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetAtms(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( AtmTCommons(atmId=AtmId(atmIdExample.value),
      bankId=BankId(bankIdExample.value),
      name=nameExample.value,
      address= Address(line1=line1Example.value,
      line2=line2Example.value,
      line3=line3Example.value,
      city=cityExample.value,
      county=Some(countyExample.value),
      state=stateExample.value,
      postCode=postCodeExample.value,
      countryCode=countryCodeExample.value),
      location= Location(latitude=latitudeExample.value.toDouble,
      longitude=longitudeExample.value.toDouble,
      date=Some(toDate(dateExample)),
      user=Some( BasicResourceUser(userId=userIdExample.value,
      provider=providerExample.value,
      username=usernameExample.value))),
      meta=Meta( License(id=idExample.value,
      name=nameExample.value)),
      OpeningTimeOnMonday=Some("string"),
      ClosingTimeOnMonday=Some("string"),
      OpeningTimeOnTuesday=Some("string"),
      ClosingTimeOnTuesday=Some("string"),
      OpeningTimeOnWednesday=Some("string"),
      ClosingTimeOnWednesday=Some("string"),
      OpeningTimeOnThursday=Some("string"),
      ClosingTimeOnThursday=Some("string"),
      OpeningTimeOnFriday=Some("string"),
      ClosingTimeOnFriday=Some("string"),
      OpeningTimeOnSaturday=Some("string"),
      ClosingTimeOnSaturday=Some("string"),
      OpeningTimeOnSunday=Some("string"),
      ClosingTimeOnSunday=Some("string"),
      isAccessible=Some(isAccessibleExample.value.toBoolean),
      locatedAt=Some(locatedAtExample.value),
      moreInfo=Some(moreInfoExample.value),
      hasDepositCapability=Some(hasDepositCapabilityExample.value.toBoolean))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getAtms(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[(List[AtmT], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetAtms => InBound, OutBoundGetAtms => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_atms", req, callContext)
        response.map(convertToTuple[List[AtmTCommons]](callContext))        
  }
          
  messageDocs += getCurrentFxRateDoc
  def getCurrentFxRateDoc = MessageDoc(
    process = "obp.getCurrentFxRate",
    messageFormat = messageFormat,
    description = "Get Current Fx Rate",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCurrentFxRate(bankId=BankId(bankIdExample.value),
      fromCurrencyCode=fromCurrencyCodeExample.value,
      toCurrencyCode=toCurrencyCodeExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCurrentFxRate(status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= FXRateCommons(bankId=BankId(bankIdExample.value),
      fromCurrencyCode=fromCurrencyCodeExample.value,
      toCurrencyCode=toCurrencyCodeExample.value,
      conversionValue=conversionValueExample.value.toDouble,
      inverseConversionValue=inverseConversionValueExample.value.toDouble,
      effectiveDate=toDate(effectiveDateExample)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = {
        import com.openbankproject.commons.dto.{InBoundGetCurrentFxRate => InBound, OutBoundGetCurrentFxRate => OutBound}  
        val callContext: Option[CallContext] = None
        val req = OutBound(bankId, fromCurrencyCode, toCurrencyCode)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_current_fx_rate", req, callContext)
        response.map(convertToTuple[FXRateCommons](callContext))        
  }
          
  messageDocs += createTransactionAfterChallengev300Doc
  def createTransactionAfterChallengev300Doc = MessageDoc(
    process = "obp.createTransactionAfterChallengev300",
    messageFormat = messageFormat,
    description = "Create Transaction After Challengev300",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionAfterChallengev300(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      transReqId=TransactionRequestId(transactionRequestIdExample.value),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value))
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionAfterChallengev300(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= TransactionRequest(id=TransactionRequestId(transactionRequestIdExample.value),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value)),
      to_sepa=Some(TransactionRequestIban(transactionRequestIban.value)),
      to_counterparty=Some(TransactionRequestCounterpartyId(transactionRequestCounterpartyIdExample.value)),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to=ToAccountTransferToPhone(toExample.value))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`=typeExample.value,
      number=numberExample.value)))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name=nameExample.value,
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      transaction_ids="string",
      status=statusExample.value,
      start_date=toDate(transactionRequestStartDateExample),
      end_date=toDate(transactionRequestEndDateExample),
      challenge= TransactionRequestChallenge(id=challengeIdExample.value,
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary=summaryExample.value,
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value)),
      charge_policy="string",
      counterparty_id=CounterpartyId(transactionRequestCounterpartyIdExample.value),
      name=nameExample.value,
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createTransactionAfterChallengev300(initiator: User, fromAccount: BankAccount, transReqId: TransactionRequestId, transactionRequestType: TransactionRequestType, callContext: Option[CallContext]): OBPReturnType[Box[TransactionRequest]] = {
        import com.openbankproject.commons.dto.{InBoundCreateTransactionAfterChallengev300 => InBound, OutBoundCreateTransactionAfterChallengev300 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, initiator, fromAccount, transReqId, transactionRequestType)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_transaction_after_challengev300", req, callContext)
        response.map(convertToTuple[TransactionRequest](callContext))        
  }
          
  messageDocs += makePaymentv300Doc
  def makePaymentv300Doc = MessageDoc(
    process = "obp.makePaymentv300",
    messageFormat = messageFormat,
    description = "Make Paymentv300",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundMakePaymentv300(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      toCounterparty= CounterpartyTraitCommons(createdByUserId=createdByUserIdExample.value,
      name=counterpartyNameExample.value,
      description=descriptionExample.value,
      currency=currencyExample.value,
      thisBankId=thisBankIdExample.value,
      thisAccountId=thisAccountIdExample.value,
      thisViewId=thisViewIdExample.value,
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=counterpartyOtherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=counterpartyOtherAccountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme=counterpartyOtherAccountSecondaryRoutingSchemeExample.value,
      otherAccountSecondaryRoutingAddress=counterpartyOtherAccountSecondaryRoutingAddressExample.value,
      otherBankRoutingScheme=counterpartyOtherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=counterpartyOtherBankRoutingAddressExample.value,
      otherBranchRoutingScheme=counterpartyOtherBranchRoutingSchemeExample.value,
      otherBranchRoutingAddress=counterpartyOtherBranchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      chargePolicy=chargePolicyExample.value)
    ),
    exampleInboundMessage = (
     InBoundMakePaymentv300(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=TransactionId(transactionIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def makePaymentv300(initiator: User, fromAccount: BankAccount, toAccount: BankAccount, toCounterparty: CounterpartyTrait, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, transactionRequestType: TransactionRequestType, chargePolicy: String, callContext: Option[CallContext]): Future[Box[(TransactionId, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundMakePaymentv300 => InBound, OutBoundMakePaymentv300 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, initiator, fromAccount, toAccount, toCounterparty, transactionRequestCommonBody, transactionRequestType, chargePolicy)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_make_paymentv300", req, callContext)
        response.map(convertToTuple[TransactionId](callContext))        
  }
          
  messageDocs += createTransactionRequestv300Doc
  def createTransactionRequestv300Doc = MessageDoc(
    process = "obp.createTransactionRequestv300",
    messageFormat = messageFormat,
    description = "Create Transaction Requestv300",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTransactionRequestv300(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      initiator= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      viewId=ViewId(viewIdExample.value),
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      toCounterparty= CounterpartyTraitCommons(createdByUserId=createdByUserIdExample.value,
      name=counterpartyNameExample.value,
      description=descriptionExample.value,
      currency=currencyExample.value,
      thisBankId=thisBankIdExample.value,
      thisAccountId=thisAccountIdExample.value,
      thisViewId=thisViewIdExample.value,
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=counterpartyOtherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=counterpartyOtherAccountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme=counterpartyOtherAccountSecondaryRoutingSchemeExample.value,
      otherAccountSecondaryRoutingAddress=counterpartyOtherAccountSecondaryRoutingAddressExample.value,
      otherBankRoutingScheme=counterpartyOtherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=counterpartyOtherBankRoutingAddressExample.value,
      otherBranchRoutingScheme=counterpartyOtherBranchRoutingSchemeExample.value,
      otherBranchRoutingAddress=counterpartyOtherBranchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))),
      transactionRequestType=TransactionRequestType(transactionRequestTypeExample.value),
      transactionRequestCommonBody= TransactionRequestCommonBodyJSONCommons(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      detailsPlain="string",
      chargePolicy=chargePolicyExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateTransactionRequestv300(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= TransactionRequest(id=TransactionRequestId(transactionRequestIdExample.value),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value)),
      to_sepa=Some(TransactionRequestIban(transactionRequestIban.value)),
      to_counterparty=Some(TransactionRequestCounterpartyId(transactionRequestCounterpartyIdExample.value)),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to=ToAccountTransferToPhone(toExample.value))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`=typeExample.value,
      number=numberExample.value)))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name=nameExample.value,
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      transaction_ids="string",
      status=statusExample.value,
      start_date=toDate(transactionRequestStartDateExample),
      end_date=toDate(transactionRequestEndDateExample),
      challenge= TransactionRequestChallenge(id=challengeIdExample.value,
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary=summaryExample.value,
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value)),
      charge_policy="string",
      counterparty_id=CounterpartyId(transactionRequestCounterpartyIdExample.value),
      name=nameExample.value,
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createTransactionRequestv300(initiator: User, viewId: ViewId, fromAccount: BankAccount, toAccount: BankAccount, toCounterparty: CounterpartyTrait, transactionRequestType: TransactionRequestType, transactionRequestCommonBody: TransactionRequestCommonBodyJSON, detailsPlain: String, chargePolicy: String, callContext: Option[CallContext]): Future[Box[(TransactionRequest, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundCreateTransactionRequestv300 => InBound, OutBoundCreateTransactionRequestv300 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, initiator, viewId, fromAccount, toAccount, toCounterparty, transactionRequestType, transactionRequestCommonBody, detailsPlain, chargePolicy)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_transaction_requestv300", req, callContext)
        response.map(convertToTuple[TransactionRequest](callContext))        
  }
          
  messageDocs += makePaymentV400Doc
  def makePaymentV400Doc = MessageDoc(
    process = "obp.makePaymentV400",
    messageFormat = messageFormat,
    description = "Make Payment V400",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundMakePaymentV400(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      transactionRequest= TransactionRequest(id=TransactionRequestId(transactionRequestIdExample.value),
      `type`=transactionRequestTypeExample.value,
      from= TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value),
      body= TransactionRequestBodyAllTypes(to_sandbox_tan=Some( TransactionRequestAccount(bank_id=bank_idExample.value,
      account_id=account_idExample.value)),
      to_sepa=Some(TransactionRequestIban(transactionRequestIban.value)),
      to_counterparty=Some(TransactionRequestCounterpartyId(transactionRequestCounterpartyIdExample.value)),
      to_transfer_to_phone=Some( TransactionRequestTransferToPhone(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to=ToAccountTransferToPhone(toExample.value))),
      to_transfer_to_atm=Some( TransactionRequestTransferToAtm(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      message=messageExample.value,
      from= FromAccountTransfer(mobile_phone_number="string",
      nickname=nicknameExample.value),
      to= ToAccountTransferToAtm(legal_name="string",
      date_of_birth="string",
      mobile_phone_number="string",
      kyc_document= ToAccountTransferToAtmKycDocument(`type`=typeExample.value,
      number=numberExample.value)))),
      to_transfer_to_account=Some( TransactionRequestTransferToAccount(value= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value,
      transfer_type="string",
      future_date="string",
      to= ToAccountTransferToAccount(name=nameExample.value,
      bank_code="string",
      branch_number="string",
      account= ToAccountTransferToAccountAccount(number=accountNumberExample.value,
      iban=ibanExample.value)))),
      to_sepa_credit_transfers=Some( SepaCreditTransfers(debtorAccount=PaymentAccount("string"),
      instructedAmount= AmountOfMoneyJsonV121(currency=currencyExample.value,
      amount=amountExample.value),
      creditorAccount=PaymentAccount("string"),
      creditorName="string")),
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value),
      description=descriptionExample.value),
      transaction_ids="string",
      status=statusExample.value,
      start_date=toDate(transactionRequestStartDateExample),
      end_date=toDate(transactionRequestEndDateExample),
      challenge= TransactionRequestChallenge(id=challengeIdExample.value,
      allowed_attempts=123,
      challenge_type="string"),
      charge= TransactionRequestCharge(summary=summaryExample.value,
      value= AmountOfMoney(currency=currencyExample.value,
      amount=amountExample.value)),
      charge_policy="string",
      counterparty_id=CounterpartyId(transactionRequestCounterpartyIdExample.value),
      name=nameExample.value,
      this_bank_id=BankId(bankIdExample.value),
      this_account_id=AccountId(accountIdExample.value),
      this_view_id=ViewId(viewIdExample.value),
      other_account_routing_scheme="string",
      other_account_routing_address="string",
      other_bank_routing_scheme="string",
      other_bank_routing_address="string",
      is_beneficiary=true,
      future_date=Some("string")),
      reasons=Some(List( TransactionRequestReason(code=codeExample.value,
      documentNumber=Some(documentNumberExample.value),
      amount=Some(amountExample.value),
      currency=Some(currencyExample.value),
      description=Some(descriptionExample.value)))))
    ),
    exampleInboundMessage = (
     InBoundMakePaymentV400(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=TransactionId(transactionIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def makePaymentV400(transactionRequest: TransactionRequest, reasons: Option[List[TransactionRequestReason]], callContext: Option[CallContext]): Future[Box[(TransactionId, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundMakePaymentV400 => InBound, OutBoundMakePaymentV400 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, transactionRequest, reasons)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_make_payment_v400", req, callContext)
        response.map(convertToTuple[TransactionId](callContext))        
  }
          
  messageDocs += cancelPaymentV400Doc
  def cancelPaymentV400Doc = MessageDoc(
    process = "obp.cancelPaymentV400",
    messageFormat = messageFormat,
    description = "Cancel Payment V400",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCancelPaymentV400(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      transactionId=TransactionId(transactionIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundCancelPaymentV400(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CancelPayment(canBeCancelled=true,
      startSca=Some(true)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def cancelPaymentV400(transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[CancelPayment]] = {
        import com.openbankproject.commons.dto.{InBoundCancelPaymentV400 => InBound, OutBoundCancelPaymentV400 => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, transactionId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_cancel_payment_v400", req, callContext)
        response.map(convertToTuple[CancelPayment](callContext))        
  }
          
  messageDocs += createCounterpartyDoc
  def createCounterpartyDoc = MessageDoc(
    process = "obp.createCounterparty",
    messageFormat = messageFormat,
    description = "Create Counterparty",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateCounterparty(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      name=nameExample.value,
      description=descriptionExample.value,
      currency=currencyExample.value,
      createdByUserId=createdByUserIdExample.value,
      thisBankId=thisBankIdExample.value,
      thisAccountId=thisAccountIdExample.value,
      thisViewId=thisViewIdExample.value,
      otherAccountRoutingScheme=otherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=otherAccountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme=otherAccountSecondaryRoutingSchemeExample.value,
      otherAccountSecondaryRoutingAddress=otherAccountSecondaryRoutingAddressExample.value,
      otherBankRoutingScheme=otherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=otherBankRoutingAddressExample.value,
      otherBranchRoutingScheme=otherBranchRoutingSchemeExample.value,
      otherBranchRoutingAddress=otherBranchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value)))
    ),
    exampleInboundMessage = (
     InBoundCreateCounterparty(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CounterpartyTraitCommons(createdByUserId=createdByUserIdExample.value,
      name=nameExample.value,
      description=descriptionExample.value,
      currency=currencyExample.value,
      thisBankId=thisBankIdExample.value,
      thisAccountId=thisAccountIdExample.value,
      thisViewId=thisViewIdExample.value,
      counterpartyId=counterpartyIdExample.value,
      otherAccountRoutingScheme=otherAccountRoutingSchemeExample.value,
      otherAccountRoutingAddress=otherAccountRoutingAddressExample.value,
      otherAccountSecondaryRoutingScheme=otherAccountSecondaryRoutingSchemeExample.value,
      otherAccountSecondaryRoutingAddress=otherAccountSecondaryRoutingAddressExample.value,
      otherBankRoutingScheme=otherBankRoutingSchemeExample.value,
      otherBankRoutingAddress=otherBankRoutingAddressExample.value,
      otherBranchRoutingScheme=otherBranchRoutingSchemeExample.value,
      otherBranchRoutingAddress=otherBranchRoutingAddressExample.value,
      isBeneficiary=isBeneficiaryExample.value.toBoolean,
      bespoke=List( CounterpartyBespoke(key=keyExample.value,
      value=valueExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createCounterparty(name: String, description: String, currency: String, createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherAccountSecondaryRoutingScheme: String, otherAccountSecondaryRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, otherBranchRoutingScheme: String, otherBranchRoutingAddress: String, isBeneficiary: Boolean, bespoke: List[CounterpartyBespoke], callContext: Option[CallContext]): Box[(CounterpartyTrait, Option[CallContext])] = {
        import com.openbankproject.commons.dto.{InBoundCreateCounterparty => InBound, OutBoundCreateCounterparty => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, name, description, currency, createdByUserId, thisBankId, thisAccountId, thisViewId, otherAccountRoutingScheme, otherAccountRoutingAddress, otherAccountSecondaryRoutingScheme, otherAccountSecondaryRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress, otherBranchRoutingScheme, otherBranchRoutingAddress, isBeneficiary, bespoke)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_counterparty", req, callContext)
        response.map(convertToTuple[CounterpartyTraitCommons](callContext))        
  }
          
  messageDocs += checkCustomerNumberAvailableDoc
  def checkCustomerNumberAvailableDoc = MessageDoc(
    process = "obp.checkCustomerNumberAvailable",
    messageFormat = messageFormat,
    description = "Check Customer Number Available",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCheckCustomerNumberAvailable(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      customerNumber=customerNumberExample.value)
    ),
    exampleInboundMessage = (
     InBoundCheckCustomerNumberAvailable(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def checkCustomerNumberAvailable(bankId: BankId, customerNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{InBoundCheckCustomerNumberAvailable => InBound, OutBoundCheckCustomerNumberAvailable => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, customerNumber)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_check_customer_number_available", req, callContext)
        response.map(convertToTuple[Boolean](callContext))        
  }
          
  messageDocs += createCustomerDoc
  def createCustomerDoc = MessageDoc(
    process = "obp.createCustomer",
    messageFormat = messageFormat,
    description = "Create Customer",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateCustomer(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(outBoundCreateCustomerLastOkDateExample),
      creditRating=Some( CreditRating(rating=ratingExample.value,
      source=sourceExample.value)),
      creditLimit=Some( AmountOfMoney(currency=currencyExample.value,
      amount=creditLimitAmountExample.value)),
      title=titleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateCustomer(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(customerLastOkDateExample),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createCustomer(bankId: BankId, legalName: String, mobileNumber: String, email: String, faceImage: CustomerFaceImageTrait, dateOfBirth: Date, relationshipStatus: String, dependents: Int, dobOfDependents: List[Date], highestEducationAttained: String, employmentStatus: String, kycStatus: Boolean, lastOkDate: Date, creditRating: Option[CreditRatingTrait], creditLimit: Option[AmountOfMoneyTrait], title: String, branchId: String, nameSuffix: String, callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{InBoundCreateCustomer => InBound, OutBoundCreateCustomer => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, legalName, mobileNumber, email, faceImage, dateOfBirth, relationshipStatus, dependents, dobOfDependents, highestEducationAttained, employmentStatus, kycStatus, lastOkDate, creditRating, creditLimit, title, branchId, nameSuffix)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_customer", req, callContext)
        response.map(convertToTuple[CustomerCommons](callContext))        
  }
          
  messageDocs += updateCustomerScaDataDoc
  def updateCustomerScaDataDoc = MessageDoc(
    process = "obp.updateCustomerScaData",
    messageFormat = messageFormat,
    description = "Update Customer Sca Data",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateCustomerScaData(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value,
      mobileNumber=Some(mobileNumberExample.value),
      email=Some(emailExample.value),
      customerNumber=Some(customerNumberExample.value))
    ),
    exampleInboundMessage = (
     InBoundUpdateCustomerScaData(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(customerLastOkDateExample),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def updateCustomerScaData(customerId: String, mobileNumber: Option[String], email: Option[String], customerNumber: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{InBoundUpdateCustomerScaData => InBound, OutBoundUpdateCustomerScaData => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId, mobileNumber, email, customerNumber)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_update_customer_sca_data", req, callContext)
        response.map(convertToTuple[CustomerCommons](callContext))        
  }
          
  messageDocs += updateCustomerCreditDataDoc
  def updateCustomerCreditDataDoc = MessageDoc(
    process = "obp.updateCustomerCreditData",
    messageFormat = messageFormat,
    description = "Update Customer Credit Data",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateCustomerCreditData(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value,
      creditRating=Some(creditRatingExample.value),
      creditSource=Some("string"),
      creditLimit=Some( AmountOfMoney(currency=currencyExample.value,
      amount=creditLimitAmountExample.value)))
    ),
    exampleInboundMessage = (
     InBoundUpdateCustomerCreditData(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(customerLastOkDateExample),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def updateCustomerCreditData(customerId: String, creditRating: Option[String], creditSource: Option[String], creditLimit: Option[AmountOfMoney], callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{InBoundUpdateCustomerCreditData => InBound, OutBoundUpdateCustomerCreditData => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId, creditRating, creditSource, creditLimit)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_update_customer_credit_data", req, callContext)
        response.map(convertToTuple[CustomerCommons](callContext))        
  }
          
  messageDocs += updateCustomerGeneralDataDoc
  def updateCustomerGeneralDataDoc = MessageDoc(
    process = "obp.updateCustomerGeneralData",
    messageFormat = messageFormat,
    description = "Update Customer General Data",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateCustomerGeneralData(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value,
      legalName=Some(legalNameExample.value),
      faceImage=Some( CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value)),
      dateOfBirth=Some(toDate(dateOfBirthExample)),
      relationshipStatus=Some(relationshipStatusExample.value),
      dependents=Some(dependentsExample.value.toInt),
      highestEducationAttained=Some(highestEducationAttainedExample.value),
      employmentStatus=Some(employmentStatusExample.value),
      title=Some(titleExample.value),
      branchId=Some(branchIdExample.value),
      nameSuffix=Some(nameSuffixExample.value))
    ),
    exampleInboundMessage = (
     InBoundUpdateCustomerGeneralData(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(customerLastOkDateExample),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def updateCustomerGeneralData(customerId: String, legalName: Option[String], faceImage: Option[CustomerFaceImageTrait], dateOfBirth: Option[Date], relationshipStatus: Option[String], dependents: Option[Int], highestEducationAttained: Option[String], employmentStatus: Option[String], title: Option[String], branchId: Option[String], nameSuffix: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
        import com.openbankproject.commons.dto.{InBoundUpdateCustomerGeneralData => InBound, OutBoundUpdateCustomerGeneralData => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId, legalName, faceImage, dateOfBirth, relationshipStatus, dependents, highestEducationAttained, employmentStatus, title, branchId, nameSuffix)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_update_customer_general_data", req, callContext)
        response.map(convertToTuple[CustomerCommons](callContext))        
  }
          
  messageDocs += getCustomersByUserIdDoc
  def getCustomersByUserIdDoc = MessageDoc(
    process = "obp.getCustomersByUserId",
    messageFormat = messageFormat,
    description = "Get Customers By User Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomersByUserId(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      userId=userIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCustomersByUserId(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(customerLastOkDateExample),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCustomersByUserId(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetCustomersByUserId => InBound, OutBoundGetCustomersByUserId => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, userId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_customers_by_user_id", req, callContext)
        response.map(convertToTuple[List[CustomerCommons]](callContext))        
  }
          
  messageDocs += getCustomerByCustomerIdDoc
  def getCustomerByCustomerIdDoc = MessageDoc(
    process = "obp.getCustomerByCustomerId",
    messageFormat = messageFormat,
    description = "Get Customer By Customer Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerByCustomerId(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCustomerByCustomerId(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(customerLastOkDateExample),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCustomerByCustomerId(customerId: String, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetCustomerByCustomerId => InBound, OutBoundGetCustomerByCustomerId => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_customer_by_customer_id", req, callContext)
        response.map(convertToTuple[CustomerCommons](callContext))        
  }
          
  messageDocs += getCustomerByCustomerNumberDoc
  def getCustomerByCustomerNumberDoc = MessageDoc(
    process = "obp.getCustomerByCustomerNumber",
    messageFormat = messageFormat,
    description = "Get Customer By Customer Number",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerByCustomerNumber(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerNumber=customerNumberExample.value,
      bankId=BankId(bankIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetCustomerByCustomerNumber(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(customerLastOkDateExample),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCustomerByCustomerNumber(customerNumber: String, bankId: BankId, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetCustomerByCustomerNumber => InBound, OutBoundGetCustomerByCustomerNumber => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerNumber, bankId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_customer_by_customer_number", req, callContext)
        response.map(convertToTuple[CustomerCommons](callContext))        
  }
          
  messageDocs += getCustomerAddressDoc
  def getCustomerAddressDoc = MessageDoc(
    process = "obp.getCustomerAddress",
    messageFormat = messageFormat,
    description = "Get Customer Address",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerAddress(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCustomerAddress(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( CustomerAddressCommons(customerId=customerIdExample.value,
      customerAddressId=customerAddressIdExample.value,
      line1=line1Example.value,
      line2=line2Example.value,
      line3=line3Example.value,
      city=cityExample.value,
      county=countyExample.value,
      state=stateExample.value,
      postcode=postcodeExample.value,
      countryCode=countryCodeExample.value,
      status=statusExample.value,
      tags=tagsExample.value,
      insertDate=toDate(insertDateExample))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCustomerAddress(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAddress]]] = {
        import com.openbankproject.commons.dto.{InBoundGetCustomerAddress => InBound, OutBoundGetCustomerAddress => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_customer_address", req, callContext)
        response.map(convertToTuple[List[CustomerAddressCommons]](callContext))        
  }
          
  messageDocs += createCustomerAddressDoc
  def createCustomerAddressDoc = MessageDoc(
    process = "obp.createCustomerAddress",
    messageFormat = messageFormat,
    description = "Create Customer Address",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateCustomerAddress(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value,
      line1=line1Example.value,
      line2=line2Example.value,
      line3=line3Example.value,
      city=cityExample.value,
      county=countyExample.value,
      state=stateExample.value,
      postcode=postcodeExample.value,
      countryCode=countryCodeExample.value,
      tags=tagsExample.value,
      status=statusExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateCustomerAddress(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerAddressCommons(customerId=customerIdExample.value,
      customerAddressId=customerAddressIdExample.value,
      line1=line1Example.value,
      line2=line2Example.value,
      line3=line3Example.value,
      city=cityExample.value,
      county=countyExample.value,
      state=stateExample.value,
      postcode=postcodeExample.value,
      countryCode=countryCodeExample.value,
      status=statusExample.value,
      tags=tagsExample.value,
      insertDate=toDate(insertDateExample)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createCustomerAddress(customerId: String, line1: String, line2: String, line3: String, city: String, county: String, state: String, postcode: String, countryCode: String, tags: String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = {
        import com.openbankproject.commons.dto.{InBoundCreateCustomerAddress => InBound, OutBoundCreateCustomerAddress => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId, line1, line2, line3, city, county, state, postcode, countryCode, tags, status)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_customer_address", req, callContext)
        response.map(convertToTuple[CustomerAddressCommons](callContext))        
  }
          
  messageDocs += updateCustomerAddressDoc
  def updateCustomerAddressDoc = MessageDoc(
    process = "obp.updateCustomerAddress",
    messageFormat = messageFormat,
    description = "Update Customer Address",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateCustomerAddress(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerAddressId=customerAddressIdExample.value,
      line1=line1Example.value,
      line2=line2Example.value,
      line3=line3Example.value,
      city=cityExample.value,
      county=countyExample.value,
      state=stateExample.value,
      postcode=postcodeExample.value,
      countryCode=countryCodeExample.value,
      tags=tagsExample.value,
      status=statusExample.value)
    ),
    exampleInboundMessage = (
     InBoundUpdateCustomerAddress(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerAddressCommons(customerId=customerIdExample.value,
      customerAddressId=customerAddressIdExample.value,
      line1=line1Example.value,
      line2=line2Example.value,
      line3=line3Example.value,
      city=cityExample.value,
      county=countyExample.value,
      state=stateExample.value,
      postcode=postcodeExample.value,
      countryCode=countryCodeExample.value,
      status=statusExample.value,
      tags=tagsExample.value,
      insertDate=toDate(insertDateExample)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def updateCustomerAddress(customerAddressId: String, line1: String, line2: String, line3: String, city: String, county: String, state: String, postcode: String, countryCode: String, tags: String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAddress]] = {
        import com.openbankproject.commons.dto.{InBoundUpdateCustomerAddress => InBound, OutBoundUpdateCustomerAddress => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerAddressId, line1, line2, line3, city, county, state, postcode, countryCode, tags, status)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_update_customer_address", req, callContext)
        response.map(convertToTuple[CustomerAddressCommons](callContext))        
  }
          
  messageDocs += deleteCustomerAddressDoc
  def deleteCustomerAddressDoc = MessageDoc(
    process = "obp.deleteCustomerAddress",
    messageFormat = messageFormat,
    description = "Delete Customer Address",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteCustomerAddress(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerAddressId=customerAddressIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundDeleteCustomerAddress(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def deleteCustomerAddress(customerAddressId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{InBoundDeleteCustomerAddress => InBound, OutBoundDeleteCustomerAddress => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerAddressId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_delete_customer_address", req, callContext)
        response.map(convertToTuple[Boolean](callContext))        
  }
          
  messageDocs += createTaxResidenceDoc
  def createTaxResidenceDoc = MessageDoc(
    process = "obp.createTaxResidence",
    messageFormat = messageFormat,
    description = "Create Tax Residence",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateTaxResidence(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value,
      domain=domainExample.value,
      taxNumber=taxNumberExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateTaxResidence(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= TaxResidenceCommons(customerId=customerIdExample.value,
      taxResidenceId=taxResidenceIdExample.value,
      domain=domainExample.value,
      taxNumber=taxNumberExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createTaxResidence(customerId: String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[TaxResidence]] = {
        import com.openbankproject.commons.dto.{InBoundCreateTaxResidence => InBound, OutBoundCreateTaxResidence => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId, domain, taxNumber)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_tax_residence", req, callContext)
        response.map(convertToTuple[TaxResidenceCommons](callContext))        
  }
          
  messageDocs += getTaxResidenceDoc
  def getTaxResidenceDoc = MessageDoc(
    process = "obp.getTaxResidence",
    messageFormat = messageFormat,
    description = "Get Tax Residence",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTaxResidence(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetTaxResidence(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( TaxResidenceCommons(customerId=customerIdExample.value,
      taxResidenceId=taxResidenceIdExample.value,
      domain=domainExample.value,
      taxNumber=taxNumberExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getTaxResidence(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[TaxResidence]]] = {
        import com.openbankproject.commons.dto.{InBoundGetTaxResidence => InBound, OutBoundGetTaxResidence => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_tax_residence", req, callContext)
        response.map(convertToTuple[List[TaxResidenceCommons]](callContext))        
  }
          
  messageDocs += deleteTaxResidenceDoc
  def deleteTaxResidenceDoc = MessageDoc(
    process = "obp.deleteTaxResidence",
    messageFormat = messageFormat,
    description = "Delete Tax Residence",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteTaxResidence(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      taxResourceId="string")
    ),
    exampleInboundMessage = (
     InBoundDeleteTaxResidence(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def deleteTaxResidence(taxResourceId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{InBoundDeleteTaxResidence => InBound, OutBoundDeleteTaxResidence => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, taxResourceId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_delete_tax_residence", req, callContext)
        response.map(convertToTuple[Boolean](callContext))        
  }
          
  messageDocs += getCustomersDoc
  def getCustomersDoc = MessageDoc(
    process = "obp.getCustomers",
    messageFormat = messageFormat,
    description = "Get Customers",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomers(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      limit=limitExample.value.toInt,
      offset=offsetExample.value.toInt,
      fromDate=fromDateExample.value,
      toDate=toDateExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCustomers(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(customerLastOkDateExample),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCustomers(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]] = {
        import com.openbankproject.commons.dto.{InBoundGetCustomers => InBound, OutBoundGetCustomers => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, OBPQueryParam.getLimit(queryParams), OBPQueryParam.getOffset(queryParams), OBPQueryParam.getFromDate(queryParams), OBPQueryParam.getToDate(queryParams))
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_customers", req, callContext)
        response.map(convertToTuple[List[CustomerCommons]](callContext))        
  }
          
  messageDocs += getCustomersByCustomerPhoneNumberDoc
  def getCustomersByCustomerPhoneNumberDoc = MessageDoc(
    process = "obp.getCustomersByCustomerPhoneNumber",
    messageFormat = messageFormat,
    description = "Get Customers By Customer Phone Number",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomersByCustomerPhoneNumber(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      phoneNumber=phoneNumberExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCustomersByCustomerPhoneNumber(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(customerLastOkDateExample),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCustomersByCustomerPhoneNumber(bankId: BankId, phoneNumber: String, callContext: Option[CallContext]): OBPReturnType[Box[List[Customer]]] = {
        import com.openbankproject.commons.dto.{InBoundGetCustomersByCustomerPhoneNumber => InBound, OutBoundGetCustomersByCustomerPhoneNumber => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, phoneNumber)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_customers_by_customer_phone_number", req, callContext)
        response.map(convertToTuple[List[CustomerCommons]](callContext))        
  }
          
  messageDocs += getCheckbookOrdersDoc
  def getCheckbookOrdersDoc = MessageDoc(
    process = "obp.getCheckbookOrders",
    messageFormat = messageFormat,
    description = "Get Checkbook Orders",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCheckbookOrders(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=bankIdExample.value,
      accountId=accountIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCheckbookOrders(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CheckbookOrdersJson(account= AccountV310Json(bank_id=bank_idExample.value,
      account_id=account_idExample.value,
      account_type="string",
      account_routings=List( AccountRoutingJsonV121(scheme=schemeExample.value,
      address=addressExample.value)),
      branch_routings=List( BranchRoutingJsonV141(scheme=schemeExample.value,
      address=addressExample.value))),
      orders=List(OrderJson( OrderObjectJson(order_id="string",
      order_date="string",
      number_of_checkbooks="string",
      distribution_channel="string",
      status=statusExample.value,
      first_check_number="string",
      shipping_code="string")))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCheckbookOrders(bankId: String, accountId: String, callContext: Option[CallContext]): Future[Box[(CheckbookOrdersJson, Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetCheckbookOrders => InBound, OutBoundGetCheckbookOrders => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_checkbook_orders", req, callContext)
        response.map(convertToTuple[CheckbookOrdersJson](callContext))        
  }
          
  messageDocs += getStatusOfCreditCardOrderDoc
  def getStatusOfCreditCardOrderDoc = MessageDoc(
    process = "obp.getStatusOfCreditCardOrder",
    messageFormat = messageFormat,
    description = "Get Status Of Credit Card Order",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetStatusOfCreditCardOrder(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=bankIdExample.value,
      accountId=accountIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetStatusOfCreditCardOrder(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( CardObjectJson(card_type="string",
      card_description="string",
      use_type="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getStatusOfCreditCardOrder(bankId: String, accountId: String, callContext: Option[CallContext]): Future[Box[(List[CardObjectJson], Option[CallContext])]] = {
        import com.openbankproject.commons.dto.{InBoundGetStatusOfCreditCardOrder => InBound, OutBoundGetStatusOfCreditCardOrder => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_status_of_credit_card_order", req, callContext)
        response.map(convertToTuple[List[CardObjectJson]](callContext))        
  }
          
  messageDocs += createUserAuthContextDoc
  def createUserAuthContextDoc = MessageDoc(
    process = "obp.createUserAuthContext",
    messageFormat = messageFormat,
    description = "Create User Auth Context",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateUserAuthContext(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      userId=userIdExample.value,
      key=keyExample.value,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateUserAuthContext(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= UserAuthContextCommons(userAuthContextId=userAuthContextIdExample.value,
      userId=userIdExample.value,
      key=keyExample.value,
      value=valueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createUserAuthContext(userId: String, key: String, value: String, callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContext]] = {
        import com.openbankproject.commons.dto.{InBoundCreateUserAuthContext => InBound, OutBoundCreateUserAuthContext => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, userId, key, value)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_user_auth_context", req, callContext)
        response.map(convertToTuple[UserAuthContextCommons](callContext))        
  }
          
  messageDocs += createUserAuthContextUpdateDoc
  def createUserAuthContextUpdateDoc = MessageDoc(
    process = "obp.createUserAuthContextUpdate",
    messageFormat = messageFormat,
    description = "Create User Auth Context Update",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateUserAuthContextUpdate(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      userId=userIdExample.value,
      key=keyExample.value,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateUserAuthContextUpdate(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= UserAuthContextUpdateCommons(userAuthContextUpdateId=userAuthContextUpdateIdExample.value,
      userId=userIdExample.value,
      key=keyExample.value,
      value=valueExample.value,
      challenge=challengeExample.value,
      status=statusExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createUserAuthContextUpdate(userId: String, key: String, value: String, callContext: Option[CallContext]): OBPReturnType[Box[UserAuthContextUpdate]] = {
        import com.openbankproject.commons.dto.{InBoundCreateUserAuthContextUpdate => InBound, OutBoundCreateUserAuthContextUpdate => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, userId, key, value)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_user_auth_context_update", req, callContext)
        response.map(convertToTuple[UserAuthContextUpdateCommons](callContext))        
  }
          
  messageDocs += deleteUserAuthContextsDoc
  def deleteUserAuthContextsDoc = MessageDoc(
    process = "obp.deleteUserAuthContexts",
    messageFormat = messageFormat,
    description = "Delete User Auth Contexts",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteUserAuthContexts(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      userId=userIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundDeleteUserAuthContexts(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def deleteUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{InBoundDeleteUserAuthContexts => InBound, OutBoundDeleteUserAuthContexts => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, userId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_delete_user_auth_contexts", req, callContext)
        response.map(convertToTuple[Boolean](callContext))        
  }
          
  messageDocs += deleteUserAuthContextByIdDoc
  def deleteUserAuthContextByIdDoc = MessageDoc(
    process = "obp.deleteUserAuthContextById",
    messageFormat = messageFormat,
    description = "Delete User Auth Context By Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteUserAuthContextById(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      userAuthContextId=userAuthContextIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundDeleteUserAuthContextById(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def deleteUserAuthContextById(userAuthContextId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{InBoundDeleteUserAuthContextById => InBound, OutBoundDeleteUserAuthContextById => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, userAuthContextId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_delete_user_auth_context_by_id", req, callContext)
        response.map(convertToTuple[Boolean](callContext))        
  }
          
  messageDocs += getUserAuthContextsDoc
  def getUserAuthContextsDoc = MessageDoc(
    process = "obp.getUserAuthContexts",
    messageFormat = messageFormat,
    description = "Get User Auth Contexts",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetUserAuthContexts(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      userId=userIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetUserAuthContexts(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( UserAuthContextCommons(userAuthContextId=userAuthContextIdExample.value,
      userId=userIdExample.value,
      key=keyExample.value,
      value=valueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[UserAuthContext]]] = {
        import com.openbankproject.commons.dto.{InBoundGetUserAuthContexts => InBound, OutBoundGetUserAuthContexts => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, userId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_user_auth_contexts", req, callContext)
        response.map(convertToTuple[List[UserAuthContextCommons]](callContext))        
  }
          
  messageDocs += createOrUpdateProductAttributeDoc
  def createOrUpdateProductAttributeDoc = MessageDoc(
    process = "obp.createOrUpdateProductAttribute",
    messageFormat = messageFormat,
    description = "Create Or Update Product Attribute",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateProductAttribute(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      productAttributeId=Some(productAttributeIdExample.value),
      name=nameExample.value,
      productAttributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateProductAttribute(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= ProductAttributeCommons(bankId=BankId(bankIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      productAttributeId=productAttributeIdExample.value,
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createOrUpdateProductAttribute(bankId: BankId, productCode: ProductCode, productAttributeId: Option[String], name: String, productAttributeType: ProductAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[ProductAttribute]] = {
        import com.openbankproject.commons.dto.{InBoundCreateOrUpdateProductAttribute => InBound, OutBoundCreateOrUpdateProductAttribute => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, productCode, productAttributeId, name, productAttributeType, value)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_or_update_product_attribute", req, callContext)
        response.map(convertToTuple[ProductAttributeCommons](callContext))        
  }
          
  messageDocs += getProductAttributeByIdDoc
  def getProductAttributeByIdDoc = MessageDoc(
    process = "obp.getProductAttributeById",
    messageFormat = messageFormat,
    description = "Get Product Attribute By Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProductAttributeById(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      productAttributeId=productAttributeIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetProductAttributeById(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= ProductAttributeCommons(bankId=BankId(bankIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      productAttributeId=productAttributeIdExample.value,
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getProductAttributeById(productAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[ProductAttribute]] = {
        import com.openbankproject.commons.dto.{InBoundGetProductAttributeById => InBound, OutBoundGetProductAttributeById => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, productAttributeId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_product_attribute_by_id", req, callContext)
        response.map(convertToTuple[ProductAttributeCommons](callContext))        
  }
          
  messageDocs += getProductAttributesByBankAndCodeDoc
  def getProductAttributesByBankAndCodeDoc = MessageDoc(
    process = "obp.getProductAttributesByBankAndCode",
    messageFormat = messageFormat,
    description = "Get Product Attributes By Bank And Code",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProductAttributesByBankAndCode(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bank=BankId(bankExample.value),
      productCode=ProductCode(productCodeExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetProductAttributesByBankAndCode(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( ProductAttributeCommons(bankId=BankId(bankIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      productAttributeId=productAttributeIdExample.value,
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getProductAttributesByBankAndCode(bank: BankId, productCode: ProductCode, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductAttribute]]] = {
        import com.openbankproject.commons.dto.{InBoundGetProductAttributesByBankAndCode => InBound, OutBoundGetProductAttributesByBankAndCode => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bank, productCode)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_product_attributes_by_bank_and_code", req, callContext)
        response.map(convertToTuple[List[ProductAttributeCommons]](callContext))        
  }
          
  messageDocs += deleteProductAttributeDoc
  def deleteProductAttributeDoc = MessageDoc(
    process = "obp.deleteProductAttribute",
    messageFormat = messageFormat,
    description = "Delete Product Attribute",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteProductAttribute(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      productAttributeId=productAttributeIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundDeleteProductAttribute(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def deleteProductAttribute(productAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{InBoundDeleteProductAttribute => InBound, OutBoundDeleteProductAttribute => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, productAttributeId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_delete_product_attribute", req, callContext)
        response.map(convertToTuple[Boolean](callContext))        
  }
          
  messageDocs += getAccountAttributeByIdDoc
  def getAccountAttributeByIdDoc = MessageDoc(
    process = "obp.getAccountAttributeById",
    messageFormat = messageFormat,
    description = "Get Account Attribute By Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAccountAttributeById(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      accountAttributeId=accountAttributeIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetAccountAttributeById(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= AccountAttributeCommons(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      accountAttributeId=accountAttributeIdExample.value,
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.AccountAttributeType.example,
      value=valueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountAttribute]] = {
        import com.openbankproject.commons.dto.{InBoundGetAccountAttributeById => InBound, OutBoundGetAccountAttributeById => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, accountAttributeId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_account_attribute_by_id", req, callContext)
        response.map(convertToTuple[AccountAttributeCommons](callContext))        
  }
          
  messageDocs += getTransactionAttributeByIdDoc
  def getTransactionAttributeByIdDoc = MessageDoc(
    process = "obp.getTransactionAttributeById",
    messageFormat = messageFormat,
    description = "Get Transaction Attribute By Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionAttributeById(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      transactionAttributeId=transactionAttributeIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetTransactionAttributeById(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= TransactionAttributeCommons(bankId=BankId(bankIdExample.value),
      transactionId=TransactionId(transactionIdExample.value),
      transactionAttributeId=transactionAttributeIdExample.value,
      attributeType=com.openbankproject.commons.model.enums.TransactionAttributeType.example,
      name=transactionAttributeNameExample.value,
      value=transactionAttributeValueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getTransactionAttributeById(transactionAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionAttribute]] = {
        import com.openbankproject.commons.dto.{InBoundGetTransactionAttributeById => InBound, OutBoundGetTransactionAttributeById => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, transactionAttributeId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_transaction_attribute_by_id", req, callContext)
        response.map(convertToTuple[TransactionAttributeCommons](callContext))        
  }
          
  messageDocs += createOrUpdateAccountAttributeDoc
  def createOrUpdateAccountAttributeDoc = MessageDoc(
    process = "obp.createOrUpdateAccountAttribute",
    messageFormat = messageFormat,
    description = "Create Or Update Account Attribute",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateAccountAttribute(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      productAttributeId=Some(productAttributeIdExample.value),
      name=nameExample.value,
      accountAttributeType=com.openbankproject.commons.model.enums.AccountAttributeType.example,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateAccountAttribute(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= AccountAttributeCommons(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      accountAttributeId=accountAttributeIdExample.value,
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.AccountAttributeType.example,
      value=valueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createOrUpdateAccountAttribute(bankId: BankId, accountId: AccountId, productCode: ProductCode, productAttributeId: Option[String], name: String, accountAttributeType: AccountAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountAttribute]] = {
        import com.openbankproject.commons.dto.{InBoundCreateOrUpdateAccountAttribute => InBound, OutBoundCreateOrUpdateAccountAttribute => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, productCode, productAttributeId, name, accountAttributeType, value)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_or_update_account_attribute", req, callContext)
        response.map(convertToTuple[AccountAttributeCommons](callContext))        
  }
          
  messageDocs += createOrUpdateCustomerAttributeDoc
  def createOrUpdateCustomerAttributeDoc = MessageDoc(
    process = "obp.createOrUpdateCustomerAttribute",
    messageFormat = messageFormat,
    description = "Create Or Update Customer Attribute",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateCustomerAttribute(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      customerId=CustomerId(customerIdExample.value),
      customerAttributeId=Some(customerAttributeIdExample.value),
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.CustomerAttributeType.example,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateCustomerAttribute(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerAttributeCommons(bankId=BankId(bankIdExample.value),
      customerId=CustomerId(customerIdExample.value),
      customerAttributeId=customerAttributeIdExample.value,
      attributeType=com.openbankproject.commons.model.enums.CustomerAttributeType.example,
      name=customerAttributeNameExample.value,
      value=customerAttributeValueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createOrUpdateCustomerAttribute(bankId: BankId, customerId: CustomerId, customerAttributeId: Option[String], name: String, attributeType: CustomerAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAttribute]] = {
        import com.openbankproject.commons.dto.{InBoundCreateOrUpdateCustomerAttribute => InBound, OutBoundCreateOrUpdateCustomerAttribute => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, customerId, customerAttributeId, name, attributeType, value)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_or_update_customer_attribute", req, callContext)
        response.map(convertToTuple[CustomerAttributeCommons](callContext))        
  }
          
  messageDocs += createOrUpdateTransactionAttributeDoc
  def createOrUpdateTransactionAttributeDoc = MessageDoc(
    process = "obp.createOrUpdateTransactionAttribute",
    messageFormat = messageFormat,
    description = "Create Or Update Transaction Attribute",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateTransactionAttribute(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      transactionId=TransactionId(transactionIdExample.value),
      transactionAttributeId=Some(transactionAttributeIdExample.value),
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.TransactionAttributeType.example,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateTransactionAttribute(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= TransactionAttributeCommons(bankId=BankId(bankIdExample.value),
      transactionId=TransactionId(transactionIdExample.value),
      transactionAttributeId=transactionAttributeIdExample.value,
      attributeType=com.openbankproject.commons.model.enums.TransactionAttributeType.example,
      name=transactionAttributeNameExample.value,
      value=transactionAttributeValueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createOrUpdateTransactionAttribute(bankId: BankId, transactionId: TransactionId, transactionAttributeId: Option[String], name: String, attributeType: TransactionAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionAttribute]] = {
        import com.openbankproject.commons.dto.{InBoundCreateOrUpdateTransactionAttribute => InBound, OutBoundCreateOrUpdateTransactionAttribute => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, transactionId, transactionAttributeId, name, attributeType, value)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_or_update_transaction_attribute", req, callContext)
        response.map(convertToTuple[TransactionAttributeCommons](callContext))        
  }
          
  messageDocs += createAccountAttributesDoc
  def createAccountAttributesDoc = MessageDoc(
    process = "obp.createAccountAttributes",
    messageFormat = messageFormat,
    description = "Create Account Attributes",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateAccountAttributes(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      accountAttributes=List( ProductAttributeCommons(bankId=BankId(bankIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      productAttributeId=productAttributeIdExample.value,
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value)))
    ),
    exampleInboundMessage = (
     InBoundCreateAccountAttributes(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( AccountAttributeCommons(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      accountAttributeId=accountAttributeIdExample.value,
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.AccountAttributeType.example,
      value=valueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createAccountAttributes(bankId: BankId, accountId: AccountId, productCode: ProductCode, accountAttributes: List[ProductAttribute], callContext: Option[CallContext]): OBPReturnType[Box[List[AccountAttribute]]] = {
        import com.openbankproject.commons.dto.{InBoundCreateAccountAttributes => InBound, OutBoundCreateAccountAttributes => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, productCode, accountAttributes)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_account_attributes", req, callContext)
        response.map(convertToTuple[List[AccountAttributeCommons]](callContext))        
  }
          
  messageDocs += getAccountAttributesByAccountDoc
  def getAccountAttributesByAccountDoc = MessageDoc(
    process = "obp.getAccountAttributesByAccount",
    messageFormat = messageFormat,
    description = "Get Account Attributes By Account",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAccountAttributesByAccount(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetAccountAttributesByAccount(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( AccountAttributeCommons(bankId=BankId(bankIdExample.value),
      accountId=AccountId(accountIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      accountAttributeId=accountAttributeIdExample.value,
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.AccountAttributeType.example,
      value=valueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getAccountAttributesByAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): OBPReturnType[Box[List[AccountAttribute]]] = {
        import com.openbankproject.commons.dto.{InBoundGetAccountAttributesByAccount => InBound, OutBoundGetAccountAttributesByAccount => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_account_attributes_by_account", req, callContext)
        response.map(convertToTuple[List[AccountAttributeCommons]](callContext))        
  }
          
  messageDocs += getCustomerAttributesDoc
  def getCustomerAttributesDoc = MessageDoc(
    process = "obp.getCustomerAttributes",
    messageFormat = messageFormat,
    description = "Get Customer Attributes",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerAttributes(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      customerId=CustomerId(customerIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetCustomerAttributes(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( CustomerAttributeCommons(bankId=BankId(bankIdExample.value),
      customerId=CustomerId(customerIdExample.value),
      customerAttributeId=customerAttributeIdExample.value,
      attributeType=com.openbankproject.commons.model.enums.CustomerAttributeType.example,
      name=customerAttributeNameExample.value,
      value=customerAttributeValueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCustomerAttributes(bankId: BankId, customerId: CustomerId, callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAttribute]]] = {
        import com.openbankproject.commons.dto.{InBoundGetCustomerAttributes => InBound, OutBoundGetCustomerAttributes => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_customer_attributes", req, callContext)
        response.map(convertToTuple[List[CustomerAttributeCommons]](callContext))        
  }
          
  messageDocs += getCustomerIdsByAttributeNameValuesDoc
  def getCustomerIdsByAttributeNameValuesDoc = MessageDoc(
    process = "obp.getCustomerIdsByAttributeNameValues",
    messageFormat = messageFormat,
    description = "Get Customer Ids By Attribute Name Values",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerIdsByAttributeNameValues(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      nameValues=Map("some_name" -> List("name1", "name2")))
    ),
    exampleInboundMessage = (
     InBoundGetCustomerIdsByAttributeNameValues(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=listExample.value.split("[,;]").toList)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCustomerIdsByAttributeNameValues(bankId: BankId, nameValues: Map[String,List[String]], callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] = {
        import com.openbankproject.commons.dto.{InBoundGetCustomerIdsByAttributeNameValues => InBound, OutBoundGetCustomerIdsByAttributeNameValues => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, nameValues)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_customer_ids_by_attribute_name_values", req, callContext)
        response.map(convertToTuple[List[String]](callContext))        
  }
          
  messageDocs += getCustomerAttributesForCustomersDoc
  def getCustomerAttributesForCustomersDoc = MessageDoc(
    process = "obp.getCustomerAttributesForCustomers",
    messageFormat = messageFormat,
    description = "Get Customer Attributes For Customers",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerAttributesForCustomers(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customers=List( CustomerCommons(customerId=customerIdExample.value,
      bankId=bankIdExample.value,
      number=customerNumberExample.value,
      legalName=legalNameExample.value,
      mobileNumber=mobileNumberExample.value,
      email=emailExample.value,
      faceImage= CustomerFaceImage(date=toDate(customerFaceImageDateExample),
      url=urlExample.value),
      dateOfBirth=toDate(dateOfBirthExample),
      relationshipStatus=relationshipStatusExample.value,
      dependents=dependentsExample.value.toInt,
      dobOfDependents=dobOfDependentsExample.value.split("[,;]").map(parseDate).flatMap(_.toSeq).toList,
      highestEducationAttained=highestEducationAttainedExample.value,
      employmentStatus=employmentStatusExample.value,
      creditRating= CreditRating(rating=ratingExample.value,
      source=sourceExample.value),
      creditLimit= CreditLimit(currency=currencyExample.value,
      amount=creditLimitAmountExample.value),
      kycStatus=kycStatusExample.value.toBoolean,
      lastOkDate=toDate(customerLastOkDateExample),
      title=customerTitleExample.value,
      branchId=branchIdExample.value,
      nameSuffix=nameSuffixExample.value)))
    ),
    exampleInboundMessage = (
     InBoundGetCustomerAttributesForCustomers(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= List(
         CustomerAndAttribute(
             MessageDocsSwaggerDefinitions.customerCommons,
             List(MessageDocsSwaggerDefinitions.customerAttribute)
          )
         )
      )
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCustomerAttributesForCustomers(customers: List[Customer], callContext: Option[CallContext]): OBPReturnType[Box[List[CustomerAndAttribute]]] = {
        import com.openbankproject.commons.dto.{InBoundGetCustomerAttributesForCustomers => InBound, OutBoundGetCustomerAttributesForCustomers => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customers)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_customer_attributes_for_customers", req, callContext)
        response.map(convertToTuple[List[CustomerAndAttribute]](callContext))        
  }
          
  messageDocs += getTransactionIdsByAttributeNameValuesDoc
  def getTransactionIdsByAttributeNameValuesDoc = MessageDoc(
    process = "obp.getTransactionIdsByAttributeNameValues",
    messageFormat = messageFormat,
    description = "Get Transaction Ids By Attribute Name Values",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionIdsByAttributeNameValues(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      nameValues=Map("some_name" -> List("name1", "name2")))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionIdsByAttributeNameValues(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=listExample.value.split("[,;]").toList)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getTransactionIdsByAttributeNameValues(bankId: BankId, nameValues: Map[String,List[String]], callContext: Option[CallContext]): OBPReturnType[Box[List[String]]] = {
        import com.openbankproject.commons.dto.{InBoundGetTransactionIdsByAttributeNameValues => InBound, OutBoundGetTransactionIdsByAttributeNameValues => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, nameValues)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_transaction_ids_by_attribute_name_values", req, callContext)
        response.map(convertToTuple[List[String]](callContext))        
  }
          
  messageDocs += getTransactionAttributesDoc
  def getTransactionAttributesDoc = MessageDoc(
    process = "obp.getTransactionAttributes",
    messageFormat = messageFormat,
    description = "Get Transaction Attributes",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetTransactionAttributes(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      transactionId=TransactionId(transactionIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetTransactionAttributes(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( TransactionAttributeCommons(bankId=BankId(bankIdExample.value),
      transactionId=TransactionId(transactionIdExample.value),
      transactionAttributeId=transactionAttributeIdExample.value,
      attributeType=com.openbankproject.commons.model.enums.TransactionAttributeType.example,
      name=transactionAttributeNameExample.value,
      value=transactionAttributeValueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getTransactionAttributes(bankId: BankId, transactionId: TransactionId, callContext: Option[CallContext]): OBPReturnType[Box[List[TransactionAttribute]]] = {
        import com.openbankproject.commons.dto.{InBoundGetTransactionAttributes => InBound, OutBoundGetTransactionAttributes => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, transactionId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_transaction_attributes", req, callContext)
        response.map(convertToTuple[List[TransactionAttributeCommons]](callContext))        
  }
          
  messageDocs += getCustomerAttributeByIdDoc
  def getCustomerAttributeByIdDoc = MessageDoc(
    process = "obp.getCustomerAttributeById",
    messageFormat = messageFormat,
    description = "Get Customer Attribute By Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCustomerAttributeById(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerAttributeId=customerAttributeIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCustomerAttributeById(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerAttributeCommons(bankId=BankId(bankIdExample.value),
      customerId=CustomerId(customerIdExample.value),
      customerAttributeId=customerAttributeIdExample.value,
      attributeType=com.openbankproject.commons.model.enums.CustomerAttributeType.example,
      name=customerAttributeNameExample.value,
      value=customerAttributeValueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCustomerAttributeById(customerAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerAttribute]] = {
        import com.openbankproject.commons.dto.{InBoundGetCustomerAttributeById => InBound, OutBoundGetCustomerAttributeById => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerAttributeId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_customer_attribute_by_id", req, callContext)
        response.map(convertToTuple[CustomerAttributeCommons](callContext))        
  }
          
  messageDocs += createOrUpdateCardAttributeDoc
  def createOrUpdateCardAttributeDoc = MessageDoc(
    process = "obp.createOrUpdateCardAttribute",
    messageFormat = messageFormat,
    description = "Create Or Update Card Attribute",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateCardAttribute(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=Some(BankId(bankIdExample.value)),
      cardId=Some(cardIdExample.value),
      cardAttributeId=Some(cardAttributeIdExample.value),
      name=nameExample.value,
      cardAttributeType=com.openbankproject.commons.model.enums.CardAttributeType.example,
      value=valueExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateCardAttribute(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CardAttributeCommons(bankId=Some(BankId(bankIdExample.value)),
      cardId=Some(cardIdExample.value),
      cardAttributeId=Some(cardAttributeIdExample.value),
      name=cardAttributeNameExample.value,
      attributeType=com.openbankproject.commons.model.enums.CardAttributeType.example,
      value=cardAttributeValueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createOrUpdateCardAttribute(bankId: Option[BankId], cardId: Option[String], cardAttributeId: Option[String], name: String, cardAttributeType: CardAttributeType.Value, value: String, callContext: Option[CallContext]): OBPReturnType[Box[CardAttribute]] = {
        import com.openbankproject.commons.dto.{InBoundCreateOrUpdateCardAttribute => InBound, OutBoundCreateOrUpdateCardAttribute => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, cardId, cardAttributeId, name, cardAttributeType, value)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_or_update_card_attribute", req, callContext)
        response.map(convertToTuple[CardAttributeCommons](callContext))        
  }
          
  messageDocs += getCardAttributeByIdDoc
  def getCardAttributeByIdDoc = MessageDoc(
    process = "obp.getCardAttributeById",
    messageFormat = messageFormat,
    description = "Get Card Attribute By Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCardAttributeById(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      cardAttributeId=cardAttributeIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCardAttributeById(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CardAttributeCommons(bankId=Some(BankId(bankIdExample.value)),
      cardId=Some(cardIdExample.value),
      cardAttributeId=Some(cardAttributeIdExample.value),
      name=cardAttributeNameExample.value,
      attributeType=com.openbankproject.commons.model.enums.CardAttributeType.example,
      value=cardAttributeValueExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCardAttributeById(cardAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[CardAttribute]] = {
        import com.openbankproject.commons.dto.{InBoundGetCardAttributeById => InBound, OutBoundGetCardAttributeById => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, cardAttributeId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_card_attribute_by_id", req, callContext)
        response.map(convertToTuple[CardAttributeCommons](callContext))        
  }
          
  messageDocs += getCardAttributesFromProviderDoc
  def getCardAttributesFromProviderDoc = MessageDoc(
    process = "obp.getCardAttributesFromProvider",
    messageFormat = messageFormat,
    description = "Get Card Attributes From Provider",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetCardAttributesFromProvider(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      cardId=cardIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetCardAttributesFromProvider(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( CardAttributeCommons(bankId=Some(BankId(bankIdExample.value)),
      cardId=Some(cardIdExample.value),
      cardAttributeId=Some(cardAttributeIdExample.value),
      name=cardAttributeNameExample.value,
      attributeType=com.openbankproject.commons.model.enums.CardAttributeType.example,
      value=cardAttributeValueExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getCardAttributesFromProvider(cardId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[CardAttribute]]] = {
        import com.openbankproject.commons.dto.{InBoundGetCardAttributesFromProvider => InBound, OutBoundGetCardAttributesFromProvider => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, cardId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_card_attributes_from_provider", req, callContext)
        response.map(convertToTuple[List[CardAttributeCommons]](callContext))        
  }
          
  messageDocs += createAccountApplicationDoc
  def createAccountApplicationDoc = MessageDoc(
    process = "obp.createAccountApplication",
    messageFormat = messageFormat,
    description = "Create Account Application",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateAccountApplication(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      productCode=ProductCode(productCodeExample.value),
      userId=Some(userIdExample.value),
      customerId=Some(customerIdExample.value))
    ),
    exampleInboundMessage = (
     InBoundCreateAccountApplication(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= AccountApplicationCommons(accountApplicationId=accountApplicationIdExample.value,
      productCode=ProductCode(productCodeExample.value),
      userId=userIdExample.value,
      customerId=customerIdExample.value,
      dateOfApplication=toDate(dateOfApplicationExample),
      status=statusExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createAccountApplication(productCode: ProductCode, userId: Option[String], customerId: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] = {
        import com.openbankproject.commons.dto.{InBoundCreateAccountApplication => InBound, OutBoundCreateAccountApplication => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, productCode, userId, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_account_application", req, callContext)
        response.map(convertToTuple[AccountApplicationCommons](callContext))        
  }
          
  messageDocs += getAllAccountApplicationDoc
  def getAllAccountApplicationDoc = MessageDoc(
    process = "obp.getAllAccountApplication",
    messageFormat = messageFormat,
    description = "Get All Account Application",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
          OutBoundGetAllAccountApplication(MessageDocsSwaggerDefinitions.outboundAdapterCallContext)
    ),
    exampleInboundMessage = (
     InBoundGetAllAccountApplication(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( AccountApplicationCommons(accountApplicationId=accountApplicationIdExample.value,
      productCode=ProductCode(productCodeExample.value),
      userId=userIdExample.value,
      customerId=customerIdExample.value,
      dateOfApplication=toDate(dateOfApplicationExample),
      status=statusExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getAllAccountApplication(callContext: Option[CallContext]): OBPReturnType[Box[List[AccountApplication]]] = {
        import com.openbankproject.commons.dto.{InBoundGetAllAccountApplication => InBound, OutBoundGetAllAccountApplication => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_all_account_application", req, callContext)
        response.map(convertToTuple[List[AccountApplicationCommons]](callContext))        
  }
          
  messageDocs += getAccountApplicationByIdDoc
  def getAccountApplicationByIdDoc = MessageDoc(
    process = "obp.getAccountApplicationById",
    messageFormat = messageFormat,
    description = "Get Account Application By Id",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetAccountApplicationById(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      accountApplicationId=accountApplicationIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetAccountApplicationById(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= AccountApplicationCommons(accountApplicationId=accountApplicationIdExample.value,
      productCode=ProductCode(productCodeExample.value),
      userId=userIdExample.value,
      customerId=customerIdExample.value,
      dateOfApplication=toDate(dateOfApplicationExample),
      status=statusExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getAccountApplicationById(accountApplicationId: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] = {
        import com.openbankproject.commons.dto.{InBoundGetAccountApplicationById => InBound, OutBoundGetAccountApplicationById => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, accountApplicationId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_account_application_by_id", req, callContext)
        response.map(convertToTuple[AccountApplicationCommons](callContext))        
  }
          
  messageDocs += updateAccountApplicationStatusDoc
  def updateAccountApplicationStatusDoc = MessageDoc(
    process = "obp.updateAccountApplicationStatus",
    messageFormat = messageFormat,
    description = "Update Account Application Status",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundUpdateAccountApplicationStatus(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      accountApplicationId=accountApplicationIdExample.value,
      status=statusExample.value)
    ),
    exampleInboundMessage = (
     InBoundUpdateAccountApplicationStatus(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= AccountApplicationCommons(accountApplicationId=accountApplicationIdExample.value,
      productCode=ProductCode(productCodeExample.value),
      userId=userIdExample.value,
      customerId=customerIdExample.value,
      dateOfApplication=toDate(dateOfApplicationExample),
      status=statusExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def updateAccountApplicationStatus(accountApplicationId: String, status: String, callContext: Option[CallContext]): OBPReturnType[Box[AccountApplication]] = {
        import com.openbankproject.commons.dto.{InBoundUpdateAccountApplicationStatus => InBound, OutBoundUpdateAccountApplicationStatus => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, accountApplicationId, status)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_update_account_application_status", req, callContext)
        response.map(convertToTuple[AccountApplicationCommons](callContext))        
  }
          
  messageDocs += getOrCreateProductCollectionDoc
  def getOrCreateProductCollectionDoc = MessageDoc(
    process = "obp.getOrCreateProductCollection",
    messageFormat = messageFormat,
    description = "Get Or Create Product Collection",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetOrCreateProductCollection(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      collectionCode=collectionCodeExample.value,
      productCodes=listExample.value.split("[,;]").toList)
    ),
    exampleInboundMessage = (
     InBoundGetOrCreateProductCollection(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( ProductCollectionCommons(collectionCode=collectionCodeExample.value,
      productCode=productCodeExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getOrCreateProductCollection(collectionCode: String, productCodes: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] = {
        import com.openbankproject.commons.dto.{InBoundGetOrCreateProductCollection => InBound, OutBoundGetOrCreateProductCollection => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, collectionCode, productCodes)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_or_create_product_collection", req, callContext)
        response.map(convertToTuple[List[ProductCollectionCommons]](callContext))        
  }
          
  messageDocs += getProductCollectionDoc
  def getProductCollectionDoc = MessageDoc(
    process = "obp.getProductCollection",
    messageFormat = messageFormat,
    description = "Get Product Collection",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProductCollection(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      collectionCode=collectionCodeExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetProductCollection(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( ProductCollectionCommons(collectionCode=collectionCodeExample.value,
      productCode=productCodeExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getProductCollection(collectionCode: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollection]]] = {
        import com.openbankproject.commons.dto.{InBoundGetProductCollection => InBound, OutBoundGetProductCollection => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, collectionCode)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_product_collection", req, callContext)
        response.map(convertToTuple[List[ProductCollectionCommons]](callContext))        
  }
          
  messageDocs += getOrCreateProductCollectionItemDoc
  def getOrCreateProductCollectionItemDoc = MessageDoc(
    process = "obp.getOrCreateProductCollectionItem",
    messageFormat = messageFormat,
    description = "Get Or Create Product Collection Item",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetOrCreateProductCollectionItem(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      collectionCode=collectionCodeExample.value,
      memberProductCodes=listExample.value.split("[,;]").toList)
    ),
    exampleInboundMessage = (
     InBoundGetOrCreateProductCollectionItem(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( ProductCollectionItemCommons(collectionCode=collectionCodeExample.value,
      memberProductCode=memberProductCodeExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getOrCreateProductCollectionItem(collectionCode: String, memberProductCodes: List[String], callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] = {
        import com.openbankproject.commons.dto.{InBoundGetOrCreateProductCollectionItem => InBound, OutBoundGetOrCreateProductCollectionItem => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, collectionCode, memberProductCodes)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_or_create_product_collection_item", req, callContext)
        response.map(convertToTuple[List[ProductCollectionItemCommons]](callContext))        
  }
          
  messageDocs += getProductCollectionItemDoc
  def getProductCollectionItemDoc = MessageDoc(
    process = "obp.getProductCollectionItem",
    messageFormat = messageFormat,
    description = "Get Product Collection Item",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProductCollectionItem(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      collectionCode=collectionCodeExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetProductCollectionItem(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( ProductCollectionItemCommons(collectionCode=collectionCodeExample.value,
      memberProductCode=memberProductCodeExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getProductCollectionItem(collectionCode: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItem]]] = {
        import com.openbankproject.commons.dto.{InBoundGetProductCollectionItem => InBound, OutBoundGetProductCollectionItem => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, collectionCode)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_product_collection_item", req, callContext)
        response.map(convertToTuple[List[ProductCollectionItemCommons]](callContext))        
  }
          
  messageDocs += getProductCollectionItemsTreeDoc
  def getProductCollectionItemsTreeDoc = MessageDoc(
    process = "obp.getProductCollectionItemsTree",
    messageFormat = messageFormat,
    description = "Get Product Collection Items Tree",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetProductCollectionItemsTree(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      collectionCode=collectionCodeExample.value,
      bankId=bankIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetProductCollectionItemsTree(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( ProductCollectionItemsTree(productCollectionItem= ProductCollectionItemCommons(collectionCode=collectionCodeExample.value,
      memberProductCode=memberProductCodeExample.value),
      product= ProductCommons(bankId=BankId(bankIdExample.value),
      code=ProductCode(productCodeExample.value),
      parentProductCode=ProductCode(parentProductCodeExample.value),
      name=nameExample.value,
      category=categoryExample.value,
      family=familyExample.value,
      superFamily=superFamilyExample.value,
      moreInfoUrl=moreInfoUrlExample.value,
      details=detailsExample.value,
      description=descriptionExample.value,
      meta=Meta( License(id=idExample.value,
      name=nameExample.value))),
      attributes=List( ProductAttributeCommons(bankId=BankId(bankIdExample.value),
      productCode=ProductCode(productCodeExample.value),
      productAttributeId=productAttributeIdExample.value,
      name=nameExample.value,
      attributeType=com.openbankproject.commons.model.enums.ProductAttributeType.example,
      value=valueExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getProductCollectionItemsTree(collectionCode: String, bankId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[ProductCollectionItemsTree]]] = {
        import com.openbankproject.commons.dto.{InBoundGetProductCollectionItemsTree => InBound, OutBoundGetProductCollectionItemsTree => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, collectionCode, bankId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_product_collection_items_tree", req, callContext)
        response.map(convertToTuple[List[ProductCollectionItemsTree]](callContext))        
  }
          
  messageDocs += createMeetingDoc
  def createMeetingDoc = MessageDoc(
    process = "obp.createMeeting",
    messageFormat = messageFormat,
    description = "Create Meeting",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateMeeting(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      staffUser= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      customerUser= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      providerId=providerIdExample.value,
      purposeId=purposeIdExample.value,
      when=toDate(whenExample),
      sessionId=sessionIdExample.value,
      customerToken=customerTokenExample.value,
      staffToken=staffTokenExample.value,
      creator= ContactDetails(name=nameExample.value,
      phone=phoneExample.value,
      email=emailExample.value),
      invitees=List( Invitee(contactDetails= ContactDetails(name=nameExample.value,
      phone=phoneExample.value,
      email=emailExample.value),
      status=statusExample.value)))
    ),
    exampleInboundMessage = (
     InBoundCreateMeeting(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= MeetingCommons(meetingId=meetingIdExample.value,
      providerId=providerIdExample.value,
      purposeId=purposeIdExample.value,
      bankId=bankIdExample.value,
      present= MeetingPresent(staffUserId=staffUserIdExample.value,
      customerUserId=customerUserIdExample.value),
      keys= MeetingKeys(sessionId=sessionIdExample.value,
      customerToken=customerTokenExample.value,
      staffToken=staffTokenExample.value),
      when=toDate(whenExample),
      creator= ContactDetails(name=nameExample.value,
      phone=phoneExample.value,
      email=emailExample.value),
      invitees=List( Invitee(contactDetails= ContactDetails(name=nameExample.value,
      phone=phoneExample.value,
      email=emailExample.value),
      status=statusExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createMeeting(bankId: BankId, staffUser: User, customerUser: User, providerId: String, purposeId: String, when: Date, sessionId: String, customerToken: String, staffToken: String, creator: ContactDetails, invitees: List[Invitee], callContext: Option[CallContext]): OBPReturnType[Box[Meeting]] = {
        import com.openbankproject.commons.dto.{InBoundCreateMeeting => InBound, OutBoundCreateMeeting => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, staffUser, customerUser, providerId, purposeId, when, sessionId, customerToken, staffToken, creator, invitees)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_meeting", req, callContext)
        response.map(convertToTuple[MeetingCommons](callContext))        
  }
          
  messageDocs += getMeetingsDoc
  def getMeetingsDoc = MessageDoc(
    process = "obp.getMeetings",
    messageFormat = messageFormat,
    description = "Get Meetings",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetMeetings(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      user= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value))
    ),
    exampleInboundMessage = (
     InBoundGetMeetings(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( MeetingCommons(meetingId=meetingIdExample.value,
      providerId=providerIdExample.value,
      purposeId=purposeIdExample.value,
      bankId=bankIdExample.value,
      present= MeetingPresent(staffUserId=staffUserIdExample.value,
      customerUserId=customerUserIdExample.value),
      keys= MeetingKeys(sessionId=sessionIdExample.value,
      customerToken=customerTokenExample.value,
      staffToken=staffTokenExample.value),
      when=toDate(whenExample),
      creator= ContactDetails(name=nameExample.value,
      phone=phoneExample.value,
      email=emailExample.value),
      invitees=List( Invitee(contactDetails= ContactDetails(name=nameExample.value,
      phone=phoneExample.value,
      email=emailExample.value),
      status=statusExample.value)))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getMeetings(bankId: BankId, user: User, callContext: Option[CallContext]): OBPReturnType[Box[List[Meeting]]] = {
        import com.openbankproject.commons.dto.{InBoundGetMeetings => InBound, OutBoundGetMeetings => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, user)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_meetings", req, callContext)
        response.map(convertToTuple[List[MeetingCommons]](callContext))        
  }
          
  messageDocs += getMeetingDoc
  def getMeetingDoc = MessageDoc(
    process = "obp.getMeeting",
    messageFormat = messageFormat,
    description = "Get Meeting",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetMeeting(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=BankId(bankIdExample.value),
      user= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      meetingId=meetingIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetMeeting(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= MeetingCommons(meetingId=meetingIdExample.value,
      providerId=providerIdExample.value,
      purposeId=purposeIdExample.value,
      bankId=bankIdExample.value,
      present= MeetingPresent(staffUserId=staffUserIdExample.value,
      customerUserId=customerUserIdExample.value),
      keys= MeetingKeys(sessionId=sessionIdExample.value,
      customerToken=customerTokenExample.value,
      staffToken=staffTokenExample.value),
      when=toDate(whenExample),
      creator= ContactDetails(name=nameExample.value,
      phone=phoneExample.value,
      email=emailExample.value),
      invitees=List( Invitee(contactDetails= ContactDetails(name=nameExample.value,
      phone=phoneExample.value,
      email=emailExample.value),
      status=statusExample.value))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getMeeting(bankId: BankId, user: User, meetingId: String, callContext: Option[CallContext]): OBPReturnType[Box[Meeting]] = {
        import com.openbankproject.commons.dto.{InBoundGetMeeting => InBound, OutBoundGetMeeting => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, user, meetingId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_meeting", req, callContext)
        response.map(convertToTuple[MeetingCommons](callContext))        
  }
          
  messageDocs += createOrUpdateKycCheckDoc
  def createOrUpdateKycCheckDoc = MessageDoc(
    process = "obp.createOrUpdateKycCheck",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Check",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycCheck(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      id=idExample.value,
      customerNumber=customerNumberExample.value,
      date=toDate(dateExample),
      how=howExample.value,
      staffUserId=staffUserIdExample.value,
      mStaffName="string",
      mSatisfied=true,
      comments=commentsExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycCheck(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= KycCheckCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycCheck="string",
      customerNumber=customerNumberExample.value,
      date=toDate(dateExample),
      how=howExample.value,
      staffUserId=staffUserIdExample.value,
      staffName=staffNameExample.value,
      satisfied=satisfiedExample.value.toBoolean,
      comments=commentsExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createOrUpdateKycCheck(bankId: String, customerId: String, id: String, customerNumber: String, date: Date, how: String, staffUserId: String, mStaffName: String, mSatisfied: Boolean, comments: String, callContext: Option[CallContext]): OBPReturnType[Box[KycCheck]] = {
        import com.openbankproject.commons.dto.{InBoundCreateOrUpdateKycCheck => InBound, OutBoundCreateOrUpdateKycCheck => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, customerId, id, customerNumber, date, how, staffUserId, mStaffName, mSatisfied, comments)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_or_update_kyc_check", req, callContext)
        response.map(convertToTuple[KycCheckCommons](callContext))        
  }
          
  messageDocs += createOrUpdateKycDocumentDoc
  def createOrUpdateKycDocumentDoc = MessageDoc(
    process = "obp.createOrUpdateKycDocument",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Document",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycDocument(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      id=idExample.value,
      customerNumber=customerNumberExample.value,
      `type`=typeExample.value,
      number=numberExample.value,
      issueDate=toDate(issueDateExample),
      issuePlace=issuePlaceExample.value,
      expiryDate=toDate(expiryDateExample))
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycDocument(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= KycDocumentCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycDocument="string",
      customerNumber=customerNumberExample.value,
      `type`=typeExample.value,
      number=numberExample.value,
      issueDate=toDate(issueDateExample),
      issuePlace=issuePlaceExample.value,
      expiryDate=toDate(expiryDateExample)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createOrUpdateKycDocument(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, number: String, issueDate: Date, issuePlace: String, expiryDate: Date, callContext: Option[CallContext]): OBPReturnType[Box[KycDocument]] = {
        import com.openbankproject.commons.dto.{InBoundCreateOrUpdateKycDocument => InBound, OutBoundCreateOrUpdateKycDocument => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, customerId, id, customerNumber, `type`, number, issueDate, issuePlace, expiryDate)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_or_update_kyc_document", req, callContext)
        response.map(convertToTuple[KycDocument](callContext))        
  }
          
  messageDocs += createOrUpdateKycMediaDoc
  def createOrUpdateKycMediaDoc = MessageDoc(
    process = "obp.createOrUpdateKycMedia",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Media",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycMedia(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      id=idExample.value,
      customerNumber=customerNumberExample.value,
      `type`=typeExample.value,
      url=urlExample.value,
      date=toDate(dateExample),
      relatesToKycDocumentId=relatesToKycDocumentIdExample.value,
      relatesToKycCheckId=relatesToKycCheckIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycMedia(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= KycMediaCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycMedia="string",
      customerNumber=customerNumberExample.value,
      `type`=typeExample.value,
      url=urlExample.value,
      date=toDate(dateExample),
      relatesToKycDocumentId=relatesToKycDocumentIdExample.value,
      relatesToKycCheckId=relatesToKycCheckIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createOrUpdateKycMedia(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String, callContext: Option[CallContext]): OBPReturnType[Box[KycMedia]] = {
        import com.openbankproject.commons.dto.{InBoundCreateOrUpdateKycMedia => InBound, OutBoundCreateOrUpdateKycMedia => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, customerId, id, customerNumber, `type`, url, date, relatesToKycDocumentId, relatesToKycCheckId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_or_update_kyc_media", req, callContext)
        response.map(convertToTuple[KycMediaCommons](callContext))        
  }
          
  messageDocs += createOrUpdateKycStatusDoc
  def createOrUpdateKycStatusDoc = MessageDoc(
    process = "obp.createOrUpdateKycStatus",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Status",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycStatus(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      ok=okExample.value.toBoolean,
      date=toDate(dateExample))
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycStatus(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= KycStatusCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      ok=okExample.value.toBoolean,
      date=toDate(dateExample)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createOrUpdateKycStatus(bankId: String, customerId: String, customerNumber: String, ok: Boolean, date: Date, callContext: Option[CallContext]): OBPReturnType[Box[KycStatus]] = {
        import com.openbankproject.commons.dto.{InBoundCreateOrUpdateKycStatus => InBound, OutBoundCreateOrUpdateKycStatus => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, customerId, customerNumber, ok, date)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_or_update_kyc_status", req, callContext)
        response.map(convertToTuple[KycStatusCommons](callContext))        
  }
          
  messageDocs += getKycChecksDoc
  def getKycChecksDoc = MessageDoc(
    process = "obp.getKycChecks",
    messageFormat = messageFormat,
    description = "Get Kyc Checks",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetKycChecks(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetKycChecks(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( KycCheckCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycCheck="string",
      customerNumber=customerNumberExample.value,
      date=toDate(dateExample),
      how=howExample.value,
      staffUserId=staffUserIdExample.value,
      staffName=staffNameExample.value,
      satisfied=satisfiedExample.value.toBoolean,
      comments=commentsExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getKycChecks(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycCheck]]] = {
        import com.openbankproject.commons.dto.{InBoundGetKycChecks => InBound, OutBoundGetKycChecks => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_kyc_checks", req, callContext)
        response.map(convertToTuple[List[KycCheckCommons]](callContext))        
  }
          
  messageDocs += getKycDocumentsDoc
  def getKycDocumentsDoc = MessageDoc(
    process = "obp.getKycDocuments",
    messageFormat = messageFormat,
    description = "Get Kyc Documents",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetKycDocuments(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetKycDocuments(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( KycDocumentCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycDocument="string",
      customerNumber=customerNumberExample.value,
      `type`=typeExample.value,
      number=numberExample.value,
      issueDate=toDate(issueDateExample),
      issuePlace=issuePlaceExample.value,
      expiryDate=toDate(expiryDateExample))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getKycDocuments(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycDocument]]] = {
        import com.openbankproject.commons.dto.{InBoundGetKycDocuments => InBound, OutBoundGetKycDocuments => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_kyc_documents", req, callContext)
        response.map(convertToTuple[List[KycDocumentCommons]](callContext))        
  }
          
  messageDocs += getKycMediasDoc
  def getKycMediasDoc = MessageDoc(
    process = "obp.getKycMedias",
    messageFormat = messageFormat,
    description = "Get Kyc Medias",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetKycMedias(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetKycMedias(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( KycMediaCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      idKycMedia="string",
      customerNumber=customerNumberExample.value,
      `type`=typeExample.value,
      url=urlExample.value,
      date=toDate(dateExample),
      relatesToKycDocumentId=relatesToKycDocumentIdExample.value,
      relatesToKycCheckId=relatesToKycCheckIdExample.value)))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getKycMedias(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycMedia]]] = {
        import com.openbankproject.commons.dto.{InBoundGetKycMedias => InBound, OutBoundGetKycMedias => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_kyc_medias", req, callContext)
        response.map(convertToTuple[List[KycMediaCommons]](callContext))        
  }
          
  messageDocs += getKycStatusesDoc
  def getKycStatusesDoc = MessageDoc(
    process = "obp.getKycStatuses",
    messageFormat = messageFormat,
    description = "Get Kyc Statuses",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundGetKycStatuses(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerId=customerIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundGetKycStatuses(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=List( KycStatusCommons(bankId=bankIdExample.value,
      customerId=customerIdExample.value,
      customerNumber=customerNumberExample.value,
      ok=okExample.value.toBoolean,
      date=toDate(dateExample))))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getKycStatuses(customerId: String, callContext: Option[CallContext]): OBPReturnType[Box[List[KycStatus]]] = {
        import com.openbankproject.commons.dto.{InBoundGetKycStatuses => InBound, OutBoundGetKycStatuses => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_get_kyc_statuses", req, callContext)
        response.map(convertToTuple[List[KycStatusCommons]](callContext))        
  }
          
  messageDocs += createMessageDoc
  def createMessageDoc = MessageDoc(
    process = "obp.createMessage",
    messageFormat = messageFormat,
    description = "Create Message",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateMessage(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      user= UserCommons(userPrimaryKey=UserPrimaryKey(123),
      userId=userIdExample.value,
      idGivenByProvider="string",
      provider=providerExample.value,
      emailAddress=emailAddressExample.value,
      name=userNameExample.value),
      bankId=BankId(bankIdExample.value),
      message=messageExample.value,
      fromDepartment=fromDepartmentExample.value,
      fromPerson=fromPersonExample.value)
    ),
    exampleInboundMessage = (
     InBoundCreateMessage(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= CustomerMessageCommons(messageId="string",
      date=toDate(dateExample),
      message=messageExample.value,
      fromDepartment=fromDepartmentExample.value,
      fromPerson=fromPersonExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createMessage(user: User, bankId: BankId, message: String, fromDepartment: String, fromPerson: String, callContext: Option[CallContext]): OBPReturnType[Box[CustomerMessage]] = {
        import com.openbankproject.commons.dto.{InBoundCreateMessage => InBound, OutBoundCreateMessage => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, user, bankId, message, fromDepartment, fromPerson)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_message", req, callContext)
        response.map(convertToTuple[CustomerMessageCommons](callContext))        
  }
          
  messageDocs += makeHistoricalPaymentDoc
  def makeHistoricalPaymentDoc = MessageDoc(
    process = "obp.makeHistoricalPayment",
    messageFormat = messageFormat,
    description = "Make Historical Payment",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundMakeHistoricalPayment(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      fromAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      toAccount= BankAccountCommons(accountId=AccountId(accountIdExample.value),
      accountType=accountTypeExample.value,
      balance=BigDecimal(balanceExample.value),
      currency=currencyExample.value,
      name=bankAccountNameExample.value,
      label=labelExample.value,
      number=bankAccountNumberExample.value,
      bankId=BankId(bankIdExample.value),
      lastUpdate=toDate(bankAccountLastUpdateExample),
      branchId=branchIdExample.value,
      accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
      address=accountRoutingAddressExample.value)),
      accountRules=List( AccountRule(scheme=accountRuleSchemeExample.value,
      value=accountRuleValueExample.value)),
      accountHolder=bankAccountAccountHolderExample.value,
      attributes=Some(List( Attribute(name=attributeNameExample.value,
      `type`=attributeTypeExample.value,
      value=attributeValueExample.value)))),
      posted=toDate(postedExample),
      completed=toDate(completedExample),
      amount=BigDecimal(amountExample.value),
      currency=currencyExample.value,
      description=descriptionExample.value,
      transactionRequestType=transactionRequestTypeExample.value,
      chargePolicy=chargePolicyExample.value)
    ),
    exampleInboundMessage = (
     InBoundMakeHistoricalPayment(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=TransactionId(transactionIdExample.value))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def makeHistoricalPayment(fromAccount: BankAccount, toAccount: BankAccount, posted: Date, completed: Date, amount: BigDecimal, currency: String, description: String, transactionRequestType: String, chargePolicy: String, callContext: Option[CallContext]): OBPReturnType[Box[TransactionId]] = {
        import com.openbankproject.commons.dto.{InBoundMakeHistoricalPayment => InBound, OutBoundMakeHistoricalPayment => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, fromAccount, toAccount, posted, completed, amount, currency, description, transactionRequestType, chargePolicy)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_make_historical_payment", req, callContext)
        response.map(convertToTuple[TransactionId](callContext))        
  }
          
  messageDocs += createDirectDebitDoc
  def createDirectDebitDoc = MessageDoc(
    process = "obp.createDirectDebit",
    messageFormat = messageFormat,
    description = "Create Direct Debit",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundCreateDirectDebit(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      bankId=bankIdExample.value,
      accountId=accountIdExample.value,
      customerId=customerIdExample.value,
      userId=userIdExample.value,
      counterpartyId=counterpartyIdExample.value,
      dateSigned=toDate(dateSignedExample),
      dateStarts=toDate(dateStartsExample),
      dateExpires=Some(toDate(dateExpiresExample)))
    ),
    exampleInboundMessage = (
     InBoundCreateDirectDebit(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data= DirectDebitTraitCommons(directDebitId=directDebitIdExample.value,
      bankId=bankIdExample.value,
      accountId=accountIdExample.value,
      customerId=customerIdExample.value,
      userId=userIdExample.value,
      counterpartyId=counterpartyIdExample.value,
      dateSigned=toDate(dateSignedExample),
      dateCancelled=toDate(dateCancelledExample),
      dateStarts=toDate(dateStartsExample),
      dateExpires=toDate(dateExpiresExample),
      active=activeExample.value.toBoolean))
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def createDirectDebit(bankId: String, accountId: String, customerId: String, userId: String, counterpartyId: String, dateSigned: Date, dateStarts: Date, dateExpires: Option[Date], callContext: Option[CallContext]): OBPReturnType[Box[DirectDebitTrait]] = {
        import com.openbankproject.commons.dto.{InBoundCreateDirectDebit => InBound, OutBoundCreateDirectDebit => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, bankId, accountId, customerId, userId, counterpartyId, dateSigned, dateStarts, dateExpires)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_create_direct_debit", req, callContext)
        response.map(convertToTuple[DirectDebitTraitCommons](callContext))        
  }
          
  messageDocs += deleteCustomerAttributeDoc
  def deleteCustomerAttributeDoc = MessageDoc(
    process = "obp.deleteCustomerAttribute",
    messageFormat = messageFormat,
    description = "Delete Customer Attribute",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
     OutBoundDeleteCustomerAttribute(outboundAdapterCallContext=MessageDocsSwaggerDefinitions.outboundAdapterCallContext,
      customerAttributeId=customerAttributeIdExample.value)
    ),
    exampleInboundMessage = (
     InBoundDeleteCustomerAttribute(inboundAdapterCallContext=MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
      status=MessageDocsSwaggerDefinitions.inboundStatus,
      data=true)
    ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def deleteCustomerAttribute(customerAttributeId: String, callContext: Option[CallContext]): OBPReturnType[Box[Boolean]] = {
        import com.openbankproject.commons.dto.{InBoundDeleteCustomerAttribute => InBound, OutBoundDeleteCustomerAttribute => OutBound}  
        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, customerAttributeId)
        val response: Future[Box[InBound]] = sendRequest[InBound]("obp_delete_customer_attribute", req, callContext)
        response.map(convertToTuple[Boolean](callContext))        
  }
          
// ---------- created on 2020-12-14T15:30:08Z
//---------------- dynamic end ---------------------please don't modify this line                    

  private val availableOperation = DynamicEntityOperation.values.map(it => s""""$it"""").mkString("[", ", ", "]")

  messageDocs += dynamicEntityProcessDoc
  def dynamicEntityProcessDoc = MessageDoc(
    process = "obp.dynamicEntityProcess",
    messageFormat = messageFormat,
    description = s"operate committed dynamic entity data, the available value of 'operation' can be: ${availableOperation}",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundDynamicEntityProcessDoc(outboundAdapterCallContext = OutboundAdapterCallContext(correlationId=correlationIdExample.value,
        sessionId=Some(sessionIdExample.value),
        consumerId=Some(consumerIdExample.value),
        generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
          value=valueExample.value))),
        outboundAdapterAuthInfo=Some( OutboundAdapterAuthInfo(userId=Some(userIdExample.value),
          username=Some(usernameExample.value),
          linkedCustomers=Some(List( BasicLinkedCustomer(customerId=customerIdExample.value,
            customerNumber=customerNumberExample.value,
            legalName=legalNameExample.value))),
          userAuthContext=Some(List( BasicUserAuthContext(key=keyExample.value,
            value=valueExample.value))),
          authViews=Some(List( AuthView(view= ViewBasic(id=viewIdExample.value,
            name=viewNameExample.value,
            description=viewDescriptionExample.value),
            account= AccountBasic(id=accountIdExample.value,
              accountRoutings=List( AccountRouting(scheme=accountRoutingSchemeExample.value,
                address=accountRoutingAddressExample.value)),
              customerOwners=List( InternalBasicCustomer(bankId=bankIdExample.value,
                customerId=customerIdExample.value,
                customerNumber=customerNumberExample.value,
                legalName=legalNameExample.value,
                dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")))),
              userOwners=List( InternalBasicUser(userId=userIdExample.value,
                emailAddress=emailExample.value,
                name=usernameExample.value))))))))),
        operation = DynamicEntityOperation.UPDATE,
        entityName = "FooBar",
        requestBody = Some(FooBar(name = "James Brown", number = 1234567890)),
        entityId = Some("foobar-id-value"))
      ),
    exampleInboundMessage = (
      InBoundDynamicEntityProcessDoc(inboundAdapterCallContext= InboundAdapterCallContext(correlationId=correlationIdExample.value,
        sessionId=Some(sessionIdExample.value),
        generalContext=Some(List( BasicGeneralContext(key=keyExample.value,
          value=valueExample.value)))),
        status= Status(errorCode=statusErrorCodeExample.value,
          backendMessages=List( InboundStatusMessage(source=sourceExample.value,
            status=inboundStatusMessageStatusExample.value,
            errorCode=inboundStatusMessageErrorCodeExample.value,
            text=inboundStatusMessageTextExample.value))),
        data=FooBar(name = "James Brown", number = 1234567890, fooBarId = Some("foobar-id-value")))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def dynamicEntityProcess(operation: DynamicEntityOperation,
                                    entityName: String,
                                    requestBody: Option[JObject],
                                    entityId: Option[String],
                                    bankId: Option[String],
                                    callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = {
    import com.openbankproject.commons.dto.{InBoundDynamicEntityProcess => InBound, OutBoundDynamicEntityProcess => OutBound}
    val procedureName = StringHelpers.snakify("dynamicEntityProcess")
    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull , operation, entityName, requestBody, entityId, bankId)
    val result: OBPReturnType[Box[JValue]] = sendRequest[InBound](procedureName, req, callContext).map(convertToTuple(callContext))
    result
  }

  private[this] def sendRequest[T <: InBoundTrait[_]: TypeTag : Manifest](procedureName: String, outBound: TopicTrait, callContext: Option[CallContext]): Future[Box[T]] = {
    //transfer accountId to accountReference and customerId to customerReference in outBound
    this.convertToReference(outBound)
    Future{
      StoredProcedureUtils.callProcedure[T](procedureName, outBound)
    }.map(convertToId(_)) recoverWith {
      case e: Exception => Future(Failure(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}"))
    }
  }


  //-----helper methods

  //TODO hongwei confirm the third valu: OutboundAdapterCallContext#adapterAuthInfo
  private[this] def buildCallContext(inboundAdapterCallContext: InboundAdapterCallContext, callContext: Option[CallContext]): Option[CallContext] =
    for (cc <- callContext)
      yield cc.copy(correlationId = inboundAdapterCallContext.correlationId, sessionId = inboundAdapterCallContext.sessionId)

  private[this] def buildCallContext(boxedInboundAdapterCallContext: Box[InboundAdapterCallContext], callContext: Option[CallContext]): Option[CallContext] = boxedInboundAdapterCallContext match {
    case Full(inboundAdapterCallContext) => buildCallContext(inboundAdapterCallContext, callContext)
    case _ => callContext
  }

  /**
   * helper function to convert customerId and accountId in a given instance
   * @param obj
   * @param customerIdConverter customerId converter, to or from customerReference
   * @param accountIdConverter accountId converter, to or from accountReference
   * @tparam T type of instance
   * @return modified instance
   */
  private def convertId[T](obj: T, customerIdConverter: String=> String, accountIdConverter: String=> String): T = {
    //1st: We must not convert when connector == mapped. this will ignore the implicitly_convert_ids props.
    //2rd: if connector != mapped, we still need the `implicitly_convert_ids == true`

    def isCustomerId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
        ownerType =:= typeOf[CustomerId] ||
        (fieldName.equalsIgnoreCase("customerId") && fieldType =:= typeOf[String]) ||
        (ownerType <:< typeOf[Customer] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
      }

    def isAccountId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
        ownerType <:< typeOf[AccountId] ||
        (fieldName.equalsIgnoreCase("accountId") && fieldType =:= typeOf[String])
        (ownerType <:< typeOf[CoreAccount] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[AccountBalance] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[AccountHeld] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
      }

    if(APIUtil.getPropsValue("connector","mapped") != "mapped" && APIUtil.getPropsAsBoolValue("implicitly_convert_ids",false)){
      ReflectUtils.resetNestedFields(obj){
        case (fieldName, fieldType, fieldValue: String, ownerType) if isCustomerId(fieldName, fieldType, fieldValue, ownerType) => customerIdConverter(fieldValue)
        case (fieldName, fieldType, fieldValue: String, ownerType) if isAccountId(fieldName, fieldType, fieldValue, ownerType) => accountIdConverter(fieldValue)
      }
      obj
    } else
      obj
  }

  /**
   * convert given instance nested CustomerId to customerReference, AccountId to accountReference
   * @param obj
   * @tparam T type of instance
   * @return modified instance
   */
  def convertToReference[T](obj: T): T = {
    import code.api.util.ErrorMessages.{CustomerNotFoundByCustomerId, InvalidAccountIdFormat}
    def customerIdConverter(customerId: String): String = MappedCustomerIdMappingProvider
      .getCustomerPlainTextReference(CustomerId(customerId))
      .openOrThrowException(s"$CustomerNotFoundByCustomerId the invalid customerId is $customerId")
    def accountIdConverter(accountId: String): String = MappedAccountIdMappingProvider
      .getAccountPlainTextReference(AccountId(accountId))
      .openOrThrowException(s"$InvalidAccountIdFormat the invalid accountId is $accountId")
    convertId[T](obj, customerIdConverter, accountIdConverter)
  }

  /**
   * convert given instance nested customerReference to CustomerId, accountReference to AccountId
   * @param obj
   * @tparam T type of instance
   * @return modified instance
   */
  def convertToId[T](obj: T): T = {
    import code.api.util.ErrorMessages.{CustomerNotFoundByCustomerId, InvalidAccountIdFormat}
    def customerIdConverter(customerReference: String): String = MappedCustomerIdMappingProvider
      .getOrCreateCustomerId(customerReference)
      .map(_.value)
      .openOrThrowException(s"$CustomerNotFoundByCustomerId the invalid customerReference is $customerReference")
    def accountIdConverter(accountReference: String): String = MappedAccountIdMappingProvider
      .getOrCreateAccountId(accountReference)
      .map(_.value).openOrThrowException(s"$InvalidAccountIdFormat the invalid accountReference is $accountReference")
    if(obj.isInstanceOf[EmptyBox]) {
        obj
    } else {
      convertId[T](obj, customerIdConverter, accountIdConverter)
    }
  }
}
object StoredProcedureConnector_vDec2019 extends StoredProcedureConnector_vDec2019


