package code.api.berlin.group.v1_3

import java.text.SimpleDateFormat
import java.util.Date

import code.api.util.APIUtil._
import code.api.util.{APIUtil, ConsentJWT, CustomJsonFormats, JwtUtil}
import code.bankconnectors.Connector
import code.consent.Consent
import code.database.authorisation.Authorisation
import code.model.ModeratedTransaction
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import com.openbankproject.commons.model.{BankAccount, TransactionRequest, User, _}
import net.liftweb.common.{Box, Full}
import net.liftweb.json
import net.liftweb.json.{JValue, parse}

import scala.collection.immutable.List

case class JvalueCaseClass(jvalueToCaseclass: JValue)

object JSONFactory_BERLIN_GROUP_1_3 extends CustomJsonFormats {


  case class LinkHrefJson(
    href: String
  )
  
  case class CoreAccountLinksJsonV13(
    balances: LinkHrefJson //,
//    trasactions: LinkHrefJson // These links are only supported, when the corresponding consent has been already granted.
  )
  
  case class CoreAccountBalancesJson(
    balanceAmount:AmountOfMoneyV13 = AmountOfMoneyV13("EUR","123"),
    balanceType: String = "closingBooked",
    lastChangeDateTime: String = "2019-01-28T06:26:52.185Z",
    referenceDate: String = "2020-07-02",
    lastCommittedTransaction: String = "string",
  )
  case class CoreAccountJsonV13(
                                 resourceId: String,
                                 iban: String,
                                 bban: String,
                                 currency: String,
                                 name: String,
                                 product: String,
                                 cashAccountType: String,
//                                 status: String="enabled",
                                 bic: String,
//                                 linkedAccounts: String ="string",
//                                 usage: String ="PRIV",
//                                 details: String ="",
                                 balances: CoreAccountBalancesJson,
                                 _links: CoreAccountLinksJsonV13,
  )

  case class CoreAccountsJsonV13(accounts: List[CoreAccountJsonV13])
  case class CoreCardAccountsJsonV13(cardAccounts: List[CoreAccountJsonV13])

  case class AccountDetailsLinksJsonV13(
                                         balances: LinkHrefJson,
                                         transactions: LinkHrefJson
                                       )

  case class AccountJsonV13(
                             resourceId: String,
                             iban: String,
                             currency: String,
                             product: String,
                             cashAccountType: String,
                             name: String,
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
                             balanceType: String = "closingBooked",
                             lastChangeDateTime: String = "2020-07-02T10:23:57.814Z",
                             lastCommittedTransaction: String = "string",
                             referenceDate: String = "2020-07-02",
    
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
  case class CreditorAccountJson(
    iban: String,
  )
  case class TransactionJsonV13(
    transactionId: String,
    creditorName: String,
    creditorAccount: CreditorAccountJson,
    transactionAmount: AmountOfMoneyV13,
    bookingDate: Date,
    valueDate: Date,
    remittanceInformationUnstructured: String,
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
    creditorAccount: CreditorAccountJson,
    mandateId: String,
    transactionAmount: AmountOfMoneyV13,
    bookingDate: Date,
    valueDate: Date,
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
    booked: List[TransactionJsonV13], 
    pending: List[TransactionJsonV13],
    _links: TransactionsV13TransactionsLinks 
  )

  case class CardTransactionsV13Transactions(
    booked: List[CardTransactionJsonV13],
    pending: List[CardTransactionJsonV13],
    _links: CardTransactionsLinksV13
  )
  
  case class TransactionsJsonV13(
    account:FromAccount,
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
    combinedServiceIndicator: Boolean
  )
  case class ConsentLinksV13(
    startAuthorisation: String
  )

  case class PostConsentResponseJson(
    consentId: String,
    consentStatus: String,
    _links: ConsentLinksV13
  )


  case class GetConsentResponseJson(
    access: ConsentAccessJson,
    recurringIndicator: Boolean,
    validUntil: String,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean,
    lastActionDate: String,
    consentStatus: String
  )
  
  case class StartConsentAuthorisationJson(
    scaStatus: String,
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
  
  
  def createAccountListJson(bankAccounts: List[BankAccount], user: User): CoreAccountsJsonV13 = {
    CoreAccountsJsonV13(bankAccounts.map {
      x =>
        val (iBan: String, bBan: String) = getIbanAndBban(x)

        val balance =
          CoreAccountBalancesJson(
            balanceAmount = AmountOfMoneyV13(x.currency,x.balance.toString()),
            balanceType = APIUtil.stringOrNull(x.accountType),
            lastChangeDateTime=APIUtil.DateWithDayFormat.format(x.lastUpdate),
            referenceDate =APIUtil.DateWithMsRollback.format(x.lastUpdate),
            lastCommittedTransaction = "String"
          )
        CoreAccountJsonV13(
          resourceId = x.accountId.value,
          iban = iBan,
          bban = bBan,
          currency = x.currency,
          name = x.name,
          bic = getBicFromBankId(x.bankId.value),
          cashAccountType = x.accountType,
          product = x.accountType,
          balances = balance,
          _links = CoreAccountLinksJsonV13(LinkHrefJson(s"/${OBP_BERLIN_GROUP_1_3.version}/accounts/${x.accountId.value}/balances")) 
        )
     }
    )
  }

  def createCardAccountListJson(bankAccounts: List[BankAccount], user: User): CoreCardAccountsJsonV13 = {
    CoreCardAccountsJsonV13(bankAccounts.map {
      x =>
        val (iBan: String, bBan: String) = getIbanAndBban(x)

        val balance =
          CoreAccountBalancesJson(
            balanceAmount = AmountOfMoneyV13(x.currency,x.balance.toString()),
            balanceType = APIUtil.stringOrNull(x.accountType),
            lastChangeDateTime=APIUtil.DateWithDayFormat.format(x.lastUpdate),
            referenceDate =APIUtil.DateWithMsRollback.format(x.lastUpdate),
            lastCommittedTransaction = "String"
          )
        CoreAccountJsonV13(
          resourceId = x.accountId.value,
          iban = iBan,
          bban = bBan,
          currency = x.currency,
          name = x.name,
          bic = getBicFromBankId(x.bankId.value),
          cashAccountType = x.accountType,
          product = x.accountType,
          balances = balance,
          _links = CoreAccountLinksJsonV13(LinkHrefJson(s"/${OBP_BERLIN_GROUP_1_3.version}/accounts/${x.accountId.value}/balances"))
        )
    }
    )
  }
  
  def createCardAccountDetailsJson(bankAccount: BankAccount, user: User): CardAccountDetailsJsonV13 = {
    val accountDetailsJsonV13 = createAccountDetailsJson(bankAccount: BankAccount, user: User)
    CardAccountDetailsJsonV13(accountDetailsJsonV13.account)
  }
  
  def createAccountDetailsJson(bankAccount: BankAccount, user: User): AccountDetailsJsonV13 = {
    val (iBan: String, bBan: String) = getIbanAndBban(bankAccount)
    val account = AccountJsonV13(
      resourceId = bankAccount.accountId.value,
      iban = iBan,
      currency = bankAccount.currency,
      name = bankAccount.name,
      cashAccountType = bankAccount.accountType,
      product = bankAccount.accountType,
      _links = AccountDetailsLinksJsonV13(
        LinkHrefJson(s"/${OBP_BERLIN_GROUP_1_3.version}/accounts/${bankAccount.accountId.value}/balances"),
        LinkHrefJson(s"/${OBP_BERLIN_GROUP_1_3.version}/accounts/${bankAccount.accountId.value}/transactions")
      ) 
    )
    AccountDetailsJsonV13(account)
  }

  private def getIbanAndBban(x: BankAccount) = {
    val iBan = x.accountRoutings.find(_.scheme == AccountRoutingScheme.IBAN.toString)
      .map(_.address).getOrElse("")
    val bBan = if (iBan.size > 4) iBan.substring(4) else ""
    (iBan, bBan)
  }

  def createCardAccountBalanceJSON(bankAccount: BankAccount): CardAccountBalancesV13 = {
    val accountBalancesV13 = createAccountBalanceJSON(bankAccount: BankAccount)
    CardAccountBalancesV13(accountBalancesV13.account,accountBalancesV13.`balances`)
  }
  
  def createAccountBalanceJSON(bankAccount: BankAccount): AccountBalancesV13 = {

    val (iban: String, bban: String) = getIbanAndBban(bankAccount)

    AccountBalancesV13(
      account = FromAccount(
        iban = iban,
      ),
      `balances` = AccountBalance(
        balanceAmount = AmountOfMoneyV13(
          currency = APIUtil.stringOrNull(bankAccount.currency),
          amount = bankAccount.balance.toString()
        ),
        balanceType = APIUtil.stringOrNull(bankAccount.accountType),
        lastChangeDateTime = APIUtil.DateWithMsRollback.format(bankAccount.lastUpdate),
        referenceDate= APIUtil.DateWithDayFormat.format(bankAccount.lastUpdate),
        lastCommittedTransaction = "String"
      ) :: Nil
    ) 
  }
  
  def createTransactionJSON(bankAccount: BankAccount, transaction : ModeratedTransaction, creditorAccount: CreditorAccountJson) : TransactionJsonV13 = {
    val bookingDate = transaction.startDate.getOrElse(null)
    val valueDate = transaction.finishDate.getOrElse(null)
    val creditorName = bankAccount.label
    TransactionJsonV13(
      transactionId = transaction.id.value,
      creditorName = creditorName,
      creditorAccount = creditorAccount,
      transactionAmount = AmountOfMoneyV13(APIUtil.stringOptionOrNull(transaction.currency), transaction.amount.get.toString()),
      bookingDate = bookingDate,
      valueDate = valueDate,
      remittanceInformationUnstructured = APIUtil.stringOptionOrNull(transaction.description)
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
      transactionAmount = AmountOfMoneyV13(APIUtil.stringOptionOrNull(transaction.currency), transaction.amount.get.toString()),
      transactionDate = transaction.finishDate.get,
      bookingDate = transaction.startDate.get,
      originalAmount = AmountOfMoneyV13(orignalCurrency, orignalBalnce),
      maskedPan = "",
      proprietaryBankTransactionCode = "",
      invoiced = true,
      transactionDetails = APIUtil.stringOptionOrNull(transaction.description)
    )
  }

  
  def createTransactionFromRequestJSON(bankAccount: BankAccount, transactionRequest : TransactionRequest, creditorAccount: CreditorAccountJson) : TransactionJsonV13 = {
    val creditorName = bankAccount.accountHolder
    val remittanceInformationUnstructured = stringOrNull(transactionRequest.body.description)
    TransactionJsonV13(
      transactionId = transactionRequest.id.value,
      creditorName = creditorName,
      creditorAccount = creditorAccount,
      transactionAmount = AmountOfMoneyV13(transactionRequest.charge.value.currency, transactionRequest.charge.value.amount),
      bookingDate = transactionRequest.start_date,
      valueDate = transactionRequest.end_date,
      remittanceInformationUnstructured = remittanceInformationUnstructured
    )
  }

  private def extractAccountData(scheme: String, address: String): (String, String, String, String, String) = {
    val (iban: String, bban: String, pan: String, maskedPan: String, currency: String) = Connector.connector.vend.getBankAccountByRouting(
      None,
      scheme,
      address,
      None
    ) match {
      case Full((account, _)) =>
        val (iban: String, bban: String) = getIbanAndBban(account)
        val (pan, maskedPan) = (account.number, getMaskedPrimaryAccountNumber(accountNumber = account.number))
        (iban, bban, pan, maskedPan, account.currency)
      case _ => ("", "", "", "", "")
    }
    (iban, bban, pan, maskedPan, currency)
  }

  def createTransactionsJson(bankAccount: BankAccount, transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : TransactionsJsonV13 = {
    val accountId = bankAccount.accountId.value
    val (iban: String, bban: String) = getIbanAndBban(bankAccount)
   
    val creditorAccount = CreditorAccountJson(
      iban = iban,
    )
    TransactionsJsonV13(
      FromAccount(
        iban = iban,
      ),
      TransactionsV13Transactions(
        booked= transactions.map(transaction => createTransactionJSON(bankAccount, transaction, creditorAccount)),
        pending = transactionRequests.filter(_.status!="COMPLETED").map(transactionRequest => createTransactionFromRequestJSON(bankAccount, transactionRequest, creditorAccount)),
        _links = TransactionsV13TransactionsLinks(LinkHrefJson(s"/v1.3/accounts/$accountId"))
      )
    )
  }

  def createTransactionJson(bankAccount: BankAccount, transaction: ModeratedTransaction) : SingleTransactionJsonV13 = {
    val (iban: String, bban: String) = getIbanAndBban(bankAccount)
    val creditorAccount = CreditorAccountJson(
      iban = iban,
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
            transaction.amount.getOrElse("").toString,
          ),
          bookingDate = transaction.startDate.getOrElse(null),
          valueDate = transaction.finishDate.getOrElse(null),
          remittanceInformationUnstructured = transaction.description.getOrElse(""),
          bankTransactionCode ="",
        )
      )
    )
  }

  def createCardTransactionsJson(bankAccount: BankAccount, transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : CardTransactionsJsonV13 = {
    val accountId = bankAccount.accountId.value
    val (iban: String, bban: String) = getIbanAndBban(bankAccount)
    // get the latest end_date of `COMPLETED` transactionRequests
    val latestCompletedEndDate = transactionRequests.sortBy(_.end_date).reverse.filter(_.status == "COMPLETED").map(_.end_date).headOption.getOrElse(null)
    //get the latest end_date of !`COMPLETED` transactionRequests
    val latestUncompletedEndDate = transactionRequests.sortBy(_.end_date).reverse.filter(_.status != "COMPLETED").map(_.end_date).headOption.getOrElse(null)

    CardTransactionsJsonV13(
      CardBalanceAccount(
        maskedPan = getMaskedPrimaryAccountNumber(accountNumber = bankAccount.number)
      ),
      CardTransactionsV13Transactions(
        booked= transactions.map(t => createCardTransactionJson(t)),
        pending = Nil,
        _links = CardTransactionsLinksV13(LinkHrefJson(s"/v1.3/card-accounts/$accountId"))
      )
    )
  }
  
  def createPostConsentResponseJson(consent: Consent) : PostConsentResponseJson = {
    PostConsentResponseJson(
      consentId = consent.consentId,
      consentStatus = consent.status.toLowerCase(),
      _links= ConsentLinksV13(s"v1.3/consents/${consent.consentId}/authorisations")
    )
  }

  def createGetConsentResponseJson(createdConsent: Consent) : GetConsentResponseJson = {
    val jsonWebTokenAsJValue: Box[ConsentJWT] = JwtUtil.getSignedPayloadAsJson(createdConsent.jsonWebToken)
      .map(parse(_).extract[ConsentJWT])
    val access: ConsentAccessJson = jsonWebTokenAsJValue
      .flatMap(_.access).getOrElse(ConsentAccessJson())
    GetConsentResponseJson(
      access = access,
      recurringIndicator = createdConsent.recurringIndicator,
      validUntil = new SimpleDateFormat(DateWithDay).format(createdConsent.validUntil), 
      frequencyPerDay = createdConsent.frequencyPerDay,
      combinedServiceIndicator= createdConsent.combinedServiceIndicator,
      lastActionDate= new SimpleDateFormat(DateWithDay).format(createdConsent.lastActionDate),
      consentStatus= createdConsent.status.toLowerCase()
    )
  }

  def createStartConsentAuthorisationJson(consent: Consent, challenge: ChallengeTrait) : StartConsentAuthorisationJson = {
    StartConsentAuthorisationJson(
      scaStatus = challenge.scaStatus.map(_.toString).getOrElse("None"),
      pushMessage = "started", //TODO Not implement how to fill this.
      _links =  ScaStatusJsonV13(s"/v1.3/consents/${consent.consentId}/authorisations/${challenge.challengeId}")//TODO, Not sure, what is this for??
    )
  }

  def createTransactionRequestJson(transactionRequest : TransactionRequest) : InitiatePaymentResponseJson = {
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
    InitiatePaymentResponseJson(
      transactionStatus = transactionRequest.status match {
        case "COMPLETED" => "ACCP"
        case "INITIATED" => "RCVD"
      },
      paymentId = paymentId,
      _links = InitiatePaymentResponseLinks(
        scaRedirect = LinkHrefJson(s"$getServerUrl/otp?flow=payment&paymentService=payments&paymentProduct=sepa_credit_transfers&paymentId=$paymentId"),
        self = LinkHrefJson(s"/v1.3/payments/sepa-credit-transfers/$paymentId"),
        status = LinkHrefJson(s"/v1.3/payments/$paymentId/status"),
        scaStatus = LinkHrefJson(s"/v1.3/payments/$paymentId/authorisations/${paymentId}")
      )
    )
  }
  def createCancellationTransactionRequestJson(transactionRequest : TransactionRequest) : CancelPaymentResponseJson = {
    val paymentId = transactionRequest.id.value
    CancelPaymentResponseJson(
      "ACTC",
      _links = CancelPaymentResponseLinks(
        self = LinkHrefJson(s"/v1.3/payments/sepa-credit-transfers/$paymentId"),
        status = LinkHrefJson(s"/v1.3/payments/sepa-credit-transfers/$paymentId/status"),
        startAuthorisation = LinkHrefJson(s"/v1.3/payments/sepa-credit-transfers/cancellation-authorisations/${paymentId}")
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
        _links = ScaStatusJsonV13(s"/v1.3/payments/sepa-credit-transfers/${challenge.challengeId}")
      )
  }

  def createStartPaymentCancellationAuthorisationsJson(challenges: List[ChallengeTrait],
                                                       paymentService: String,
                                                       paymentProduct: String,
                                                       paymentId: String): List[StartPaymentAuthorisationJson] = {
    challenges.map(createStartPaymentCancellationAuthorisationJson(_, paymentService, paymentProduct, paymentId))
  }
  def createStartPaymentCancellationAuthorisationJson(challenge: ChallengeTrait,
                                                      paymentService: String,
                                                      paymentProduct: String,
                                                      paymentId: String
                                                     ) = {
      StartPaymentAuthorisationJson(
        scaStatus = challenge.scaStatus.map(_.toString).getOrElse(""),
        authorisationId = challenge.challengeId,
        psuMessage = "Please check your SMS at a mobile device.",
        _links = ScaStatusJsonV13(s"/v1.3/${paymentService}/${paymentProduct}/${paymentId}/cancellation-authorisations/${challenge.challengeId}")
      )
  }
}
