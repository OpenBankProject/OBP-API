package code.api.berlin.group.v1_3

import code.api.Constant.bgRemoveSignOfAmounts
import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.model.TransactionStatus.mapTransactionStatus
import code.api.berlin.group.v1_3.model._
import code.api.util.APIUtil._
import code.api.util.ErrorMessages.MissingPropsValueAtThisInstance
import code.api.util.{APIUtil, ConsentJWT, CustomJsonFormats, JwtUtil}
import code.consent.ConsentTrait
import code.model.ModeratedTransaction
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.{AccountRoutingScheme, TransactionRequestStatus}
import net.liftweb.common.Box.tryo
import net.liftweb.common.{Box, Full}
import net.liftweb.json.{JValue, parse}

import java.text.SimpleDateFormat
import java.util.Date
import scala.concurrent.Future
case class JvalueCaseClass(jvalueToCaseclass: JValue)

object JSONFactory_BERLIN_GROUP_1_3 extends CustomJsonFormats with MdcLoggable{

  case class ErrorMessageBG(category: String, code: String, path: Option[String], text: String)
  case class ErrorMessagesBG(tppMessages: List[ErrorMessageBG])

  case class PostSigningBasketJsonV13(
    paymentIds: Option[List[String]],
    consentIds: Option[List[String]]
  )

  case class SigningBasketLinksV13(
                                   self: LinkHrefJson,
                                   status: LinkHrefJson,
                                   startAuthorisation: LinkHrefJson
                                 )
  case class SigningBasketResponseJson(
                                        transactionStatus: String,
                                        basketId: String,
                                        _links: SigningBasketLinksV13)
  case class SigningBasketGetResponseJson(
                                        transactionStatus: String,
                                        payments: Option[List[String]],
                                        consents: Option[List[String]]
                                         )
  case class LinkHrefJson(
    href: String
  )
  
  case class CoreAccountLinksJsonV13(
    balances: Option[LinkHrefJson] = None,
    transactions: Option[LinkHrefJson] = None // These links are only supported, when the corresponding consent has been already granted.
  )
  
  case class CoreAccountBalanceJson(
    balanceAmount:AmountOfMoneyV13,// = AmountOfMoneyV13("EUR","123"),
    balanceType: String, //= "openingBooked",
    lastChangeDateTime: Option[String] //= "2019-01-28T06:26:52.185Z",
//    referenceDate: String = "2020-07-02",
//    lastCommittedTransaction: String = "string",
  )
  case class CoreAccountJsonV13(
                                 resourceId: String,
                                 iban: String,
                                 bban: Option[String],
                                 currency: String,
                                 name: Option[String],
                                 product: String,
                                 cashAccountType: String,
//                                 status: String="enabled",
//                                 linkedAccounts: String ="string",
//                                 usage: String ="PRIV",
//                                 details: String ="",
                                 balances: Option[List[CoreAccountBalanceJson]] = None,
                                 _links: CoreAccountLinksJsonV13,
  )

  case class CoreAccountsJsonV13(accounts: List[CoreAccountJsonV13])
  case class CoreCardAccountsJsonV13(cardAccounts: List[CoreAccountJsonV13])

  case class AccountDetailsLinksJsonV13(
                                         balances: Option[LinkHrefJson],
                                         transactions: Option[LinkHrefJson]
                                       )

  case class AccountJsonV13(
                             resourceId: String,
                             iban: String,
                             currency: String,
                             product: String,
                             cashAccountType: String,
                             name: Option[String],
                             balances: Option[List[CoreAccountBalanceJson]] = None,
                             _links: AccountDetailsLinksJsonV13,
                           )

  case class AccountDetailsJsonV13(account: AccountJsonV13)
  
  case class CardAccountDetailsJsonV13(cardAccount: AccountJsonV13)
  
  case class AmountOfMoneyV13(
    currency : String,
    amount : String
  )
  case class AccountBalance(
                             balanceAmount : AmountOfMoneyV13 = AmountOfMoneyV13("EUR","123"),
                             balanceType: String = "openingBooked",
                             lastChangeDateTime: Option[String] = None,
                             lastCommittedTransaction: Option[String] = None,
                             referenceDate: Option[String] = None,
    
  )
  case class FromAccount(
    iban : String 
  )
  case class CardBalanceAccount(
    maskedPan: String,
  )
  case class AccountBalancesV13(
                                 account:FromAccount,
                                 `balances`: List[AccountBalance]
  )
  case class CardAccountBalancesV13(
                                 cardAccount:FromAccount,
                                 `balances`: List[AccountBalance]
                               )
  case class TransactionsLinksV13(
    account: String
  )
  case class CardTransactionsLinksV13(
    cardAccount: LinkHrefJson
  )
  case class TransactionsV13TransactionsLinks(
    account: LinkHrefJson ,
   
  )
  case class BgTransactionAccountJson(
    iban: Option[String],
    currency : Option[String] = None,
  )
  case class FromAccountJson(
    iban: String,
    currency : Option[String] = None,
  )
  case class TransactionJsonV13(
    transactionId: String,
    creditorName: Option[String],
    creditorAccount: Option[BgTransactionAccountJson],
    debtorName: Option[String],
    debtorAccount: Option[BgTransactionAccountJson],
    transactionAmount: AmountOfMoneyV13,
    bookingDate: Option[String],
    valueDate: Option[String],
    remittanceInformationUnstructured: Option[String]
  )
  case class SingleTransactionJsonV13(
    description: String,
    value: SingleTransactionValueJsonV13
  )
  case class SingleTransactionValueJsonV13(
    transactionsDetails: transactionsDetailsJsonV13
  )
  case class transactionsDetailsJsonV13(
    transactionId: String,
    creditorName: String,
    creditorAccount: BgTransactionAccountJson,
    mandateId: String,
    transactionAmount: AmountOfMoneyV13,
    bookingDate: String,
    valueDate: String,
    remittanceInformationUnstructured: String,
    bankTransactionCode: String,
  )
  
  case class CardTransactionJsonV13(
    cardTransactionId: String,
    transactionAmount: AmountOfMoneyV13,
    transactionDate: Date,
    bookingDate: Date,
    originalAmount: AmountOfMoneyV13,
    maskedPan: String,
    proprietaryBankTransactionCode: String = "",
    invoiced:Boolean,
    transactionDetails:String
  )
  
  case class TransactionsV13Transactions(
    booked: Option[List[TransactionJsonV13]], 
    pending: Option[List[TransactionJsonV13]] = None,
    _links: TransactionsV13TransactionsLinks 
  )

  case class CardTransactionsV13Transactions(
    booked: List[CardTransactionJsonV13],
    pending: Option[List[CardTransactionJsonV13]] = None,
    _links: CardTransactionsLinksV13
  )
  
  case class TransactionsJsonV13(
    account: FromAccountJson,
    transactions:TransactionsV13Transactions,
  )

  case class CardTransactionsJsonV13(
    cardAccount:CardBalanceAccount,
    transactions:CardTransactionsV13Transactions,
  )
  
  case class ConsentStatusJsonV13(
    consentStatus: String
  )  
  case class ScaStatusJsonV13(
    scaStatus: String
  )  
  case class AuthorisationJsonV13(authorisationIds: List[String])
  case class CancellationJsonV13(cancellationIds: List[String])

  case class ConsentAccessAccountsJson(
    iban: Option[String],
    bban: Option[String],
    pan: Option[String],
    maskedPan: Option[String],
    msisdn: Option[String],
    currency: Option[String]
  )
  case class ConsentAccessJson(
    accounts: Option[List[ConsentAccessAccountsJson]] = Some(Nil), //For now, only set the `Nil`, not fully support this yet. 
    balances: Option[List[ConsentAccessAccountsJson]] = None,
    transactions: Option[List[ConsentAccessAccountsJson]] = None,
    availableAccounts: Option[String] = None,
    allPsd2: Option[String] = None
  )
  case class PostConsentJson(
    access: ConsentAccessJson,
    recurringIndicator: Boolean,
    validUntil: String,
    frequencyPerDay: Int,
    combinedServiceIndicator: Option[Boolean]
  )
  case class ConsentLinksV13(
    startAuthorisation: Option[Href] = None,
    scaRedirect: Option[Href] = None,
    status: Option[Href] = None,
    scaStatus: Option[Href] = None,
    startAuthorisationWithPsuIdentification: Option[Href] = None,
    startAuthorisationWithPsuAuthentication: Option[Href] = None,
  )

  case class PostConsentResponseJson(
    consentId: String,
    consentStatus: String,
    _links: ConsentLinksV13
  )
  case class Href(href: String)

  case class PutConsentResponseJson(
    scaStatus: String,
    _links: ConsentLinksV13
  )




  case class GetConsentResponseJson(
    access: ConsentAccessJson,
    recurringIndicator: Boolean,
    validUntil: String,
    frequencyPerDay: Int,
    combinedServiceIndicator: Option[Boolean],
    lastActionDate: String,
    consentStatus: String
  )
  
  case class StartConsentAuthorisationJson(
    scaStatus: String,
    authorisationId: String,
    pushMessage: String,
    _links: ScaStatusJsonV13
  )

  case class InitiatePaymentResponseLinks(
    scaRedirect: LinkHrefJson,
    self: LinkHrefJson,
    status: LinkHrefJson,
    scaStatus: LinkHrefJson
  )  
  case class CancelPaymentResponseLinks(
                                         self: LinkHrefJson,
                                         status: LinkHrefJson,
                                         startAuthorisation: LinkHrefJson
  )
  case class InitiatePaymentResponseJson(
    transactionStatus: String,
    paymentId: String,
    _links: InitiatePaymentResponseLinks
  )
  case class CancelPaymentResponseJson(
    transactionStatus: String,
    _links: CancelPaymentResponseLinks
  )
  case class CheckAvailabilityOfFundsJson(
    instructedAmount: AmountOfMoneyJsonV121,
    account: PaymentAccount,
  )
  
  case class StartPaymentAuthorisationJson(scaStatus: String, 
                                           authorisationId: String,
                                           psuMessage: String,
                                           _links: ScaStatusJsonV13
                                          )

  case class UpdatePaymentPsuDataJson(
    scaAuthenticationData: String
  )


  def flattenOBPReturnType(
    list: List[OBPReturnType[List[BankAccountBalanceTrait]]]
  ): OBPReturnType[List[BankAccountBalanceTrait]] = {
    Future.sequence(list).map { results =>
      val combinedBalances = results.flatMap(_._1) // Combine all balances
      val callContext = results.headOption.flatMap(_._2) // Use the first CallContext
      (combinedBalances, callContext)
    }
  }
  
  def createAccountListJson(bankAccounts: List[BankAccount],
                            canReadBalancesAccounts:  List[BankIdAccountId],
                            canReadTransactionsAccounts:  List[BankIdAccountId],
                            user: User,
                            withBalanceParam:Option[Boolean],
                            balances: List[BankAccountBalanceTrait]
  ): CoreAccountsJsonV13 = {
    CoreAccountsJsonV13(bankAccounts.map {
      x =>
        val (iBan: String, bBan: String) = getIbanAndBban(x)
        val commonPath = s"${OBP_BERLIN_GROUP_1_3.apiVersion.urlPrefix}/${OBP_BERLIN_GROUP_1_3.version}/accounts/${x.accountId.value}"
        val balanceRef = LinkHrefJson(s"/$commonPath/balances")
        val canReadBalances = canReadBalancesAccounts.map(_.accountId.value).contains(x.accountId.value)
        val transactionRef = LinkHrefJson(s"/$commonPath/transactions")
        val canReadTransactions = canReadTransactionsAccounts.map(_.accountId.value).contains(x.accountId.value)
        val accountBalances = if(withBalanceParam == Some(true)){
          Some(balances.filter(_.accountId.equals(x.accountId)).flatMap(balance => (List(CoreAccountBalanceJson(
            balanceAmount = AmountOfMoneyV13(x.currency, balance.balanceAmount.toString()),
            balanceType = balance.balanceType,
            lastChangeDateTime = balance.lastChangeDateTime.map(APIUtil.DateWithMsFormat.format(_))
          )))))
        }else{
          None
        }
      
        val cashAccountType = x.attributes.getOrElse(Nil).filter(_.name== "cashAccountType").map(_.value).headOption.getOrElse("")
        
        CoreAccountJsonV13(
          resourceId = x.accountId.value,
          iban = iBan,
          bban = None,
          currency = x.currency,
          name = if(APIUtil.getPropsAsBoolValue("BG_v1312_show_account_name", defaultValue = true)) Some(x.name) else None,
          cashAccountType = cashAccountType,
          product = x.accountType,
          balances = if(canReadBalances) accountBalances else None,
          _links = CoreAccountLinksJsonV13(
            balances = if(canReadBalances) Some(balanceRef) else None,
            transactions = if(canReadTransactions) Some(transactionRef) else None,
          )
        )
     }
    )
  }

  def createCardAccountListJson(bankAccounts: List[BankAccount],
                                canReadBalancesAccounts: List[BankIdAccountId],
                                canReadTransactionsAccounts: List[BankIdAccountId],
                                user: User): CoreCardAccountsJsonV13 = {
    CoreCardAccountsJsonV13(bankAccounts.map {
      x =>
        val (iBan: String, bBan: String) = getIbanAndBban(x)
        val commonPath = s"${OBP_BERLIN_GROUP_1_3.apiVersion.urlPrefix}/${OBP_BERLIN_GROUP_1_3.version}/accounts/${x.accountId.value}"
        val balanceRef = LinkHrefJson(s"/$commonPath/balances")
        val canReadBalances = canReadBalancesAccounts.map(_.accountId.value).contains(x.accountId.value)
        val transactionRef = LinkHrefJson(s"/$commonPath/transactions")
        val canReadTransactions = canReadTransactionsAccounts.map(_.accountId.value).contains(x.accountId.value)

        val cashAccountType = x.attributes.getOrElse(Nil).filter(_.name== "cashAccountType").map(_.value).headOption.getOrElse("")

        CoreAccountJsonV13(
          resourceId = x.accountId.value,
          iban = iBan,
          bban = None,
          currency = x.currency,
          name = if(APIUtil.getPropsAsBoolValue("BG_v1312_show_account_name", defaultValue = true)) Some(x.name) else None,
          cashAccountType = cashAccountType,
          product = x.accountType,
          balances = None,
          _links = CoreAccountLinksJsonV13(
            balances = if (canReadBalances) Some(balanceRef) else None,
            transactions = if (canReadTransactions) Some(transactionRef) else None,
          )
        )
    }
    )
  }
  
  def createCardAccountDetailsJson(bankAccount: BankAccount,
                                   canReadBalancesAccounts: List[BankIdAccountId],
                                   canReadTransactionsAccounts: List[BankIdAccountId],
                                   withBalanceParam: Option[Boolean],
                                   balances: List[BankAccountBalanceTrait],
                                   user: User): CardAccountDetailsJsonV13 = {
    val accountDetailsJsonV13 = createAccountDetailsJson(bankAccount, canReadBalancesAccounts, canReadTransactionsAccounts, withBalanceParam, balances, user)
    CardAccountDetailsJsonV13(accountDetailsJsonV13.account)
  }
  
  def createAccountDetailsJson(bankAccount: BankAccount,
                               canReadBalancesAccounts: List[BankIdAccountId],
                               canReadTransactionsAccounts: List[BankIdAccountId],
                               withBalanceParam: Option[Boolean],
                               balances: List[BankAccountBalanceTrait],
                               user: User): AccountDetailsJsonV13 = {
    val (iBan: String, bBan: String) = getIbanAndBban(bankAccount)
    val commonPath = s"${OBP_BERLIN_GROUP_1_3.apiVersion.urlPrefix}/${OBP_BERLIN_GROUP_1_3.version}/accounts/${bankAccount.accountId.value}"
    val balanceRef = LinkHrefJson(s"/$commonPath/balances")
    val canReadBalances = canReadBalancesAccounts.map(_.accountId.value).contains(bankAccount.accountId.value)
    val transactionRef = LinkHrefJson(s"/$commonPath/transactions")
    val canReadTransactions = canReadTransactionsAccounts.map(_.accountId.value).contains(bankAccount.accountId.value)
    val cashAccountType = bankAccount.attributes.getOrElse(Nil).filter(_.name== "cashAccountType").map(_.value).headOption.getOrElse("")
    val accountBalances = if (withBalanceParam.contains(true)) {
      Some(balances.filter(_.accountId.equals(bankAccount.accountId)).flatMap(balance => (List(CoreAccountBalanceJson(
        balanceAmount = AmountOfMoneyV13(bankAccount.currency, balance.balanceAmount.toString()),
        balanceType = balance.balanceType,
        lastChangeDateTime = balance.lastChangeDateTime.map(APIUtil.DateWithMsFormat.format(_))
      )))))
    } else {
      None
    }

    val account = AccountJsonV13(
      resourceId = bankAccount.accountId.value,
      iban = iBan,
      currency = bankAccount.currency,
      name = if(APIUtil.getPropsAsBoolValue("BG_v1312_show_account_name", defaultValue = true)) Some(bankAccount.name) else None,
      cashAccountType = cashAccountType,
      product = bankAccount.accountType,
      balances = if(canReadBalances) accountBalances else None,
      _links = AccountDetailsLinksJsonV13(
        balances = if (canReadBalances) Some(balanceRef) else None,
        transactions = if (canReadTransactions) Some(transactionRef) else None,
      ) 
    )
    AccountDetailsJsonV13(account)
  }

  private def getIbanAndBban(x: BankAccount) = {
    val iBan = x.accountRoutings.find(_.scheme.equalsIgnoreCase(AccountRoutingScheme.IBAN.toString))
      .map(_.address).getOrElse("")
    val bBan = if (iBan.size > 4) iBan.substring(4) else ""
    (iBan, bBan)
  }

  def createCardAccountBalanceJSON(bankAccount: BankAccount, accountBalances: List[BankAccountBalanceTrait]): CardAccountBalancesV13 = {
    val accountBalancesV13 = createAccountBalanceJSON(bankAccount: BankAccount, accountBalances)
    CardAccountBalancesV13(accountBalancesV13.account,accountBalancesV13.`balances`)
  }
  
  def createAccountBalanceJSON(bankAccount: BankAccount, accountBalances: List[BankAccountBalanceTrait]): AccountBalancesV13 = {

    val (iban: String, bban: String) = getIbanAndBban(bankAccount)

    AccountBalancesV13(
      account = FromAccount(
        iban = iban,
      ),
      `balances` = accountBalances.map(accountBalance => AccountBalance(
        balanceAmount = AmountOfMoneyV13(bankAccount.currency, accountBalance.balanceAmount.toString()),
        balanceType = accountBalance.balanceType,
        lastChangeDateTime = accountBalance.lastChangeDateTime.map(APIUtil.DateWithMsFormat.format(_)),
        referenceDate = accountBalance.referenceDate,
      ) 
    ))
  }
  
  def createTransactionJSON(transaction : ModeratedTransaction) : TransactionJsonV13 = {
    val bookingDate = transaction.startDate.orNull
    val valueDate = if(transaction.finishDate.isDefined) Some(BgSpecValidation.formatToISODate(transaction.finishDate.orNull)) else None
    
    val out: Boolean = transaction.amount.get.toString().startsWith("-")
    val in: Boolean = !out

    val isIban = transaction.bankAccount.flatMap(_.accountRoutingScheme.map(_.toUpperCase == "IBAN")).getOrElse(false)
    // Creditor - when Direction is OUT
    val creditorName = if(out) transaction.otherBankAccount.map(_.label.display) else None
    val creditorAccountIban = if(out) {
      val creditorIban = if(isIban) transaction.otherBankAccount.map(_.iban.getOrElse("")) else Some("")
      Some(BgTransactionAccountJson(iban = creditorIban))
    } else None

    // Debtor - when direction is IN
    val debtorName = if(in) transaction.bankAccount.map(_.label.getOrElse("")) else None
    val debtorAccountIban = if(in) {
      val debtorIban  = if(isIban) transaction.bankAccount.map(_.accountRoutingAddress.getOrElse("")) else Some("")
      Some(BgTransactionAccountJson(iban = debtorIban))
    } else None
    
    TransactionJsonV13(
      transactionId = transaction.id.value,
      creditorName = creditorName,
      creditorAccount = creditorAccountIban,
      debtorName = debtorName,
      debtorAccount = debtorAccountIban,
      transactionAmount = AmountOfMoneyV13(
        transaction.currency.getOrElse(""),
        if(bgRemoveSignOfAmounts)
          transaction.amount.get.toString().trim.stripPrefix("-")
        else
          transaction.amount.get.toString()
      ),
      bookingDate = Some(BgSpecValidation.formatToISODate(bookingDate)) ,
      valueDate = valueDate,
      remittanceInformationUnstructured = transaction.description
    )
  }

  def createCardTransactionJson(transaction : ModeratedTransaction) : CardTransactionJsonV13 = {
    val orignalBalnce = transaction.bankAccount.map(_.balance).getOrElse("")
    val orignalCurrency = transaction.bankAccount.map(_.currency).getOrElse(None).getOrElse("")
      
    val address = transaction.otherBankAccount.map(_.accountRoutingAddress).getOrElse(None).getOrElse("")
    val scheme: String = transaction.otherBankAccount.map(_.accountRoutingScheme).getOrElse(None).getOrElse("")
//    val (iban, bban, pan, maskedPan, currency) = extractAccountData(scheme, address)
    CardTransactionJsonV13(
      cardTransactionId = transaction.id.value,
      transactionAmount = AmountOfMoneyV13(transaction.currency.getOrElse(""),
        if(bgRemoveSignOfAmounts)
          transaction.amount.get.toString().trim.stripPrefix("-")
        else
          transaction.amount.get.toString()
      ),
      transactionDate = transaction.finishDate.get,
      bookingDate = transaction.startDate.get,
      originalAmount = AmountOfMoneyV13(orignalCurrency, orignalBalnce),
      maskedPan = "",
      proprietaryBankTransactionCode = "",
      invoiced = true,
      transactionDetails = transaction.description.getOrElse("")
    )
  }
  
//  def createTransactionFromRequestJSON(bankAccount: BankAccount, tr : TransactionRequest) : TransactionJsonV13 = {
//    val creditorName = bankAccount.accountHolder
//    val remittanceInformationUnstructured = tr.body.description
//    val (iban: String, bban: String) = getIbanAndBban(bankAccount)
//
//    val creditorAccountIban = if (tr.other_account_routing_scheme == "IBAN") stringOrNone(tr.other_account_routing_address) else None
//    val debtorAccountIdIban = stringOrNone(iban)
//    
//    TransactionJsonV13(
//      transactionId = tr.id.value,
//      creditorName = stringOrNone(creditorName),
//      creditorAccount = if (creditorAccountIban.isEmpty) None else Some(BgTransactionAccountJson(creditorAccountIban)), // If creditorAccountIban is None, it will return None
//      debtorName = stringOrNone(bankAccount.name),
//      debtorAccount = if (debtorAccountIdIban.isEmpty) None else Some(BgTransactionAccountJson(debtorAccountIdIban)),// If debtorAccountIdIban is None, it will return None
//      transactionAmount = AmountOfMoneyV13(tr.charge.value.currency, tr.charge.value.amount.trim.stripPrefix("-")),
//      bookingDate = Some(BgSpecValidation.formatToISODate(tr.start_date)),
//      valueDate = Some(BgSpecValidation.formatToISODate(tr.end_date)),
//      remittanceInformationUnstructured = Some(remittanceInformationUnstructured)
//    )
//  }

  def createTransactionsJson(bankAccount: BankAccount, transactions: List[ModeratedTransaction], bookingStatus: String, transactionRequests: List[TransactionRequest] = Nil) : TransactionsJsonV13 = {
    val accountId = bankAccount.accountId.value
    val (iban: String, bban: String) = getIbanAndBban(bankAccount)
   
    val account = FromAccountJson(
      iban = iban,
      currency = Some(bankAccount.currency)
    )
    
    val bookedTransactions = transactions.filter(_.status==Some(TransactionRequestStatus.COMPLETED.toString)).map(transaction => createTransactionJSON(transaction))
    val pendingTransactions = transactions.filter(_.status!=Some(TransactionRequestStatus.COMPLETED.toString)).map(transaction => createTransactionJSON(transaction))
    logger.debug(s"createTransactionsJson.bookedTransactions = $bookedTransactions")
    logger.debug(s"createTransactionsJson.pendingTransactions = $pendingTransactions")
    
    TransactionsJsonV13(
      account,
      TransactionsV13Transactions(
        booked = if(bookingStatus == "booked" || bookingStatus == "both") Some(bookedTransactions) else None,
        pending = if(bookingStatus == "pending" || bookingStatus == "both") Some(pendingTransactions) else None,
        _links = TransactionsV13TransactionsLinks(LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/accounts/$accountId"))
      )
    )
  }

  def createTransactionJson(bankAccount: BankAccount, transaction: ModeratedTransaction) : SingleTransactionJsonV13 = {
    val (iban: String, bban: String) = getIbanAndBban(bankAccount)
    val creditorAccount = BgTransactionAccountJson(
      iban = stringOrNone(iban),
    )
    SingleTransactionJsonV13(
      description = transaction.description.getOrElse(""),
      value=SingleTransactionValueJsonV13(
        transactionsDetails = transactionsDetailsJsonV13(
          transactionId = transaction.id.value,
          creditorName = transaction.bankAccount.map(_.label).flatten.getOrElse(""),
          creditorAccount,
          mandateId =transaction.UUID,
          transactionAmount=AmountOfMoneyV13(
            transaction.currency.getOrElse(""),
            if(bgRemoveSignOfAmounts)
              transaction.amount.get.toString().trim.stripPrefix("-")
            else
              transaction.amount.get.toString()
          ),
          bookingDate = transaction.startDate.map(APIUtil.DateWithMsFormat.format(_)).getOrElse(""),
          valueDate = transaction.finishDate.map(APIUtil.DateWithMsFormat.format(_)).getOrElse(""),
          remittanceInformationUnstructured = transaction.description.getOrElse(""),
          bankTransactionCode ="",
        )
      )
    )
  }

  def createCardTransactionsJson(bankAccount: BankAccount, transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest] = Nil) : CardTransactionsJsonV13 = {
    val accountId = bankAccount.accountId.value
    val (iban: String, bban: String) = getIbanAndBban(bankAccount)
    // get the latest end_date of `COMPLETED` transactionRequests
    val latestCompletedEndDate = transactionRequests.sortBy(_.end_date).reverse.filter(_.status == "COMPLETED").map(_.end_date).headOption.getOrElse("")
    //get the latest end_date of !`COMPLETED` transactionRequests
    val latestUncompletedEndDate = transactionRequests.sortBy(_.end_date).reverse.filter(_.status != "COMPLETED").map(_.end_date).headOption.getOrElse("")

    CardTransactionsJsonV13(
      CardBalanceAccount(
        maskedPan = getMaskedPrimaryAccountNumber(accountNumber = bankAccount.number)
      ),
      CardTransactionsV13Transactions(
        booked= transactions.map(t => createCardTransactionJson(t)),
        pending = None,
        _links = CardTransactionsLinksV13(LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/card-accounts/$accountId"))
      )
    )
  }
  
  def createPostConsentResponseJson(consent: ConsentTrait) : PostConsentResponseJson = {
    def redirectionWithDedicatedStartOfAuthorization = {
      PostConsentResponseJson(
        consentId = consent.consentId,
        consentStatus = consent.status.toLowerCase(),
        _links = ConsentLinksV13(
          startAuthorisation = Some(Href(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/consents/${consent.consentId}/authorisations"))
        )
      )
    }

    getPropsValue("psu_authentication_method") match {
      case Full("redirection") =>
        val scaRedirectUrlPattern = getPropsValue("psu_authentication_method_sca_redirect_url")
          .openOr(MissingPropsValueAtThisInstance + "psu_authentication_method_sca_redirect_url")
        val scaRedirectUrl =
          if(scaRedirectUrlPattern.contains("PLACEHOLDER"))
            scaRedirectUrlPattern.replace("PLACEHOLDER", consent.consentId)
          else
            s"$scaRedirectUrlPattern/${consent.consentId}"
        PostConsentResponseJson(
          consentId = consent.consentId,
          consentStatus = consent.status.toLowerCase(),
          _links = ConsentLinksV13(
            scaRedirect = Some(Href(s"$scaRedirectUrl")),
            status = Some(Href(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/consents/${consent.consentId}/status")),
            // TODO Introduce a working link
            // scaStatus = Some(Href(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/consents/${consent.consentId}/authorisations/AUTHORISATIONID")),
          )
        )
      case Full("redirection_with_dedicated_start_of_authorization") =>
        redirectionWithDedicatedStartOfAuthorization
      case Full("embedded") =>
        PostConsentResponseJson(
          consentId = consent.consentId,
          consentStatus = consent.status.toLowerCase(),
          _links = ConsentLinksV13(
            startAuthorisationWithPsuAuthentication = Some(Href(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/consents/${consent.consentId}/authorisations"))
          )
        )
      case Full("decoupled") =>
        PostConsentResponseJson(
          consentId = consent.consentId,
          consentStatus = consent.status.toLowerCase(),
          _links = ConsentLinksV13(
            startAuthorisationWithPsuIdentification = Some(Href(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/consents/${consent.consentId}/authorisations"))
          )
        )
      case _ =>
        redirectionWithDedicatedStartOfAuthorization
    }

  }
  def createPutConsentResponseJson(consent: ConsentTrait) : ScaStatusResponse = {
    ScaStatusResponse(
      scaStatus = consent.status.toLowerCase(),
      _links = Some(LinksAll(scaStatus = Some(HrefType(Some(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/consents/${consent.consentId}/authorisations")))))
    )
  }

  def createGetConsentResponseJson(createdConsent: ConsentTrait) : GetConsentResponseJson = {
    val jsonWebTokenAsJValue: Box[ConsentJWT] = JwtUtil.getSignedPayloadAsJson(createdConsent.jsonWebToken)
      .map(parse(_).extract[ConsentJWT])
    val access: ConsentAccessJson = jsonWebTokenAsJValue
      .flatMap(_.access).getOrElse(ConsentAccessJson())
    GetConsentResponseJson(
      access = access,
      recurringIndicator = createdConsent.recurringIndicator,
      validUntil = if(createdConsent.validUntil == null) null else new SimpleDateFormat(DateWithDay).format(createdConsent.validUntil), 
      frequencyPerDay = createdConsent.frequencyPerDay,
      combinedServiceIndicator = None,
      lastActionDate = if(createdConsent.lastActionDate == null) null else new SimpleDateFormat(DateWithDay).format(createdConsent.lastActionDate),
      consentStatus = createdConsent.status.toLowerCase()
    )
  }

  def createStartConsentAuthorisationJson(consent: ConsentTrait, challenge: ChallengeTrait) : StartConsentAuthorisationJson = {
    StartConsentAuthorisationJson(
      scaStatus = challenge.scaStatus.map(_.toString).getOrElse("None"),
      authorisationId = challenge.authenticationMethodId.getOrElse("None"),
      pushMessage = "started", //TODO Not implement how to fill this.
      _links =  ScaStatusJsonV13(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/consents/${consent.consentId}/authorisations/${challenge.challengeId}")//TODO, Not sure, what is this for??
    )
  }

  def createTransactionRequestJson(transactionRequest : TransactionRequestBGV1) : InitiatePaymentResponseJson = {
//    - 'ACCC': 'AcceptedSettlementCompleted' -
//      Settlement on the creditor's account has been completed.
//      - 'ACCP': 'AcceptedCustomerProfile' -
//      Preceding check of technical validation was successful.
//      Customer profile check was also successful.
//    - 'ACSC': 'AcceptedSettlementCompleted' -
//      Settlement on the debtor�s account has been completed.
//    - 'ACSP': 'AcceptedSettlementInProcess' -
//      All preceding checks such as technical validation and customer profile were successful and therefore the payment initiation has been accepted for execution.
//      - 'ACTC': 'AcceptedTechnicalValidation' -
//      Authentication and syntactical and semantical validation are successful.
//    - 'ACWC': 'AcceptedWithChange' -
//      Instruction is accepted but a change will be made, such as date or remittance not sent.
//      - 'ACWP': 'AcceptedWithoutPosting' -
//      Payment instruction included in the credit transfer is accepted without being posted to the creditor customer�s account.
//      - 'RCVD': 'Received' -
//      Payment initiation has been received by the receiving agent.
//      - 'PDNG': 'Pending' -
//      Payment initiation or individual transaction included in the payment initiation is pending.
//    Further checks and status update will be performed.
//    - 'RJCT': 'Rejected' -
//      Payment initiation or individual transaction included in the payment initiation has been rejected.
//      - 'CANC': 'Cancelled'
//    Payment initiation has been cancelled before execution
//    Remark: This codeis accepted as new code by ISO20022.
//      - 'ACFC': 'AcceptedFundsChecked' -
//      Preceding check of technical validation and customer profile was successful and an automatic funds check was positive .
//      Remark: This code is accepted as new code by ISO20022.
//      - 'PATC': 'PartiallyAcceptedTechnical'
//    Correct The payment initiation needs multiple authentications, where some but not yet all have been performed. Syntactical and semantical validations are successful.
//    Remark: This code is accepted as new code by ISO20022.
//      - 'PART': 'PartiallyAccepted' -
//      A number of transactions have been accepted, whereas another number of transactions have not yet achieved 'accepted' status.
//      Remark: This code may be
    //map OBP transactionRequestId to BerlinGroup PaymentId
    val paymentId = transactionRequest.id.value
    val scaRedirectUrlPattern = getPropsValue("psu_make_payment_sca_redirect_url")
      .openOr(MissingPropsValueAtThisInstance + "psu_make_payment_sca_redirect_url")
    val scaRedirectUrl =
      if (scaRedirectUrlPattern.contains("PLACEHOLDER"))
        scaRedirectUrlPattern.replace("PLACEHOLDER", paymentId)
      else
        s"$scaRedirectUrlPattern/${paymentId}"
    InitiatePaymentResponseJson(
      transactionStatus = mapTransactionStatus(transactionRequest.status),
      paymentId = paymentId,
      _links = InitiatePaymentResponseLinks(
        scaRedirect = LinkHrefJson(s"$scaRedirectUrl/$paymentId"),
        self = LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/payments/sepa-credit-transfers/$paymentId"),
        status = LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/payments/$paymentId/status"),
        scaStatus = LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/payments/$paymentId/authorisations/${paymentId}")
      )
    )
  }
  def createCancellationTransactionRequestJson(transactionRequest : TransactionRequest) : CancelPaymentResponseJson = {
    val paymentId = transactionRequest.id.value
    CancelPaymentResponseJson(
      "ACTC",
      _links = CancelPaymentResponseLinks(
        self = LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/payments/sepa-credit-transfers/$paymentId"),
        status = LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/payments/sepa-credit-transfers/$paymentId/status"),
        startAuthorisation = LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/payments/sepa-credit-transfers/cancellation-authorisations/${paymentId}")
      )
    )
  }

  def createStartPaymentAuthorisationsJson(challenges: List[ChallengeTrait]): List[StartPaymentAuthorisationJson] = {
    challenges.map(createStartPaymentAuthorisationJson)
  }

  def createStartPaymentAuthorisationJson(challenge: ChallengeTrait) = {
      StartPaymentAuthorisationJson(
        scaStatus = challenge.scaStatus.map(_.toString).getOrElse(""),
        authorisationId = challenge.challengeId,
        psuMessage = "Please check your SMS at a mobile device.",
        _links = ScaStatusJsonV13(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/payments/sepa-credit-transfers/${challenge.challengeId}")
      )
  }

  def createUpdatePaymentPsuDataTransactionAuthorisationJson(challenge: ChallengeTrait) = {
    ScaStatusResponse(
      scaStatus = challenge.scaStatus.map(_.toString).getOrElse(""),
      psuMessage = Some("Please check your SMS at a mobile device."),
      _links = Some(LinksAll(scaStatus = Some(HrefType(Some(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/payments/sepa-credit-transfers/${challenge.challengeId}"))))
      )
    )
  }
  def createStartPaymentCancellationAuthorisationJson(challenge: ChallengeTrait,
                                                      paymentService: String,
                                                      paymentProduct: String,
                                                      paymentId: String
                                                     ) = {
    ScaStatusResponse(
        scaStatus = challenge.scaStatus.map(_.toString).getOrElse(""),
        psuMessage = Some("Please check your SMS at a mobile device."),
        _links = Some(LinksAll(scaStatus = Some(HrefType(Some(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/${paymentService}/${paymentProduct}/${paymentId}/cancellation-authorisations/${challenge.challengeId}"))))
      )
    )
  }

  def createStartPaymentInitiationCancellationAuthorisation(
    challenge: ChallengeTrait,
    paymentService: String,
    paymentProduct: String,
    paymentId: String
  ) = {
    UpdatePsuAuthenticationResponse(
        scaStatus = challenge.scaStatus.map(_.toString).getOrElse(""),
        authorisationId = Some(challenge.challengeId),
        psuMessage = Some("Please check your SMS at a mobile device."),
        _links = Some(LinksUpdatePsuAuthentication(
          scaStatus = Some(HrefType(Some(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/${paymentService}/${paymentProduct}/${paymentId}/cancellation-authorisations/${challenge.challengeId}"))))
        )
      )
  }


  def createStartSigningBasketAuthorisationJson(basketId: String, challenge: ChallengeTrait): StartPaymentAuthorisationJson = {
    StartPaymentAuthorisationJson(
      scaStatus = challenge.scaStatus.map(_.toString).getOrElse(""),
      authorisationId = challenge.challengeId,
      psuMessage = "Please check your SMS at a mobile device.",
      _links = ScaStatusJsonV13(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/signing-baskets/${basketId}/authorisations/${challenge.challengeId}")
    )
  }

  def createSigningBasketResponseJson(basket: SigningBasketTrait): SigningBasketResponseJson = {
    SigningBasketResponseJson(
      basketId = basket.basketId,
      transactionStatus = basket.status.toLowerCase(),
      _links = SigningBasketLinksV13(
        self = LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/signing-baskets/${basket.basketId}"),
        status = LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/signing-baskets/${basket.basketId}/status"),
        startAuthorisation = LinkHrefJson(s"/${ConstantsBG.berlinGroupVersion1.apiShortVersion}/signing-baskets/${basket.basketId}/authorisations")
      )
    )
  }

  def getSigningBasketResponseJson(basket: SigningBasketContent): SigningBasketGetResponseJson = {
    SigningBasketGetResponseJson(
      transactionStatus = basket.basket.status.toLowerCase(),
      payments = basket.payments,
      consents = basket.consents,
    )
  }

  def getSigningBasketStatusResponseJson(basket: SigningBasketContent): SigningBasketGetResponseJson = {
    SigningBasketGetResponseJson(
      transactionStatus = basket.basket.status.toLowerCase(),
      payments = None,
      consents = None,
    )
  }

  def checkTransactionAuthorisation(JsonPost: JValue) = tryo {
    JsonPost.extract[TransactionAuthorisation]
  }.isDefined

  def checkUpdatePsuAuthentication(JsonPost: JValue) = tryo {
    JsonPost.extract[UpdatePsuAuthentication]
  }.isDefined

  def checkSelectPsuAuthenticationMethod(JsonPost: JValue) = tryo {
    JsonPost.extract[SelectPsuAuthenticationMethod]
  }.isDefined

  def checkAuthorisationConfirmation(JsonPost: JValue) = tryo {
    JsonPost.extract[AuthorisationConfirmation]
  }.isDefined
}
