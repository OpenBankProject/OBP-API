package code.bankconnectors

import code.api.Constant._
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.model.TransactionStatus.mapTransactionStatus
import code.api.cache.Caching
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util._
import code.branches.MappedBranch
import code.fx.fx.TTL
import code.management.ImporterAPI.ImporterTransaction
import code.model.dataAccess.{BankAccountRouting, MappedBank, MappedBankAccount}
import code.model.toBankAccountExtended
import code.transaction.MappedTransaction
import code.transactionrequests._
import code.util.Helper
import code.util.Helper._
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.{AccountRoutingScheme, PaymentServiceTypes, TransactionRequestStatus, TransactionRequestTypes}
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common._
import net.liftweb.json.Serialization.write
import net.liftweb.json.{NoTypeHints, Serialization}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.{now, tryo}

import java.util.Date
import java.util.UUID.randomUUID
import scala.concurrent._
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random



//Try to keep LocalMappedConnector smaller, so put OBP internal code here. these methods will not be exposed to CBS side.
object LocalMappedConnectorInternal extends MdcLoggable {
  
  def createTransactionRequestBGInternal(
    initiator: Option[User],
    paymentServiceType: PaymentServiceTypes,
    transactionRequestType: TransactionRequestTypes,
    transactionRequestBody: BerlinGroupTransactionRequestCommonBodyJson,
    callContext: Option[CallContext]
  ): Future[(Full[TransactionRequestBGV1], Option[CallContext])] = {
    for {

      user <- NewStyle.function.tryons(s"$UnknownError Can not get user for mapped createTransactionRequestBGInternal method  ", 400, callContext) {
        initiator.head
      }
      transDetailsSerialized <- NewStyle.function.tryons(s"$UnknownError Can not serialize in request Json ", 400, callContext) {
        write(transactionRequestBody)(Serialization.formats(NoTypeHints))
      }

      //for Berlin Group, the account routing address is the IBAN.
      fromAccountIban = transactionRequestBody.debtorAccount.iban
      toAccountIban = transactionRequestBody.creditorAccount.iban

      (fromAccount, callContext) <- NewStyle.function.getBankAccountByIban(fromAccountIban, callContext)
      (ibanChecker, callContext) <- NewStyle.function.validateAndCheckIbanNumber(toAccountIban, callContext)
      _ <- Helper.booleanToFuture(invalidIban, cc = callContext) {
        ibanChecker.isValid == true
      }
      (toAccount, callContext) <- NewStyle.function.getToBankAccountByIban(toAccountIban, callContext)

      // Removed view SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID
      viewId = ViewId(SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID)
      fromBankIdAccountId = BankIdAccountId(fromAccount.bankId, fromAccount.accountId)
      view <- NewStyle.function.checkAccountAccessAndGetView(viewId, fromBankIdAccountId, Full(user), callContext)
      _ <- Helper.booleanToFuture(InsufficientAuthorisationToCreateTransactionRequest, cc = callContext) {
        view.canAddTransactionRequestToAnyAccount
      }

      (paymentLimit, callContext) <- Connector.connector.vend.getPaymentLimit(
        fromAccount.bankId.value,
        fromAccount.accountId.value,
        viewId.value,
        transactionRequestType.toString,
        transactionRequestBody.instructedAmount.currency,
        user.userId,
        user.name,
        callContext
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetPaymentLimit ", 400), i._2)
      }

      paymentLimitAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetPaymentLimit. payment limit amount ${paymentLimit.amount} not convertible to number", 400, callContext) {
        BigDecimal(paymentLimit.amount)
      }

      //We already checked the value in API level.
      transactionAmount = BigDecimal(transactionRequestBody.instructedAmount.amount)

      _ <- Helper.booleanToFuture(s"$InvalidJsonValue the payment amount is over the payment limit($paymentLimit)", 400, callContext) {
        transactionAmount <= paymentLimitAmount
      }

      // Prevent default value for transaction request type (at least).
      _ <- Helper.booleanToFuture(s"$InvalidTransactionRequestCurrency From Account Currency is ${fromAccount.currency}, but Requested instructedAmount.currency is: ${transactionRequestBody.instructedAmount.currency}", cc = callContext) {
        transactionRequestBody.instructedAmount.currency == fromAccount.currency
      }

      // Get the threshold for a challenge. i.e. over what value do we require an out of Band security challenge to be sent?
      (challengeThreshold, callContext) <- Connector.connector.vend.getChallengeThreshold(
        fromAccount.bankId.value,
        fromAccount.accountId.value,
        viewId.value,
        transactionRequestType.toString,
        transactionRequestBody.instructedAmount.currency,
        user.userId,
        user.name,
        callContext
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChallengeThreshold ", 400), i._2)
      }
      challengeThresholdAmount <- NewStyle.function.tryons(s"$InvalidConnectorResponseForGetChallengeThreshold. challengeThreshold amount ${challengeThreshold.amount} not convertible to number", 400, callContext) {
        BigDecimal(challengeThreshold.amount)
      }
      (status, callContext) <- NewStyle.function.getStatus(
        challengeThresholdAmount,
        transactionAmount,
        TransactionRequestType(transactionRequestType.toString),
        callContext
      )
      (chargeLevel, callContext) <- Connector.connector.vend.getChargeLevelC2(
        BankId(fromAccount.bankId.value),
        AccountId(fromAccount.accountId.value),
        viewId,
        user.userId,
        user.name,
        transactionRequestType.toString,
        transactionRequestBody.instructedAmount.currency,
        transactionRequestBody.instructedAmount.amount,
        toAccount.accountRoutings,
        Nil,
        callContext
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChargeLevel ", 400), i._2)
      }

      chargeLevelAmount <- NewStyle.function.tryons(s"$InvalidNumber chargeLevel.amount: ${chargeLevel.amount} can not be transferred to decimal !", 400, callContext) {
        BigDecimal(chargeLevel.amount)
      }

      (chargeValue, callContext) <- NewStyle.function.getChargeValue(chargeLevelAmount, transactionAmount, callContext)
      
      charge = TransactionRequestCharge("Total charges for completed transaction", AmountOfMoney(transactionRequestBody.instructedAmount.currency, chargeValue))

      // Always create a new Transaction Request
      transactionRequest <- Future {
        val transactionRequest = TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl210(
          TransactionRequestId(generateUUID()),
          TransactionRequestType(transactionRequestType.toString),
          fromAccount,
          toAccount,
          TransactionRequestCommonBodyJSONCommons(
            transactionRequestBody.instructedAmount,
            ""
          ),
          transDetailsSerialized,
          mapTransactionStatus(status.toString),
          charge,
          "", // chargePolicy is not used in BG so far.
          Some(paymentServiceType.toString),
          Some(transactionRequestBody),
          Some(ConstantsBG.berlinGroupVersion1.apiStandard),
          Some(ConstantsBG.berlinGroupVersion1.apiShortVersion),
          callContext
        )
        transactionRequest
      } map {
        unboxFullOrFail(_, callContext, s"$InvalidConnectorResponseForCreateTransactionRequestImpl210")
      }

      // If no challenge necessary, create Transaction immediately and put in data store and object to return
      (transactionRequest, callContext) <- status match {
        case TransactionRequestStatus.COMPLETED =>
          for {
            (createdTransactionId, callContext) <- NewStyle.function.makePaymentv210(
              fromAccount,
              toAccount,
              transactionRequest.id,
              TransactionRequestCommonBodyJSONCommons(
                transactionRequestBody.instructedAmount,
                "" //BG no description so far
              ),
              transactionAmount,
              "", //BG no description so far
              TransactionRequestType(transactionRequestType.toString),
              "", // chargePolicy is not used in BG so far.,
              callContext
            )
            //set challenge to null, otherwise it have the default value "challenge": {"id": "","allowed_attempts": 0,"challenge_type": ""}
            transactionRequest <- Future(transactionRequest.copy(challenge = null))

            //save transaction_id into database
            _ <- Connector.connector.vend.saveTransactionRequestTransaction(transactionRequest.id, createdTransactionId,callContext)
            //update transaction_id field for variable 'transactionRequest'
            transactionRequest <- Future(transactionRequest.copy(transaction_ids = createdTransactionId.value))

          } yield {
            logger.debug(s"createTransactionRequestv210.createdTransactionId return: $transactionRequest")
            (transactionRequest, callContext)
          }
        case _ => Future(transactionRequest, callContext)
      }
    } yield {
      logger.debug(transactionRequest)
      (Full(TransactionRequestBGV1(transactionRequest.id, transactionRequest.status)), callContext)
    }
  }



  /*
    Bank account creation
   */

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  //again assume national identifier is unique
  def createBankAndAccount(
    bankName: String,
    bankNationalIdentifier: String,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String,
    callContext: Option[CallContext]
  ): Box[(Bank, BankAccount)] = {
    //don't require and exact match on the name, just the identifier
    val bank = MappedBank.find(By(MappedBank.national_identifier, bankNationalIdentifier)) match {
      case Full(b) =>
        logger.debug(s"bank with id ${b.bankId} and national identifier ${b.nationalIdentifier} found")
        b
      case _ =>
        logger.debug(s"creating bank with national identifier $bankNationalIdentifier")
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
      accountNumber, accountType,
      accountLabel, currency,
      0L, accountHolderName,
      "",
      List.empty
    )

    account.map(account => (bank, account))
  }


  def createAccountIfNotExisting(
    bankId: BankId,
    accountId: AccountId,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    balanceInSmallestCurrencyUnits: Long,
    accountHolderName: String,
    branchId: String,
    accountRoutings: List[AccountRouting],
  ): Box[BankAccount] = {
    Connector.connector.vend.getBankAccountLegacy(bankId, accountId, None).map(_._1) match {
      case Full(a) =>
        logger.debug(s"account with id $accountId at bank with id $bankId already exists. No need to create a new one.")
        Full(a)
      case _ => tryo {
        accountRoutings.map(accountRouting =>
          BankAccountRouting.create
            .BankId(bankId.value)
            .AccountId(accountId.value)
            .AccountRoutingScheme(accountRouting.scheme)
            .AccountRoutingAddress(accountRouting.address)
            .saveMe()
        )
        MappedBankAccount.create
          .bank(bankId.value)
          .theAccountId(accountId.value)
          .accountNumber(accountNumber)
          .kind(accountType)
          .accountLabel(accountLabel)
          .accountCurrency(currency.toUpperCase)
          .accountBalance(balanceInSmallestCurrencyUnits)
          .holder(accountHolderName)
          .mBranchId(branchId)
          .saveMe()
      }
    }
  }


  //transaction import api uses bank national identifiers to uniquely indentify banks,
  //which is unfortunate as theoretically the national identifier is unique to a bank within
  //one country
  private def getBankByNationalIdentifier(nationalIdentifier: String): Box[Bank] = {
    MappedBank.find(By(MappedBank.national_identifier, nationalIdentifier))
  }

  private def getAccountByNumber(bankId: BankId, number: String): Box[BankAccount] = {
    MappedBankAccount.find(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.accountNumber, number))
  }

  private val bigDecimalFailureHandler: PartialFunction[Throwable, Unit] = {
    case ex: NumberFormatException => {
      logger.warn(s"could not convert amount to a BigDecimal: $ex")
    }
  }

  //used by transaction import api call to check for duplicates
  def getMatchingTransactionCount(bankNationalIdentifier: String, accountNumber: String, amount: String, completed: Date, otherAccountHolder: String): Box[Int] = {
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
  
  
  def createImportedTransaction(transaction: ImporterTransaction): Box[Transaction] = {
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

  //used by the transaction import api
  def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal): Box[Boolean] = {
    //this will be Full(true) if everything went well
    val result = for {
      (bank, _) <- Connector.connector.vend.getBankLegacy(bankId, None)
      account <- Connector.connector.vend.getBankAccountLegacy(bankId, accountId, None).map(_._1).map(_.asInstanceOf[MappedBankAccount])
    } yield {
      account.accountBalance(Helper.convertToSmallestCurrencyUnits(newBalance, account.currency)).save
      setBankAccountLastUpdated(bank.nationalIdentifier, account.number, now).openOrThrowException(attemptedToOpenAnEmptyBox)
    }

    Full(result.getOrElse(false))
  }

  def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber: String, updateDate: Date): Box[Boolean] = {
    val result = for {
      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
      account <- getAccountByNumber(bankId, accountNumber)
    } yield {
      val acc = MappedBankAccount.find(
        By(MappedBankAccount.bank, bankId.value),
        By(MappedBankAccount.theAccountId, account.accountId.value)
      )
      acc match {
        case Full(a) => a.accountLastUpdate(updateDate).save
        case _ => logger.warn("can't set bank account.lastUpdated because the account was not found"); false
      }
    }
    Full(result.getOrElse(false))
  }


  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
  def createSandboxBankAccount(
    bankId: BankId,
    accountId: AccountId,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    initialBalance: BigDecimal,
    accountHolderName: String,
    branchId: String,
    accountRoutings: List[AccountRouting]
  ): Box[BankAccount] = {

    for {
      (_, _) <- Connector.connector.vend.getBankLegacy(bankId, None) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
      balanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(initialBalance, currency)
      account <- LocalMappedConnectorInternal.createAccountIfNotExisting (
        bankId,
        accountId,
        accountNumber,
        accountType,
        accountLabel,
        currency,
        balanceInSmallestCurrencyUnits,
        accountHolderName,
        branchId,
        accountRoutings
      ) ?~! AccountRoutingAlreadyExist
    } yield {
      account
    }

  }

  //generates an unused account number and then creates the sandbox account using that number
  @deprecated("This return Box, not a future, try to use @createBankAccount instead. ", "10-05-2019")
  def createBankAccountLegacy(
    bankId: BankId,
    accountId: AccountId,
    accountType: String,
    accountLabel: String,
    currency: String,
    initialBalance: BigDecimal,
    accountHolderName: String,
    branchId: String,
    accountRoutings: List[AccountRouting]
  ): Box[BankAccount] = {
    val uniqueAccountNumber = {
      def exists(number: String) = LocalMappedConnectorInternal.accountExists(bankId, number).openOrThrowException(attemptedToOpenAnEmptyBox)

      def appendUntilOkay(number: String): String = {
        val newNumber = number + Random.nextInt(10)
        if (!exists(newNumber)) newNumber
        else appendUntilOkay(newNumber)
      }

      //generates a random 8 digit account number
      val firstTry = (Random.nextDouble() * 10E8).toInt.toString
      appendUntilOkay(firstTry)
    }

    LocalMappedConnectorInternal.createSandboxBankAccount(
      bankId,
      accountId,
      uniqueAccountNumber,
      accountType,
      accountLabel,
      currency,
      initialBalance,
      accountHolderName,
      branchId: String, //added field in V220
      accountRoutings
    )

  }

  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  def accountExists(bankId : BankId, accountNumber : String) : Box[Boolean] = {
    Full(MappedBankAccount.count(
      By(MappedBankAccount.bank, bankId.value),
      By(MappedBankAccount.accountNumber, accountNumber)) > 0)
  }

  def getBranchLocal(bankId: BankId, branchId: BranchId): Box[BranchT] = {
    MappedBranch
      .find(
        By(MappedBranch.mBankId, bankId.value),
        By(MappedBranch.mBranchId, branchId.value))
      .map(
        branch =>
          branch.branchRouting.map(_.scheme) == null && branch.branchRouting.map(_.address) == null match {
            case true => branch.mBranchRoutingScheme("OBP").mBranchRoutingAddress(branch.branchId.value)
            case _ => branch
          }
      )
  }

  /**
   * get the TransactionRequestTypeCharge from the TransactionRequestTypeCharge table
   * In Mapped, we will ignore accountId, viewId for now.
   */
  def getTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType): Box[TransactionRequestTypeCharge] = {
    val transactionRequestTypeChargeMapper = MappedTransactionRequestTypeCharge.find(
      By(MappedTransactionRequestTypeCharge.mBankId, bankId.value),
      By(MappedTransactionRequestTypeCharge.mTransactionRequestTypeId, transactionRequestType.value))

    val transactionRequestTypeCharge = transactionRequestTypeChargeMapper match {
      case Full(transactionRequestType) => TransactionRequestTypeChargeMock(
        transactionRequestType.transactionRequestTypeId,
        transactionRequestType.bankId,
        transactionRequestType.chargeCurrency,
        transactionRequestType.chargeAmount,
        transactionRequestType.chargeSummary
      )
      //If it is empty, return the default value : "0.0000000" and set the BankAccount currency
      case _ =>
        val fromAccountCurrency: String = Connector.connector.vend.getBankAccountLegacy(bankId, accountId, None).map(_._1).openOrThrowException(attemptedToOpenAnEmptyBox).currency
        TransactionRequestTypeChargeMock(transactionRequestType.value, bankId.value, fromAccountCurrency, "0.00", "Warning! Default value!")
    }

    Full(transactionRequestTypeCharge)
  }

  def getPhysicalCardsForBankLocal(bank: Bank, user: User, queryParams: List[OBPQueryParam]): Box[List[PhysicalCard]] = {
    val list = code.cards.PhysicalCard.physicalCardProvider.vend.getPhysicalCardsForBank(bank, user, queryParams)
    val cardList = for (l <- list) yield
      new PhysicalCard(
        cardId = l.cardId,
        bankId = l.bankId,
        bankCardNumber = l.bankCardNumber,
        cardType = l.cardType,
        nameOnCard = l.nameOnCard,
        issueNumber = l.issueNumber,
        serialNumber = l.serialNumber,
        validFrom = l.validFrom,
        expires = l.expires,
        enabled = l.enabled,
        cancelled = l.cancelled,
        onHotList = l.onHotList,
        technology = l.technology,
        networks = l.networks,
        allows = l.allows,
        account = l.account,
        replacement = l.replacement,
        pinResets = l.pinResets,
        collected = l.collected,
        posted = l.posted,
        customerId = l.customerId,
        cvv = l.cvv,
        brand = l.brand
      )
    Full(cardList)
  }

  def getCurrentFxRateCached(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String, callContext: Option[CallContext]): Box[FXRate] = {
    /**
     * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
     * is just a temporary value field with UUID values in order to prevent any ambiguity.
     * The real value will be assigned by Macro during compile time at this line of a code:
     * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
     */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(TTL seconds) {
        Connector.connector.vend.getCurrentFxRate(bankId, fromCurrencyCode, toCurrencyCode, callContext)
      }
    }
  }
  
  /**
   * Saves a transaction with @amount, @toAccount and @transactionRequestType for @fromAccount and @toCounterparty. <br>
   * Returns the id of the saved transactionId.<br>
   */
  def saveTransaction(
    fromAccount: BankAccount,
    toAccount: BankAccount,
    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
    amount: BigDecimal,
    description: String,
    transactionRequestType: TransactionRequestType,
    chargePolicy: String): Box[TransactionId] = {
    for {

      currency <- Full(fromAccount.currency)
      //update the balance of the fromAccount for which a transaction is being created
      newAccountBalance <- Full(Helper.convertToSmallestCurrencyUnits(fromAccount.balance, currency) + Helper.convertToSmallestCurrencyUnits(amount, currency))

      //Here is the `LocalMappedConnector`, once get this point, fromAccount must be a mappedBankAccount. So can use asInstanceOf.... 
      _ <- tryo(fromAccount.asInstanceOf[MappedBankAccount].accountBalance(newAccountBalance).save) ?~! UpdateBankAccountException

      mappedTransaction <- tryo(MappedTransaction.create
        //No matter which type (SANDBOX_TAN,SEPA,FREE_FORM,COUNTERPARTYE), always filled the following nine fields.
        .bank(fromAccount.bankId.value)
        .account(fromAccount.accountId.value)
        .transactionType(transactionRequestType.value)
        .amount(Helper.convertToSmallestCurrencyUnits(amount, currency))
        .newAccountBalance(newAccountBalance)
        .currency(currency)
        .tStartDate(now)
        .tFinishDate(now)
        .description(description)
        //Old data: other BankAccount(toAccount: BankAccount)simulate counterparty 
        .counterpartyAccountHolder(toAccount.accountHolder)
        .counterpartyAccountNumber(toAccount.number)
        .counterpartyAccountKind(toAccount.accountType)
        .counterpartyBankName(toAccount.bankName)
        .counterpartyIban(toAccount.accountRoutings.find(_.scheme == AccountRoutingScheme.IBAN.toString).map(_.address).getOrElse(""))
        .counterpartyNationalId(toAccount.nationalIdentifier)
        //New data: real counterparty (toCounterparty: CounterpartyTrait)
        //      .CPCounterPartyId(toAccount.accountId.value)
        .CPOtherAccountRoutingScheme(toAccount.accountRoutings.headOption.map(_.scheme).getOrElse(""))
        .CPOtherAccountRoutingAddress(toAccount.accountRoutings.headOption.map(_.address).getOrElse(""))
        .CPOtherBankRoutingScheme(toAccount.bankRoutingScheme)
        .CPOtherBankRoutingAddress(toAccount.bankRoutingAddress)
        .chargePolicy(chargePolicy)
        .saveMe) ?~! s"$CreateTransactionsException, exception happened when create new mappedTransaction"
    } yield {
      mappedTransaction.theTransactionId
    }
  }

  def getTransactionRequestsInternal(fromBankId: BankId, fromAccountId: AccountId, counterpartyId: CounterpartyId, queryParams: List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[Box[List[MappedTransactionRequest]]] = {

    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedTransactionRequest.updatedAt, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedTransactionRequest.updatedAt, date) }.headOption
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(MappedTransactionRequest.updatedAt, Ascending)
          case OBPDescending => OrderBy(MappedTransactionRequest.updatedAt, Descending)
        }
    }

    val optionalParams: Seq[QueryParam[MappedTransactionRequest]] = Seq(fromDate.toSeq, toDate.toSeq, ordering.toSeq).flatten
    val mapperParams = Seq(
      By(MappedTransactionRequest.mFrom_BankId, fromBankId.value), 
      By(MappedTransactionRequest.mFrom_AccountId, fromAccountId.value),
      By(MappedTransactionRequest.mCounterpartyId, counterpartyId.value),
      By(MappedTransactionRequest.mStatus, TransactionRequestStatus.COMPLETED.toString)
    ) ++ optionalParams

    Future {
      (Full(MappedTransactionRequest.findAll(mapperParams: _*)), callContext)
    }
  }
  
  def getTransactionRequestStatuses() : Box[TransactionRequestStatus] = Failure(NotImplemented + nameOf(getTransactionRequestStatuses _))

}
