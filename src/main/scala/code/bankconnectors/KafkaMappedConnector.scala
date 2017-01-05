package code.bankconnectors

/*
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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

import code.api.util.ErrorMessages
import code.api.v2_1_0.{BranchJsonPost, BranchJsonPut}
import code.branches.Branches.{Branch, BranchId}
import code.branches.MappedBranch
import code.fx.fx
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.comments.MappedComment
import code.metadata.counterparties.{Counterparties, CounterpartyTrait}
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.MappedTag
import code.metadata.transactionimages.MappedTransactionImage
import code.metadata.wheretags.MappedWhereTag
import code.model._
import code.model.dataAccess._
import code.products.Products.ProductCode
import code.transaction.MappedTransaction
import code.transactionrequests.MappedTransactionRequest
import code.transactionrequests.TransactionRequests._
import code.util.{Helper, TTLCache}
import code.views.Views
import net.liftweb.json
import net.liftweb.mapper._
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import net.liftweb.json._
import net.liftweb.common.{Box, Empty, Failure, Full, Loggable}
import code.products.MappedProduct
import code.products.Products.{Product, ProductCode}
import code.products.MappedProduct
import code.products.Products.{Product, ProductCode}

object KafkaMappedConnector extends Connector with Loggable {

  var producer = new KafkaProducer()
  var consumer = new KafkaConsumer()
  type AccountType = KafkaBankAccount

  // Local TTL Cache
  val cacheTTL              = Props.get("connector.cache.ttl.seconds", "0").toInt
  val cachedUser            = TTLCache[KafkaInboundValidatedUser](cacheTTL)
  val cachedBank            = TTLCache[KafkaInboundBank](cacheTTL)
  val cachedAccount         = TTLCache[KafkaInboundAccount](cacheTTL)
  val cachedBanks           = TTLCache[List[KafkaInboundBank]](cacheTTL)
  val cachedAccounts        = TTLCache[List[KafkaInboundAccount]](cacheTTL)
  val cachedPublicAccounts  = TTLCache[List[KafkaInboundAccount]](cacheTTL)
  val cachedUserAccounts    = TTLCache[List[KafkaInboundAccount]](cacheTTL)

  val formatVersion: String  = "Nov2016"

  implicit val formats = net.liftweb.json.DefaultFormats

  def getUser( username: String, password: String ): Box[InboundUser] = {
    for {
      req <- tryo {Map[String, String](
        "north" -> "getUser",
        "version" -> formatVersion,
        "name" -> username,
        "password" -> password
        )}
      u <- tryo{cachedUser.getOrElseUpdate( req.toString, () => process(req).extract[KafkaInboundValidatedUser])}
      recUsername <- tryo{u.display_name}
    } yield {
      if (username == u.display_name) new InboundUser( recUsername, password, recUsername)
      else null
    }
  }

  def updateUserAccountViews( user: APIUser ) = {
    val accounts = for {
      username <- tryo {user.name}
      req <- tryo {Map[String, String](
        "north" -> "getUserAccounts",
        "version" -> formatVersion,
        "name" -> username)}
    // Generate random uuid to be used as request-response match id
    } yield {
      cachedUserAccounts.getOrElseUpdate(req.toString, () => process(req).extract[List[KafkaInboundAccount]])
    }

    val views = for {
      acc <- accounts.getOrElse(List.empty)
      username <- tryo {user.name}
      views <- tryo {createViews( BankId(acc.bank),
        AccountId(acc.id),
        acc.owners.contains(username),
        acc.generate_public_view,
        acc.generate_accountants_view,
        acc.generate_auditors_view
      )}
      existing_views <- tryo {Views.views.vend.views(new KafkaBankAccount(acc))}
    } yield {
      setAccountOwner(username, BankId(acc.bank), AccountId(acc.id), acc.owners)
      views.foreach(v => {
        Views.views.vend.addPermission(v.uid, user)
        logger.info(s"------------> updated view ${v.uid} for apiuser ${user} and account ${acc}")
      })
      existing_views.filterNot(_.users.contains(user.apiId)).foreach (v => {
        Views.views.vend.addPermission(v.uid, user)
        logger.info(s"------------> added apiuser ${user} to view ${v.uid} for account ${acc}")
      })
    }
  }


  //gets banks handled by this connector
  override def getBanks: List[Bank] = {
    val req = Map( 
      "north" -> "getBanks",
      "version" -> formatVersion,
      "name" -> OBPUser.getCurrentUserUsername
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
    res
  }

  // Gets current challenge level for transaction request
  override def getChallengeThreshold(userId: String, accountId: String, transactionRequestType: String, currency: String): (BigDecimal, String) = {
    // Create argument list
    val req = Map( 
      "north" -> "getChallengeThreshold",
      "version" -> formatVersion,
      "name" -> OBPUser.getCurrentUserUsername,
      "userId" -> userId,
      "accountId" -> accountId,
      "transactionRequestType" -> transactionRequestType,
      "currency" -> currency
      )
    val r: Option[KafkaInboundChallengeLevel] = process(req).extractOpt[KafkaInboundChallengeLevel]
    // Return result
    r match {
      // Check does the response data match the requested data
      case Some(x)  => (x.limit, x.currency)
      case _ => {
        val limit = BigDecimal("50")
        val rate = fx.exchangeRate ("EUR", currency)
        val convertedLimit = fx.convert(limit, rate)
        (convertedLimit, currency)
      }
    }
  }

  // Gets bank identified by bankId
  override def getBank(id: BankId): Box[Bank] = {
    // Create argument list
    val req = Map(
      "north" -> "getBank",
      "version" -> formatVersion,
      "bankId" -> id.toString,
      "name" -> OBPUser.getCurrentUserUsername
      )
    val r = {
      cachedBank.getOrElseUpdate( req.toString, () => process(req).extract[KafkaInboundBank])
    }
    // Return result
    Full(new KafkaBank(r))
  }

  // Gets transaction identified by bankid, accountid and transactionId
  def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Box[Transaction] = {
    val req = Map(
      "north" -> "getTransaction",
      "version" -> formatVersion,
      "name" -> OBPUser.getCurrentUserUsername,
      "bankId" -> bankId.toString,
      "accountId" -> accountId.toString,
      "transactionId" -> transactionId.toString
      )
    // Since result is single account, we need only first list entry
    val r = process(req).extractOpt[KafkaInboundTransaction]
    r match {
      // Check does the response data match the requested data
      case Some(x) if transactionId.value != x.id => Failure(ErrorMessages.InvalidGetTransactionConnectorResponse, Empty, Empty)
      case Some(x) if transactionId.value == x.id => createNewTransaction(x)
      case _ => Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
    }

  }

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
    val optionalParams : Seq[QueryParam[MappedTransaction]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering.toSeq).flatten
    val mapperParams = Seq(By(MappedTransaction.bank, bankId.value), By(MappedTransaction.account, accountId.value)) ++ optionalParams

    val req = Map(
      "north" -> "getTransactions",
      "version" -> formatVersion,
      "name" -> OBPUser.getCurrentUserUsername,
      "bankId" -> bankId.toString,
      "accountId" -> accountId.toString,
      "queryParams" -> queryParams.toString
      )
    implicit val formats = net.liftweb.json.DefaultFormats
    val rList = process(req).extract[List[KafkaInboundTransaction]]
    // Check does the response data match the requested data
    val isCorrect = rList.forall(x=>x.this_account.id == accountId.value && x.this_account.bank == bankId.value)
    if (!isCorrect) throw new Exception(ErrorMessages.InvalidGetTransactionsConnectorResponse)
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

  override def getBankAccount(bankId: BankId, accountId: AccountId): Box[KafkaBankAccount] = {
    // Generate random uuid to be used as request-response match id
    val req = Map(
      "north" -> "getBankAccount",
      "version" -> formatVersion,
      "name"  -> OBPUser.getCurrentUserUsername,
      "bankId" -> bankId.toString,
      "accountId" -> accountId.value
      )
    // Since result is single account, we need only first list entry
    implicit val formats = net.liftweb.json.DefaultFormats
    val r = {
      cachedAccount.getOrElseUpdate( req.toString, () => process(req).extract[KafkaInboundAccount])
    }
    // Check does the response data match the requested data
    val accResp = List((BankId(r.bank), AccountId(r.id))).toSet
    val acc = List((bankId, accountId)).toSet
    if ((accResp diff acc).size > 0) throw new Exception(ErrorMessages.InvalidGetBankAccountConnectorResponse)

    createMappedAccountDataIfNotExisting(r.bank, r.id, r.label)

    Full(new KafkaBankAccount(r))
  }

  override def getBankAccounts(accts: List[(BankId, AccountId)]): List[KafkaBankAccount] = {
    // Generate random uuid to be used as request-respose match id
    val req = Map(
      "north" -> "getBankAccounts",
      "version" -> formatVersion,
      "name"  -> OBPUser.getCurrentUserUsername,
      "bankIds" -> accts.map(a => a._1).mkString(","),
      "accountIds" -> accts.map(a => a._2).mkString(",")
      )
    // Since result is single account, we need only first list entry
    implicit val formats = net.liftweb.json.DefaultFormats
    val r = {
      cachedAccounts.getOrElseUpdate( req.toString, () => process(req).extract[List[KafkaInboundAccount]])
    }
    // Check does the response data match the requested data
    val accRes = for(row <- r) yield {
      (BankId(row.bank), AccountId(row.id))
    }
    if ((accRes.toSet diff accts.toSet).size > 0) throw new Exception(ErrorMessages.InvalidGetBankAccountsConnectorResponse)

    r.map { t =>
      createMappedAccountDataIfNotExisting(t.bank, t.id, t.label)
      new KafkaBankAccount(t) }
  }

  private def getAccountByNumber(bankId : BankId, number : String) : Box[AccountType] = {
    // Generate random uuid to be used as request-respose match id
    val req = Map(
      "north" -> "getBankAccount",
      "version" -> formatVersion,
      "name" -> OBPUser.getCurrentUserUsername,
      "bankId" -> bankId.toString,
      "number" -> number
    )
    // Since result is single account, we need only first list entry
    implicit val formats = net.liftweb.json.DefaultFormats
    val r = {
      cachedAccount.getOrElseUpdate( req.toString, () => process(req).extract[KafkaInboundAccount])
    }
    createMappedAccountDataIfNotExisting(r.bank, r.id, r.label)
    Full(new KafkaBankAccount(r))
  }

  def getCounterpartyFromTransaction(thisAccountBankId : BankId, thisAccountId : AccountId, metadata : CounterpartyMetadata) : Box[Counterparty] = {
    //because we don't have a db backed model for OtherBankAccounts, we need to construct it from an
    //OtherBankAccountMetadata and a transaction
    val t = getTransactions(thisAccountBankId, thisAccountId).map { t =>
      t.filter { e =>
        if (e.otherAccount.thisAccountId == metadata.getAccountNumber)
          true
        else
          false
      }
    }.get.head

    val res = new Counterparty(
      //counterparty id is defined to be the id of its metadata as we don't actually have an id for the counterparty itself
      counterPartyId = metadata.metadataId,
      label = metadata.getHolder,
      nationalIdentifier = t.otherAccount.nationalIdentifier,
      otherBankRoutingAddress = None,
      otherAccountRoutingAddress = t.otherAccount.otherAccountRoutingAddress,
      thisAccountId = AccountId(metadata.getAccountNumber),
      thisBankId = t.otherAccount.thisBankId,
      kind = t.otherAccount.kind,
      otherBankId = thisAccountBankId,
      otherAccountId = thisAccountId,
      alreadyFoundMetadata = Some(metadata),

      //TODO V210 following five fields are new, need to be fiexed
      name = "",
      otherBankRoutingScheme = "",
      otherAccountRoutingScheme="",
      otherAccountProvider = "",
      isBeneficiary = true
    )
    Full(res)
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
      bank <- getBank(bankId)
      account <- getBankAccountType(bankId, accountId)
    } {
      spawn{
        val useMessageQueue = Props.getBool("messageQueue.updateBankAccountsTransaction", false)
        val outDatedTransactions = Box!!account.lastUpdate match {
          case Full(l) => now after time(l.getTime + hours(Props.getInt("messageQueue.updateTransactionsInterval", 1)))
          case _ => true
        }
        //if(outDatedTransactions && useMessageQueue) {
        //  UpdatesRequestSender.sendMsg(UpdateBankAccount(account.number, bank.national_identifier.get))
        //}
      }
    }
  }
  */

  //gets the users who are the legal owners/holders of the account
  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] =
    MappedAccountHolder.findAll(
      By(MappedAccountHolder.accountBankPermalink, bankId.value),
      By(MappedAccountHolder.accountPermalink, accountId.value)).map(accHolder => accHolder.user.obj).flatten.toSet


  // Get all counterparties related to an account
  override def getCounterpartiesFromTransaction(bankId: BankId, accountId: AccountId): List[Counterparty] =
    Counterparties.counterparties.vend.getMetadatas(bankId, accountId).flatMap(getCounterpartyFromTransaction(bankId, accountId, _))

  // Get one counterparty related to a bank account
  override def getCounterpartyFromTransaction(bankId: BankId, accountId: AccountId, counterpartyID: String): Box[Counterparty] =
    // Get the metadata and pass it to getOtherBankAccount to construct the other account.
    Counterparties.counterparties.vend.getMetadata(bankId, accountId, counterpartyID).flatMap(getCounterpartyFromTransaction(bankId, accountId, _))

  def getCounterparty(thisAccountBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = Empty

  def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId): Box[CounterpartyTrait] =Empty

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
                     ) : Box[PhysicalCard] = {
    Empty
  }


  override def makePaymentImpl(fromAccount: AccountType, toAccount: AccountType, amt: BigDecimal, description : String): Box[TransactionId] = {
    val fromTransAmt = -amt //from account balance should decrease
    val toTransAmt = amt //to account balance should increase

    //we need to save a copy of this payment as a transaction in each of the accounts involved, with opposite amounts
    val sentTransactionId = saveTransaction(fromAccount, toAccount, fromTransAmt, description)
    saveTransaction(toAccount, fromAccount, toTransAmt, description)

    sentTransactionId
  }


  /**
   * Saves a transaction with amount @amt and counterparty @counterparty for account @account. Returns the id
   * of the saved transaction.
   */
  private def saveTransaction(account : AccountType,
                              counterparty : BankAccount,
                              amt : BigDecimal,
                              description : String) : Box[TransactionId] = {

    val transactionTime = now
    val currency = account.currency

    //update the balance of the account for which a transaction is being created
    val newAccountBalance : Long = account.balance.toLong + Helper.convertToSmallestCurrencyUnits(amt, account.currency)
    //account.balance = newAccountBalance

    val req : Map[String,String] = Map(
      "north" -> "saveTransaction",
      "version" -> formatVersion,
      "name" -> OBPUser.getCurrentUserUsername,
      "accountId" -> account.accountId.value,
      "currency" -> currency,
      "amount" -> amt.toString,
      "otherAccountId" -> counterparty.accountId.value,
      "otherAccountCurrency" -> counterparty.currency,
      "transactionType" -> "AC"
    )


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
  override def getTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId) : Box[Boolean] = ???

  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                            account : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                            status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = {
    val mappedTransactionRequest = MappedTransactionRequest.create
      .mTransactionRequestId(transactionRequestId.value)
      .mType(transactionRequestType.value)
      .mFrom_BankId(account.bankId.value)
      .mFrom_AccountId(account.accountId.value)
      .mBody_To_BankId(counterparty.bankId.value)
      .mBody_To_AccountId(counterparty.accountId.value)
      .mBody_Value_Currency(body.value.currency)
      .mBody_Value_Amount(body.value.amount)
      .mBody_Description(body.description)
      .mStatus(status)
      .mStartDate(now)
      .mEndDate(now).saveMe
    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest)
  }


  override def createTransactionRequestImpl210(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                               account : BankAccount, details: String,
                                               status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = {
    val mappedTransactionRequest = MappedTransactionRequest.create
      .mTransactionRequestId(transactionRequestId.value)
      .mType(transactionRequestType.value)
      .mFrom_BankId(account.bankId.value)
      .mFrom_AccountId(account.accountId.value)
      .mDetails(details)
      .mStatus(status)
      .mStartDate(now)
      .mEndDate(now).saveMe
    Full(mappedTransactionRequest).flatMap(_.toTransactionRequest)
  }

  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = {
    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    mappedTransactionRequest match {
        case Full(tr: MappedTransactionRequest) => Full(tr.mTransactionIDs(transactionId.value).save)
        case _ => Failure("Couldn't find transaction request ${transactionRequestId}")
      }
  }

  override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] = {
    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    mappedTransactionRequest match {
      case Full(tr: MappedTransactionRequest) => Full{
        tr.mChallenge_Id(challenge.id)
        tr.mChallenge_AllowedAttempts(challenge.allowed_attempts)
        tr.mChallenge_ChallengeType(challenge.challenge_type).save
      }
      case _ => Failure(s"Couldn't find transaction request ${transactionRequestId} to set transactionId")
    }
  }

  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = {
    val mappedTransactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    mappedTransactionRequest match {
      case Full(tr: MappedTransactionRequest) => Full(tr.mStatus(status).save)
      case _ => Failure(s"Couldn't find transaction request ${transactionRequestId} to set status")
    }
  }


  override def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    val transactionRequests = MappedTransactionRequest.findAll(By(MappedTransactionRequest.mFrom_AccountId, fromAccount.accountId.value),
                                                               By(MappedTransactionRequest.mFrom_BankId, fromAccount.bankId.value))

    Full(transactionRequests.flatMap(_.toTransactionRequest))
  }

  override def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    val transactionRequests = MappedTransactionRequest.findAll(By(MappedTransactionRequest.mFrom_AccountId, fromAccount.accountId.value),
      By(MappedTransactionRequest.mFrom_BankId, fromAccount.bankId.value))

    Full(transactionRequests.flatMap(_.toTransactionRequest))
  }

  override def getTransactionRequestImpl(transactionRequestId: TransactionRequestId) : Box[TransactionRequest] = {
    val transactionRequest = MappedTransactionRequest.find(By(MappedTransactionRequest.mTransactionRequestId, transactionRequestId.value))
    transactionRequest.flatMap(_.toTransactionRequest)
  }


  override def getTransactionRequestTypesImpl(fromAccount : BankAccount) : Box[List[TransactionRequestType]] = {
    //TODO: write logic / data access
    Full(List(TransactionRequestType("SANDBOX_TAN")))
  }

  /*
    Bank account creation
   */

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  //again assume national identifier is unique
  override def createBankAndAccount(bankName: String, bankNationalIdentifier: String, accountNumber: String,
                                    accountType: String, accountLabel: String,  currency: String, accountHolderName: String): (Bank, BankAccount) = {
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
  override def removeAccount(bankId: BankId, accountId: AccountId) : Boolean = {
    //delete comments on transactions of this account
    val commentsDeleted = MappedComment.bulkDelete_!!(
      By(MappedComment.bank, bankId.value),
      By(MappedComment.account, accountId.value)
    )

    //delete narratives on transactions of this account
    val narrativesDeleted = MappedNarrative.bulkDelete_!!(
      By(MappedNarrative.bank, bankId.value),
      By(MappedNarrative.account, accountId.value)
    )

    //delete narratives on transactions of this account
    val tagsDeleted = MappedTag.bulkDelete_!!(
      By(MappedTag.bank, bankId.value),
      By(MappedTag.account, accountId.value)
    )

    //delete WhereTags on transactions of this account
    val whereTagsDeleted = MappedWhereTag.bulkDelete_!!(
      By(MappedWhereTag.bank, bankId.value),
      By(MappedWhereTag.account, accountId.value)
    )

    //delete transaction images on transactions of this account
    val transactionImagesDeleted = MappedTransactionImage.bulkDelete_!!(
      By(MappedTransactionImage.bank, bankId.value),
      By(MappedTransactionImage.account, accountId.value)
    )

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
  override def createSandboxBankAccount(bankId: BankId, accountId: AccountId, accountNumber: String,
                                        accountType: String, accountLabel: String, currency: String,
                                        initialBalance: BigDecimal, accountHolderName: String): Box[BankAccount] = {

    for {
      bank <- getBank(bankId) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
    } yield {

      val balanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(initialBalance, currency)
      createAccountIfNotExisting(bankId, accountId, accountNumber, accountType, accountLabel, currency, balanceInSmallestCurrencyUnits, accountHolderName)
    }

  }

  //sets a user as an account owner/holder
  override def setAccountHolder(bankAccountUID: BankAccountUID, user: User): Unit = {
    MappedAccountHolder.createMappedAccountHolder(user.apiId.value, bankAccountUID.accountId.value, bankAccountUID.bankId.value)
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
  private def getBankByNationalIdentifier(nationalIdentifier : String) : Box[Bank] = {
    MappedBank.find(By(MappedBank.national_identifier, nationalIdentifier))
  }


  private val bigDecimalFailureHandler : PartialFunction[Throwable, Unit] = {
    case ex : NumberFormatException => {
      logger.warn(s"could not convert amount to a BigDecimal: $ex")
    }
  }

  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, otherAccountHolder: String): Int = {
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

  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date) : Boolean = {
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




  /////////////////////////////////////////////////////////////////////////////



  // Helper for creating a transaction
  def createNewTransaction(r: KafkaInboundTransaction):Box[Transaction] = {
    var datePosted: Date = null
    if (r.details.posted != null) // && r.details.posted.matches("^[0-9]{8}$"))
      datePosted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(r.details.posted)

    var dateCompleted: Date = null
    if (r.details.completed != null) // && r.details.completed.matches("^[0-9]{8}$"))
      dateCompleted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(r.details.completed)

    for {
        counterparty <- tryo{r.counterparty}
        thisAccount <- getBankAccount(BankId(r.this_account.bank), AccountId(r.this_account.id))
        //creates a dummy OtherBankAccount without an OtherBankAccountMetadata, which results in one being generated (in OtherBankAccount init)
        dummyOtherBankAccount <- tryo{createCounterparty(counterparty.get, thisAccount, None)}
        //and create the proper OtherBankAccount with the correct "id" attribute set to the metadataId of the OtherBankAccountMetadata object
        //note: as we are passing in the OtherBankAccountMetadata we don't incur another db call to get it in OtherBankAccount init
        counterparty <- tryo{createCounterparty(counterparty.get, thisAccount, Some(dummyOtherBankAccount.metadata))}
      } yield {
        // Create new transaction
        new Transaction(
          r.id,                             // uuid:String
          TransactionId(r.id),              // id:TransactionId
          thisAccount,                      // thisAccount:BankAccount
          counterparty,                     // otherAccount:OtherBankAccount
          r.details.`type`,                 // transactionType:String
          BigDecimal(r.details.value),      // val amount:BigDecimal
          thisAccount.currency,             // currency:String
          Some(r.details.description),      // description:Option[String]
          datePosted,                       // startDate:Date
          dateCompleted,                    // finishDate:Date
          BigDecimal(r.details.new_balance) // balance:BigDecimal)
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
  }

  // Helper for creating other bank account
  def createCounterparty(c: KafkaInboundTransactionCounterparty, o: KafkaBankAccount, alreadyFoundMetadata : Option[CounterpartyMetadata]) = {
    new Counterparty(
      counterPartyId = alreadyFoundMetadata.map(_.metadataId).getOrElse(""),
      label = c.account_number.getOrElse(c.name.getOrElse("")),
      nationalIdentifier = "",
      otherBankRoutingAddress = None,
      otherAccountRoutingAddress = None,
      thisAccountId = AccountId(c.account_number.getOrElse("")),
      thisBankId = BankId(""),
      kind = "",
      otherBankId = o.bankId,
      otherAccountId = o.accountId,
      alreadyFoundMetadata = alreadyFoundMetadata,

      //TODO V210 following five fields are new, need to be fiexed
      name = "",
      otherBankRoutingScheme = "",
      otherAccountRoutingScheme="",
      otherAccountProvider = "",
      isBeneficiary = true


    )
  }

  override def getProducts(bankId: BankId): Box[List[Product]] = Empty

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = Empty

  override  def createOrUpdateBranch(branch: BranchJsonPost ): Box[Branch] = Empty

  override def getBranch(bankId : BankId, branchId: BranchId) : Box[MappedBranch]= Empty

  case class KafkaBankAccount(r: KafkaInboundAccount) extends BankAccount {
    def accountId : AccountId       = AccountId(r.id)
    def accountType : String        = r.`type`
    def balance : BigDecimal        = BigDecimal(r.balance.amount)
    def currency : String           = r.balance.currency
    def name : String               = r.owners.head
    def swift_bic : Option[String]  = Some("swift_bic") //TODO
    def iban : Option[String]       = Some(r.IBAN)
    def number : String             = r.number
    def bankId : BankId             = BankId(r.bank)
    def lastUpdate : Date           = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).parse(today.getTime.toString)
    def accountHolder : String      = r.owners.head

    // Fields modifiable from OBP are stored in mapper
    def label : String              = (for {
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, r.id))
    } yield {
      d.getLabel
    }).getOrElse(r.number)

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

  case class KafkaInboundValidatedUser(
                                       email : String,
                                       display_name : String)

  case class KafkaInboundAccount(
                                  id : String,
                                  bank : String,
                                  label : String,
                                  number : String,
                                  `type` : String,
                                  balance : KafkaInboundBalance,
                                  IBAN : String,
                                  owners : List[String],
                                  generate_public_view : Boolean,
                                  generate_accountants_view : Boolean,
                                  generate_auditors_view : Boolean)

  case class KafkaInboundBalance(
                                 currency : String,
                                 amount : String)

  case class KafkaInboundTransaction(
                                      id : String,
                                      this_account : KafkaInboundAccountId,
                                      counterparty : Option[KafkaInboundTransactionCounterparty],
                                      details : KafkaInboundTransactionDetails)

  case class KafkaInboundTransactionCounterparty(
                                           name : Option[String],  // Also known as Label
                                           account_number : Option[String])

  case class KafkaInboundAccountId(
                                   id : String,
                                   bank : String)

  case class KafkaInboundTransactionDetails(
                                        `type` : String,
                                        description : String,
                                        posted : String,
                                        completed : String,
                                        new_balance : String,
                                        value : String)


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
                                       limit: BigDecimal,
                                       currency: String
                                        )



  def process(request: Map[String,String]): json.JValue = {
    val reqId = UUID.randomUUID().toString
    if (producer.send(reqId, request, "1")) {
      // Request sent, now we wait for response with the same reqId
      val res = consumer.getResponse(reqId)
      return res
    }
    return json.parse("""{"error":"could not send message to kafka"}""")
  }


}


import java.util.{Properties, UUID}

import kafka.consumer.{Consumer, _}
import kafka.message._
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.utils.Json
import net.liftweb.common.Loggable
import net.liftweb.json
import net.liftweb.json._
import net.liftweb.util.Props


class KafkaConsumer(val zookeeper: String = Props.get("kafka.zookeeper_host").openOrThrowException("no kafka.zookeeper_host set"),
                    val topic: String     = Props.get("kafka.response_topic").openOrThrowException("no kafka.response_topic set"),
                    val delay: Long       = 0) extends Loggable {

  val zkProps = new Properties()
  zkProps.put("log4j.logger.org.apache.zookeeper", "ERROR")
  org.apache.log4j.PropertyConfigurator.configure(zkProps)

  def createConsumerConfig(zookeeper: String, groupId: String): ConsumerConfig = {
    val props = new Properties()
    props.put("zookeeper.connect", zookeeper)
    props.put("group.id", groupId)
    props.put("auto.offset.reset", "smallest")
    props.put("auto.commit.enable", "true")
    props.put("zookeeper.sync.time.ms", "2000")
    props.put("auto.commit.interval.ms", "1000")
    props.put("zookeeper.session.timeout.ms", "6000")
    props.put("zookeeper.connection.timeout.ms", "6000")
    props.put("consumer.timeout.ms", "20000")
    val config = new ConsumerConfig(props)
    config
  }

  def getResponse(reqId: String): json.JValue = {
    // create consumer with unique groupId in order to prevent race condition with kafka
    val config = createConsumerConfig(zookeeper, UUID.randomUUID.toString)
    val consumer = Consumer.create(config)
    // recreate stream for topic if not existing
    val consumerMap = consumer.createMessageStreams(Map(topic -> 1))

    val streams = consumerMap.get(topic).get
    // process streams
    for (stream <- streams) {
      val it = stream.iterator()
      try {
        // wait for message
        while (it.hasNext()) {
          val mIt = it.next()
          // skip null entries
          if (mIt != null && mIt.key != null && mIt.message != null) {
            val msg = new String(mIt.message(), "UTF8")
            val key = new String(mIt.key(), "UTF8")
            // check if the id matches
            if (key == reqId) {
              // Parse JSON message
              val j = json.parse(msg)
              // disconnect from Kafka
              consumer.shutdown()
              // return as JSON
              return j \\ "data"
            }
          } else {
            logger.warn("KafkaConsumer: Got null value/key from kafka. Might be south-side connector issue.")
          }
        }
        return json.parse("""{"error":"KafkaConsumer could not fetch response"}""") //TODO: replace with standard message
      }
      catch {
        case e:kafka.consumer.ConsumerTimeoutException =>
          logger.error("KafkaConsumer: timeout")
          return json.parse("""{"error":"KafkaConsumer timeout"}""") //TODO: replace with standard message
      }
    }
    // disconnect from kafka
    consumer.shutdown()
    logger.info("KafkaProducer: shutdown")
    return json.parse("""{"info":"KafkaConsumer shutdown"}""") //TODO: replace with standard message
  }
}


class KafkaProducer(
                          topic: String          = Props.get("kafka.request_topic").openOrThrowException("no kafka.request_topic set"),
                          brokerList: String     = Props.get("kafka.host")openOr("localhost:9092"),
                          clientId: String       = UUID.randomUUID().toString,
                          synchronously: Boolean = true,
                          compress: Boolean      = true,
                          batchSize: Integer     = 200,
                          messageSendMaxRetries: Integer = 3,
                          requestRequiredAcks: Integer   = -1
                          ) extends Loggable {


  // determine compression codec
  val codec = if (compress) DefaultCompressionCodec.codec else NoCompressionCodec.codec

  // configure producer
  val props = new Properties()
  props.put("compression.codec", codec.toString)
  props.put("producer.type", if (synchronously) "sync" else "async")
  props.put("metadata.broker.list", brokerList)
  props.put("batch.num.messages", batchSize.toString)
  props.put("message.send.max.retries", messageSendMaxRetries.toString)
  props.put("request.required.acks", requestRequiredAcks.toString)
  props.put("client.id", clientId.toString)

  // create producer
  val producer = new Producer[AnyRef, AnyRef](new ProducerConfig(props))

  // create keyed message since we will use the key as id for matching response to a request
  def kafkaMesssage(key: Array[Byte], message: Array[Byte], partition: Array[Byte]): KeyedMessage[AnyRef, AnyRef] = {
    if (partition == null) {
      // no partiton specified
      new KeyedMessage(topic, key, message)
    } else {
      // specific partition 
      new KeyedMessage(topic, key, partition, message)
    }
  }

  implicit val formats = DefaultFormats

  def send(key: String, request: Map[String, String], partition: String = null): Boolean = {
    val message      = Json.encode(request)
    // translate strings to utf8 before sending to kafka
    send(key.getBytes("UTF8"), message.getBytes("UTF8"), if (partition == null) null else partition.getBytes("UTF8"))
  }

  def send(key: Array[Byte], message: Array[Byte], partition: Array[Byte]): Boolean = {
    try {
      // actually send the message to kafka
      producer.send(kafkaMesssage(key, message, partition))
    } catch {
      case e: kafka.common.FailedToSendMessageException =>
        logger.error("KafkaProducer: Failed to send message")
        return false
      case e: Throwable =>
        logger.error("KafkaProducer: Unknown error while trying to send message")
        e.printStackTrace()
        return false
    }
    true
  }

}
