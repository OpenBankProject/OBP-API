package code.api.berlin.group.v2

import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable

object JSONFactory_BERLIN_GROUP_v2 extends CustomJsonFormats with MdcLoggable {

  // ── Common types ──────────────────────────────────────────────────────
  case class AmountV2(currency: String, amount: String)
  case class LinkHrefV2(href: String)

  // ── Account reference (shared) ────────────────────────────────────────
  case class AccountReferenceV2(iban: String, currency: Option[String])

  // ── AIS Account types ─────────────────────────────────────────────────
  case class AccountLinksV2(
    balances: Option[LinkHrefV2],
    transactions: Option[LinkHrefV2]
  )
  case class AccountJsonV2(
    resourceId: String,
    iban: String,
    currency: String,
    name: Option[String],
    product: String,
    cashAccountType: String,
    balances: Option[List[BalanceJsonV2]],
    _links: AccountLinksV2
  )
  case class AccountListJsonV2(accounts: List[AccountJsonV2])

  // ── AIS Balance types ─────────────────────────────────────────────────
  case class BalanceJsonV2(
    balanceAmount: AmountV2,
    balanceType: String,
    lastChangeDateTime: Option[String],
    referenceDate: Option[String]
  )
  case class BalanceResponseV2(
    account: AccountReferenceV2,
    balances: List[BalanceJsonV2]
  )

  // ── AIS Transaction types ─────────────────────────────────────────────
  case class TransactionJsonV2(
    transactionId: String,
    transactionAmount: AmountV2,
    bookingDate: String,
    valueDate: String,
    remittanceInformationUnstructured: Option[String]
  )
  case class TransactionListJsonV2(
    booked: List[TransactionJsonV2],
    pending: List[TransactionJsonV2]
  )
  case class TransactionsResponseV2(
    account: AccountReferenceV2,
    transactions: TransactionListJsonV2
  )

  // ── Card Account types ────────────────────────────────────────────────
  case class CardAccountJsonV2(
    resourceId: String,
    maskedPan: String,
    currency: String,
    name: Option[String],
    product: String,
    cashAccountType: String,
    balances: Option[List[BalanceJsonV2]],
    _links: AccountLinksV2
  )
  case class CardAccountListJsonV2(cardAccounts: List[CardAccountJsonV2])

  // ── PIS types ─────────────────────────────────────────────────────────
  case class PaymentLinksV2(
    self: LinkHrefV2,
    status: LinkHrefV2,
    scaStatus: Option[LinkHrefV2]
  )
  case class PaymentInitiationResponseV2(
    transactionStatus: String,
    paymentId: String,
    _links: PaymentLinksV2
  )
  case class PaymentStatusResponseV2(transactionStatus: String)
  case class PaymentDetailsResponseV2(
    transactionStatus: String,
    paymentId: String,
    debtorAccount: AccountReferenceV2,
    instructedAmount: AmountV2,
    creditorAccount: AccountReferenceV2,
    creditorName: String
  )
  case class BulkPaymentExtendedStatusResponseV2(
    transactionStatus: String,
    paymentId: String,
    fundsAvailable: Boolean
  )

  // ── PIIS types ────────────────────────────────────────────────────────
  case class FundsConfirmationResponseV2(fundsAvailable: Boolean)

  // ── Authorisation types ───────────────────────────────────────────────
  case class AuthLinksV2(scaStatus: LinkHrefV2)
  case class AuthorisationResponseV2(
    authorisationId: String,
    scaStatus: String,
    _links: AuthLinksV2
  )
  case class AuthorisationSubResourcesResponseV2(authorisationIds: List[String])
  case class AuthorisationStatusResponseV2(scaStatus: String)
  case class UpdatePsuDataResponseV2(
    scaStatus: String,
    _links: AuthLinksV2
  )
  case class UpdateDebtorAccountResponseV2(
    transactionStatus: String,
    debtorAccount: AccountReferenceV2
  )

  // ── Mocked data constants ─────────────────────────────────────────────
  private val mockAccountId = "3dc3d5b3-7023-4848-9853-f5400a64e80f"
  private val mockIban = "DE2310010010123456789"
  private val mockCurrency = "EUR"
  private val mockPaymentId = "1234-wertiq-983"
  private val mockAuthorisationId = "a9b3e214-5c72-4b1e-bf4d-eb1c0e306c5d"

  // ── AIS mock factory methods ──────────────────────────────────────────

  def mockAccountList: AccountListJsonV2 = {
    logger.debug("mockAccountList called")
    val account = AccountJsonV2(
      resourceId = mockAccountId,
      iban = mockIban,
      currency = mockCurrency,
      name = Some("Main Account"),
      product = "Girokonto",
      cashAccountType = "CACC",
      balances = None,
      _links = AccountLinksV2(
        balances = Some(LinkHrefV2(s"/v2/accounts/$mockAccountId/balances")),
        transactions = Some(LinkHrefV2(s"/v2/accounts/$mockAccountId/transactions"))
      )
    )
    AccountListJsonV2(accounts = List(account))
  }

  def mockAccountDetails(accountId: String): AccountJsonV2 = {
    logger.debug(s"mockAccountDetails called with accountId=$accountId")
    AccountJsonV2(
      resourceId = accountId,
      iban = mockIban,
      currency = mockCurrency,
      name = Some("Main Account"),
      product = "Girokonto",
      cashAccountType = "CACC",
      balances = Some(List(
        BalanceJsonV2(
          balanceAmount = AmountV2(mockCurrency, "500.00"),
          balanceType = "closingBooked",
          lastChangeDateTime = Some("2024-01-15T10:30:00Z"),
          referenceDate = Some("2024-01-15")
        )
      )),
      _links = AccountLinksV2(
        balances = Some(LinkHrefV2(s"/v2/accounts/$accountId/balances")),
        transactions = Some(LinkHrefV2(s"/v2/accounts/$accountId/transactions"))
      )
    )
  }

  def mockBalances(accountId: String): BalanceResponseV2 = {
    logger.debug(s"mockBalances called with accountId=$accountId")
    BalanceResponseV2(
      account = AccountReferenceV2(iban = mockIban, currency = Some(mockCurrency)),
      balances = List(
        BalanceJsonV2(
          balanceAmount = AmountV2(mockCurrency, "500.00"),
          balanceType = "closingBooked",
          lastChangeDateTime = Some("2024-01-15T10:30:00Z"),
          referenceDate = Some("2024-01-15")
        ),
        BalanceJsonV2(
          balanceAmount = AmountV2(mockCurrency, "520.00"),
          balanceType = "expected",
          lastChangeDateTime = Some("2024-01-15T10:30:00Z"),
          referenceDate = Some("2024-01-15")
        )
      )
    )
  }

  def mockTransactions(accountId: String): TransactionsResponseV2 = {
    logger.debug(s"mockTransactions called with accountId=$accountId")
    TransactionsResponseV2(
      account = AccountReferenceV2(iban = mockIban, currency = Some(mockCurrency)),
      transactions = TransactionListJsonV2(
        booked = List(
          TransactionJsonV2(
            transactionId = "1234567",
            transactionAmount = AmountV2(mockCurrency, "-36.50"),
            bookingDate = "2024-01-14",
            valueDate = "2024-01-14",
            remittanceInformationUnstructured = Some("Rent January")
          ),
          TransactionJsonV2(
            transactionId = "1234568",
            transactionAmount = AmountV2(mockCurrency, "100.00"),
            bookingDate = "2024-01-15",
            valueDate = "2024-01-15",
            remittanceInformationUnstructured = Some("Salary")
          )
        ),
        pending = List(
          TransactionJsonV2(
            transactionId = "1234569",
            transactionAmount = AmountV2(mockCurrency, "-10.00"),
            bookingDate = "2024-01-16",
            valueDate = "2024-01-16",
            remittanceInformationUnstructured = Some("Online Purchase")
          )
        )
      )
    )
  }

  def mockTransactionDetails(accountId: String, transactionId: String): TransactionJsonV2 = {
    logger.debug(s"mockTransactionDetails called with accountId=$accountId, transactionId=$transactionId")
    TransactionJsonV2(
      transactionId = transactionId,
      transactionAmount = AmountV2(mockCurrency, "-36.50"),
      bookingDate = "2024-01-14",
      valueDate = "2024-01-14",
      remittanceInformationUnstructured = Some("Rent January")
    )
  }

  // ── Card Account mock factory methods ─────────────────────────────────

  def mockCardAccountList: CardAccountListJsonV2 = {
    logger.debug("mockCardAccountList called")
    val cardAccount = CardAccountJsonV2(
      resourceId = mockAccountId,
      maskedPan = "525412******3241",
      currency = mockCurrency,
      name = Some("Main Card Account"),
      product = "Credit Card",
      cashAccountType = "CARD",
      balances = None,
      _links = AccountLinksV2(
        balances = Some(LinkHrefV2(s"/v2/card-accounts/$mockAccountId/balances")),
        transactions = Some(LinkHrefV2(s"/v2/card-accounts/$mockAccountId/transactions"))
      )
    )
    CardAccountListJsonV2(cardAccounts = List(cardAccount))
  }

  def mockCardAccountDetails(accountId: String): CardAccountJsonV2 = {
    logger.debug(s"mockCardAccountDetails called with accountId=$accountId")
    CardAccountJsonV2(
      resourceId = accountId,
      maskedPan = "525412******3241",
      currency = mockCurrency,
      name = Some("Main Card Account"),
      product = "Credit Card",
      cashAccountType = "CARD",
      balances = Some(List(
        BalanceJsonV2(
          balanceAmount = AmountV2(mockCurrency, "1500.00"),
          balanceType = "closingBooked",
          lastChangeDateTime = Some("2024-01-15T10:30:00Z"),
          referenceDate = Some("2024-01-15")
        )
      )),
      _links = AccountLinksV2(
        balances = Some(LinkHrefV2(s"/v2/card-accounts/$accountId/balances")),
        transactions = Some(LinkHrefV2(s"/v2/card-accounts/$accountId/transactions"))
      )
    )
  }

  def mockCardAccountBalances(accountId: String): BalanceResponseV2 = {
    logger.debug(s"mockCardAccountBalances called with accountId=$accountId")
    BalanceResponseV2(
      account = AccountReferenceV2(iban = mockIban, currency = Some(mockCurrency)),
      balances = List(
        BalanceJsonV2(
          balanceAmount = AmountV2(mockCurrency, "1500.00"),
          balanceType = "closingBooked",
          lastChangeDateTime = Some("2024-01-15T10:30:00Z"),
          referenceDate = Some("2024-01-15")
        )
      )
    )
  }

  def mockCardAccountTransactions(accountId: String): TransactionsResponseV2 = {
    logger.debug(s"mockCardAccountTransactions called with accountId=$accountId")
    TransactionsResponseV2(
      account = AccountReferenceV2(iban = mockIban, currency = Some(mockCurrency)),
      transactions = TransactionListJsonV2(
        booked = List(
          TransactionJsonV2(
            transactionId = "card-tx-001",
            transactionAmount = AmountV2(mockCurrency, "-25.00"),
            bookingDate = "2024-01-14",
            valueDate = "2024-01-14",
            remittanceInformationUnstructured = Some("Card Purchase - Store")
          )
        ),
        pending = List.empty
      )
    )
  }

  // ── PIS mock factory methods ──────────────────────────────────────────

  def mockPaymentInitiation(paymentProduct: String): PaymentInitiationResponseV2 = {
    logger.debug(s"mockPaymentInitiation called with paymentProduct=$paymentProduct")
    PaymentInitiationResponseV2(
      transactionStatus = "RCVD",
      paymentId = mockPaymentId,
      _links = PaymentLinksV2(
        self = LinkHrefV2(s"/v2/payments/$paymentProduct/$mockPaymentId"),
        status = LinkHrefV2(s"/v2/payments/$paymentProduct/$mockPaymentId/status"),
        scaStatus = Some(LinkHrefV2(s"/v2/payments/$paymentProduct/$mockPaymentId/authorisations/$mockAuthorisationId"))
      )
    )
  }

  def mockPaymentStatus: PaymentStatusResponseV2 = {
    logger.debug("mockPaymentStatus called")
    PaymentStatusResponseV2(transactionStatus = "ACCP")
  }

  def mockPaymentDetails(paymentService: String, paymentProduct: String, paymentId: String): PaymentDetailsResponseV2 = {
    logger.debug(s"mockPaymentDetails called with paymentService=$paymentService, paymentProduct=$paymentProduct, paymentId=$paymentId")
    PaymentDetailsResponseV2(
      transactionStatus = "ACCP",
      paymentId = paymentId,
      debtorAccount = AccountReferenceV2(iban = mockIban, currency = Some(mockCurrency)),
      instructedAmount = AmountV2(mockCurrency, "123.50"),
      creditorAccount = AccountReferenceV2(iban = "DE75512108001245126199", currency = Some(mockCurrency)),
      creditorName = "Merchant AG"
    )
  }

  def mockBulkPaymentExtendedStatus(paymentProduct: String, paymentId: String): BulkPaymentExtendedStatusResponseV2 = {
    logger.debug(s"mockBulkPaymentExtendedStatus called with paymentProduct=$paymentProduct, paymentId=$paymentId")
    BulkPaymentExtendedStatusResponseV2(
      transactionStatus = "ACCP",
      paymentId = paymentId,
      fundsAvailable = true
    )
  }

  // ── PIIS mock factory methods ─────────────────────────────────────────

  def mockFundsConfirmation: FundsConfirmationResponseV2 = {
    logger.debug("mockFundsConfirmation called")
    FundsConfirmationResponseV2(fundsAvailable = true)
  }

  // ── Authorisation mock factory methods ────────────────────────────────

  def mockAuthorisationStart(resourcePath: String, resourceId: String): AuthorisationResponseV2 = {
    logger.debug(s"mockAuthorisationStart called with resourcePath=$resourcePath, resourceId=$resourceId")
    AuthorisationResponseV2(
      authorisationId = mockAuthorisationId,
      scaStatus = "received",
      _links = AuthLinksV2(
        scaStatus = LinkHrefV2(s"/v2/$resourcePath/$resourceId/authorisations/$mockAuthorisationId")
      )
    )
  }

  def mockAuthorisationSubResources(resourcePath: String, resourceId: String): AuthorisationSubResourcesResponseV2 = {
    logger.debug(s"mockAuthorisationSubResources called with resourcePath=$resourcePath, resourceId=$resourceId")
    AuthorisationSubResourcesResponseV2(
      authorisationIds = List(mockAuthorisationId)
    )
  }

  def mockAuthorisationStatus(authorisationId: String): AuthorisationStatusResponseV2 = {
    logger.debug(s"mockAuthorisationStatus called with authorisationId=$authorisationId")
    AuthorisationStatusResponseV2(scaStatus = "finalised")
  }

  def mockUpdatePsuData(authorisationId: String): UpdatePsuDataResponseV2 = {
    logger.debug(s"mockUpdatePsuData called with authorisationId=$authorisationId")
    UpdatePsuDataResponseV2(
      scaStatus = "psuAuthenticated",
      _links = AuthLinksV2(
        scaStatus = LinkHrefV2(s"/v2/payments/sepa-credit-transfers/$mockPaymentId/authorisations/$authorisationId")
      )
    )
  }

  def mockUpdateDebtorAccount(resourceId: String): UpdateDebtorAccountResponseV2 = {
    logger.debug(s"mockUpdateDebtorAccount called with resourceId=$resourceId")
    UpdateDebtorAccountResponseV2(
      transactionStatus = "ACTC",
      debtorAccount = AccountReferenceV2(iban = mockIban, currency = Some(mockCurrency))
    )
  }
}
