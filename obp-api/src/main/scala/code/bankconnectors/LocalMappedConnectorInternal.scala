package code.bankconnectors

import code.api.ChargePolicy
import code.api.Constant._
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.model.TransactionStatus.mapTransactionStatus
import code.api.cache.Caching
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.util.newstyle.ViewNewStyle
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_1_0._
import code.api.v4_0_0._
import code.api.v6_0_0.{TransactionRequestBodyCardanoJsonV600, TransactionRequestBodyEthSendRawTransactionJsonV600, TransactionRequestBodyEthereumJsonV600}
import code.bankconnectors.ethereum.DecodeRawTx
import code.branches.MappedBranch
import code.fx.fx
import code.fx.fx.TTL
import code.management.ImporterAPI.ImporterTransaction
import code.model.dataAccess.{BankAccountRouting, MappedBank, MappedBankAccount}
import code.model.toBankAccountExtended
import code.transaction.MappedTransaction
import code.transactionrequests._
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE
import com.openbankproject.commons.model.enums.TransactionRequestTypes._
import com.openbankproject.commons.model.enums.{TransactionRequestStatus, _}
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.Serialization.write
import net.liftweb.json.{NoTypeHints, Serialization}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.{now, tryo}
import net.liftweb.util.StringHelpers

import java.time.{LocalDate, ZoneId}
import java.util.UUID.randomUUID
import java.util.{Calendar, Date}
import scala.collection.immutable.{List, Nil}
import scala.concurrent.Future
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
      view <- ViewNewStyle.checkAccountAccessAndGetView(viewId, fromBankIdAccountId, Full(user), callContext)
      _ <- Helper.booleanToFuture(InsufficientAuthorisationToCreateTransactionRequest, cc = callContext) {
        val allowed_actions = view.allowed_actions
        allowed_actions.exists(_ ==CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT)
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
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetChallengeThreshold - ${nameOf(Connector.connector.vend.getChallengeThreshold _)}", 400), i._2)
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
        .status(com.openbankproject.commons.model.enums.TransactionRequestStatus.COMPLETED.toString)
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




  // This text is used in the various Create Transaction Request resource docs
  val transactionRequestGeneralText =
    s"""
       |
       |For an introduction to Transaction Requests, see: ${Glossary.getGlossaryItemLink("Transaction-Request-Introduction")}
       |
       |""".stripMargin

  val lowAmount = AmountOfMoneyJsonV121("EUR", "12.50")

  val sharedChargePolicy = ChargePolicy.withName("SHARED")
  
  def createTransactionRequest(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType, json: JValue): Future[(TransactionRequestWithChargeJSON400, Option[CallContext])] = {
    for {
      (Full(u), callContext) <- SS.user

      transactionRequestTypeValue <- NewStyle.function.tryons(s"$InvalidTransactionRequestType: '${transactionRequestType.value}'. OBP does not support it.", 400, callContext) {
        TransactionRequestTypes.withName(transactionRequestType.value)
      }

      (fromAccount, callContext) <- transactionRequestTypeValue match {
        case CARD =>
          for{
            transactionRequestBodyCard <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $CARD json format", 400, callContext) {
              json.extract[TransactionRequestBodyCardJsonV400]
            }
            //   1.1 get Card from card_number
            (cardFromCbs,callContext) <- NewStyle.function.getPhysicalCardByCardNumber(transactionRequestBodyCard.card.card_number, callContext)

            // 1.2 check card name/expire month. year.
            calendar = Calendar.getInstance
            _ = calendar.setTime(cardFromCbs.expires)
            yearFromCbs = calendar.get(Calendar.YEAR).toString
            monthFromCbs = calendar.get(Calendar.MONTH).toString
            nameOnCardFromCbs= cardFromCbs.nameOnCard
            cvvFromCbs= cardFromCbs.cvv.getOrElse("")
            brandFromCbs= cardFromCbs.brand.getOrElse("")

            _ <- Helper.booleanToFuture(s"$InvalidJsonValue brand is not matched", cc=callContext) {
              transactionRequestBodyCard.card.brand.equalsIgnoreCase(brandFromCbs)
            }

            dateFromJsonBody <- NewStyle.function.tryons(s"$InvalidDateFormat year should be 'yyyy', " +
              s"eg: 2023, but current expiry_year(${transactionRequestBodyCard.card.expiry_year}), " +
              s"month should be 'xx', eg: 02, but current expiry_month(${transactionRequestBodyCard.card.expiry_month})", 400, callContext) {
              DateWithMonthFormat.parse(s"${transactionRequestBodyCard.card.expiry_year}-${transactionRequestBodyCard.card.expiry_month}")
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue your credit card is expired.", cc=callContext) {
              org.apache.commons.lang3.time.DateUtils.addMonths(new Date(), 1).before(dateFromJsonBody)
            }

            _ <- Helper.booleanToFuture(s"$InvalidJsonValue expiry_year is not matched", cc=callContext) {
              transactionRequestBodyCard.card.expiry_year.equalsIgnoreCase(yearFromCbs)
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue expiry_month is not matched", cc=callContext) {
              transactionRequestBodyCard.card.expiry_month.toInt.equals(monthFromCbs.toInt+1)
            }

            _ <- Helper.booleanToFuture(s"$InvalidJsonValue name_on_card is not matched", cc=callContext) {
              transactionRequestBodyCard.card.name_on_card.equalsIgnoreCase(nameOnCardFromCbs)
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue cvv is not matched", cc=callContext) {
              HashUtil.Sha256Hash(transactionRequestBodyCard.card.cvv).equals(cvvFromCbs)
            }

          } yield{
            (cardFromCbs.account, callContext)
          }
        case _ => NewStyle.function.getBankAccount(bankId,accountId, callContext)
      }
      _ <- NewStyle.function.isEnabledTransactionRequests(callContext)
      _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext) {
        isValidID(fromAccount.accountId.value)
      }
      _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext) {
        isValidID(fromAccount.bankId.value)
      }

      _ <- NewStyle.function.checkAuthorisationToCreateTransactionRequest(viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId), u, callContext)

      _ <- Helper.booleanToFuture(s"${InvalidTransactionRequestType}: '${transactionRequestType.value}'. Current Sandbox does not support it. ", cc=callContext) {
        APIUtil.getPropsValue("transactionRequests_supported_types", "").split(",").contains(transactionRequestType.value)
      }

      // Check the input JSON format, here is just check the common parts of all four types
      transDetailsJson <- transactionRequestTypeValue match {
        case ETH_SEND_RAW_TRANSACTION => for {
          // Parse raw transaction JSON
          transactionRequestBodyEthSendRawTransactionJsonV600 <- NewStyle.function.tryons(
            s"$InvalidJsonFormat It should be $TransactionRequestBodyEthSendRawTransactionJsonV600  json format",
            400,
            callContext
          ) {
            json.extract[TransactionRequestBodyEthSendRawTransactionJsonV600]
          }
          // Decode raw transaction to extract 'from' address
          decodedTx = DecodeRawTx.decodeRawTxToJson(transactionRequestBodyEthSendRawTransactionJsonV600.params)
          from = decodedTx.from
          _ <- Helper.booleanToFuture(
            s"$BankAccountNotFoundByAccountId Ethereum 'from' address must be the same as the accountId",
            cc = callContext
          ) {
            from.getOrElse("") == accountId.value
          }
          // Construct TransactionRequestBodyEthereumJsonV600 for downstream processing
          transactionRequestBodyEthereum = TransactionRequestBodyEthereumJsonV600(
            params = Some(transactionRequestBodyEthSendRawTransactionJsonV600.params),
            to = decodedTx.to.getOrElse(""),
            value = AmountOfMoneyJsonV121("ETH", decodedTx.value.getOrElse("0")),
            description = transactionRequestBodyEthSendRawTransactionJsonV600.description
          )
        } yield (transactionRequestBodyEthereum)
        case _ =>
          NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $TransactionRequestBodyCommonJSON ", 400, callContext) {
            json.extract[TransactionRequestBodyCommonJSON]
          }
      }

      transactionAmountNumber <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${transDetailsJson.value.amount} ", 400, callContext) {
        BigDecimal(transDetailsJson.value.amount)
      }

      _ <- Helper.booleanToFuture(s"${NotPositiveAmount} Current input is: '${transactionAmountNumber}'", cc=callContext) {
        transactionAmountNumber > BigDecimal("0")
      }

      _ <- (transactionRequestTypeValue match {
        case ETH_SEND_RAW_TRANSACTION | ETH_SEND_TRANSACTION => Future.successful(true) // Allow ETH (non-ISO) for Ethereum requests
        case _ => Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.value.currency}'", cc=callContext) {
          APIUtil.isValidCurrencyISOCode(transDetailsJson.value.currency)
        }
      })

      (createdTransactionRequest, callContext) <- transactionRequestTypeValue match {
        case REFUND => {
          for {
            transactionRequestBodyRefundJson <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $ACCOUNT json format", 400, callContext) {
              json.extract[TransactionRequestBodyRefundJsonV400]
            }

            transactionId = TransactionId(transactionRequestBodyRefundJson.refund.transaction_id)

            (fromAccount, toAccount, transaction, callContext) <- transactionRequestBodyRefundJson.to match {
              case Some(refundRequestTo) if refundRequestTo.account_id.isDefined && refundRequestTo.bank_id.isDefined =>
                val toBankId = BankId(refundRequestTo.bank_id.get)
                val toAccountId = AccountId(refundRequestTo.account_id.get)
                for {
                  (transaction, callContext) <- NewStyle.function.getTransaction(fromAccount.bankId, fromAccount.accountId, transactionId, callContext)
                  (toAccount, callContext) <- NewStyle.function.checkBankAccountExists(toBankId, toAccountId, callContext)
                } yield (fromAccount, toAccount, transaction, callContext)

              case Some(refundRequestTo) if refundRequestTo.counterparty_id.isDefined =>
                val toCounterpartyId = CounterpartyId(refundRequestTo.counterparty_id.get)
                for {
                  (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(toCounterpartyId, callContext)
                  (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, isOutgoingAccount = true, callContext)
                  _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
                    toCounterparty.isBeneficiary
                  }
                  (transaction, callContext) <- NewStyle.function.getTransaction(fromAccount.bankId, fromAccount.accountId, transactionId, callContext)
                } yield (fromAccount, toAccount, transaction, callContext)

              case None if transactionRequestBodyRefundJson.from.isDefined =>
                val fromCounterpartyId = CounterpartyId(transactionRequestBodyRefundJson.from.get.counterparty_id)
                val toAccount = fromAccount
                for {
                  (fromCounterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(fromCounterpartyId, callContext)
                  (fromAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(fromCounterparty, isOutgoingAccount = false, callContext)
                  _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
                    fromCounterparty.isBeneficiary
                  }
                  (transaction, callContext) <- NewStyle.function.getTransaction(toAccount.bankId, toAccount.accountId, transactionId, callContext)
                } yield (fromAccount, toAccount, transaction, callContext)
            }

            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transactionRequestBodyRefundJson)(Serialization.formats(NoTypeHints))
            }

            _ <- Helper.booleanToFuture(s"${RefundedTransaction} Current input amount is: '${transDetailsJson.value.amount}'. It can not be more than the original amount(${(transaction.amount).abs})", cc=callContext) {
              (transaction.amount).abs  >= transactionAmountNumber
            }
            //TODO, we need additional field to guarantee the transaction is refunded...
            //                  _ <- Helper.booleanToFuture(s"${RefundedTransaction}") {
            //                    !((transaction.description.toString contains(" Refund to ")) && (transaction.description.toString contains(" and transaction_id(")))
            //                  }

            //we add the extra info (counterparty name + transaction_id) for this special Refund endpoint.
            newDescription = s"${transactionRequestBodyRefundJson.description} - Refund for transaction_id: (${transactionId.value}) to ${transaction.otherAccount.counterpartyName}"

            //This is the refund endpoint, the original fromAccount is the `toAccount` which will receive money.
            refundToAccount = fromAccount
            //This is the refund endpoint, the original toAccount is the `fromAccount` which will lose money.
            refundFromAccount = toAccount

            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              refundFromAccount,
              refundToAccount,
              transactionRequestType,
              transactionRequestBodyRefundJson.copy(description = newDescription),
              transDetailsSerialized,
              sharedChargePolicy.toString,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              None,
              callContext) //in ACCOUNT, ChargePolicy set default "SHARED"

            _ <- NewStyle.function.createOrUpdateTransactionRequestAttribute(
              bankId = bankId,
              transactionRequestId = createdTransactionRequest.id,
              transactionRequestAttributeId = None,
              name = "original_transaction_id",
              attributeType = TransactionRequestAttributeType.withName("STRING"),
              value = transactionId.value,
              callContext = callContext
            )

            refundReasonCode = transactionRequestBodyRefundJson.refund.reason_code
            _ <- if (refundReasonCode.nonEmpty) {
              NewStyle.function.createOrUpdateTransactionRequestAttribute(
                bankId = bankId,
                transactionRequestId = createdTransactionRequest.id,
                transactionRequestAttributeId = None,
                name = "refund_reason_code",
                attributeType = TransactionRequestAttributeType.withName("STRING"),
                value = refundReasonCode,
                callContext = callContext)
            } else Future.successful()

            (newTransactionRequestStatus, callContext) <- NewStyle.function.notifyTransactionRequest(refundFromAccount, refundToAccount, createdTransactionRequest, callContext)
            _ <- NewStyle.function.saveTransactionRequestStatusImpl(createdTransactionRequest.id, newTransactionRequestStatus.toString, callContext)
            createdTransactionRequest <- Future(createdTransactionRequest.copy(status = newTransactionRequestStatus.toString))

          } yield (createdTransactionRequest, callContext)
        }
        case ACCOUNT | SANDBOX_TAN => {
          for {
            transactionRequestBodySandboxTan <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $ACCOUNT json format", 400, callContext) {
              json.extract[TransactionRequestBodySandBoxTanJSON]
            }

            toBankId = BankId(transactionRequestBodySandboxTan.to.bank_id)
            toAccountId = AccountId(transactionRequestBodySandboxTan.to.account_id)
            (toAccount, callContext) <- NewStyle.function.checkBankAccountExists(toBankId, toAccountId, callContext)

            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transactionRequestBodySandboxTan)(Serialization.formats(NoTypeHints))
            }

            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              fromAccount,
              toAccount,
              transactionRequestType,
              transactionRequestBodySandboxTan,
              transDetailsSerialized,
              sharedChargePolicy.toString,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              None,
              callContext) //in ACCOUNT, ChargePolicy set default "SHARED"
          } yield (createdTransactionRequest, callContext)
        }
        case ACCOUNT_OTP => {
          for {
            transactionRequestBodySandboxTan <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $ACCOUNT json format", 400, callContext) {
              json.extract[TransactionRequestBodySandBoxTanJSON]
            }

            toBankId = BankId(transactionRequestBodySandboxTan.to.bank_id)
            toAccountId = AccountId(transactionRequestBodySandboxTan.to.account_id)
            (toAccount, callContext) <- NewStyle.function.checkBankAccountExists(toBankId, toAccountId, callContext)

            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transactionRequestBodySandboxTan)(Serialization.formats(NoTypeHints))
            }

            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              fromAccount,
              toAccount,
              transactionRequestType,
              transactionRequestBodySandboxTan,
              transDetailsSerialized,
              sharedChargePolicy.toString,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              None,
              callContext) //in ACCOUNT, ChargePolicy set default "SHARED"
          } yield (createdTransactionRequest, callContext)
        }
        case COUNTERPARTY => {
          for {
            _ <- Future { logger.debug(s"Before extracting counterparty id") }
            //For COUNTERPARTY, Use the counterpartyId to find the toCounterparty and set up the toAccount
            transactionRequestBodyCounterparty <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $COUNTERPARTY json format", 400, callContext) {
              json.extract[TransactionRequestBodyCounterpartyJSON]
            }
            toCounterpartyId = transactionRequestBodyCounterparty.to.counterparty_id
            _ <- Future { logger.debug(s"After extracting counterparty id: $toCounterpartyId") }
            (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(CounterpartyId(toCounterpartyId), callContext)

            transactionRequestAttributes <- if(transactionRequestBodyCounterparty.attributes.isDefined && transactionRequestBodyCounterparty.attributes.head.length > 0 ) {

              val attributes = transactionRequestBodyCounterparty.attributes.head

              val failMsg = s"$InvalidJsonFormat The attribute `type` field can only accept the following field: " +
                s"${TransactionRequestAttributeType.DOUBLE}(12.1234)," +
                s" ${TransactionRequestAttributeType.STRING}(TAX_NUMBER), " +
                s"${TransactionRequestAttributeType.INTEGER}(123) and " +
                s"${TransactionRequestAttributeType.DATE_WITH_DAY}(2012-04-23)"

              for{
                _ <- NewStyle.function.tryons(failMsg, 400, callContext) {
                  attributes.map(attribute => TransactionRequestAttributeType.withName(attribute.attribute_type))
                }
              }yield{
                attributes
              }

            } else {
              Future.successful(List.empty[TransactionRequestAttributeJsonV400])
            }

            (counterpartyLimitBox, callContext) <- Connector.connector.vend.getCounterpartyLimit(
              bankId.value,
              accountId.value,
              viewId.value,
              toCounterpartyId,
              callContext
            )
            _<- if(counterpartyLimitBox.isDefined){
              for{
                counterpartyLimit <- Future.successful(counterpartyLimitBox.head)
                maxSingleAmount = counterpartyLimit.maxSingleAmount
                maxMonthlyAmount = counterpartyLimit.maxMonthlyAmount
                maxNumberOfMonthlyTransactions = counterpartyLimit.maxNumberOfMonthlyTransactions
                maxYearlyAmount = counterpartyLimit.maxYearlyAmount
                maxNumberOfYearlyTransactions = counterpartyLimit.maxNumberOfYearlyTransactions
                maxTotalAmount = counterpartyLimit.maxTotalAmount
                maxNumberOfTransactions = counterpartyLimit.maxNumberOfTransactions

                // Get the first day of the current month
                firstDayOfMonth: LocalDate = LocalDate.now().withDayOfMonth(1)

                // Get the last day of the current month
                lastDayOfMonth: LocalDate = LocalDate.now().withDayOfMonth(
                  LocalDate.now().lengthOfMonth()
                )
                // Get the first day of the current year
                firstDayOfYear: LocalDate = LocalDate.now().withDayOfYear(1)

                // Get the last day of the current year
                lastDayOfYear: LocalDate = LocalDate.now().withDayOfYear(
                  LocalDate.now().lengthOfYear()
                )

                // Convert LocalDate to Date
                zoneId: ZoneId = ZoneId.systemDefault()
                firstCurrentMonthDate: Date = Date.from(firstDayOfMonth.atStartOfDay(zoneId).toInstant)
                // Adjust to include 23:59:59.999
                lastCurrentMonthDate: Date = Date.from(
                  lastDayOfMonth
                    .atTime(23, 59, 59, 999000000)
                    .atZone(zoneId)
                    .toInstant
                )

                firstCurrentYearDate: Date = Date.from(firstDayOfYear.atStartOfDay(zoneId).toInstant)
                // Adjust to include 23:59:59.999
                lastCurrentYearDate: Date = Date.from(
                  lastDayOfYear
                    .atTime(23, 59, 59, 999000000)
                    .atZone(zoneId)
                    .toInstant
                )

                defaultFromDate: Date = theEpochTime
                defaultToDate: Date = APIUtil.ToDateInFuture

                (sumOfTransactionsFromAccountToCounterpartyMonthly, callContext) <- NewStyle.function.getSumOfTransactionsFromAccountToCounterparty(
                  fromAccount.bankId: BankId,
                  fromAccount.accountId: AccountId,
                  CounterpartyId(toCounterpartyId): CounterpartyId,
                  firstCurrentMonthDate: Date,
                  lastCurrentMonthDate: Date,
                  callContext: Option[CallContext]
                )

                (countOfTransactionsFromAccountToCounterpartyMonthly, callContext) <- NewStyle.function.getCountOfTransactionsFromAccountToCounterparty(
                  fromAccount.bankId: BankId,
                  fromAccount.accountId: AccountId,
                  CounterpartyId(toCounterpartyId): CounterpartyId,
                  firstCurrentMonthDate: Date,
                  lastCurrentMonthDate: Date,
                  callContext: Option[CallContext]
                )

                (sumOfTransactionsFromAccountToCounterpartyYearly, callContext) <- NewStyle.function.getSumOfTransactionsFromAccountToCounterparty(
                  fromAccount.bankId: BankId,
                  fromAccount.accountId: AccountId,
                  CounterpartyId(toCounterpartyId): CounterpartyId,
                  firstCurrentYearDate: Date,
                  lastCurrentYearDate: Date,
                  callContext: Option[CallContext]
                )

                (countOfTransactionsFromAccountToCounterpartyYearly, callContext) <- NewStyle.function.getCountOfTransactionsFromAccountToCounterparty(
                  fromAccount.bankId: BankId,
                  fromAccount.accountId: AccountId,
                  CounterpartyId(toCounterpartyId): CounterpartyId,
                  firstCurrentYearDate: Date,
                  lastCurrentYearDate: Date,
                  callContext: Option[CallContext]
                )

                (sumOfAllTransactionsFromAccountToCounterparty, callContext) <- NewStyle.function.getSumOfTransactionsFromAccountToCounterparty(
                  fromAccount.bankId: BankId,
                  fromAccount.accountId: AccountId,
                  CounterpartyId(toCounterpartyId): CounterpartyId,
                  defaultFromDate: Date,
                  defaultToDate: Date,
                  callContext: Option[CallContext]
                )

                (countOfAllTransactionsFromAccountToCounterparty, callContext) <- NewStyle.function.getCountOfTransactionsFromAccountToCounterparty(
                  fromAccount.bankId: BankId,
                  fromAccount.accountId: AccountId,
                  CounterpartyId(toCounterpartyId): CounterpartyId,
                  defaultFromDate: Date,
                  defaultToDate: Date,
                  callContext: Option[CallContext]
                )


                currentTransactionAmountWithFxApplied <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $COUNTERPARTY json format", 400, callContext) {
                  val fromAccountCurrency = fromAccount.currency //eg: if from account currency is EUR
                  val transferCurrency = transactionRequestBodyCounterparty.value.currency //eg: if the payment json body currency is GBP.
                  val transferAmount = BigDecimal(transactionRequestBodyCounterparty.value.amount) //eg: if the payment json body amount is 1.
                  val debitRate = fx.exchangeRate(transferCurrency, fromAccountCurrency, Some(fromAccount.bankId.value), callContext) //eg: the rate here is 1.16278.
                  fx.convert(transferAmount, debitRate) // 1.16278 Euro
                }

                _ <- Helper.booleanToFuture(s"$CounterpartyLimitValidationError max_single_amount is $maxSingleAmount ${fromAccount.currency}, " +
                  s"but current transaction body amount is ${transactionRequestBodyCounterparty.value.amount} ${transactionRequestBodyCounterparty.value.currency}, " +
                  s"which is $currentTransactionAmountWithFxApplied ${fromAccount.currency}. ", cc = callContext) {
                  maxSingleAmount >= currentTransactionAmountWithFxApplied
                }
                _ <- Helper.booleanToFuture(s"$CounterpartyLimitValidationError max_monthly_amount is $maxMonthlyAmount, but current monthly amount is ${BigDecimal(sumOfTransactionsFromAccountToCounterpartyMonthly.amount)+currentTransactionAmountWithFxApplied}", cc = callContext) {
                  maxMonthlyAmount >= BigDecimal(sumOfTransactionsFromAccountToCounterpartyMonthly.amount)+currentTransactionAmountWithFxApplied
                }
                _ <- Helper.booleanToFuture(s"$CounterpartyLimitValidationError max_number_of_monthly_transactions is $maxNumberOfMonthlyTransactions, but current count of monthly transactions is  ${countOfTransactionsFromAccountToCounterpartyMonthly+1}", cc = callContext) {
                  maxNumberOfMonthlyTransactions >= countOfTransactionsFromAccountToCounterpartyMonthly+1
                }
                _ <- Helper.booleanToFuture(s"$CounterpartyLimitValidationError max_yearly_amount is $maxYearlyAmount, but current yearly amount is ${BigDecimal(sumOfTransactionsFromAccountToCounterpartyYearly.amount)+currentTransactionAmountWithFxApplied}", cc = callContext) {
                  maxYearlyAmount >= BigDecimal(sumOfTransactionsFromAccountToCounterpartyYearly.amount)+currentTransactionAmountWithFxApplied
                }
                result <- Helper.booleanToFuture(s"$CounterpartyLimitValidationError max_number_of_yearly_transactions is $maxNumberOfYearlyTransactions, but current count of yearly transaction is  ${countOfTransactionsFromAccountToCounterpartyYearly+1}", cc = callContext) {
                  maxNumberOfYearlyTransactions >= countOfTransactionsFromAccountToCounterpartyYearly+1
                }
                _ <- Helper.booleanToFuture(s"$CounterpartyLimitValidationError max_total_amount is $maxTotalAmount, but current amount is ${BigDecimal(sumOfAllTransactionsFromAccountToCounterparty.amount)+currentTransactionAmountWithFxApplied}", cc = callContext) {
                  maxTotalAmount >= BigDecimal(sumOfAllTransactionsFromAccountToCounterparty.amount)+currentTransactionAmountWithFxApplied
                }
                result <- Helper.booleanToFuture(s"$CounterpartyLimitValidationError max_number_of_transactions is $maxNumberOfTransactions, but current count of all transactions is  ${countOfAllTransactionsFromAccountToCounterparty+1}", cc = callContext) {
                  maxNumberOfTransactions >= countOfAllTransactionsFromAccountToCounterparty+1
                }
              }yield{
                result
              }
            }
            else {
              Future.successful(true)
            }

            (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
            // Check we can send money to it.
            _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
              toCounterparty.isBeneficiary
            }
            chargePolicy = transactionRequestBodyCounterparty.charge_policy
            _ <- Helper.booleanToFuture(s"$InvalidChargePolicy", cc=callContext) {
              ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))
            }
            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transactionRequestBodyCounterparty)(Serialization.formats(NoTypeHints))
            }
            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              fromAccount,
              toAccount,
              transactionRequestType,
              transactionRequestBodyCounterparty,
              transDetailsSerialized,
              chargePolicy,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              None,
              callContext)

            _ <- NewStyle.function.createTransactionRequestAttributes(
              bankId: BankId,
              createdTransactionRequest.id,
              transactionRequestAttributes,
              true,
              callContext: Option[CallContext]
            )
          } yield (createdTransactionRequest, callContext)
        }
        case AGENT_CASH_WITHDRAWAL => {
          for {
            //For Agent, Use the agentId to find the agent and set up the toAccount
            transactionRequestBodyAgent <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $AGENT_CASH_WITHDRAWAL json format", 400, callContext) {
              json.extract[TransactionRequestBodyAgentJsonV400]
            }
            (agent, callContext) <- NewStyle.function.getAgentByAgentNumber(BankId(transactionRequestBodyAgent.to.bank_id),transactionRequestBodyAgent.to.agent_number, callContext)
            (agentAccountLinks, callContext) <-  NewStyle.function.getAgentAccountLinksByAgentId(agent.agentId, callContext)
            agentAccountLink <- NewStyle.function.tryons(AgentAccountLinkNotFound, 400, callContext) {
              agentAccountLinks.head
            }
            // Check we can send money to it.
            _ <- Helper.booleanToFuture(s"$AgentBeneficiaryPermit", cc=callContext) {
              !agent.isPendingAgent && agent.isConfirmedAgent
            }
            (toAccount, callContext) <- NewStyle.function.getBankAccount(BankId(agentAccountLink.bankId), AccountId(agentAccountLink.accountId), callContext)
            chargePolicy = transactionRequestBodyAgent.charge_policy
            _ <- Helper.booleanToFuture(s"$InvalidChargePolicy", cc=callContext) {
              ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))
            }
            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transactionRequestBodyAgent)(Serialization.formats(NoTypeHints))
            }
            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              fromAccount,
              toAccount,
              transactionRequestType,
              transactionRequestBodyAgent,
              transDetailsSerialized,
              chargePolicy,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              None,
              callContext)
          } yield (createdTransactionRequest, callContext)
        }
        case CARD => {
          for {
            //2rd: get toAccount from counterpartyId
            transactionRequestBodyCard <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $CARD json format", 400, callContext) {
              json.extract[TransactionRequestBodyCardJsonV400]
            }
            toCounterpartyId = transactionRequestBodyCard.to.counterparty_id
            (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(CounterpartyId(toCounterpartyId), callContext)
            (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
            // Check we can send money to it.
            _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
              toCounterparty.isBeneficiary
            }
            chargePolicy = ChargePolicy.RECEIVER.toString
            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transactionRequestBodyCard)(Serialization.formats(NoTypeHints))
            }
            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              fromAccount,
              toAccount,
              transactionRequestType,
              transactionRequestBodyCard,
              transDetailsSerialized,
              chargePolicy,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              None,
              callContext)
          } yield (createdTransactionRequest, callContext)

        }
        case SIMPLE => {
          for {
            //For SAMPLE, we will create/get toCounterparty on site and set up the toAccount
            transactionRequestBodySimple <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $SIMPLE json format", 400, callContext) {
              json.extract[TransactionRequestBodySimpleJsonV400]
            }
            (toCounterparty, callContext) <- NewStyle.function.getOrCreateCounterparty(
              name = transactionRequestBodySimple.to.name,
              description = transactionRequestBodySimple.to.description,
              currency = transactionRequestBodySimple.value.currency,
              createdByUserId = u.userId,
              thisBankId = bankId.value,
              thisAccountId = accountId.value,
              thisViewId = viewId.value,
              otherBankRoutingScheme = StringHelpers.snakify(transactionRequestBodySimple.to.other_bank_routing_scheme).toUpperCase,
              otherBankRoutingAddress = transactionRequestBodySimple.to.other_bank_routing_address,
              otherBranchRoutingScheme = StringHelpers.snakify(transactionRequestBodySimple.to.other_branch_routing_scheme).toUpperCase,
              otherBranchRoutingAddress = transactionRequestBodySimple.to.other_branch_routing_address,
              otherAccountRoutingScheme = StringHelpers.snakify(transactionRequestBodySimple.to.other_account_routing_scheme).toUpperCase,
              otherAccountRoutingAddress = transactionRequestBodySimple.to.other_account_routing_address,
              otherAccountSecondaryRoutingScheme = StringHelpers.snakify(transactionRequestBodySimple.to.other_account_secondary_routing_scheme).toUpperCase,
              otherAccountSecondaryRoutingAddress = transactionRequestBodySimple.to.other_account_secondary_routing_address,
              callContext: Option[CallContext],
            )
            (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
            // Check we can send money to it.
            _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
              toCounterparty.isBeneficiary
            }
            chargePolicy = transactionRequestBodySimple.charge_policy
            _ <- Helper.booleanToFuture(s"$InvalidChargePolicy", cc=callContext) {
              ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))
            }
            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transactionRequestBodySimple)(Serialization.formats(NoTypeHints))
            }
            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              fromAccount,
              toAccount,
              transactionRequestType,
              transactionRequestBodySimple,
              transDetailsSerialized,
              chargePolicy,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              None,
              callContext)
          } yield (createdTransactionRequest, callContext)

        }
        case SEPA => {
          for {
            //For SEPA, Use the IBAN to find the toCounterparty and set up the toAccount
            transDetailsSEPAJson <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $SEPA json format", 400, callContext) {
              json.extract[TransactionRequestBodySEPAJsonV400]
            }
            toIban = transDetailsSEPAJson.to.iban
            (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByIbanAndBankAccountId(toIban, fromAccount.bankId, fromAccount.accountId, callContext)
            (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
            _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
              toCounterparty.isBeneficiary
            }
            chargePolicy = transDetailsSEPAJson.charge_policy
            _ <- Helper.booleanToFuture(s"$InvalidChargePolicy", cc=callContext) {
              ChargePolicy.values.contains(ChargePolicy.withName(chargePolicy))
            }
            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transDetailsSEPAJson)(Serialization.formats(NoTypeHints))
            }
            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              fromAccount,
              toAccount,
              transactionRequestType,
              transDetailsSEPAJson,
              transDetailsSerialized,
              chargePolicy,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              transDetailsSEPAJson.reasons.map(_.map(_.transform)),
              callContext)
          } yield (createdTransactionRequest, callContext)
        }
        case FREE_FORM => {
          for {
            transactionRequestBodyFreeForm <- NewStyle.function.tryons(s"${InvalidJsonFormat}, it should be $FREE_FORM json format", 400, callContext) {
              json.extract[TransactionRequestBodyFreeFormJSON]
            }
            // Following lines: just transfer the details body, add Bank_Id and Account_Id in the Detail part. This is for persistence and 'answerTransactionRequestChallenge'
            transactionRequestAccountJSON = TransactionRequestAccountJsonV140(bankId.value, accountId.value)
            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transactionRequestBodyFreeForm)(Serialization.formats(NoTypeHints))
            }
            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              fromAccount,
              fromAccount,
              transactionRequestType,
              transactionRequestBodyFreeForm,
              transDetailsSerialized,
              sharedChargePolicy.toString,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              None,
              callContext)
          } yield
            (createdTransactionRequest, callContext)
        }
        case CARDANO => {
          for {
            //For CARDANO, we will create/get toCounterparty on site and set up the toAccount, fromAccount we need to prepare before .
            transactionRequestBodyCardano <- NewStyle.function.tryons(s"${InvalidJsonFormat} It should be $TransactionRequestBodyCardanoJsonV600 json format", 400, callContext) {
              json.extract[TransactionRequestBodyCardanoJsonV600]
            }
            
            // Validate Cardano specific fields
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue Cardano payment address is required", cc=callContext) {
              transactionRequestBodyCardano.to.address.nonEmpty
            }
            
            // Validate Cardano address format (basic validation)
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue Cardano address format is invalid", cc=callContext) {
              transactionRequestBodyCardano.to.address.startsWith("addr_") || 
              transactionRequestBodyCardano.to.address.startsWith("addr_test") ||
              transactionRequestBodyCardano.to.address.startsWith("addr_main")
            }
            
           
            
            // Validate amount quantity is non-negative
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue Cardano amount quantity must be non-negative", cc=callContext) {
              transactionRequestBodyCardano.to.amount.quantity >= 0
            }
            
            // Validate amount unit must be 'lovelace' (case insensitive)
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue Cardano amount unit must be 'lovelace'", cc=callContext) {
              transactionRequestBodyCardano.to.amount.unit.toLowerCase == "lovelace"
            }
            
            // Validate assets if provided
            _ <- transactionRequestBodyCardano.to.assets match {
              case Some(assets) => Helper.booleanToFuture(s"$InvalidJsonValue Cardano assets must have valid policy_id and asset_name", cc=callContext) {
                assets.forall(asset => asset.policy_id.nonEmpty && asset.asset_name.nonEmpty && asset.quantity > 0)
              }
              case None => Future.successful(true)
            }
            
            // Validate that if amount is 0, there must be assets (token-only transfer)
            _ <- (transactionRequestBodyCardano.to.amount, transactionRequestBodyCardano.to.assets) match {
              case (amount, Some(assets)) if amount.quantity == 0 => Helper.booleanToFuture(s"$InvalidJsonValue Cardano token-only transfer must have assets", cc=callContext) {
                assets.nonEmpty
              }
              case (amount, None) if amount.quantity == 0 => Helper.booleanToFuture(s"$InvalidJsonValue Cardano transfer with zero amount must include assets", cc=callContext) {
                false
              }
              case _ => Future.successful(true)
            }
            
            // Validate metadata if provided
            _ <- transactionRequestBodyCardano.metadata match {
              case Some(metadata) => Helper.booleanToFuture(s"$InvalidJsonValue Cardano metadata must have valid structure", cc=callContext) {
                metadata.forall { case (label, metadataObj) =>
                  label.nonEmpty && metadataObj.string.nonEmpty
                }
              }
              case None => Future.successful(true)
            }
            
            (toCounterparty, callContext) <- NewStyle.function.getOrCreateCounterparty(
              name = "cardano-"+transactionRequestBodyCardano.to.address.take(27),
              description = transactionRequestBodyCardano.description,
              currency = transactionRequestBodyCardano.value.currency,
              createdByUserId = u.userId,
              thisBankId = bankId.value,
              thisAccountId = accountId.value,
              thisViewId = viewId.value,
              otherBankRoutingScheme = CARDANO.toString,
              otherBankRoutingAddress = transactionRequestBodyCardano.to.address,
              otherBranchRoutingScheme = CARDANO.toString,
              otherBranchRoutingAddress = transactionRequestBodyCardano.to.address,
              otherAccountRoutingScheme = CARDANO.toString,
              otherAccountRoutingAddress = transactionRequestBodyCardano.to.address,
              otherAccountSecondaryRoutingScheme = "cardano",
              otherAccountSecondaryRoutingAddress = transactionRequestBodyCardano.to.address,
              callContext = callContext
            )
            (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
            // Check we can send money to it.
            _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) {
              toCounterparty.isBeneficiary
            }
            chargePolicy = sharedChargePolicy.toString
            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transactionRequestBodyCardano)(Serialization.formats(NoTypeHints))
            }
            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              fromAccount,
              toAccount,
              transactionRequestType,
              transactionRequestBodyCardano,
              transDetailsSerialized,
              chargePolicy,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              None,
              callContext)
          } yield (createdTransactionRequest, callContext)
        }
        case ETH_SEND_RAW_TRANSACTION | ETH_SEND_TRANSACTION => {
          for {
            // Handle ETH_SEND_RAW_TRANSACTION and ETH_SEND_TRANSACTION types with proper extraction and validation
            (transactionRequestBodyEthereum, scheme) <-
              if (transactionRequestTypeValue == ETH_SEND_RAW_TRANSACTION) {
                Future.successful{(transDetailsJson.asInstanceOf[TransactionRequestBodyEthereumJsonV600], ETH_SEND_RAW_TRANSACTION.toString)}
              } else {
                for {
                  transactionRequestBodyEthereum <- NewStyle.function.tryons(
                    s"$InvalidJsonFormat It should be $TransactionRequestBodyEthereumJsonV600 json format",
                    400,
                    callContext
                  ) {
                    json.extract[TransactionRequestBodyEthereumJsonV600]
                  }
                } yield (transactionRequestBodyEthereum, ETH_SEND_TRANSACTION.toString)
              } 
            
            // Basic validations
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue Ethereum 'to' address is required", cc=callContext) {
              Option(transactionRequestBodyEthereum.to).exists(_.nonEmpty)
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue Ethereum 'to' address must start with 0x and be 42 chars", cc=callContext) {
              val toBody = transactionRequestBodyEthereum.to
              toBody.startsWith("0x") && toBody.length == 42
            }
            _ <- Helper.booleanToFuture(s"$InvalidTransactionRequestCurrency Currency must be 'ETH'", cc=callContext) {
              transactionRequestBodyEthereum.value.currency.equalsIgnoreCase("ETH")
            }

            // Create or get counterparty using the Ethereum address as secondary routing
            (toCounterparty, callContext) <- NewStyle.function.getOrCreateCounterparty(
              name = "ethereum-" + transactionRequestBodyEthereum.to.take(27),
              description = transactionRequestBodyEthereum.description,
              currency = transactionRequestBodyEthereum.value.currency,
              createdByUserId = u.userId,
              thisBankId = bankId.value,
              thisAccountId = accountId.value,
              thisViewId = viewId.value,
              otherBankRoutingScheme = scheme,
              otherBankRoutingAddress = transactionRequestBodyEthereum.to,
              otherBranchRoutingScheme = scheme,
              otherBranchRoutingAddress = transactionRequestBodyEthereum.to,
              otherAccountRoutingScheme = scheme,
              otherAccountRoutingAddress = transactionRequestBodyEthereum.to,
              otherAccountSecondaryRoutingScheme = scheme,
              otherAccountSecondaryRoutingAddress = transactionRequestBodyEthereum.to,
              callContext = callContext
            )

            (toAccount, callContext) <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
            _ <- Helper.booleanToFuture(s"$CounterpartyBeneficiaryPermit", cc=callContext) { toCounterparty.isBeneficiary }

            chargePolicy = sharedChargePolicy.toString
            transDetailsSerialized <- NewStyle.function.tryons(UnknownError, 400, callContext) {
              write(transactionRequestBodyEthereum)(Serialization.formats(NoTypeHints))
            }
            (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv400(u,
              viewId,
              fromAccount,
              toAccount,
              transactionRequestType,
              transactionRequestBodyEthereum,
              transDetailsSerialized,
              chargePolicy,
              Some(OBP_TRANSACTION_REQUEST_CHALLENGE),
              getScaMethodAtInstance(transactionRequestType.value).toOption,
              None,
              callContext)
          } yield (createdTransactionRequest, callContext)
        }
      }
      (challenges, callContext) <-  NewStyle.function.getChallengesByTransactionRequestId(createdTransactionRequest.id.value, callContext)
      (transactionRequestAttributes, callContext) <- NewStyle.function.getTransactionRequestAttributes(
        bankId,
        createdTransactionRequest.id,
        callContext
      )
    } yield {
      (JSONFactory400.createTransactionRequestWithChargeJSON(createdTransactionRequest, challenges, transactionRequestAttributes), HttpCode.`201`(callContext))
    }
  }


}
