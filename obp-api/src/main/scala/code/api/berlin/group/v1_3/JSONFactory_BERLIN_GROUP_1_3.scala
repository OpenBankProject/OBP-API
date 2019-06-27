package code.api.berlin.group.v1_3

import java.text.SimpleDateFormat
import java.util.Date
import code.api.util.APIUtil._
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi.tweakStatusNames
import code.api.util.{APIUtil, CustomJsonFormats}
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeJsonV140, TransactionRequestAccountJsonV140}
import code.api.v2_0_0.TransactionRequestChargeJsonV200
import code.api.v2_1_0.JSONFactory210.stringOrNull
import code.api.v2_1_0.TransactionRequestWithChargeJSON210
import code.model.ModeratedTransaction
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, BankAccount, CoreAccount, TransactionRequest}
import net.liftweb.json.JValue
import code.consent.Consent
import scala.collection.immutable.List

case class JvalueCaseClass(jvalueToCaseclass: JValue)

object JSONFactory_BERLIN_GROUP_1_3 extends CustomJsonFormats {

  trait links
  case class Balances(balances: String) extends links
  case class Transactions(trasactions: String) extends links
  case class ViewAccount(viewAccount: String) extends links
  case class AdditionalProp1(additionalProp1: String) extends links
  case class AdditionalProp2(additionalProp2: String) extends links
  case class AdditionalProp3(additionalProp3: String) extends links
  
  case class CoreAccountBalancesJson(
    balanceAmount:AmountOfMoneyV13 = AmountOfMoneyV13("EUR","123"),
    balanceType: String = "closingBooked",
    lastChangeDateTime: String = "2019-01-28T06:26:52.185Z",
    referenceDate: String = "string",
    lastCommittedTransaction: String = "string",
  )
  case class CoreAccountJsonV13(
    resourceId: String,
    iban: String,
    bban: String ="BARC12345612345678",
    msisdn: String ="+49 170 1234567",
    currency: String,
    name: String,
    product: String ="string",
    cashAccountType: String,
    status: String="enabled",
    bic: String,
    linkedAccounts: String ="string",
    usage: String ="PRIV",
    details: String ="string",
    balances: CoreAccountBalancesJson = CoreAccountBalancesJson(),
    _links: List[links],
  )

  case class CoreAccountsJsonV13(accounts: List[CoreAccountJsonV13])
  
  case class AmountOfMoneyV13(
    currency : String,
    content : String
  )
  case class AccountBalance(
    amount : AmountOfMoneyV13 = AmountOfMoneyV13("EUR","123"),
    balanceType: String = "closingBooked",
    lastChangeDateTime: String = "string",
    lastCommittedTransaction: String = "string",
    referenceDate: String = "string",
    
  )
  case class BalanceAccount(
    bban: String = "",
    currency: String =  "EUR",
    iban : String =  "FR7612345987650123456789014",
    maskedPan: String =  "",
    msisdn : String =  "",
    pan: String =""
  )
  case class AccountBalancesV13(
    account:BalanceAccount= BalanceAccount(),
    `balances`: List[AccountBalance] = AccountBalance() ::Nil
  )
  case class TransactionsLinksV13(
    account: String
  )
  case class TransactionsV13TransactionsLinks(
    account: String ,
   
  )
  case class ExchangeRateJson(
    sourceCurrency: String = "EUR",
    rate: String = "string",
    unitCurrency: String = "string",
    targetCurrency: String = "EUR",
    rateDate: String = "string",
    rateContract: String = "string"    
  )
  case class CreditorAccountJson(
    iban: String = "FR7612345987650123456789014",
    bban: String = "BARC12345612345678",
    pan: String =  "5409050000000000",
    maskedPan: String =  "123456xxxxxx1234",
    msisdn: String =  "+49 170 1234567",
    currency: String =  "EUR"
  )
  case class TransactionJsonV13Links(
    transactionDetails: String = "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp1: String = "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp2: String = "/v1/payments/sepa-credit-transfers/1234-wertiq-983",
    additionalProp3: String = "/v1/payments/sepa-credit-transfers/1234-wertiq-983"    
  )
  case class TransactionJsonV13(
    transactionId: String,
    entryReference: String ="",
    endToEndId: String ="",
    mandateId: String ="",
    checkId: String ="",
    creditorId: String ="",
    bookingDate: Date,
    valueDate: Date,
    transactionAmount: AmountOfMoneyV13,
    exchangeRate: ExchangeRateJson = ExchangeRateJson(),
    creditorName: String,
    creditorAccount: CreditorAccountJson = CreditorAccountJson(),
    ultimateCreditor: String = "Ultimate Creditor",
    debtorName: String = "Debtor Name",
    debtorAccount: CreditorAccountJson = CreditorAccountJson(),
    ultimateDebtor: String = "Ultimate Debtor",
    remittanceInformationUnstructured: String= "",
    remittanceInformationStructured: String = "",
    purposeCode: String = "BKDF",
    bankTransactionCode: String = "PMNT-RCDT-ESCT",
    proprietaryBankTransactionCode: String = "",
    _links: TransactionJsonV13Links = TransactionJsonV13Links(),
  )
  
  case class TransactionsV13Transactions(
    booked: List[TransactionJsonV13], 
    pending: List[TransactionJsonV13],
    _links: TransactionsV13TransactionsLinks 
  )
  case class TransactionsJsonV13Balance(
    balanceAmount :  AmountOfMoneyV13 = AmountOfMoneyV13("EUR","123"),
    balanceType: String =  "",
    lastChangeDateTime: String = "",
    referenceDate: String = "",
    lastCommittedTransaction: String =  ""
  )
  case class TransactionsJsonV13(
    account:BalanceAccount,
    transactions:TransactionsV13Transactions,
    balances: List[TransactionsJsonV13Balance] ,
    _links: TransactionsLinksV13 
  )
  
  case class ConsentStatusJsonV13(
    consentStatus: String
  )  
  case class ScaStatusJsonV13(
    scaStatus: String
  )  
  case class AuthorisationJsonV13(authorisationIds: List[String])

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

  case class PaymentAccountJson(
    iban: String
  )
  case class InstructedAmountJson(
    currency: String,
    amount: String
  )
  case class SepaCreditTransfersJson(
    debtorAccount: PaymentAccountJson,
    instructedAmount: AmountOfMoneyJsonV121,
    creditorAccount: PaymentAccountJson,
    creditorName: String
  )

  case class LinkHrefJson(
    href: String
  )
  case class InitiatePaymentResponseLinks(
    scaRedirect: LinkHrefJson,
    self: LinkHrefJson,
    status: LinkHrefJson,
    scaStatus: LinkHrefJson
  )
  case class InitiatePaymentResponseJson(
    transactionStatus: String,
    paymentId: String,
    _links: InitiatePaymentResponseLinks
  )
  
  def createAccountListJson(coreAccounts: List[CoreAccount]): CoreAccountsJsonV13 = {
    CoreAccountsJsonV13(coreAccounts.map(
      x => CoreAccountJsonV13(
        resourceId = x.id,
        iban = if (x.accountRoutings.headOption.isDefined && x.accountRoutings.head.scheme == "IBAN") x.accountRoutings.head.address else "",
        currency = "",
        name = x.label,
        status = "",
        cashAccountType = "",
        bic="",
        _links = Balances(s"/${OBP_BERLIN_GROUP_1_3.version}/accounts/${x.id}/balances") 
          :: Nil
        )
       )
    )
  }

  def createAccountBalanceJSON(bankAccount: BankAccount, transactionRequests: List[TransactionRequest]) = {
    // get the latest end_date of `COMPLETED` transactionRequests
    val latestCompletedEndDate = transactionRequests.sortBy(_.end_date).reverse.filter(_.status == "COMPLETED").map(_.end_date).headOption.getOrElse(null)

    //get the latest end_date of !`COMPLETED` transactionRequests
    val latestUncompletedEndDate = transactionRequests.sortBy(_.end_date).reverse.filter(_.status != "COMPLETED").map(_.end_date).headOption.getOrElse(null)

    // get the SUM of the amount of all !`COMPLETED` transactionRequests
    val sumOfAllUncompletedTransactionrequests = transactionRequests.filter(_.status != "COMPLETED").map(_.body.value.amount).map(BigDecimal(_)).sum
    // sum of the unCompletedTransactions and the account.balance is the current expectd amount:
    val sumOfAll = (bankAccount.balance+ sumOfAllUncompletedTransactionrequests).toString()

    AccountBalancesV13(
      account = BalanceAccount(
        currency = APIUtil.stringOrNull(bankAccount.currency),
        iban = APIUtil.stringOptionOrNull(bankAccount.iban)
      ),
      `balances` = AccountBalance(
        amount = AmountOfMoneyV13(
          currency = APIUtil.stringOrNull(bankAccount.currency),
          content = bankAccount.balance.toString()
        ),
        balanceType = APIUtil.stringOrNull(bankAccount.accountType),
        lastChangeDateTime = if(latestCompletedEndDate == null) null else APIUtil.DateWithDayFormat.format(latestCompletedEndDate),
        lastCommittedTransaction = if(latestUncompletedEndDate == null) null else latestUncompletedEndDate.toString
      ) :: Nil
    ) 
  }
  
  def createTransactionJSON(transaction : ModeratedTransaction) : TransactionJsonV13 = {
    TransactionJsonV13(
      transactionId = transaction.id.value,
      creditorName = "",
      creditorAccount = CreditorAccountJson(iban = APIUtil.stringOptionOrNull(transaction.bankAccount.get.iban)),
      transactionAmount = AmountOfMoneyV13(APIUtil.stringOptionOrNull(transaction.currency), transaction.amount.get.toString()),
      bookingDate = transaction.startDate.get,
      valueDate = transaction.finishDate.get,
      remittanceInformationUnstructured = APIUtil.stringOptionOrNull(transaction.description)
    )
  }
  
  def createTransactionFromRequestJSON(transactionRequest : TransactionRequest) : TransactionJsonV13 = {
    TransactionJsonV13(
      transactionId = transactionRequest.id.value,
      creditorName = transactionRequest.name,
      creditorAccount = CreditorAccountJson(iban = transactionRequest.from.account_id),
      transactionAmount = AmountOfMoneyV13(transactionRequest.charge.value.currency, transactionRequest.charge.value.amount),
      bookingDate = transactionRequest.start_date,
      valueDate = transactionRequest.end_date,
      remittanceInformationUnstructured = transactionRequest.body.description
    )
  }
  
  def createTransactionsJson(transactions: List[ModeratedTransaction], transactionRequests: List[TransactionRequest]) : TransactionsJsonV13 = {
    val ids = transactions.map(_.bankAccount.map(_.accountId.value)).flatten
    val accountIbans = transactions.map(_.bankAccount.map(_.iban.getOrElse(""))).flatten
    val accontCurrencys = transactions.map(_.bankAccount.map(_.currency.getOrElse(""))).flatten
    val accontBalances = transactions.map(_.bankAccount.map(_.balance)).flatten
    
    val accontId = if (ids.isEmpty) "" else ids.head
    val accountIban = if (accountIbans.isEmpty) "" else accountIbans.head
    val accountCurrency = if (accontCurrencys.isEmpty) "" else accontCurrencys.head
    val accontBalance = if (accontBalances.isEmpty) "" else accontBalances.head
    
    
    TransactionsJsonV13(
      BalanceAccount(currency = accountCurrency, iban = accountIban),
      TransactionsV13Transactions(
        booked= transactions.map(createTransactionJSON),
        pending = transactionRequests.filter(_.status!="COMPLETED").map(createTransactionFromRequestJSON),
        _links = TransactionsV13TransactionsLinks(s"/v1/accounts/$accontId")
      ),
      balances = List(TransactionsJsonV13Balance(balanceAmount = AmountOfMoneyV13(accountCurrency, accontBalance))),
      _links = TransactionsLinksV13(s"/v1/accounts/$accontId")
    )
  }

  def createPostConsentResponseJson(createdConsent: Consent) : PostConsentResponseJson = {
    PostConsentResponseJson(
      consentId = createdConsent.consentId,
      consentStatus =createdConsent.status,
      _links= ConsentLinksV13(s"v1/consents/${createdConsent.consentId}/authorisations")
    )
  }

  def createGetConsentResponseJson(createdConsent: Consent) : GetConsentResponseJson = {
    GetConsentResponseJson(
      access = ConsentAccessJson(),
      recurringIndicator = createdConsent.recurringIndicator,
      validUntil = new SimpleDateFormat(DateWithDay).format(createdConsent.validUntil), 
      frequencyPerDay = createdConsent.frequencyPerDay,
      combinedServiceIndicator= createdConsent.combinedServiceIndicator,
      lastActionDate= new SimpleDateFormat(DateWithDay).format(createdConsent.lastActionDate),
      consentStatus= createdConsent.status
    )
  }

  def createStartConsentAuthorisationJson(consent: Consent) : StartConsentAuthorisationJson = {
    StartConsentAuthorisationJson(
      scaStatus = consent.status,
      pushMessage = "started", //TODO Not implment how to fill this.
      _links =  ScaStatusJsonV13("/v1.3/payments/sepa-credit-transfers/1234-wertiq-98")//TODO, Not sure, what is this for??
    )
  }

  def createTransactionRequestJson(tr : TransactionRequest) : InitiatePaymentResponseJson = {
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
    val transactionId = tr.id.value
    InitiatePaymentResponseJson(
      transactionStatus = "RCVD", //TODO hardcode this first, there are 14 different status in BerlinGroup. 
      paymentId = transactionId,
      _links = InitiatePaymentResponseLinks(
        scaRedirect = LinkHrefJson("answer transaction request url"),
        self = LinkHrefJson(s"/v1/payments/sepa-credit-transfers/$transactionId"),
        status = LinkHrefJson(s"/v1/payments/$transactionId/status"),
        scaStatus = LinkHrefJson(s"/v1/payments/$transactionId/authorisations/${transactionId}xx")
      )
    )
  }
  
}
