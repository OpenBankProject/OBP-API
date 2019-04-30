package code.bankconnectors.vMar2017

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

import java.util.Date

import code.api.util.APIUtil.MessageDoc
import code.api.util.ErrorMessages._
import code.api.util._
import code.api.v2_1_0._
import code.bankconnectors._
import code.branches.Branches.Branch
import code.fx.{FXRate, fx}
import code.kafka.KafkaHelper
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.comments.Comments
import code.metadata.counterparties.Counterparties
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import code.model._
import code.model.dataAccess._
import com.openbankproject.commons.model.Product
import code.transaction.MappedTransaction
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._
import code.transactionrequests.TransactionRequests._
import code.transactionrequests.{TransactionRequestTypeCharge, TransactionRequests}
import code.util.Helper.MdcLoggable
import code.util.{Helper, TTLCache}
import code.views.Views
import com.openbankproject.commons.model.{Bank, _}
import net.liftweb.common._
import net.liftweb.mapper._
import net.liftweb.util.Helpers._

import scala.collection.immutable.{Nil, Seq}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait KafkaMappedConnector_vMar2017 extends Connector with KafkaHelper with MdcLoggable {

  implicit override val nameOfConnector = KafkaMappedConnector_vMar2017.getClass.getSimpleName

  // Local TTL Cache
  val cacheTTL              = APIUtil.getPropsValue("connector.cache.ttl.seconds", "10").toInt
  val cachedUser            = TTLCache[InboundValidatedUser](cacheTTL)
  val cachedBank            = TTLCache[InboundBank](cacheTTL)
  val cachedAccount         = TTLCache[InboundAccount_vMar2017](cacheTTL)
  val cachedBanks           = TTLCache[List[InboundBank]](cacheTTL)
  val cachedAccounts        = TTLCache[List[InboundAccount_vMar2017]](cacheTTL)
  val cachedPublicAccounts  = TTLCache[List[InboundAccount_vMar2017]](cacheTTL)
  val cachedUserAccounts    = TTLCache[List[InboundAccount_vMar2017]](cacheTTL)
  val cachedFxRate          = TTLCache[InboundFXRate](cacheTTL)
  val cachedCounterparty    = TTLCache[InboundCounterparty](cacheTTL)
  val cachedTransactionRequestTypeCharge = TTLCache[InboundTransactionRequestTypeCharge](cacheTTL)


  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka


  val messageFormat: String  = "Mar2017"

  val currentResourceUserId = AuthUser.getCurrentResourceUserUserId

  // Each Message Doc has a process, description, example outbound and inbound messages.

  messageDocs += MessageDoc(
    process = "obp.get.User",
    messageFormat = messageFormat,
    description = "getUser from kafka ",
    exampleOutboundMessage = (
      OutboundUserByUsernamePasswordBase(
        messageFormat = messageFormat,
        action = "obp.get.User",
        username = "susan.uk.29@example.com",
        password = "2b78e8"
      )
    ),
    exampleInboundMessage = (
      InboundValidatedUser(
        errorCode = "OBP-6001: ...",
        List(InboundStatusMessage("ESB", "Success", "0", "OK")),
        email = "susan.uk.29@example.com",
        displayName = "susan"
      )
    )
  )
  
  override def getUser(
    username: String,
    password: String
  ): Box[InboundUser] = {
    for {
      req <- Full(OutboundUserByUsernamePasswordBase(
        action = "obp.get.User",
        messageFormat = messageFormat,
        username = username,
        password = password))
      u <- tryo { cachedUser.getOrElseUpdate(req.toString, () => process(req).extract[InboundValidatedUser]) }
      recUsername <- tryo { u.displayName }
    } yield {
      if (username == u.displayName) new InboundUser(recUsername, password, recUsername)
      else null
    }
  }

  // TODO this is confused ? method name is updateUserAccountViews, but action is 'obp.get.Accounts'
  messageDocs += MessageDoc(
    process = "obp.get.Accounts",
    messageFormat = messageFormat,
    description = "updateUserAccountViews from kafka ",
    exampleOutboundMessage = (
      OutboundUserAccountViewsBase(
        action = "obp.get.Accounts",
        messageFormat = messageFormat,
        username = "susan.uk.29@example.com",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        bankId = "gh.29.uk"
      )
    ),
    exampleInboundMessage = (
      InboundAccount_vMar2017(
        errorCode = "OBP-6001: ...",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        bankId = "gh.29.uk",
        label = "Good",
        number = "123",
        `type` = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        iban = "12345",
        owners = "Susan" :: " Frank" :: Nil,
        generatePublicView = true,
        generateAccountantsView = true,
        generateAuditorsView = true
      )
        :: InboundAccount_vMar2017(
        errorCode = "OBP-6001: ...",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        bankId = "gh.29.uk",
        label = "Good",
        number = "123",
        `type` = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        iban = "12345",
        owners = "Susan" :: " Frank" :: Nil,
        generatePublicView = true,
        generateAccountantsView = true,
        generateAuditorsView = true
      )
        :: Nil
    )
  )

  override def updateUserAccountViewsOld( user: ResourceUser ) = {
    val accounts: List[InboundAccount_vMar2017] = getBanks(None).map(_._1).openOrThrowException(attemptedToOpenAnEmptyBox).flatMap { bank => {
      val bankId = bank.bankId.value
      logger.info(s"ObpJvm updateUserAccountViews for user.email ${user.email} user.name ${user.name} at bank ${bankId}")
      for {
        username <- tryo {user.name}
        req <- Full(OutboundUserAccountViewsBase(
          messageFormat = messageFormat,
          action = "obp.get.Accounts",
          username = user.name,
          userId = user.name,
          bankId = bankId))

        // Generate random uuid to be used as request-response match id
        } yield {
          cachedUserAccounts.getOrElseUpdate(req.toString, () => process(req).extract[List[InboundAccount_vMar2017]])
        }
      }
    }.flatten

    val views = for {
      acc <- accounts
      username <- tryo {user.name}
      views <- tryo {createViews( BankId(acc.bankId),
        AccountId(acc.accountId),
        acc.owners.contains(username),
        acc.generatePublicView,
        acc.generateAccountantsView,
        acc.generateAuditorsView
      )}
      existing_views <- tryo {Views.views.vend.viewsForAccount(BankIdAccountId(BankId(acc.bankId), AccountId(acc.accountId)))}
    } yield {
      setAccountHolder(username, BankId(acc.bankId), AccountId(acc.accountId), acc.owners)
      views.foreach(v => {
        Views.views.vend.addPermission(v.uid, user)
        logger.info(s"------------> updated view ${v.uid} for resourceuser ${user} and account ${acc}")
      })
      existing_views.filterNot(_.users.contains(user.userPrimaryKey)).foreach (v => {
        Views.views.vend.addPermission(v.uid, user)
        logger.info(s"------------> added resourceuser ${user} to view ${v.uid} for account ${acc}")
      })
    }
  }

  messageDocs += MessageDoc(
    process = "obp.get.Banks",
    messageFormat = messageFormat,
    description = "getBanks",
    exampleOutboundMessage = (
      OutboundBanksBase(
        action = "obp.get.Banks",
        messageFormat = messageFormat,
        username = "susan.uk.29@example.com",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a"
      )
    ),
    exampleInboundMessage = (
      InboundBank(
        bankId = "gh.29.uk",
        name = "sushan",
        logo = "TESOBE",
        url = "https://tesobe.com/"
      ):: Nil
    )
  )

  //gets banks handled by this connector
  override def getBanks(callContext: Option[CallContext]) = {
    val req = OutboundBanksBase(
      messageFormat = messageFormat,
      action = "obp.get.Banks",
      username = currentResourceUserId,
      userId = AuthUser.getCurrentUserUsername
    )

    logger.debug(s"Kafka getBanks says: req is: $req")

    logger.debug(s"Kafka getBanks before cachedBanks.getOrElseUpdate")
    val rList = {
      cachedBanks.getOrElseUpdate( req.toString, () => process(req).extract[List[InboundBank]])
    }
    logger.debug(s"Kafka getBanks says rList is $rList")

    // Loop through list of responses and create entry for each
    val res = { for ( r <- rList ) yield {
        new Bank2(r)
      }
    }
    // Return list of results

    logger.debug(s"Kafka getBanks says res is $res")
    Full(res, callContext)
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.ChallengeThreshold",
    messageFormat = messageFormat,
    description = "getChallengeThreshold from kafka ",
    exampleOutboundMessage = (
      OutboundChallengeThresholdBase(
        messageFormat = messageFormat,
        action = "obp.get.ChallengeThreshold",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        viewId = "owner",
        transactionRequestType = SANDBOX_TAN.toString,
        currency = "GBP",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com"
      )
    ),
    exampleInboundMessage = (
      InboundChallengeLevel(
        errorCode = "OBP-6001: ...",
        limit = "1000",
        currency = "EUR"
      )
    )
  )

  // Gets current challenge level for transaction request
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, username: String, callContext: Option[CallContext]) = Future{
    // Create argument list
    val req = OutboundChallengeThresholdBase(
      action = "obp.get.ChallengeThreshold",
      messageFormat = messageFormat,
      bankId = bankId,
      accountId = accountId,
      viewId = viewId,
      transactionRequestType = transactionRequestType,
      currency = currency,
      userId = userId,
      username = username)

    val r: Option[InboundChallengeLevel] = process(req).extractOpt[InboundChallengeLevel]
    // Return result
    r match {
      // Check does the response data match the requested data
      case Some(x)  => (Full(AmountOfMoney(x.currency, x.limit)), callContext)
      case _ => {
        val limit = BigDecimal("0")
        val rate = fx.exchangeRate ("EUR", currency, Some(bankId))
        val convertedLimit = fx.convert(limit, rate)
        (Full(AmountOfMoney(currency,convertedLimit.toString())), callContext)
      }
    }
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.ChargeLevel",
    messageFormat = messageFormat,
    description = "ChargeLevel from kafka ",
    exampleOutboundMessage = (OutboundChargeLevelBase(
      action = "obp.get.ChargeLevel",
      messageFormat = messageFormat,
      bankId = "gh.29.uk",
      accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
      viewId = "owner",
      userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
      username = "susan.uk.29@example.com",
      transactionRequestType = SANDBOX_TAN.toString,
      currency = "EUR"
    )
    ),
    exampleInboundMessage = (
      InboundChargeLevel(
        errorCode = "OBP-6001: ...",
        currency = "EUR",
        amount = ""
      )
    )
  )
  
  override def getChargeLevel(
    bankId: BankId,
    accountId: AccountId,
    viewId: ViewId,
    userId: String,
    username: String,
    transactionRequestType: String,
    currency: String,
    callContext:Option[CallContext]) = Future {
    // Create argument list
    val req = OutboundChargeLevelBase(
      action = "obp.get.ChargeLevel",
      messageFormat = messageFormat,
      bankId = bankId.value,
      accountId = accountId.value,
      viewId = viewId.value,
      transactionRequestType = transactionRequestType,
      currency = currency,
      userId = userId,
      username = username
    )

    val r: Option[InboundChargeLevel] = process(req).extractOpt[InboundChargeLevel]
    // Return result
    val chargeValue = r match {
      // Check does the response data match the requested data
      case Some(x) => AmountOfMoney(x.currency, x.amount)
      case _ => {
        AmountOfMoney("EUR", "0.0001")
      }
    }
    (Full(chargeValue), callContext)
  }
  
  messageDocs += MessageDoc(
    process = "obp.create.Challenge",
    messageFormat = messageFormat,
    description = "CreateChallenge from kafka ",
    exampleOutboundMessage = (
      OutboundChallengeBase(
        action = "obp.create.Challenge",
        messageFormat = messageFormat,
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        transactionRequestType = SANDBOX_TAN.toString,
        transactionRequestId = "1234567"
      )
    ),
    exampleInboundMessage = (
      InboundCreateChallange(
        errorCode = "OBP-6001: ...",
        challengeId = "1234567"
      )
    )
  )

  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, callContext: Option[CallContext]) = Future {
    // Create argument list
    val req = OutboundChallengeBase(
      messageFormat = messageFormat,
      action = "obp.create.Challenge",
      bankId = bankId.value,
      accountId = accountId.value,
      userId = userId,
      username = AuthUser.getCurrentUserUsername,
      transactionRequestType = transactionRequestType.value,
      transactionRequestId = transactionRequestId
    )
  
    val r: Option[InboundCreateChallange] = process(req
    ).extractOpt[InboundCreateChallange]
    // Return result
    r match {
      // Check does the response data match the requested data
      case Some(x) => (Full(x.challengeId), callContext)
      case _ => (Empty, callContext)
    }
  }
  
  messageDocs += MessageDoc(
    process = "obp.validate.ChallengeAnswer",
    messageFormat = messageFormat,
    description = "validateChallengeAnswer from kafka ",
    exampleOutboundMessage = (
      OutboundChallengeAnswerBase(
        messageFormat = messageFormat,
        action = "obp.validate.ChallengeAnswer",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        challengeId = "1234",
        hashOfSuppliedAnswer = ""
      )
    ),
    exampleInboundMessage = (
      InboundValidateChallangeAnswer(
        errorCode = "OBP-6001: ...",
        answer = ""
      )
    )
  )
  override def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]) = Future {
    // Create argument list
    val req = OutboundChallengeAnswerBase(
      messageFormat = messageFormat,
      action = "obp.validate.ChallengeAnswer",
      userId = currentResourceUserId,
      username = AuthUser.getCurrentUserUsername,
      challengeId = challengeId,
      hashOfSuppliedAnswer = hashOfSuppliedAnswer)

    val r: Option[InboundValidateChallangeAnswer] = process(req).extractOpt[InboundValidateChallangeAnswer]
    // Return result
    r match {
      // Check does the response data match the requested data
      case Some(x)  => (Full(x.answer.toBoolean),callContext)
      case _        => (Empty,callContext)
    }
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.Bank",
    messageFormat = messageFormat,
    description = "getBank from kafka ",
    exampleOutboundMessage = (
      OUTTBank(
        action = "obp.get.Bank",
        messageFormat = messageFormat,
        bankId = "gh.29.uk",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com"
      )
    ),
    exampleInboundMessage = (
      InboundBank(
        bankId = "gh.29.uk",
        name = "sushan",
        logo = "TESOBE",
        url = "https://tesobe.com/"
      )
    )
  )
  override def getBank(bankId: BankId, callContext: Option[CallContext]) = {
    // Create argument list
    val req = OUTTBank(
      messageFormat = messageFormat,
      action = "obp.get.Bank",
      bankId = bankId.toString,
      userId = currentResourceUserId,
      username = AuthUser.getCurrentUserUsername)

    val r = {
      cachedBank.getOrElseUpdate(req.toString, () => process(req).extract[InboundBank])
    }
    // Return result
    Full(new Bank2(r), callContext)
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.Transaction",
    messageFormat = messageFormat,
    description = "getTransaction from kafka ",
    exampleOutboundMessage = (
      OutboundTransactionQueryBase(
        action = "obp.get.Transaction",
        messageFormat = messageFormat,
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        transactionId = ""
      )
    ),
    exampleInboundMessage = (
      InternalTransaction(
        errorCode = "OBP-6001: ...",
        List(InboundStatusMessage("ESB", "Success", "0", "OK")),
        transactionId = "1234",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        amount = "100",
        bankId = "gh.29.uk",
        completedDate = "",
        counterpartyId = "1234",
        counterpartyName = "obp",
        currency = "EUR",
        description = "Good Boy",
        newBalanceAmount = "10000",
        newBalanceCurrency = "1000",
        postedDate = "",
        `type` = "AC",
        userId = "1234"
      )
    )
  )
  // Gets transaction identified by bankid, accountid and transactionId
  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext])= {
    val req = OutboundTransactionQueryBase(
      messageFormat = messageFormat,
      action = "obp.get.Transaction",
      userId = currentResourceUserId,
      username = AuthUser.getCurrentUserUsername,
      bankId = bankId.toString,
      accountId = accountId.toString,
      transactionId = transactionId.toString)

    // Since result is single account, we need only first list entry
    val r = process(req).extractOpt[InternalTransaction]
    r match {
      // Check does the response data match the requested data
      case Some(x) if transactionId.value != x.transactionId => Failure(ErrorMessages.InvalidConnectorResponseForGetTransaction, Empty, Empty)
      case Some(x) if transactionId.value == x.transactionId => createNewTransaction(x).map(transaction =>(transaction, callContext))
      case _ => Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
    }

  }
  
  messageDocs += MessageDoc(
    process = "obp.get.Transactions",
    messageFormat = messageFormat,
    description = "getTransactions from kafka",
    exampleOutboundMessage = (
      OutboundTransactionsQueryWithParamsBase(
        messageFormat = messageFormat,
        action = "obp.get.Transactions",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        queryParams = ""
      )
    ),
    exampleInboundMessage = (
      InternalTransaction(
        errorCode = "OBP-6001: ...",
        List(InboundStatusMessage("ESB", "Success", "0", "OK")),
        transactionId = "1234",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        amount = "100",
        bankId = "gh.29.uk",
        completedDate = "",
        counterpartyId = "1234",
        counterpartyName = "obp",
        currency = "EUR",
        description = "Good Boy",
        newBalanceAmount = "10000",
        newBalanceCurrency = "1000",
        postedDate = "",
        `type` = "AC",
        userId = "1234"
      ) :: Nil
    )
  )
  //TODO, this action is different from method name
  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*)= {
    val limit: OBPLimit = queryParams.collect { case OBPLimit(value) => OBPLimit(value) }.headOption.get
    val offset = queryParams.collect { case OBPOffset(value) => OBPOffset(value) }.headOption.get
    val fromDate = queryParams.collect { case OBPFromDate(date) => OBPFromDate(date) }.headOption.get
    val toDate = queryParams.collect { case OBPToDate(date) => OBPToDate(date)}.headOption.get
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(field, direction) => OBPOrdering(field, direction)}.headOption.get
    val optionalParams = Seq(limit, offset, fromDate, toDate, ordering)

    val req = OutboundTransactionsQueryWithParamsBase(
      messageFormat = messageFormat,
      action = "obp.get.Transactions",
      userId = currentResourceUserId,
      username = AuthUser.getCurrentUserUsername,
      bankId = bankId.toString,
      accountId = accountId.toString,
      queryParams = queryParams.toString)

    implicit val formats = CustomJsonFormats.formats
    val rList = process(req).extract[List[InternalTransaction]]
    // Check does the response data match the requested data
    val isCorrect = rList.forall(x=>x.accountId == accountId.value && x.bankId == bankId.value)
    if (!isCorrect) throw new Exception(ErrorMessages.InvalidConnectorResponseForGetTransactions)
    // Populate fields and generate result
    val res = for {
      r <- rList
      transaction <- createNewTransaction(r)
    } yield {
      transaction
    }
    Full(res, callContext)
    //TODO is this needed updateAccountTransactions(bankId, accountId)
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.Account",
    messageFormat = messageFormat,
    description = "getBankAccount from kafka",
    exampleOutboundMessage = (
      OutboundBankAccountBase(
        action = "obp.get.Account",
        messageFormat = messageFormat,
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"
      )
    ),
    exampleInboundMessage = (
      InboundAccount_vMar2017(
        errorCode = "OBP-6001: ...",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        bankId = "gh.29.uk",
        label = "Good",
        number = "123",
        `type` = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        iban = "12345",
        owners = "Susan" :: "Frank" :: Nil,
        generatePublicView = true,
        generateAccountantsView = true,
        generateAuditorsView = true
      )
    )
  )
  override def getBankAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]): Box[(BankAccount, Option[CallContext])] = {
    // Generate random uuid to be used as request-response match id
    val req = OutboundBankAccountBase(
      action = "obp.get.Account",
      messageFormat = messageFormat,
      userId = currentResourceUserId,
      username = AuthUser.getCurrentUserUsername,
      bankId = bankId.toString,
      accountId = accountId.value
    )

    // Since result is single account, we need only first list entry
    implicit val formats = CustomJsonFormats.formats
    val r = {
      cachedAccount.getOrElseUpdate( req.toString, () => process(req).extract[InboundAccount_vMar2017])
    }
    // Check does the response data match the requested data
    val accResp = List((BankId(r.bankId), AccountId(r.accountId))).toSet
    val acc = List((bankId, accountId)).toSet
    if ((accResp diff acc).size > 0) throw new Exception(ErrorMessages.InvalidConnectorResponseForGetBankAccount)

    createMappedAccountDataIfNotExisting(r.bankId, r.accountId, r.label)

    Full(new BankAccount2(r), callContext)
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.Accounts",
    messageFormat = messageFormat,
    description = "getBankAccounts from kafka",
    exampleOutboundMessage = (
      OutboundBankAccountsBase(
        messageFormat = messageFormat,
        action = "obp.get.Accounts",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"
      )
    ),
    exampleInboundMessage = (
      InboundAccount_vMar2017(
        errorCode = "OBP-6001: ...",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        bankId = "gh.29.uk",
        label = "Good",
        number = "123",
        `type` = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        iban = "12345",
        owners = "Susan" :: "Frank" :: Nil,
        generatePublicView = true,
        generateAccountantsView = true,
        generateAuditorsView = true
      ) :: InboundAccount_vMar2017(
        errorCode = "OBP-6001: ...",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        bankId = "gh.29.uk",
        label = "Good",
        number = "123",
        `type` = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        iban = "12345",
        owners = "Susan" :: "Frank" :: Nil,
        generatePublicView = true,
        generateAccountantsView = true,
        generateAuditorsView = true
      ) :: Nil
    )
  )

  //TODO the method name is different from action
  messageDocs += MessageDoc(
    process = "obp.get.Account",
    messageFormat = messageFormat,
    description = "getAccountByNumber from kafka",
    exampleOutboundMessage = (
      OutboundAccountByNumberBase(
        action = "obp.get.Account",
        messageFormat = messageFormat,
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "gh.29.uk",
        number = ""
      )
    ),
    exampleInboundMessage = (
      InboundAccount_vMar2017(
        errorCode = "OBP-6001: ...",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        bankId = "gh.29.uk",
        label = "Good",
        number = "123",
        `type` = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        iban = "12345",
        owners = "Susan" :: " Frank" :: Nil,
        generatePublicView = true,
        generateAccountantsView = true,
        generateAuditorsView = true
      )
    )
  )

  private def getAccountByNumber(bankId : BankId, number : String) : Box[BankAccount] = {
    // Generate random uuid to be used as request-respose match id
    val req = OutboundAccountByNumberBase(
      messageFormat = messageFormat,
      action = "obp.get.Account",
      userId = currentResourceUserId,
      username = AuthUser.getCurrentUserUsername,
      bankId = bankId.toString,
      number = number
    )

    // Since result is single account, we need only first list entry
    implicit val formats = CustomJsonFormats.formats
    val r = {
      cachedAccount.getOrElseUpdate( req.toString, () => process(req).extract[InboundAccount_vMar2017])
    }
    createMappedAccountDataIfNotExisting(r.bankId, r.accountId, r.label)
    Full(new BankAccount2(r))
  }
  
  override def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = {
    //note: kafka mode just used the mapper data
    LocalMappedConnector.getCounterparty(thisBankId, thisAccountId, couterpartyId)
  }
  
  
  messageDocs += MessageDoc(
    process = "obp.get.CounterpartyByCounterpartyId",
    messageFormat = messageFormat,
    description = "getCounterpartyByCounterpartyId from kafka ",
    exampleOutboundMessage = (
      OutboundCounterpartyByCounterpartyIdBase(
        messageFormat = messageFormat,
        action = "obp.get.CounterpartyByCounterpartyId",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        counterpartyId = "12344"
      )
    ),
    exampleInboundMessage = (
      InboundCounterparty(
        errorCode = "OBP-6001: ...",
        name = "sushan",
        createdByUserId = "12345",
        thisBankId = "gh.29.uk",
        thisAccountId = "12344",
        thisViewId = "owner",
        counterpartyId = "123",
        otherBankRoutingScheme = "obp",
        otherAccountRoutingScheme = "obp",
        otherBankRoutingAddress = "1234",
        otherAccountRoutingAddress = "1234",
        otherBranchRoutingScheme = "OBP",
        otherBranchRoutingAddress = "Berlin",
        isBeneficiary = true
      )
    )
  )

  override def getCounterpartyByCounterpartyIdFuture(counterpartyId: CounterpartyId, callContext: Option[CallContext]) = Future{
    if (APIUtil.getPropsAsBoolValue("get_counterparties_from_OBP_DB", true)) {
      (Counterparties.counterparties.vend.getCounterparty(counterpartyId.value), callContext)
    } else {
      val req = OutboundCounterpartyByCounterpartyIdBase(
        messageFormat = messageFormat,
        action = "obp.get.CounterpartyByCounterpartyId",
        userId = currentResourceUserId,
        username = AuthUser.getCurrentUserUsername,
        counterpartyId = counterpartyId.toString
      )
      // Since result is single account, we need only first list entry
      implicit val formats = CustomJsonFormats.formats
      val r = {
        cachedCounterparty.getOrElseUpdate(req.toString, () => process(req).extract[InboundCounterparty])
      }
      (tryo(CounterpartyTrait2(r)), callContext)
    }
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.CounterpartyByIban",
    messageFormat = messageFormat,
    description = "getCounterpartyByIban from kafka ",
    exampleOutboundMessage = (
      OutboundCounterpartyByIbanBase(
        messageFormat = messageFormat,
        action = "obp.get.CounterpartyByIban",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        otherAccountRoutingAddress = "1234",
        otherAccountRoutingScheme = "1234"
      )
    ),
    exampleInboundMessage = (
      CounterpartyTrait2(
        InboundCounterparty(
          errorCode = "OBP-6001: ...",
          name = "sushan",
          createdByUserId = "12345",
          thisBankId = "gh.29.uk",
          thisAccountId = "12344",
          thisViewId = "owner",
          counterpartyId = "123",
          otherBankRoutingScheme = "obp",
          otherAccountRoutingScheme = "obp",
          otherBankRoutingAddress = "1234",
          otherAccountRoutingAddress = "1234",
          otherBranchRoutingScheme = "OBP",
          otherBranchRoutingAddress = "Berlin",
          isBeneficiary = true
        )
      )
    )
  )

  override def getCounterpartyByIban(iban: String, callContext: Option[CallContext])= Future{
    if (APIUtil.getPropsAsBoolValue("get_counterparties_from_OBP_DB", true)) {
      (Counterparties.counterparties.vend.getCounterpartyByIban(iban), callContext)
    } else {
      val req = OutboundCounterpartyByIbanBase(
        messageFormat = messageFormat,
        action = "obp.get.CounterpartyByIban",
        userId = currentResourceUserId,
        username = AuthUser.getCurrentUserUsername,
        otherAccountRoutingAddress = iban,
        otherAccountRoutingScheme = "IBAN"
      )
      val r = process(req).extract[InboundCounterparty]
      (tryo{CounterpartyTrait2(r)}, callContext)
    }
  }
  
  messageDocs += MessageDoc(
    process = "obp.put.Transaction",
    messageFormat = messageFormat,
    description = "saveTransaction from kafka",
    exampleOutboundMessage = (
      OutboundSaveTransactionBase(
        action = "obp.put.Transaction",
        messageFormat = messageFormat,
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",

        // fromAccount
        fromAccountName = "OBP",
        fromAccountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        fromAccountBankId = "gh.29.uk",

        // transaction details
        transactionId = "1234",
        transactionRequestType = SANDBOX_TAN.toString,
        transactionAmount = "100",
        transactionCurrency = "EUR",
        transactionChargePolicy = "RECEIVER",
        transactionChargeAmount = "1000",
        transactionChargeCurrency = "12",
        transactionDescription = "Tesobe is a good company !",
        transactionPostedDate = "",

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
    exampleInboundMessage = (
      InboundTransactionId(
        
        errorCode = "OBP-6001: ...",
        transactionId = "1234"
      )
    )
  )

  /**
   * Saves a transaction with amount @amount and counterparty @counterparty for account @account. Returns the id
   * of the saved transaction.
   */
  private def saveTransaction(fromAccount: BankAccount,
                              toAccount: BankAccount,
                              transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                              amount: BigDecimal,
                              description: String,
                              transactionRequestType: TransactionRequestType,
                              chargePolicy: String): Box[TransactionId] = {
  
    val postedDate = APIUtil.DateWithMsFormat.format(now)
    val transactionId = APIUtil.generateUUID()

    val req = OutboundSaveTransactionBase(
      messageFormat = messageFormat,
      action = "obp.put.Transaction",
      userId = currentResourceUserId,
      username = AuthUser.getCurrentUserUsername,
  
      // fromAccount
      fromAccountName = fromAccount.name,
      fromAccountId = fromAccount.accountId.value,
      fromAccountBankId = fromAccount.bankId.value,
  
      // transaction details
      transactionId = transactionId,
      transactionRequestType = transactionRequestType.value,
      transactionAmount = amount.bigDecimal.toString,
      transactionCurrency = fromAccount.currency,
      transactionChargePolicy = chargePolicy,
      transactionChargeAmount = "0.0", // TODO get correct charge amount
      transactionChargeCurrency = fromAccount.currency, // TODO get correct charge currency
      transactionDescription = description,
      transactionPostedDate = postedDate,
  
      // toCounterparty
      toCounterpartyId = toAccount.accountId.value,
      toCounterpartyName = toAccount.name,
      toCounterpartyCurrency = fromAccount.currency, // TODO toCounterparty.currency
      toCounterpartyRoutingAddress = toAccount.accountRoutingAddress,
      toCounterpartyRoutingScheme = toAccount.accountRoutingScheme,
      toCounterpartyBankRoutingAddress = toAccount.bankRoutingAddress,
      toCounterpartyBankRoutingScheme = toAccount.bankRoutingScheme
    )


    // Since result is single account, we need only first list entry
    val r = process(req)

    r.extract[InboundTransactionId] match {
      case r: InboundTransactionId => Full(TransactionId(r.transactionId))
      case _ => Empty
    }

  }
  messageDocs += MessageDoc(
    process = "obp.get.TransactionRequestStatusesImpl",
    messageFormat = messageFormat,
    description = "getTransactionRequestStatusesImpl from kafka",
    exampleOutboundMessage = (
      OutboundTransactionRequestStatusesBase(
        messageFormat = messageFormat,
        action = "obp.get.TransactionRequestStatusesImpl"
      )
    ),
    exampleInboundMessage = (
      InboundTransactionRequestStatus(
        transactionRequestId = "123",
        bulkTransactionsStatus = InboundTransactionStatus(
          transactionId = "1234",
          transactionStatus = "",
          transactionTimestamp = ""
        ) :: InboundTransactionStatus(
          transactionId = "1234",
          transactionStatus = "",
          transactionTimestamp = ""
        ) :: Nil
      )
    )
  )
  override def getTransactionRequestStatusesImpl() : Box[TransactionRequestStatus] = {
    logger.info(s"tKafka getTransactionRequestStatusesImpl sart: ")
    val req = OutboundTransactionRequestStatusesBase(
      messageFormat = messageFormat,
      action = "obp.get.TransactionRequestStatusesImpl"
    )
    //TODO need more clear error handling to user, if it is Empty or Error now,all response Empty.
    val r = try{
      val response = process(req).extract[InboundTransactionRequestStatus]
      Full(new TransactionRequestStatus2(response))
    }catch {
      case _ : Throwable => Empty
    }

    logger.info(s"Kafka getTransactionRequestStatusesImpl response: ${r.toString}")
    r
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.CurrentFxRate",
    messageFormat = messageFormat,
    description = "getCurrentFxRate from kafka",
    exampleOutboundMessage = (
      OutboundCurrentFxRateBase(
        action = "obp.get.CurrentFxRate",
        messageFormat = messageFormat,
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "bankid543",
        fromCurrencyCode = "1234",
        toCurrencyCode = ""
      )
    ),
    exampleInboundMessage = (
      InboundFXRate(
        errorCode = "OBP-6XXXX: .... ",
        bankId = "bankid654",
        fromCurrencyCode = "1234",
        toCurrencyCode = "1234",
        conversionValue = 123.44,
        inverseConversionValue = 123.44,
        effectiveDate = ""
      )
    )
  )
  // get the latest FXRate specified by fromCurrencyCode and toCurrencyCode.
  override def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = {
    // Create request argument list
    val req = OutboundCurrentFxRateBase(
      messageFormat = messageFormat,
      action = "obp.get.CurrentFxRate",
      userId = currentResourceUserId,
      username = AuthUser.getCurrentUserUsername,
      bankId = bankId.value,
      fromCurrencyCode = fromCurrencyCode,
      toCurrencyCode = toCurrencyCode)
    
    val r = {
      cachedFxRate.getOrElseUpdate(req.toString, () => process(req).extract[InboundFXRate])
    }
    // Return result
    Full(new FXRate2(r))
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.TransactionRequestTypeCharge",
    messageFormat = messageFormat,
    description = "getTransactionRequestTypeCharge from kafka",
    exampleOutboundMessage = (
      OutboundTransactionRequestTypeChargeBase(
        action = "obp.get.TransactionRequestTypeCharge",
        messageFormat = messageFormat,
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        viewId = "owner",
        transactionRequestType = ""
      )
    ),
    exampleInboundMessage = (
      InboundTransactionRequestTypeCharge(
        errorCode = "OBP-6001: ...",
        transactionRequestType = "",
        bankId = "gh.29.uk",
        chargeCurrency = "EUR",
        chargeAmount = "2",
        chargeSummary = " charge 1 eur"
      )
    )
  )
  //get the current charge specified by bankId, accountId, viewId and transactionRequestType
  override def getTransactionRequestTypeCharge(
    bankId: BankId,
    accountId: AccountId,
    viewId: ViewId,
    transactionRequestType: TransactionRequestType
  ): Box[TransactionRequestTypeCharge] = {
  
    // Create request argument list
    val req = OutboundTransactionRequestTypeChargeBase(
      messageFormat = messageFormat,
      action = "obp.get.TransactionRequestTypeCharge",
      userId = currentResourceUserId,
      username = AuthUser.getCurrentUserUsername,
      bankId = bankId.value,
      accountId = accountId.value,
      viewId = viewId.value,
      transactionRequestType = transactionRequestType.value
    )
    
    // send the request to kafka and get response
    // TODO the error handling is not good enough, it should divide the error, empty and no-response.
    val r =  tryo {cachedTransactionRequestTypeCharge.getOrElseUpdate(req.toString, () => process(req).extract[InboundTransactionRequestTypeCharge])}
    
    // Return result
    val result = r match {
      case Full(f) => Full(TransactionRequestTypeCharge2(f))
      case _ =>
        for {
          fromAccount <- getBankAccount(bankId, accountId)
          fromAccountCurrency <- tryo{ fromAccount.currency }
        } yield {
          TransactionRequestTypeCharge2(InboundTransactionRequestTypeCharge(
             
            errorCode = "OBP-6001: ...",
            transactionRequestType.value, 
            bankId.value,
            fromAccountCurrency, 
            "0.00", 
            "Warning! Default value!"
          )
          )
        }
    }
    
    result
  }
  
  
//////////////////////////////Following is not over Kafka now //////////////////////////  
  //////////////////////////////////////////////////////////////////////////////////////////  
  
  protected override def makePaymentImpl(
    fromAccount: BankAccount,
    toAccount: BankAccount,
    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
    amt: BigDecimal,
    description: String,
    transactionRequestType: TransactionRequestType,
    chargePolicy: String): Box[TransactionId] = {
    
    val sentTransactionId = saveTransaction(fromAccount,
                                            toAccount,
                                            transactionRequestCommonBody,
                                            amt,
                                            description,
                                            transactionRequestType,
                                            chargePolicy)
    
    sentTransactionId
  }
  
  
  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                            account : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                            status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = {
    TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl(transactionRequestId,
      transactionRequestType,
      account,
      counterparty,
      body,
      status,
      charge)
  }

  //Note: now call the local mapper to store data
  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = {
    LocalMappedConnector.saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId)
  }

  override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] = {
    TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestChallengeImpl(transactionRequestId, challenge)
  }

  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = {
    TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestStatusImpl(transactionRequestId, status)
  }


  override def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)
  }

  override def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)
  }

  /*
    Bank account creation
   */

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  //again assume national identifier is unique
  override def createBankAndAccount(
    bankName: String,
    bankNationalIdentifier: String,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String
  ) = {
    //don't require and exact match on the name, just the identifier
    val bank: Bank = MappedBank.find(By(MappedBank.national_identifier, bankNationalIdentifier)) match {
      case Full(b) =>
        logger.info(s"bank with id ${b.bankId} and national identifier ${b.nationalIdentifier} found")
        b
      case _ =>
        logger.info(s"creating bank with national identifier $bankNationalIdentifier")
        //TODO: need to handle the case where generatePermalink returns a permalink that is already used for another bank
        MappedBank.create
          .permalink(Helper.generatePermalink(bankName))
          .fullBankName(bankName)
          .shortBankName(bankName)
          .national_identifier(bankNationalIdentifier)
          .saveMe()
    }

    //TODO: pass in currency as a parameter?
    val account = createAccountIfNotExisting(
      bank.bankId,
      AccountId(APIUtil.generateUUID()),
      accountNumber,
      accountType,
      accountLabel,
      currency,
      0L,
      accountHolderName
    )

    Full((bank, account))
  }

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String) = {
    Full(getAccountByNumber(bankId, accountNumber) != null)
  }

  //remove an account and associated transactions
  override def removeAccount(bankId: BankId, accountId: AccountId) = {
    //delete comments on transactions of this account
    val commentsDeleted = Comments.comments.vend.bulkDeleteComments(bankId, accountId)

    //delete narratives on transactions of this account
    val narrativesDeleted = MappedNarrative.bulkDelete_!!(
      By(MappedNarrative.bank, bankId.value),
      By(MappedNarrative.account, accountId.value)
    )

    //delete narratives on transactions of this account
    val tagsDeleted = Tags.tags.vend.bulkDeleteTags(bankId, accountId)

    //delete WhereTags on transactions of this account
    val whereTagsDeleted = WhereTags.whereTags.vend.bulkDeleteWhereTags(bankId, accountId)

    //delete transaction images on transactions of this account
    val transactionImagesDeleted = TransactionImages.transactionImages.vend.bulkDeleteTransactionImage(bankId, accountId)

    //delete transactions of account
    val transactionsDeleted = MappedTransaction.bulkDelete_!!(
      By(MappedTransaction.bank, bankId.value),
      By(MappedTransaction.account, accountId.value)
    )

    //remove view privileges
    val privilegesDeleted = Views.views.vend.removeAllPermissions(bankId, accountId)

    //delete views of account
    val viewsDeleted = Views.views.vend.removeAllViews(bankId, accountId)

    //delete account
    val account = getBankAccount(bankId, accountId)

    val accountDeleted = account match {
      case acc => true //acc.delete_! //TODO
      // case _ => false
    }

    Full(commentsDeleted && narrativesDeleted && tagsDeleted && whereTagsDeleted && transactionImagesDeleted &&
      transactionsDeleted && privilegesDeleted && viewsDeleted && accountDeleted)
}

  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
  override def createSandboxBankAccount(
    bankId: BankId,
    accountId: AccountId,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    initialBalance: BigDecimal,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String
  ): Box[BankAccount] = {

    for {
      (bank, _)<- getBank(bankId, None) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
    } yield {

      val balanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(initialBalance, currency)
      createAccountIfNotExisting(bankId, accountId, accountNumber, accountType, accountLabel, currency, balanceInSmallestCurrencyUnits, accountHolderName)
    }

  }


  private def createAccountIfNotExisting(bankId: BankId, accountId: AccountId, accountNumber: String,
                                         accountType: String, accountLabel: String, currency: String,
                                         balanceInSmallestCurrencyUnits: Long, accountHolderName: String) : BankAccount = {
    getBankAccount(bankId, accountId) match {
      case Full(a) =>
        logger.info(s"account with id $accountId at bank with id $bankId already exists. No need to create a new one.")
        a
      case _ => null //TODO
        /*
       new  KafkaBankAccount
          .bank(bankId.value)
          .theAccountId(accountId.value)
          .accountNumber(accountNumber)
          .accountType(accountType)
          .accountLabel(accountLabel)
          .accountCurrency(currency)
          .accountBalance(balanceInSmallestCurrencyUnits)
          .holder(accountHolderName)
          .saveMe()
          */
    }
  }

  private def createMappedAccountDataIfNotExisting(bankId: String, accountId: String, label: String) : Boolean = {
    MappedBankAccountData.find(By(MappedBankAccountData.accountId, accountId),
                                    By(MappedBankAccountData.bankId, bankId)) match {
      case Empty =>
        val data = new MappedBankAccountData
        data.setAccountId(accountId)
        data.setBankId(bankId)
        data.setLabel(label)
        data.save()
        true
      case _ =>
        logger.info(s"account data with id $accountId at bank with id $bankId already exists. No need to create a new one.")
        false
    }
  }

  /*
    End of bank account creation
   */


  /*
    Transaction importer api
   */

  //used by the transaction import api
  override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal) = {

    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      (bank, _)<- getBank(bankId, None)
    } yield {
      //acc.balance = newBalance
      setBankAccountLastUpdated(bank.nationalIdentifier, acc.number, now).openOrThrowException(attemptedToOpenAnEmptyBox)
    }
  
    Full(result.getOrElse(false))
  }

  //transaction import api uses bank national identifiers to uniquely indentify banks,
  //which is unfortunate as theoretically the national identifier is unique to a bank within
  //one country
  private def getBankByNationalIdentifier(nationalIdentifier : String) : Box[Bank] = {
    MappedBank.find(By(MappedBank.national_identifier, nationalIdentifier))
  }


  private val bigDecimalFailureHandler : PartialFunction[Throwable, Unit] = {
    case ex : NumberFormatException => {
      logger.warn(s"could not convert amount to a BigDecimal: $ex")
    }
  }

  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, otherAccountHolder: String) = {
    //we need to convert from the legacy bankNationalIdentifier to BankId, and from the legacy accountNumber to AccountId
    val count = for {
      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
      account <- getAccountByNumber(bankId, accountNumber)
      amountAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(amount))
    } yield {

      val amountInSmallestCurrencyUnits =
        Helper.convertToSmallestCurrencyUnits(amountAsBigDecimal, account.currency)

      MappedTransaction.count(
        By(MappedTransaction.bank, bankId.value),
        By(MappedTransaction.account, account.accountId.value),
        By(MappedTransaction.amount, amountInSmallestCurrencyUnits),
        By(MappedTransaction.tFinishDate, completed),
        By(MappedTransaction.counterpartyAccountHolder, otherAccountHolder))
    }

    //icky
    Full(count.map(_.toInt) getOrElse 0)
  }

  //used by transaction import api
  override def createImportedTransaction(transaction: ImporterTransaction): Box[Transaction] = {
    //we need to convert from the legacy bankNationalIdentifier to BankId, and from the legacy accountNumber to AccountId
    val obpTransaction = transaction.obp_transaction
    val thisAccount = obpTransaction.this_account
    val nationalIdentifier = thisAccount.bank.national_identifier
    val accountNumber = thisAccount.number
    for {
      bank <- getBankByNationalIdentifier(transaction.obp_transaction.this_account.bank.national_identifier) ?~!
        s"No bank found with national identifier $nationalIdentifier"
      bankId = bank.bankId
      account <- getAccountByNumber(bankId, accountNumber)
      details = obpTransaction.details
      amountAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(details.value.amount))
      newBalanceAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(details.new_balance.amount))
      amountInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(amountAsBigDecimal, account.currency)
      newBalanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(newBalanceAsBigDecimal, account.currency)
      otherAccount = obpTransaction.other_account
      mappedTransaction = MappedTransaction.create
        .bank(bankId.value)
        .account(account.accountId.value)
        .transactionType(details.kind)
        .amount(amountInSmallestCurrencyUnits)
        .newAccountBalance(newBalanceInSmallestCurrencyUnits)
        .currency(account.currency)
        .tStartDate(details.posted.`$dt`)
        .tFinishDate(details.completed.`$dt`)
        .description(details.label)
        .counterpartyAccountNumber(otherAccount.number)
        .counterpartyAccountHolder(otherAccount.holder)
        .counterpartyAccountKind(otherAccount.kind)
        .counterpartyNationalId(otherAccount.bank.national_identifier)
        .counterpartyBankName(otherAccount.bank.name)
        .counterpartyIban(otherAccount.bank.IBAN)
        .saveMe()
      transaction <- mappedTransaction.toTransaction(account)
    } yield transaction
  }

  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) = {
    val result = for {
      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
      account <- getAccountByNumber(bankId, accountNumber)
    } yield {
        val acc = getBankAccount(bankId, account.accountId)
        acc match {
          case a => true //a.lastUpdate = updateDate //TODO
          // case _ => logger.warn("can't set bank account.lastUpdated because the account was not found"); false
        }
    }
    Full(result.getOrElse(false))
  }

  /*
    End of transaction importer api
   */


  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String) = {
    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      (bank, _)<- getBank(bankId, None)
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, accountId.value), By(MappedBankAccountData.bankId, bank.bankId.value))
    } yield {
      d.setLabel(label)
      d.save()
    }
    Full(result.getOrElse(false))
  }


  override def getProducts(bankId: BankId): Box[List[Product]] = Empty

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = Empty

  override  def createOrUpdateBranch(branch: Branch): Box[BranchT] = Empty
  
  override def createOrUpdateBank(
    bankId: String,
    fullBankName: String,
    shortBankName: String,
    logoURL: String,
    websiteURL: String,
    swiftBIC: String,
    national_identifier: String,
    bankRoutingScheme: String,
    bankRoutingAddress: String
  ): Box[Bank] = Empty


  override def getEmptyBankAccount(): Box[BankAccount] = {
    Full(new BankAccount2(
      InboundAccount_vMar2017(
        errorCode = "OBP-6001: ...",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        bankId = "gh.29.uk",
        label = "Good",
        number = "123",
        `type` = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        iban = "12345",
        owners = Nil,
        generatePublicView = true,
        generateAccountantsView = true,
        generateAuditorsView = true
      )
    )
    )
  }

  /////////////////////////////////////////////////////////////////////////////

  // Helper for creating a transaction
  def createNewTransaction(r: InternalTransaction):Box[Transaction] = {
    var datePosted: Date = null
    if (r.postedDate != null) // && r.details.posted.matches("^[0-9]{8}$"))
      datePosted = APIUtil.DateWithMsFormat.parse(r.postedDate)

    var dateCompleted: Date = null
    if (r.completedDate != null) // && r.details.completed.matches("^[0-9]{8}$"))
      dateCompleted = APIUtil.DateWithMsFormat.parse(r.completedDate)

    for {
        counterpartyId <- tryo{r.counterpartyId}
        counterpartyName <- tryo{r.counterpartyName}
        thisAccount <- getBankAccount(BankId(r.bankId), AccountId(r.accountId))
        //creates a dummy OtherBankAccount without an OtherBankAccountMetadata, which results in one being generated (in OtherBankAccount init)
        dummyOtherBankAccount <- tryo{createCounterparty(counterpartyId, counterpartyName, thisAccount, None)}
        //and create the proper OtherBankAccount with the correct "id" attribute set to the metadataId of the OtherBankAccountMetadata object
        //note: as we are passing in the OtherBankAccountMetadata we don't incur another db call to get it in OtherBankAccount init
        counterparty <- tryo{createCounterparty(counterpartyId, counterpartyName, thisAccount, Some(dummyOtherBankAccount.metadata))}
      } yield {
        // Create new transaction
        new Transaction(
                         r.transactionId, // uuid:String
                         TransactionId(r.transactionId), // id:TransactionId
                         thisAccount, // thisAccount:BankAccount
                         counterparty, // otherAccount:OtherBankAccount
                         r.`type`, // transactionType:String
                         BigDecimal(r.amount), // val amount:BigDecimal
                         thisAccount.currency, // currency:String
                         Some(r.description), // description:Option[String]
                         datePosted, // startDate:Date
                         dateCompleted, // finishDate:Date
                         BigDecimal(r.newBalanceAmount) // balance:BigDecimal)
        )
    }
  }



  // Helper for creating other bank account
  def createCounterparty(counterpartyId: String, counterpartyName: String, o: BankAccount, alreadyFoundMetadata : Option[CounterpartyMetadata]) = {
    new Counterparty(
      counterpartyId = alreadyFoundMetadata.map(_.getCounterpartyId).getOrElse(""),
      counterpartyName = counterpartyName,
      nationalIdentifier = "1234",
      otherBankRoutingAddress = None,
      otherAccountRoutingAddress = None,
      thisAccountId = AccountId(counterpartyId),
      thisBankId = BankId(""),
      kind = "1234",
      otherBankRoutingScheme = "obp",
      otherAccountRoutingScheme="obp",
      otherAccountProvider = "obp",
      isBeneficiary = true
    )
  }

}

object KafkaMappedConnector_vMar2017 extends KafkaMappedConnector_vMar2017{
  
}


