package code.bankconnectors

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
import java.util.{Date, Locale, UUID}

import code.accountholder.AccountHolders
import code.api.util.APIUtil.{MessageDoc, saveConnectorMetric}
import code.api.util.ErrorMessages
import code.api.v2_1_0._
import code.atms.Atms.AtmId
import code.atms.MappedAtm
import code.branches.Branches.{Branch, BranchId}
import code.branches.MappedBranch
import code.fx.{FXRate, fx}
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.comments.Comments
import code.metadata.counterparties.{Counterparties, CounterpartyTrait}
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import code.model._
import code.model.dataAccess._
import code.products.Products.{Product, ProductCode}
import code.transaction.MappedTransaction
import code.transactionrequests.TransactionRequests._
import code.transactionrequests.{TransactionRequestTypeCharge, TransactionRequests}
import code.util.{Helper, TTLCache}
import code.views.Views
import net.liftweb.common._
import net.liftweb.json.Extraction
import net.liftweb.mapper._
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

import scala.collection.immutable.{Nil, Seq}
import scala.collection.mutable.ArrayBuffer
import scalacache.memoization.memoizeSync

import scalacache.ScalaCache
import scalacache.guava.GuavaCache
import scalacache.guava
import scalacache._
import concurrent.duration._
import language.postfixOps
import com.google.common.cache.CacheBuilder
import code.util.Helper.MdcLoggable
import net.liftweb.json.JsonAST.{JValue}


object KafkaMappedConnector_vJun2017 extends Connector with KafkaHelper with MdcLoggable {

  type AccountType = BankAccountJune2017

  implicit override val nameOfConnector = KafkaMappedConnector_vJun2017.getClass.getSimpleName
  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))
  val getBankTTL                            = Props.get("connector.cache.ttl.seconds.getBank", "0").toInt * 1000 // Miliseconds
  val getBanksTTL                           = Props.get("connector.cache.ttl.seconds.getBanks", "0").toInt * 1000 // Miliseconds
  val getUserTTL                            = Props.get("connector.cache.ttl.seconds.getUser", "0").toInt * 1000 // Miliseconds
  val getAccountTTL                         = Props.get("connector.cache.ttl.seconds.getAccount", "0").toInt * 1000 // Miliseconds
  val getAccountHolderTTL                   = Props.get("connector.cache.ttl.seconds.getAccountHolderTTL", "0").toInt * 1000 // Miliseconds
  val getAccountsTTL                        = Props.get("connector.cache.ttl.seconds.getAccounts", "0").toInt * 1000 // Miliseconds
  val getTransactionTTL                     = Props.get("connector.cache.ttl.seconds.getTransaction", "0").toInt * 1000 // Miliseconds
  val getTransactionsTTL                    = Props.get("connector.cache.ttl.seconds.getTransactions", "0").toInt * 1000 // Miliseconds
  val getCounterpartyFromTransactionTTL     = Props.get("connector.cache.ttl.seconds.getCounterpartyFromTransaction", "0").toInt * 1000 // Miliseconds
  val getCounterpartiesFromTransactionTTL   = Props.get("connector.cache.ttl.seconds.getCounterpartiesFromTransaction", "0").toInt * 1000 // Miliseconds
  
  
  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "Jun2017"

  implicit val formats = net.liftweb.json.DefaultFormats
  override val messageDocs = ArrayBuffer[MessageDoc]()
  val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
  val exampleDateString: String = "22/08/2013"
  val exampleDate = simpleDateFormat.parse(exampleDateString)
  val emptyObjectJson: JValue = Extraction.decompose(Nil)
  def currentResourceUserId = AuthUser.getCurrentResourceUserUserId
  def currentResourceUsername = AuthUser.getCurrentUserUsername

  
  
  
  //////////////////////////////////////////////////////////////////////////////
  // the following methods, have been implemented in new Adapter code
  messageDocs += MessageDoc(
    process = "obp.get.AdapterInfo",
    messageFormat = messageFormat,
    description = "getAdapterInfo from kafka ",
    exampleOutboundMessage = Extraction.decompose(
      OutboundAdapterInfo(
        messageFormat = messageFormat,
        action = "obp.get.getAdapterInfo",
        date = (new Date()).toString
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundAdapterInfo(
        errorCode = "OBPS-001: .... ",
        name = "Obp-Kafka-South",
        version = "June2017",
        git_commit = "...",
        date = (new Date()).toString
      )
    )
  )
  override def getAdapterInfo: Box[InboundAdapterInfo] = {
    val req = code.bankconnectors.GetAdapterInfo((new Date()).toString)
    val rr = process[code.bankconnectors.GetAdapterInfo](req)
    val r = rr.extract[InboundAdapterInfo]
    Full(r)
  }


  messageDocs += MessageDoc(
    process = "obp.get.User",
    messageFormat = messageFormat,
    description = "getUser from kafka ",
    exampleOutboundMessage = Extraction.decompose(
      OutboundUserByUsernamePasswordBase(
        messageFormat = messageFormat,
        action = "obp.get.User",
        username = "susan.uk.29@example.com",
        password = "2b78e8"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundValidatedUser(
        errorCode = "OBPS-001: .... ",
        email = "susan.uk.29@example.com",
        displayName = "susan"
      )
    )
  )
  def getUser(username: String, password: String): Box[InboundUser] = saveConnectorMetric {
    memoizeSync(getUserTTL millisecond) {
      for {
        req <- Full(
          GetUserByUsernamePassword(username = username, password = password)
        )
        u <- Full(process[GetUserByUsernamePassword](req).extract[InboundValidatedUser])
        recUsername <- tryo(u.displayName)
      } yield if (username == u.displayName) new InboundUser(recUsername,
        password, recUsername
      ) else null
    }}("getUser")

  
  messageDocs += MessageDoc(
    process = "obp.get.Banks",
    messageFormat = messageFormat,
    description = "getBanks",
    exampleOutboundMessage = Extraction.decompose(
      OutboundBanksBase(
        action = "obp.get.Banks",
        messageFormat = messageFormat,
        username = "susan.uk.29@example.com",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundBank(
        errorCode = "OBPS-001: .... ",
        bankId = "gh.29.uk",
        name = "sushan",
        logo = "TESOBE",
        url = "https://tesobe.com/"
      )
        :: InboundBank(
        errorCode = "OBPS-001: .... ",
        bankId = "gh.29.uk",
        name = "sushan",
        logo = "TESOBE",
        url = "https://tesobe.com/"
      )
        :: Nil
    )
  )
  //gets banks handled by this connector
  override def getBanks(): Box[List[Bank]] = saveConnectorMetric {
    memoizeSync(getBanksTTL millisecond){
    val req = GetBanks(
      AuthInfo(username = currentResourceUserId, userId = currentResourceUsername),
      "")
    logger.debug(s"Kafka getBanks says: req is: $req")
    val rList = process[GetBanks](req).extract[List[InboundBank]]
    val res = rList map (new Bank2(_))
    logger.debug(s"Kafka getBanks says res is $res")
    Full(res)
  }}("getBanks")

  
  messageDocs += MessageDoc(
    process = "obp.get.Bank",
    messageFormat = messageFormat,
    description = "getBank from kafka ",
    exampleOutboundMessage = Extraction.decompose(
      OUTTBank(
        action = "obp.get.Bank",
        messageFormat = messageFormat,
        bankId = "gh.29.uk",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundBank(
        
        errorCode = "OBPS-001: .... ",
        bankId = "gh.29.uk",
        name = "sushan",
        logo = "TESOBE",
        url = "https://tesobe.com/"
      )
    )
  )
  override def getBank(bankId: BankId): Box[Bank] =  saveConnectorMetric {
    memoizeSync(getBankTTL millisecond){
    val req = GetBank(
      authInfo = AuthInfo(username = currentResourceUserId, userId = currentResourceUsername),
      bankId = bankId.toString)
    
    val r =  process[GetBank](req).extract[InboundBank]
      
    Full(new Bank2(r))
      
  }}("getBank")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.Accounts",
    messageFormat = messageFormat,
    description = "getBankAccounts from kafka",
    exampleOutboundMessage = Extraction.decompose(
      OutboundBankAccountsBase(
        messageFormat = messageFormat,
        action = "obp.get.Accounts",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundAccountJune2017(
        errorCode = "OBPS-001: .... ",
        bankId = "gh.29.uk",
        branchId = "222",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        accountNumber = "123",
        accountType = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        owners = "Susan" :: " Frank" :: Nil,
        viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
        bankRoutingScheme = "iban",
        bankRoutingAddress = "bankRoutingAddress",
        branchRoutingScheme = "branchRoutingScheme",
        branchRoutingAddress = " branchRoutingAddress",
        accountRoutingScheme = "accountRoutingScheme",
        accountRoutingAddress = "accountRoutingAddress"
      ) :: Nil
    )
  )
  //New getBankAccounts
  override def getBankAccounts(user: User): Box[List[InboundAccountJune2017]] = saveConnectorMetric {
    memoizeSync(getAccountsTTL millisecond) {
    val req = GetAccounts(
      AuthInfo(userId = currentResourceUserId,username = currentResourceUsername))
    
    logger.debug(s"Kafka getBankAccounts says: req is: $req")
    val rList = process[GetAccounts](req).extract[List[InboundAccountJune2017]]
    val res = rList //map (new BankAccountJune2017(_))
    logger.debug(s"Kafka getBankAccounts says res is $res")
    Full(res)
  }}("getBankAccounts")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.Account",
    messageFormat = messageFormat,
    description = "getBankAccount from kafka",
    exampleOutboundMessage = Extraction.decompose(
      OutboundBankAccountBase(
        action = "obp.get.Account",
        messageFormat = messageFormat,
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundAccountJune2017(
        errorCode = "OBPS-001: .... ",
        bankId = "gh.29.uk",
        branchId = "222",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        accountNumber = "123",
        accountType = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        owners = "Susan" :: " Frank" :: Nil,
        viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
        bankRoutingScheme = "iban",
        bankRoutingAddress = "bankRoutingAddress",
        branchRoutingScheme = "branchRoutingScheme",
        branchRoutingAddress = " branchRoutingAddress",
        accountRoutingScheme = "accountRoutingScheme",
        accountRoutingAddress = "accountRoutingAddress"
      )
    )
  )
  override def getBankAccount(bankId: BankId, accountId: AccountId): Box[BankAccountJune2017] = saveConnectorMetric{
    memoizeSync(getAccountTTL millisecond){
    // Generate random uuid to be used as request-response match id
    val req = GetAccountbyAccountID(
      authInfo = AuthInfo(userId = currentResourceUserId, username = currentResourceUsername),
      bankId = bankId.toString,
      accountId = accountId.value
    )
    logger.debug(s"Kafka getBankAccount says: req is: $req")
    val res = process[GetAccountbyAccountID](req).extract[InboundAccountJune2017]
    
    logger.debug(s"Kafka getBankAccount says res is $res")
    
    // Check does the response data match the requested data
//    val accResp = List((BankId(res.data.bankId), AccountId(res.data.accountId))).toSet
//    val acc = List((bankId, accountId)).toSet
//    if ((accResp diff acc).size > 0) throw new Exception(ErrorMessages.InvalidConnectorResponseForGetBankAccount)
//    createMappedAccountDataIfNotExisting(res.data.bankId, res.data.accountId, "label")
  
    Full(new BankAccountJune2017(res))
  }}("getBankAccount")
    
  
  //////////////////////////////////////////////////////////////////////////////// 
  // the following methods do not implement in new Adapter code
  
  messageDocs += MessageDoc(
    process = "obp.get.Accounts",
    messageFormat = messageFormat,
    description = "getBankAccounts from kafka",
    exampleOutboundMessage = Extraction.decompose(
      OutboundBankAccountsBase(
        messageFormat = messageFormat,
        action = "obp.get.Accounts",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundAccount(
        errorCode = "OBPS-001: .... ",
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
      ) :: InboundAccount(
        errorCode = "OBPS-001: .... ",
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
  //Legacy(?) getBankAccounts
  override def getBankAccounts(accts: List[(BankId, AccountId)]): List[BankAccountJune2017] = saveConnectorMetric {
    memoizeSync(getAccountsTTL millisecond) {
      val primaryUserIdentifier = currentResourceUsername
      
      val r: List[InboundAccountJune2017] = accts.flatMap { a => {
        
        logger.info(s"KafkaMappedConnnector.getBankAccounts with params ${a._1.value} and  ${a._2.value} and primaryUserIdentifier is $primaryUserIdentifier")
        
        val req = OutboundBankAccountsBase(
          messageFormat = messageFormat,
          action = "obp.get.Accounts",
          userId = currentResourceUserId,
          username = currentResourceUsername,
          bankId = a._1.value,
          accountId = a._2.value)
        
        implicit val formats = net.liftweb.json.DefaultFormats
        val r = process(req).extract[List[InboundAccountJune2017]]
        r
      }}
      
      // Check does the response data match the requested data
      val accRes = for (row <- r) yield {
        (BankId(row.bankId), AccountId(row.accountId))
      }
      if ((accRes.toSet diff accts.toSet).size > 0) throw new Exception(ErrorMessages.InvalidConnectorResponseForGetBankAccounts)
      
      r.map { t =>
        createMappedAccountDataIfNotExisting(t.bankId, t.accountId, "label")
        new BankAccountJune2017(t)
      }
    }}("getBankAccounts2")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.Transaction",
    messageFormat = messageFormat,
    description = "getTransaction from kafka ",
    exampleOutboundMessage = Extraction.decompose(
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
    exampleInboundMessage = Extraction.decompose(
      InternalTransaction(
        errorCode = "OBPS-001: .... ",
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
  def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Box[Transaction] = {
    val req = OutboundTransactionQueryBase(
      messageFormat = messageFormat,
      action = "obp.get.Transaction",
      userId = currentResourceUserId,
      username = currentResourceUsername,
      bankId = bankId.toString,
      accountId = accountId.toString,
      transactionId = transactionId.toString)
    
    // Since result is single account, we need only first list entry
    val r = process(req).extractOpt[InternalTransaction]
    r match {
      // Check does the response data match the requested data
      case Some(x) if transactionId.value != x.transactionId => Failure(ErrorMessages.InvalidConnectorResponseForGetTransaction, Empty, Empty)
      case Some(x) if transactionId.value == x.transactionId => createNewTransaction(x)
      case _ => Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
    }
    
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.Transactions",
    messageFormat = messageFormat,
    description = "getTransactions from kafka",
    exampleOutboundMessage = Extraction.decompose(
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
    exampleInboundMessage = Extraction.decompose(
      InternalTransaction(
        errorCode = "OBPS-001: .... ",
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
      ) :: InternalTransaction(
        errorCode = "OBPS-001: .... ",
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
  override def getTransactions(bankId: BankId, accountId: AccountId, queryParams: OBPQueryParam*): Box[List[Transaction]] = {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedTransaction](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedTransaction](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedTransaction.tFinishDate, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedTransaction.tFinishDate, date) }.headOption
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(MappedTransaction.tFinishDate, Ascending)
          case OBPDescending => OrderBy(MappedTransaction.tFinishDate, Descending)
        }
    }
    val optionalParams: Seq[QueryParam[MappedTransaction]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering.toSeq).flatten
    //TODO no filter now.
    val mapperParams = Seq(By(MappedTransaction.bank, bankId.value), By(MappedTransaction.account, accountId.value)) ++ optionalParams
    
    val req = OutboundTransactionsQueryWithParamsBase(
      messageFormat = messageFormat,
      action = "obp.get.Transactions",
      userId = currentResourceUserId,
      username = currentResourceUsername,
      bankId = bankId.toString,
      accountId = accountId.toString,
      queryParams = queryParams.toString)
    
    implicit val formats = net.liftweb.json.DefaultFormats
    val rList = process(req).extract[List[InternalTransaction]]
    // Check does the response data match the requested data
    val isCorrect = rList.forall(x => x.accountId == accountId.value && x.bankId == bankId.value)
    if (!isCorrect) throw new Exception(ErrorMessages.InvalidConnectorResponseForGetTransactions)
    // Populate fields and generate result
    val res = for {
      r <- rList
      transaction <- createNewTransaction(r)
    } yield {
      transaction
    }
    Full(res)
    //TODO is this needed updateAccountTransactions(bankId, accountId)
  }
  
  
  
  
  messageDocs += MessageDoc(
    process = "obp.get.ChallengeThreshold",
    messageFormat = messageFormat,
    description = "getChallengeThreshold from kafka ",
    exampleOutboundMessage = Extraction.decompose(
      OutboundChallengeThresholdBase(
        messageFormat = messageFormat,
        action = "obp.get.ChallengeThreshold",
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        viewId = "owner",
        transactionRequestType = "SANDBOX_TAN",
        currency = "GBP",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundChallengeLevel(
        errorCode = "OBPS-001: .... ",
        limit = "1000",
        currency = "EUR"
      )
    )
  )
  // Gets current challenge level for transaction request
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, username: String): AmountOfMoney = {
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
      case Some(x) => AmountOfMoney(x.currency, x.limit)
      case _ => {
        val limit = BigDecimal("0")
        val rate = fx.exchangeRate("EUR", currency)
        val convertedLimit = fx.convert(limit, rate)
        AmountOfMoney(currency, convertedLimit.toString())
      }
    }
  }

  messageDocs += MessageDoc(
    process = "obp.get.ChargeLevel",
    messageFormat = messageFormat,
    description = "ChargeLevel from kafka ",
    exampleOutboundMessage = Extraction.decompose(OutboundChargeLevelBase(
      action = "obp.get.ChargeLevel",
      messageFormat = messageFormat,
      bankId = "gh.29.uk",
      accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
      viewId = "owner",
      userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
      username = "susan.uk.29@example.com",
      transactionRequestType = "SANDBOX_TAN",
      currency = "EUR"
    )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundChargeLevel(
        errorCode = "OBPS-001: .... ",
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
                               currency: String
                             ): Box[AmountOfMoney] = {
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
    Full(chargeValue)
  }

  
  messageDocs += MessageDoc(
    process = "obp.create.Challenge",
    messageFormat = messageFormat,
    description = "CreateChallenge from kafka ",
    exampleOutboundMessage = Extraction.decompose(
      OutboundChallengeBase(
        action = "obp.create.Challenge",
        messageFormat = messageFormat,
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        transactionRequestType = "SANDBOX_TAN",
        transactionRequestId = "1234567"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundCreateChallange(
        errorCode = "OBPS-001: .... ",
        challengeId = "1234567"
      )
    )
  )

  override def createChallenge(
                                bankId: BankId,
                                accountId: AccountId,
                                userId: String,
                                transactionRequestType: TransactionRequestType,
                                transactionRequestId: String
                              ): Box[String] = {
    // Create argument list
    val req = OutboundChallengeBase(
      messageFormat = messageFormat,
      action = "obp.create.Challenge",
      bankId = bankId.value,
      accountId = accountId.value,
      userId = userId,
      username = currentResourceUsername,
      transactionRequestType = transactionRequestType.value,
      transactionRequestId = transactionRequestId
    )

    val r: Option[InboundCreateChallange] = process(req
    ).extractOpt[InboundCreateChallange]
    // Return result
    r match {
      // Check does the response data match the requested data
      case Some(x) => Full(x.challengeId)
      case _ => Empty
    }
  }

  messageDocs += MessageDoc(
    process = "obp.validate.ChallengeAnswer",
    messageFormat = messageFormat,
    description = "validateChallengeAnswer from kafka ",
    exampleOutboundMessage = Extraction.decompose(
      OutboundChallengeAnswerBase(
        messageFormat = messageFormat,
        action = "obp.validate.ChallengeAnswer",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        challengeId = "1234",
        hashOfSuppliedAnswer = ""
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundValidateChallangeAnswer(
        errorCode = "OBPS-001: .... ",
        answer = ""
      )
    )
  )

  override def validateChallengeAnswer(
                                        challengeId: String,
                                        hashOfSuppliedAnswer: String
                                      ): Box[Boolean] = {
    // Create argument list
    val req = OutboundChallengeAnswerBase(
      messageFormat = messageFormat,
      action = "obp.validate.ChallengeAnswer",
      userId = currentResourceUserId,
      username = currentResourceUsername,
      challengeId = challengeId,
      hashOfSuppliedAnswer = hashOfSuppliedAnswer)

    val r: Option[InboundValidateChallangeAnswer] = process(req).extractOpt[InboundValidateChallangeAnswer]
    // Return result
    r match {
      // Check does the response data match the requested data
      case Some(x) => Full(x.answer.toBoolean)
      case _ => Empty
    }
  }


 
  //TODO the method name is different from action
  messageDocs += MessageDoc(
    process = "obp.get.Account",
    messageFormat = messageFormat,
    description = "getAccountByNumber from kafka",
    exampleOutboundMessage = Extraction.decompose(
      OutboundAccountByNumberBase(
        action = "obp.get.Account",
        messageFormat = messageFormat,
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "gh.29.uk",
        number = ""
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundAccountJune2017(
        errorCode = "OBPS-001: .... ",
        bankId = "gh.29.uk",
        branchId = "222",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        accountNumber = "123",
        accountType = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        owners = "Susan" :: " Frank" :: Nil,
        viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
        bankRoutingScheme = "iban",
        bankRoutingAddress = "bankRoutingAddress",
        branchRoutingScheme = "branchRoutingScheme",
        branchRoutingAddress = " branchRoutingAddress",
        accountRoutingScheme = "accountRoutingScheme",
        accountRoutingAddress = "accountRoutingAddress"
      )
    )
  )

  private def getAccountByNumber(bankId: BankId, number: String): Box[AccountType] = {
    // Generate random uuid to be used as request-respose match id
    val req = OutboundAccountByNumberBase(
      messageFormat = messageFormat,
      action = "obp.get.Account",
      userId = currentResourceUserId,
      username = currentResourceUsername,
      bankId = bankId.toString,
      number = number
    )

    // Since result is single account, we need only first list entry
    implicit val formats = net.liftweb.json.DefaultFormats
    val r = {
      process(req).extract[InboundAccountJune2017]
    }
    createMappedAccountDataIfNotExisting(r.bankId, r.accountId, "label")
    Full(new BankAccountJune2017(r))
  }

  // Get all counterparties related to an account
  override def getCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId): List[Counterparty] =
    Counterparties.counterparties.vend.getMetadatas(bankId, accountId).flatMap(getCounterpartyFromTransaction(bankId, accountId, _))

  // Get one counterparty related to a bank account
  override def getCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyID: String): Box[Counterparty] =
  // Get the metadata and pass it to getOtherBankAccount to construct the other account.
    Counterparties.counterparties.vend.getMetadata(bankId, accountId, counterpartyID).flatMap(getCounterpartyFromTransaction(bankId, accountId, _))

  def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = {
    //note: kafka mode just used the mapper data
    LocalMappedConnector.getCounterparty(thisBankId, thisAccountId, couterpartyId)
  }


  messageDocs += MessageDoc(
    process = "obp.get.CounterpartyByCounterpartyId",
    messageFormat = messageFormat,
    description = "getCounterpartyByCounterpartyId from kafka ",
    exampleOutboundMessage = Extraction.decompose(
      OutboundCounterpartyByCounterpartyIdBase(
        messageFormat = messageFormat,
        action = "obp.get.CounterpartyByCounterpartyId",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        counterpartyId = "12344"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundCounterparty(
        errorCode = "OBPS-001: .... ",
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

  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId): Box[CounterpartyTrait] = {
    if (Props.getBool("get_counterparties_from_OBP_DB", true)) {
      Counterparties.counterparties.vend.getCounterparty(counterpartyId.value)
    } else {
      val req = OutboundCounterpartyByCounterpartyIdBase(
        messageFormat = messageFormat,
        action = "obp.get.CounterpartyByCounterpartyId",
        userId = currentResourceUserId,
        username = currentResourceUsername,
        counterpartyId = counterpartyId.toString
      )
      // Since result is single account, we need only first list entry
      implicit val formats = net.liftweb.json.DefaultFormats
      val r = process(req).extract[InboundCounterparty]
      Full(CounterpartyTrait2(r))
    }
  }

  messageDocs += MessageDoc(
    process = "obp.get.CounterpartyByIban",
    messageFormat = messageFormat,
    description = "getCounterpartyByIban from kafka ",
    exampleOutboundMessage = Extraction.decompose(
      OutboundCounterpartyByIbanBase(
        messageFormat = messageFormat,
        action = "obp.get.CounterpartyByIban",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        otherAccountRoutingAddress = "1234",
        otherAccountRoutingScheme = "1234"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      CounterpartyTrait2(
        InboundCounterparty(
          errorCode = "OBPS-001: .... ",
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

  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] = {
    if (Props.getBool("get_counterparties_from_OBP_DB", true)) {
      Counterparties.counterparties.vend.getCounterpartyByIban(iban)
    } else {
      val req = OutboundCounterpartyByIbanBase(
        messageFormat = messageFormat,
        action = "obp.get.CounterpartyByIban",
        userId = currentResourceUserId,
        username = currentResourceUsername,
        otherAccountRoutingAddress = iban,
        otherAccountRoutingScheme = "IBAN"
      )
      val r = process(req).extract[InboundCounterparty]
      Full(CounterpartyTrait2(r))
    }
  }

  messageDocs += MessageDoc(
    process = "obp.put.Transaction",
    messageFormat = messageFormat,
    description = "saveTransaction from kafka",
    exampleOutboundMessage = Extraction.decompose(
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
        transactionRequestType = "SANDBOX_TAN",
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
    exampleInboundMessage = Extraction.decompose(
      InboundTransactionId(

        errorCode = "OBPS-001: .... ",
        transactionId = "1234"
      )
    )
  )

  /**
    * Saves a transaction with amount @amount and counterparty @counterparty for account @account. Returns the id
    * of the saved transaction.
    */
  private def saveTransaction(fromAccount: BankAccountJune2017,
                              toAccount: BankAccountJune2017,
                              toCounterparty: CounterpartyTrait,
                              amount: BigDecimal,
                              description: String,
                              transactionRequestType: TransactionRequestType,
                              chargePolicy: String): Box[TransactionId] = {

    val postedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).format(now)
    val transactionId = UUID.randomUUID().toString

    val req =
      if (toAccount != null && toCounterparty == null) {
        OutboundSaveTransactionBase(
          messageFormat = messageFormat,
          action = "obp.put.Transaction",
          userId = currentResourceUserId,
          username = currentResourceUsername,

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

          // toAccount or toCounterparty
          toCounterpartyId = toAccount.accountId.value,
          toCounterpartyName = toAccount.name,
          toCounterpartyCurrency = toAccount.currency,
          toCounterpartyRoutingAddress = toAccount.accountId.value,
          toCounterpartyRoutingScheme = "OBP",
          toCounterpartyBankRoutingAddress = toAccount.bankId.value,
          toCounterpartyBankRoutingScheme = "OBP")
      } else {
        OutboundSaveTransactionBase(
          messageFormat = messageFormat,
          action = "obp.put.Transaction",
          userId = currentResourceUserId,
          username = currentResourceUsername,

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
          // toAccount or toCounterparty
          toCounterpartyId = toCounterparty.counterpartyId,
          toCounterpartyName = toCounterparty.name,
          toCounterpartyCurrency = fromAccount.currency, // TODO toCounterparty.currency
          toCounterpartyRoutingAddress = toCounterparty.otherAccountRoutingAddress,
          toCounterpartyRoutingScheme = toCounterparty.otherAccountRoutingScheme,
          toCounterpartyBankRoutingAddress = toCounterparty.otherBankRoutingAddress,
          toCounterpartyBankRoutingScheme = toCounterparty.otherBankRoutingScheme)
      }

    if (toAccount == null && toCounterparty == null) {
      logger.error(s"error calling saveTransaction: toAccount=${toAccount} toCounterparty=${toCounterparty}")
      return Empty
    }

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
    exampleOutboundMessage = Extraction.decompose(
      OutboundTransactionRequestStatusesBase(
        messageFormat = messageFormat,
        action = "obp.get.TransactionRequestStatusesImpl"
      )
    ),
    exampleInboundMessage = Extraction.decompose(
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

  override def getTransactionRequestStatusesImpl(): Box[TransactionRequestStatus] = {
    logger.info(s"tKafka getTransactionRequestStatusesImpl sart: ")
    val req = OutboundTransactionRequestStatusesBase(
      messageFormat = messageFormat,
      action = "obp.get.TransactionRequestStatusesImpl"
    )
    //TODO need more clear error handling to user, if it is Empty or Error now,all response Empty.
    val r = try {
      val response = process(req).extract[InboundTransactionRequestStatus]
      Full(new TransactionRequestStatus2(response))
    } catch {
      case _ => Empty
    }

    logger.info(s"Kafka getTransactionRequestStatusesImpl response: ${r.toString}")
    r
  }

  messageDocs += MessageDoc(
    process = "obp.get.CurrentFxRate",
    messageFormat = messageFormat,
    description = "getCurrentFxRate from kafka",
    exampleOutboundMessage = Extraction.decompose(
      OutboundCurrentFxRateBase(
        action = "obp.get.CurrentFxRate",
        messageFormat = messageFormat,
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        bankId = "bankid54",
        fromCurrencyCode = "1234",
        toCurrencyCode = ""
      )
    ),
    exampleInboundMessage = Extraction.decompose(
      InboundFXRate(
        errorCode = "OBP-XXX: .... ",
        bankId = "bankid54",
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
      username = currentResourceUsername,
      bankId = bankId.value,
      fromCurrencyCode = fromCurrencyCode,
      toCurrencyCode = toCurrencyCode)

    val r = process(req).extract[InboundFXRate]
    
    // Return result
    Full(new FXRate2(r))
  }

  messageDocs += MessageDoc(
    process = "obp.get.TransactionRequestTypeCharge",
    messageFormat = messageFormat,
    description = "getTransactionRequestTypeCharge from kafka",
    exampleOutboundMessage = Extraction.decompose(
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
    exampleInboundMessage = Extraction.decompose(
      InboundTransactionRequestTypeCharge(
        errorCode = "OBPS-001: .... ",
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
      username = currentResourceUsername,
      bankId = bankId.value,
      accountId = accountId.value,
      viewId = viewId.value,
      transactionRequestType = transactionRequestType.value
    )

    // send the request to kafka and get response
    // TODO the error handling is not good enough, it should divide the error, empty and no-response.
    val r = tryo { process(req).extract[InboundTransactionRequestTypeCharge]}

    // Return result
    val result = r match {
      case Full(f) => Full(TransactionRequestTypeCharge2(f))
      case _ =>
        for {
          fromAccount <- getBankAccount(bankId, accountId)
          fromAccountCurrency <- tryo {
            fromAccount.currency
          }
        } yield {
          TransactionRequestTypeCharge2(InboundTransactionRequestTypeCharge(

            errorCode = "OBPS-001: .... ",
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


  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]] = {
    //note: kafka mode just used the mapper data
    LocalMappedConnector.getCounterparties(thisBankId, thisAccountId, viewId)
  }

  override def getPhysicalCards(user: User): List[PhysicalCard] =
    List()

  override def getPhysicalCardsForBank(bank: Bank, user: User): List[PhysicalCard] =
    List()

  def AddPhysicalCard(bankCardNumber: String,
                      nameOnCard: String,
                      issueNumber: String,
                      serialNumber: String,
                      validFrom: Date,
                      expires: Date,
                      enabled: Boolean,
                      cancelled: Boolean,
                      onHotList: Boolean,
                      technology: String,
                      networks: List[String],
                      allows: List[String],
                      accountId: String,
                      bankId: String,
                      replacement: Option[CardReplacementInfo],
                      pinResets: List[PinResetInfo],
                      collected: Option[CardCollectionInfo],
                      posted: Option[CardPostedInfo]
                     ): Box[PhysicalCard] = {
    Empty
  }


  protected override def makePaymentImpl(fromAccount: BankAccountJune2017,
                                         toAccount: BankAccountJune2017,
                                         toCounterparty: CounterpartyTrait,
                                         amt: BigDecimal,
                                         description: String,
                                         transactionRequestType: TransactionRequestType,
                                         chargePolicy: String): Box[TransactionId] = {

    val sentTransactionId = saveTransaction(fromAccount,
      toAccount,
      toCounterparty,
      amt,
      description,
      transactionRequestType,
      chargePolicy)

    sentTransactionId
  }


  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                            account: BankAccount, counterparty: BankAccount, body: TransactionRequestBody,
                                            status: String, charge: TransactionRequestCharge): Box[TransactionRequest] = {
    TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl(transactionRequestId,
      transactionRequestType,
      account,
      counterparty,
      body,
      status,
      charge)
  }


  //Note: now call the local mapper to store data
  protected override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
                                                         transactionRequestType: TransactionRequestType,
                                                         fromAccount: BankAccount,
                                                         toAccount: BankAccount,
                                                         toCounterparty: CounterpartyTrait,
                                                         transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                                         details: String, status: String,
                                                         charge: TransactionRequestCharge,
                                                         chargePolicy: String): Box[TransactionRequest] = {

    LocalMappedConnector.createTransactionRequestImpl210(transactionRequestId: TransactionRequestId,
      transactionRequestType: TransactionRequestType,
      fromAccount: BankAccount, toAccount: BankAccount,
      toCounterparty: CounterpartyTrait,
      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
      details: String,
      status: String,
      charge: TransactionRequestCharge,
      chargePolicy: String)
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


  override def getTransactionRequestsImpl(fromAccount: BankAccount): Box[List[TransactionRequest]] = {
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)
  }

  override def getTransactionRequestsImpl210(fromAccount: BankAccount): Box[List[TransactionRequest]] = {
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)
  }

  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId): Box[TransactionRequest] = {
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequest(transactionRequestId)
  }


  override def getTransactionRequestTypesImpl(fromAccount: BankAccount): Box[List[TransactionRequestType]] = {
    val validTransactionRequestTypes = Props.get("transactionRequests_supported_types", "").split(",").map(x => TransactionRequestType(x)).toList
    Full(validTransactionRequestTypes)
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
                                   ): (Bank, BankAccount) = {
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
      AccountId(UUID.randomUUID().toString),
      accountNumber,
      accountType,
      accountLabel,
      currency,
      0L,
      accountHolderName
    )

    (bank, account)
  }

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String): Boolean = {
    getAccountByNumber(bankId, accountNumber) != null
  }

  //remove an account and associated transactions
  override def removeAccount(bankId: BankId, accountId: AccountId): Boolean = {
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
      case _ => false
    }

    commentsDeleted && narrativesDeleted && tagsDeleted && whereTagsDeleted && transactionImagesDeleted &&
      transactionsDeleted && privilegesDeleted && viewsDeleted && accountDeleted
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
      bank <- getBank(bankId) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
    } yield {

      val balanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(initialBalance, currency)
      createAccountIfNotExisting(bankId, accountId, accountNumber, accountType, accountLabel, currency, balanceInSmallestCurrencyUnits, accountHolderName)
    }

  }

  //sets a user as an account owner/holder
  override def setAccountHolder(bankAccountUID: BankIdAccountId, user: User): Unit = {
    AccountHolders.accountHolders.vend.createAccountHolder(user.resourceUserId.value, bankAccountUID.accountId.value, bankAccountUID.bankId.value)
  }

  private def createAccountIfNotExisting(bankId: BankId, accountId: AccountId, accountNumber: String,
                                         accountType: String, accountLabel: String, currency: String,
                                         balanceInSmallestCurrencyUnits: Long, accountHolderName: String): BankAccount = {
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

  private def createMappedAccountDataIfNotExisting(bankId: String, accountId: String, label: String): Boolean = {
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
  override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal): Boolean = {

    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      bank <- getBank(bankId)
    } yield {
      //acc.balance = newBalance
      setBankAccountLastUpdated(bank.nationalIdentifier, acc.number, now)
    }

    result.getOrElse(false)
  }

  //transaction import api uses bank national identifiers to uniquely indentify banks,
  //which is unfortunate as theoretically the national identifier is unique to a bank within
  //one country
  private def getBankByNationalIdentifier(nationalIdentifier: String): Box[Bank] = {
    MappedBank.find(By(MappedBank.national_identifier, nationalIdentifier))
  }


  private val bigDecimalFailureHandler: PartialFunction[Throwable, Unit] = {
    case ex: NumberFormatException => {
      logger.warn(s"could not convert amount to a BigDecimal: $ex")
    }
  }

  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier: String, accountNumber: String, amount: String, completed: Date, otherAccountHolder: String): Int = {
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
    count.map(_.toInt) getOrElse 0
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

  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber: String, updateDate: Date): Boolean = {
    val result = for {
      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
      account <- getAccountByNumber(bankId, accountNumber)
    } yield {
      val acc = getBankAccount(bankId, account.accountId)
      acc match {
        case a => true //a.lastUpdate = updateDate //TODO
        case _ => logger.warn("can't set bank account.lastUpdated because the account was not found"); false
      }
    }
    result.getOrElse(false)
  }

  /*
    End of transaction importer api
   */


  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String): Boolean = {
    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      bank <- getBank(bankId)
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, accountId.value), By(MappedBankAccountData.bankId, bank.bankId.value))
    } yield {
      d.setLabel(label)
      d.save()
    }
    result.getOrElse(false)
  }


  override def getProducts(bankId: BankId): Box[List[Product]] = Empty

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = Empty

  override def createOrUpdateBranch(branch: BranchJsonPost, branchRoutingScheme: String, branchRoutingAddress: String): Box[Branch] = Empty

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

  override def getBranch(bankId: BankId, branchId: BranchId): Box[MappedBranch] = Empty // TODO Return Not Implemented


  override def getAtm(bankId: BankId, atmId: AtmId): Box[MappedAtm] = Empty // TODO Return Not Implemented

  override def getEmptyBankAccount(): Box[AccountType] = {
    Full(new BankAccountJune2017(
      InboundAccountJune2017(
        errorCode = "OBPS-001: .... ",
        bankId = "gh.29.uk",
        branchId = "222",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        accountNumber = "123",
        accountType = "AC",
        balanceAmount = "50",
        balanceCurrency = "EUR",
        owners = "Susan" :: " Frank" :: Nil,
        viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
        bankRoutingScheme = "iban",
        bankRoutingAddress = "bankRoutingAddress",
        branchRoutingScheme = "branchRoutingScheme",
        branchRoutingAddress = " branchRoutingAddress",
        accountRoutingScheme = "accountRoutingScheme",
        accountRoutingAddress = "accountRoutingAddress"
      )
    )
    )
  }

  /////////////////////////////////////////////////////////////////////////////

  // Helper for creating a transaction
  def createNewTransaction(r: InternalTransaction): Box[Transaction] = {
    var datePosted: Date = null
    if (r.postedDate != null) // && r.details.posted.matches("^[0-9]{8}$"))
      datePosted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(r.postedDate)

    var dateCompleted: Date = null
    if (r.completedDate != null) // && r.details.completed.matches("^[0-9]{8}$"))
      dateCompleted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(r.completedDate)

    for {
      counterpartyId <- tryo {
        r.counterpartyId
      }
      counterpartyName <- tryo {
        r.counterpartyName
      }
      thisAccount <- getBankAccount(BankId(r.bankId), AccountId(r.accountId))
      //creates a dummy OtherBankAccount without an OtherBankAccountMetadata, which results in one being generated (in OtherBankAccount init)
      dummyOtherBankAccount <- tryo {
        createCounterparty(counterpartyId, counterpartyName, thisAccount, None)
      }
      //and create the proper OtherBankAccount with the correct "id" attribute set to the metadataId of the OtherBankAccountMetadata object
      //note: as we are passing in the OtherBankAccountMetadata we don't incur another db call to get it in OtherBankAccount init
      counterparty <- tryo {
        createCounterparty(counterpartyId, counterpartyName, thisAccount, Some(dummyOtherBankAccount.metadata))
      }
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
  def createCounterparty(counterpartyId: String, counterpartyName: String, o: BankAccountJune2017, alreadyFoundMetadata: Option[CounterpartyMetadata]) = {
    new Counterparty(
      counterPartyId = alreadyFoundMetadata.map(_.metadataId).getOrElse(""),
      label = counterpartyName,
      nationalIdentifier = "1234",
      otherBankRoutingAddress = None,
      otherAccountRoutingAddress = None,
      thisAccountId = AccountId(counterpartyId),
      thisBankId = BankId(""),
      kind = "1234",
      otherBankId = o.bankId,
      otherAccountId = o.accountId,
      alreadyFoundMetadata = alreadyFoundMetadata,
      name = "sushan",
      otherBankRoutingScheme = "obp",
      otherAccountRoutingScheme = "obp",
      otherAccountProvider = "obp",
      isBeneficiary = true
    )
  }

}

