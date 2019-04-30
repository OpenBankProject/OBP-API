package code.bankconnectors

/*
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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

import code.api.util.ErrorMessages._
import code.api.util._
import code.bankconnectors.vMar2017.KafkaMappedConnector_vMar2017
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

import scala.collection.immutable.{List, Seq}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object KafkaMappedConnector extends Connector with KafkaHelper with MdcLoggable {

  implicit override val nameOfConnector = KafkaMappedConnector.getClass.getSimpleName

  // Local TTL Cache
  val cacheTTL              = APIUtil.getPropsValue("connector.cache.ttl.seconds", "10").toInt
  val cachedUser            = TTLCache[KafkaInboundValidatedUser](cacheTTL)
  val cachedBank            = TTLCache[KafkaInboundBank](cacheTTL)
  val cachedAccount         = TTLCache[KafkaInboundAccount](cacheTTL)
  val cachedBanks           = TTLCache[List[KafkaInboundBank]](cacheTTL)
  val cachedAccounts        = TTLCache[List[KafkaInboundAccount]](cacheTTL)
  val cachedPublicAccounts  = TTLCache[List[KafkaInboundAccount]](cacheTTL)
  val cachedUserAccounts    = TTLCache[List[KafkaInboundAccount]](cacheTTL)
  val cachedFxRate          = TTLCache[KafkaInboundFXRate](cacheTTL)
  val cachedCounterparty    = TTLCache[KafkaInboundCounterparty](cacheTTL)
  val cachedTransactionRequestTypeCharge = TTLCache[KafkaInboundTransactionRequestTypeCharge](cacheTTL)

  //
  // "Versioning" of the messages sent by this or similar connector might work like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Probably should be in a separate file e.g. Nov2016_messages.scala
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  //

  val formatVersion: String  = "Nov2016"

  // TODO Create and use a case class for each Map so we can document each structure.


  override def getUser( username: String, password: String ): Box[InboundUser] = {
    for {
      req <- tryo {Map[String, String](
        "north" -> "getUser",
        "version" -> formatVersion, // rename version to messageFormat or maybe connector (see above)
        "name" -> "get", // For OBP-JVM compatibility but key name will change
        "target" -> "user", // For OBP-JVM compatibility but key name will change
        "username" -> username,
        "password" -> password
        )}
      u <- tryo{cachedUser.getOrElseUpdate( req.toString, () => process(req).extract[KafkaInboundValidatedUser])}
      recUsername <- tryo{u.displayName}
    } yield {
      if (username == u.displayName) new InboundUser( recUsername, password, recUsername)
      else null
    }
  }

  override def updateUserAccountViewsOld( user: ResourceUser ) = {
    val accounts: List[KafkaInboundAccount] = getBanks(None).map(_._1).openOrThrowException(attemptedToOpenAnEmptyBox).flatMap { bank => {
      val bankId = bank.bankId.value
      logger.info(s"ObpJvm updateUserAccountViews for user.email ${user.email} user.name ${user.name} at bank ${bankId}")
      for {
        username <- tryo {user.name}
        req <- tryo { Map[String, String](
          "north" -> "getBankAccounts",
          "version" -> formatVersion,
          "name" -> "get",
          "username" -> user.name,
          "userId" -> user.userId,
          "bankId" -> bankId,
          "target" -> "accounts")}
        // Generate random uuid to be used as request-response match id
        } yield {
          cachedUserAccounts.getOrElseUpdate(req.toString, () => process(req).extract[List[KafkaInboundAccount]]) 
        }
      }
    }.flatten

    val views = for {
      acc <- accounts
      username <- tryo {user.name}
      views <- tryo {createViews( BankId(acc.bankId),
        AccountId(acc.accountId),
        acc.owners.contains(username),
        acc.generate_public_view,
        acc.generate_accountants_view,
        acc.generate_auditors_view
      )}
      existing_views <- tryo {Views.views.vend.viewsForAccount(BankIdAccountId(BankId(acc.bankId), AccountId(acc.accountId)))}
    } yield {
      setAccountHolder(username, BankId(acc.bankId), AccountId(acc.accountId), acc.owners)
      views.foreach(v => {
        Views.views.vend.addPermission(v.uid, user)
        //logger.info(s"------------> updated view ${v.uid} for resourceuser ${user} and account ${acc}")
      })
      existing_views.filterNot(_.users.contains(user.userPrimaryKey)).foreach (v => {
        Views.views.vend.addPermission(v.uid, user)
        //logger.info(s"------------> added resourceuser ${user} to view ${v.uid} for account ${acc}")
      })
    }
  }


  //gets banks handled by this connector
  override def getBanks(callContext: Option[CallContext]) = {
    val req = Map(
      "north" -> "getBanks",
      "version" -> formatVersion,
      "name" -> "get",
      "target" -> "banks",
      "userId" -> AuthUser.getCurrentResourceUserUserId,
      "username" -> AuthUser.getCurrentUserUsername
      )

    logger.debug(s"Kafka getBanks says: req is: $req")

    logger.debug(s"Kafka getBanks before cachedBanks.getOrElseUpdate")
    val rList = {
      cachedBanks.getOrElseUpdate( req.toString, () => process(req).extract[List[KafkaInboundBank]])
    }

    logger.debug(s"Kafka getBanks says rList is $rList")

    // Loop through list of responses and create entry for each
    val res = { for ( r <- rList ) yield {
        new KafkaBank(r)
      }
    }
    // Return list of results

    logger.debug(s"Kafka getBanks says res is $res")
    Full(res, callContext)
  }

  // Gets current challenge level for transaction request
  override def getChallengeThreshold(bankId: String, accountId: String, viewId: String, transactionRequestType: String, currency: String, userId: String, userName: String, callContext: Option[CallContext]) = Future{
    // Create argument list
    val req = Map(
      "north" -> "getChallengeThreshold",
      "action" -> "obp.getChallengeThreshold",
      "version" -> formatVersion,
      "name" -> AuthUser.getCurrentUserUsername,
      "bankId" -> bankId,
      "accountId" -> accountId,
      "viewId" -> viewId,
      "transactionRequestType" -> transactionRequestType,
      "currency" -> currency,
      "userId" -> userId,
      "username" -> userName
      )
    val r: Option[KafkaInboundChallengeLevel] = process(req).extractOpt[KafkaInboundChallengeLevel]
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

  override def getChargeLevel(bankId: BankId,
                              accountId: AccountId,
                              viewId: ViewId,
                              userId: String,
                              userName: String,
                              transactionRequestType: String,
                              currency: String,
                              callContext:Option[CallContext]) = Future{
    // Create argument list
    val req = Map(
                   "north" -> "getChargeLevel",
                   "action" -> "obp.getChargeLevel",
                   "version" -> formatVersion,
                   "name" -> AuthUser.getCurrentUserUsername,
                   "bankId" -> bankId.value,
                   "accountId" -> accountId.value,
                   "viewId" -> viewId.value,
                   "transactionRequestType" -> transactionRequestType,
                   "currency" -> currency,
                   "userId" -> userId,
                   "username" -> userName
                 )
    val r: Option[KafkaInboundChargeLevel] = process(req).extractOpt[KafkaInboundChargeLevel]
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

  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, callContext: Option[CallContext]) = Future {
    // Create argument list
    val req = Map(
      "north" -> "createChallenge",
      "version" -> formatVersion,
      "name" -> AuthUser.getCurrentUserUsername,
      "bankId" -> bankId.value,
      "accountId" -> accountId.value,
      "userId" -> userId,
      "username" -> AuthUser.getCurrentUserUsername,
      "transactionRequestType" -> transactionRequestType.value,
      "transactionRequestId" -> transactionRequestId
    )
    val r: Option[KafkaInboundCreateChallange] = process(req).extractOpt[KafkaInboundCreateChallange]
    // Return result
    r match {
      // Check does the response data match the requested data
      case Some(x)  => (Full(x.challengeId), callContext)
      case _        => (Empty, callContext)
    }
  }

  override def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext])  = Future{
    // Create argument list
    val req = Map(
      "north" -> "validateChallengeAnswer",
      "version" -> formatVersion,
      "userId" -> AuthUser.getCurrentResourceUserUserId,
      "username" -> AuthUser.getCurrentUserUsername,
      "challengeId" -> challengeId,
      "hashOfSuppliedAnswer" -> hashOfSuppliedAnswer
    )
    val r: Option[KafkaInboundValidateChallangeAnswer] = process(req).extractOpt[KafkaInboundValidateChallangeAnswer]
    // Return result
    r match {
      // Check does the response data match the requested data
        //TODO, error handling, if return the error message, it is not a boolean.
      case Some(x)  => (Full(x.answer.toBoolean), callContext)
      case _        => (Empty, callContext)
    }
  }

  // Gets bank identified by bankId
  override def getBank(bankId: BankId, callContext: Option[CallContext]) = {
    // Create argument list
    val req = Map(
      "north" -> "getBank",
      "version" -> formatVersion,
      "name" -> "get",
      "target" -> "bank",
      "bankId" -> bankId.toString,
      "userId" -> AuthUser.getCurrentResourceUserUserId,
      "username" -> AuthUser.getCurrentUserUsername
      )
    val r = {
      cachedBank.getOrElseUpdate( req.toString, () => process(req).extract[KafkaInboundBank])
    }
    // Return result
    Full(new KafkaBank(r), callContext)
  }

  // Gets transaction identified by bankid, accountid and transactionId
  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]) = {
    val req = Map(
      "north" -> "getTransaction",
      "version" -> formatVersion,
      "userId" -> AuthUser.getCurrentResourceUserUserId,
      "username" -> AuthUser.getCurrentUserUsername,
      "name" -> "get",
      "target" -> "transaction",
      "bankId" -> bankId.toString,
      "accountId" -> accountId.toString,
      "transactionId" -> transactionId.toString
      )
    // Since result is single account, we need only first list entry
    val r = process(req).extractOpt[KafkaInboundTransaction]
    r match {
      // Check does the response data match the requested data
      case Some(x) if transactionId.value != x.transactionId => Failure(ErrorMessages.InvalidConnectorResponseForGetTransaction, Empty, Empty)
      case Some(x) if transactionId.value == x.transactionId => createNewTransaction(x).map(transaction => (transaction, callContext))
      case _ => Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
    }

  }

  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = {
    val limit: OBPLimit = queryParams.collect { case OBPLimit(value) => OBPLimit(value) }.headOption.get
    val offset = queryParams.collect { case OBPOffset(value) => OBPOffset(value) }.headOption.get
    val fromDate = queryParams.collect { case OBPFromDate(date) => OBPFromDate(date) }.headOption.get
    val toDate = queryParams.collect { case OBPToDate(date) => OBPToDate(date)}.headOption.get
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(field, direction) => OBPOrdering(field, direction)}.headOption.get
    val optionalParams = Seq(limit, offset, fromDate, toDate, ordering)

    val req = Map(
      "north" -> "getTransactions",
      "version" -> formatVersion,
      "userId" -> AuthUser.getCurrentResourceUserUserId,
      "username" -> AuthUser.getCurrentUserUsername,
      "name" -> "get",
      "target" -> "transactions",
      "bankId" -> bankId.toString,
      "accountId" -> accountId.toString,
      "queryParams" -> queryParams.toString
      )
    val rList = process(req).extract[List[KafkaInboundTransaction]]
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

  override def getBankAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) = {
    // Generate random uuid to be used as request-response match id
    val req = Map(
      "north" -> "getBankAccount",
      "version" -> formatVersion,
      "userId" -> AuthUser.getCurrentResourceUserUserId,
      "username" -> AuthUser.getCurrentUserUsername,
      "name" -> "get",
      "target" -> "account",
      "bankId" -> bankId.toString,
      "accountId" -> accountId.value
      )
    // Since result is single account, we need only first list entry
    val r = {
      cachedAccount.getOrElseUpdate( req.toString, () => process(req).extract[KafkaInboundAccount])
    }
    // Check does the response data match the requested data
    val accResp = List((BankId(r.bankId), AccountId(r.accountId))).toSet
    val acc = List((bankId, accountId)).toSet
    if ((accResp diff acc).size > 0) throw new Exception(ErrorMessages.InvalidConnectorResponseForGetBankAccount)

    createMappedAccountDataIfNotExisting(r.bankId, r.accountId, r.label)

    Full(new KafkaBankAccount(r),callContext)
  }

   @deprecated("No sense to use list of its to get bankaccount back.","26/04/2019")
   def getBankAccounts(accts: List[(BankId, AccountId)]): List[BankAccount] = {
    val primaryUserIdentifier = AuthUser.getCurrentUserUsername

    val r:List[KafkaInboundAccount] = accts.flatMap { a => {

        logger.info (s"KafkaMappedConnnector.getBankAccounts with params ${a._1.value} and  ${a._2.value} and primaryUserIdentifier is $primaryUserIdentifier")

        val req = Map(
          "north" -> "getBankAccounts",
          "version" -> formatVersion,
          "userId" -> AuthUser.getCurrentResourceUserUserId,
          "username" -> AuthUser.getCurrentUserUsername,
          "name" -> "get",
          "target" -> "accounts",
          "bankId" -> a._1.value,
          "accountId" -> a._2.value
        )
        val r = {
          cachedAccounts.getOrElseUpdate( req.toString, () => process(req).extract[List[KafkaInboundAccount]])
        }
        r
      }
    }


    // Check does the response data match the requested data
    val accRes = for(row <- r) yield {
      (BankId(row.bankId), AccountId(row.accountId))
    }
    if ((accRes.toSet diff accts.toSet).size > 0) throw new Exception(ErrorMessages.InvalidConnectorResponseForGetBankAccounts)

    r.map { t =>
      createMappedAccountDataIfNotExisting(t.bankId, t.accountId, t.label)
      new KafkaBankAccount(t) }
  }

  private def getAccountByNumber(bankId : BankId, number : String) : Box[BankAccount] = {
    // Generate random uuid to be used as request-respose match id
    val req = Map(
      "north" -> "getBankAccount",
      "version" -> formatVersion,
      "userId" -> AuthUser.getCurrentResourceUserUserId,
      "username" -> AuthUser.getCurrentUserUsername,
      "name" -> "get",
      "target" -> "accounts",
      "bankId" -> bankId.toString,
      "number" -> number
    )
    // Since result is single account, we need only first list entry
    val r = {
      cachedAccount.getOrElseUpdate( req.toString, () => process(req).extract[KafkaInboundAccount])
    }
    createMappedAccountDataIfNotExisting(r.bankId, r.accountId, r.label)
    Full(new KafkaBankAccount(r))
  }

  /**
   *
   * refreshes transactions via hbci if the transaction info is sourced from hbci
   *
   *  Checks if the last update of the account was made more than one hour ago.
   *  if it is the case we put a message in the message queue to ask for
   *  transactions updates
   *
   *  It will be used each time we fetch transactions from the DB. But the test
   *  is performed in a different thread.
   */
  /*
  private def updateAccountTransactions(bankId : BankId, accountId : AccountId) = {

    for {
      (bank, _)<- getBank(bankId, None)
      account <- getBankAccountType(bankId, accountId)
    } {
      spawn{
        val useMessageQueue = APIUtil.getPropsAsBoolValue("messageQueue.updateBankAccountsTransaction", false)
        val outDatedTransactions = Box!!account.lastUpdate match {
          case Full(l) => now after time(l.getTime + hours(APIUtil.getPropsAsIntValue("messageQueue.updateTransactionsInterval", 1)))
          case _ => true
        }
        //if(outDatedTransactions && useMessageQueue) {
        //  UpdatesRequestSender.sendMsg(UpdateBankAccount(account.number, bank.national_identifier.get))
        //}
      }
    }
  }
  */


  override def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = {
    //note: kafka mode just used the mapper data
    LocalMappedConnector.getCounterparty(thisBankId, thisAccountId, couterpartyId)
  }

  // Get one counterparty by the Counterparty Id
  override def getCounterpartyByCounterpartyIdFuture(counterpartyId: CounterpartyId, callContext: Option[CallContext]) = Future{

    if (APIUtil.getPropsAsBoolValue("get_counterparties_from_OBP_DB", true)) {
      (Counterparties.counterparties.vend.getCounterparty(counterpartyId.value), callContext)
    } else {
      val req = Map(
        "north" -> "getCounterpartyByCounterpartyId",
        "version" -> formatVersion,
        "name" -> AuthUser.getCurrentUserUsername,
        "action" -> "obp.getCounterpartyByCounterpartyId",
        "counterpartyId" -> counterpartyId.toString
      )
      // Since result is single account, we need only first list entry
      val r = {
        cachedCounterparty.getOrElseUpdate( req.toString, () => process(req).extract[KafkaInboundCounterparty])
      }
      (tryo(new KafkaCounterparty(r)), callContext)
    }
  }





  override def getCounterpartyByIban(iban: String, callContext: Option[CallContext]) = Future {

    if (APIUtil.getPropsAsBoolValue("get_counterparties_from_OBP_DB", true)) {
      (Counterparties.counterparties.vend.getCounterpartyByIban(iban), callContext)
    } else {
      val req = Map(
        "north" -> "getCounterpartyByIban",
        "version" -> formatVersion,
        "userId" -> AuthUser.getCurrentResourceUserUserId,
        "username" -> AuthUser.getCurrentUserUsername,
        "name" -> "get",
        "target" -> COUNTERPARTY.toString,
        "action" -> "obp.getCounterpartyByIban",
        "otherAccountRoutingAddress" -> iban,
        "otherAccountRoutingScheme" -> "IBAN"
      )

      val r = process(req).extract[KafkaInboundCounterparty]

      (tryo{KafkaCounterparty(r)}, callContext)
    }
  }

  override def createOrUpdatePhysicalCard(bankCardNumber: String,
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
                     ) : Box[PhysicalCard] = {
    Empty
  }


  protected override def makePaymentImpl(fromAccount: BankAccount,
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
                              chargePolicy: String) = {
  
    val transactionTime = now
    val currency = fromAccount.currency

    //update the balance of the account for which a transaction is being created
    //val newAccountBalance : Long = account.balance.toLong + Helper.convertToSmallestCurrencyUnits(amount, account.currency)
    //account.balance = newAccountBalance

    val req: Map[String, String] = Map("north" -> "putTransaction",
                                       "action" -> "obp.putTransaction",
                                       "version" -> formatVersion,
                                       "userId" -> AuthUser.getCurrentResourceUserUserId,
                                       "username" -> AuthUser.getCurrentUserUsername,
                                       "name" -> "put",
                                       "target" -> "transaction",
                                       "description" -> description,
                                       "transactionRequestType" -> transactionRequestType.value,
                                       "toCurrency" -> currency, //Now, http request currency must equal fromAccount.currency
                                       "toAmount" -> amount.toString,
                                       "chargePolicy" -> chargePolicy,
                                       //fromAccount
                                       "fromBankId" -> fromAccount.bankId.value,
                                       "fromAccountId" -> fromAccount.accountId.value,
                                       //toAccount
                                       "toBankId" -> toAccount.bankId.value,
                                       "toAccountId" -> toAccount.accountId.value,
                                       //toCounterty
                                       "toCounterpartyId" -> toAccount.accountId.value,
                                       "toCounterpartyOtherBankRoutingAddress" -> toAccount.bankRoutingAddress,
                                       "toCounterpartyOtherAccountRoutingAddress" -> toAccount.accountRoutingAddress,
                                       "toCounterpartyOtherAccountRoutingScheme" -> toAccount.accountRoutingScheme,
                                       "toCounterpartyOtherBankRoutingScheme" -> toAccount.bankRoutingScheme,
                                       "type" -> "AC")


    // Since result is single account, we need only first list entry
    val r = process(req)

    r.extract[KafkaInboundTransactionId] match {
      case r: KafkaInboundTransactionId => Full(TransactionId(r.transactionId))
      case _ => Full(TransactionId("0"))
    }

  }

  /*
    Transaction Requests
  */
  override def getTransactionRequestStatusesImpl() : Box[TransactionRequestStatus] ={
    logger.info("Kafka getTransactionRequestStatusesImpl response -- This is KafkaMappedConnector, just call KafkaMappedConnector_vMar2017 methods:")
    KafkaMappedConnector_vMar2017.getTransactionRequestStatusesImpl()
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
  override def accountExists(bankId: BankId, accountNumber: String)= {
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


    // get the latest FXRate specified by fromCurrencyCode and toCurrencyCode.
  override def getCurrentFxRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = {
    // Create request argument list
    val req = Map(
      "north" -> "getCurrentFxRate",
      "version" -> formatVersion,
      "name" -> AuthUser.getCurrentUserUsername,
      "bankId" -> bankId.value,
      "fromCurrencyCode" -> fromCurrencyCode,
      "toCurrencyCode" -> toCurrencyCode
      )
    val r = {
      cachedFxRate.getOrElseUpdate(req.toString, () => process(req).extract[KafkaInboundFXRate])
    }
    // Return result
    Full(new KafkaFXRate(r))
  }

  //get the current charge specified by bankId, accountId, viewId and transactionRequestType
  override def getTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType): Box[TransactionRequestTypeCharge] = {

    // Create request argument list
    val req = Map(
      "north" -> "getTransactionRequestTypeCharge",
      "version" -> formatVersion,
      "name" -> AuthUser.getCurrentUserUsername,
      "bankId" -> bankId.value,
      "accountId" -> accountId.value,
      "viewId" -> viewId.value,
      "transactionRequestType" -> transactionRequestType.value
    )
    // send the request to kafka and get response
    // TODO the error handling is not good enough, it should divide the error, empty and no-response.
    val r =  tryo {
        cachedTransactionRequestTypeCharge.getOrElseUpdate(req.toString, () => process(req).extract[KafkaInboundTransactionRequestTypeCharge])
      }
    // Return result
    val result = r match {
      case Full(f) => Full(KafkaTransactionRequestTypeCharge(f))
      case _ =>
        for {
          fromAccount <- getBankAccount(bankId, accountId)
          fromAccountCurrency <- tryo{ fromAccount.currency }
        } yield {
          KafkaTransactionRequestTypeCharge(KafkaInboundTransactionRequestTypeCharge(transactionRequestType.value, bankId.value, fromAccountCurrency, "0.00", "Warning! Default value!"))
        }
    }
    result
  }

  override def getEmptyBankAccount(): Box[BankAccount] = {
    Full(new KafkaBankAccount(KafkaInboundAccount(accountId = "",
                                                  bankId = "",
                                                  label = "",
                                                  number = "",
                                                  `type` = "",
                                                  balanceAmount = "",
                                                  balanceCurrency = "",
                                                  iban = "",
                                                  owners = Nil,
                                                  generate_public_view = true,
                                                  generate_accountants_view = true,
                                                  generate_auditors_view = true)))
  }

  /////////////////////////////////////////////////////////////////////////////



  // Helper for creating a transaction
  def createNewTransaction(r: KafkaInboundTransaction):Box[Transaction] = {
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


  case class KafkaBank(r: KafkaInboundBank) extends Bank {
    def fullName           = r.name
    def shortName          = r.name
    def logoUrl            = r.logo
    def bankId             = BankId(r.bankId)
    def nationalIdentifier = "None"  //TODO
    def swiftBic           = "None"  //TODO
    def websiteUrl         = r.url
    def bankRoutingScheme = "None"
    def bankRoutingAddress = "None"
  }

  // Helper for creating other bank account
  def createCounterparty(counterpartyId: String, counterpartyName: String, o: BankAccount, alreadyFoundMetadata : Option[CounterpartyMetadata]) = {
    new Counterparty(
      counterpartyId = alreadyFoundMetadata.map(_.getCounterpartyId).getOrElse(""),
      counterpartyName = counterpartyName,
      nationalIdentifier = "",
      otherBankRoutingAddress = None,
      otherAccountRoutingAddress = None,
      thisAccountId = AccountId(counterpartyId),
      thisBankId = BankId(""),
      kind = "",
      otherBankRoutingScheme = "",
      otherAccountRoutingScheme="",
      otherAccountProvider = "",
      isBeneficiary = true
    )
  }
  case class KafkaBankAccount(r: KafkaInboundAccount) extends BankAccount {
    def accountId : AccountId       = AccountId(r.accountId)
    def accountType : String        = r.`type`
    def balance : BigDecimal        = BigDecimal(r.balanceAmount)
    def currency : String           = r.balanceCurrency
    def name : String               = r.owners.head
    def iban : Option[String]       = Some(r.iban)
    def number : String             = r.number
    def bankId : BankId             = BankId(r.bankId)
    def lastUpdate : Date           = APIUtil.DateWithMsFormat.parse(today.getTime.toString)
    def accountHolder : String      = r.owners.head
    def accountRoutingScheme: String = r.accountRoutingScheme
    def accountRoutingAddress: String = r.accountRoutingAddress
    def branchId: String              = r.branchId
    def accountRoutings: List[AccountRouting] = List()
    def accountRules: List[AccountRule] = List()

    // Fields modifiable from OBP are stored in mapper
    def label : String              = (for {
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, r.accountId))
    } yield {
      d.getLabel
    }).getOrElse(r.number)

  }

  case class KafkaFXRate(kafkaInboundFxRate: KafkaInboundFXRate) extends FXRate {
    def bankId: BankId = kafkaInboundFxRate.bank_id
    def fromCurrencyCode : String= kafkaInboundFxRate.from_currency_code
    def toCurrencyCode : String= kafkaInboundFxRate.to_currency_code
    def conversionValue : Double= kafkaInboundFxRate.conversion_value
    def inverseConversionValue : Double= kafkaInboundFxRate.inverse_conversion_value
    //TODO need to add error handling here for String --> Date transfer
    def effectiveDate : Date= APIUtil.DateWithMsFormat.parse(kafkaInboundFxRate.effective_date)
  }

  case class KafkaCounterparty(counterparty: KafkaInboundCounterparty) extends CounterpartyTrait {
    def createdByUserId: String = counterparty.created_by_user_id
    def name: String = counterparty.name
    def thisBankId: String = counterparty.this_bank_id
    def thisAccountId: String = counterparty.this_account_id
    def thisViewId: String = counterparty.this_view_id
    def counterpartyId: String = counterparty.counterparty_id
    def otherAccountRoutingScheme: String = counterparty.other_account_routing_scheme
    def otherAccountRoutingAddress: String = counterparty.other_account_routing_address
    def otherBankRoutingScheme: String = counterparty.other_bank_routing_scheme
    def otherBankRoutingAddress: String = counterparty.other_bank_routing_address
    def otherBranchRoutingScheme: String = counterparty.other_branch_routing_scheme
    def otherBranchRoutingAddress: String = counterparty.other_branch_routing_address
    def isBeneficiary : Boolean = counterparty.is_beneficiary
    def description: String = ""
    def otherAccountSecondaryRoutingScheme: String = ""
    def otherAccountSecondaryRoutingAddress: String = ""
    def bespoke: List[CounterpartyBespoke] = Nil
  }

  case class KafkaTransactionRequestTypeCharge(kafkaInboundTransactionRequestTypeCharge: KafkaInboundTransactionRequestTypeCharge) extends TransactionRequestTypeCharge{
    def transactionRequestTypeId: String = kafkaInboundTransactionRequestTypeCharge.transaction_request_type_id
    def bankId: String = kafkaInboundTransactionRequestTypeCharge.bank_id
    def chargeCurrency: String = kafkaInboundTransactionRequestTypeCharge.charge_currency
    def chargeAmount: String = kafkaInboundTransactionRequestTypeCharge.charge_amount
    def chargeSummary: String = kafkaInboundTransactionRequestTypeCharge.charge_summary
  }

  case class KafkaInboundBank(
                              bankId : String,
                              name : String,
                              logo : String,
                              url : String)


  /** Bank Branches
    *
    * @param id Uniquely identifies the Branch within the Bank. SHOULD be url friendly (no spaces etc.) Used in URLs
    * @param bank_id MUST match bank_id in Banks
    * @param name Informal name for the Branch
    * @param address Address
    * @param location Geolocation
    * @param meta Meta information including the license this information is published under
    * @param lobby Info about when the lobby doors are open
    * @param driveUp Info about when automated facilities are open e.g. cash point machine
    */
  case class KafkaInboundBranch(
                                 id : String,
                                 bank_id: String,
                                 name : String,
                                 address : KafkaInboundAddress,
                                 location : KafkaInboundLocation,
                                 meta : KafkaInboundMeta,
                                 lobby : Option[KafkaInboundLobby],
                                 driveUp : Option[KafkaInboundDriveUp])

  case class KafkaInboundLicense(
                                 id : String,
                                 name : String)

  case class KafkaInboundMeta(
                              license : KafkaInboundLicense)

  case class KafkaInboundLobby(
                               hours : String)

  case class KafkaInboundDriveUp(
                                 hours : String)

  /**
    *
    * @param line_1 Line 1 of Address
    * @param line_2 Line 2 of Address
    * @param line_3 Line 3 of Address
    * @param city City
    * @param county County i.e. Division of State
    * @param state State i.e. Division of Country
    * @param post_code Post Code or Zip Code
    * @param country_code 2 letter country code: ISO 3166-1 alpha-2
    */
  case class KafkaInboundAddress(
                                 line_1 : String,
                                 line_2 : String,
                                 line_3 : String,
                                 city : String,
                                 county : String, // Division of State
                                 state : String, // Division of Country
                                 post_code : String,
                                 country_code: String)

  case class KafkaInboundLocation(
                                  latitude : Double,
                                  longitude : Double)

  case class KafkaInboundValidatedUser(email: String,
                                       displayName: String)

  // TODO Be consistent use camelCase

  case class KafkaInboundAccount(
                                  accountId : String,
                                  bankId : String,
                                  label : String,
                                  number : String,
                                  `type` : String,
                                  balanceAmount: String,
                                  balanceCurrency: String,
                                  iban : String,
                                  owners : List[String],
                                  generate_public_view : Boolean,
                                  generate_accountants_view : Boolean,
                                  generate_auditors_view : Boolean,
                                  accountRoutingScheme: String  = "None",
                                  accountRoutingAddress: String  = "None",
                                  branchId: String  = "None"
                                 )

  case class KafkaInboundTransaction(
                                      transactionId : String,
                                      accountId : String,
                                      amount: String,
                                      bankId : String,
                                      completedDate: String,
                                      counterpartyId: String,
                                      counterpartyName: String,
                                      currency: String,
                                      description: String,
                                      newBalanceAmount: String,
                                      newBalanceCurrency: String,
                                      postedDate: String,
                                      `type`: String,
                                      userId: String
                                      )

  case class KafkaInboundAtm(
                              id : String,
                              bank_id: String,
                              name : String,
                              address : KafkaInboundAddress,
                              location : KafkaInboundLocation,
                              meta : KafkaInboundMeta
                           )

  case class KafkaInboundProduct(
                                 bank_id : String,
                                 code: String,
                                 name : String,
                                 category : String,
                                 family : String,
                                 super_family : String,
                                 more_info_url : String,
                                 meta : KafkaInboundMeta
                               )

  case class KafkaInboundAccountData(
                                      banks : List[KafkaInboundBank],
                                      users : List[InboundUser],
                                      accounts : List[KafkaInboundAccount]
                                   )

  // We won't need this. TODO clean up.
  case class KafkaInboundData(
                               banks : List[KafkaInboundBank],
                               users : List[InboundUser],
                               accounts : List[KafkaInboundAccount],
                               transactions : List[KafkaInboundTransaction],
                               branches: List[KafkaInboundBranch],
                               atms: List[KafkaInboundAtm],
                               products: List[KafkaInboundProduct],
                               crm_events: List[KafkaInboundCrmEvent]
                            )

  case class KafkaInboundCrmEvent(
                                   id : String, // crmEventId
                                   bank_id : String,
                                   customer: KafkaInboundCustomer,
                                   category : String,
                                   detail : String,
                                   channel : String,
                                   actual_date: String
                                 )

  case class KafkaInboundCustomer(
                                   name: String,
                                   number : String // customer number, also known as ownerId (owner of accounts) aka API User?
                                 )

  case class KafkaInboundTransactionId(
                                        transactionId : String
                                      )

  case class KafkaOutboundTransaction(
                                      north: String,
                                      version: String,
                                      name: String,
                                      accountId: String,
                                      currency: String,
                                      amount: String,
                                      otherAccountId: String,
                                      otherAccountCurrency: String,
                                      transactionType: String)

  case class KafkaInboundChallengeLevel(
                                       limit: String,
                                       currency: String
                                        )
  case class KafkaInboundTransactionRequestStatus(
                                             transactionRequestId : String,
                                             bulkTransactionsStatus: List[KafkaInboundTransactionStatus]
                                           )
  case class KafkaInboundTransactionStatus(
                                transactionId : String,
                                transactionStatus: String,
                                transactionTimestamp: String
                              )
  case class KafkaInboundCreateChallange(challengeId: String)
  case class KafkaInboundValidateChallangeAnswer(answer: String)

  case class KafkaInboundChargeLevel(
                                      currency: String,
                                      amount: String
                                    )

  case class KafkaInboundFXRate(
                                 bank_id: BankId,
                                 from_currency_code: String,
                                 to_currency_code: String,
                                 conversion_value: Double,
                                 inverse_conversion_value: Double,
                                 effective_date: String
                               )

  case class KafkaInboundCounterparty(
                                       name: String,
                                       created_by_user_id: String,
                                       this_bank_id: String,
                                       this_account_id: String,
                                       this_view_id: String,
                                       counterparty_id: String,
                                       other_bank_routing_scheme: String,
                                       other_account_routing_scheme: String,
                                       other_bank_routing_address: String,
                                       other_account_routing_address: String,
                                       other_branch_routing_scheme: String,
                                       other_branch_routing_address: String,
                                       is_beneficiary: Boolean
                                     )


  case class KafkaInboundTransactionRequestTypeCharge(
                                transaction_request_type_id: String,
                                bank_id: String,
                                charge_currency: String,
                                charge_amount: String,
                                charge_summary: String
                               )


}


