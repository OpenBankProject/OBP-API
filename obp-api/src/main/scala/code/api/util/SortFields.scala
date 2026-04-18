package code.api.util

/**
 * Global whitelist of field names accepted by the `sort_by` query parameter.
 *
 * Constants are spelled to match the wire value — `USER_ID` holds the string
 * "user_id" — so `SortFields.USER_ID` reads naturally as "the user_id sort field".
 *
 * `All` is the union used by the central validator in
 * `APIUtil.getHttpParamValuesByName`. Endpoints may narrow further via their
 * own per-endpoint whitelists.
 */
object SortFields {

  // Temporal
  val CREATED_DATE = "created_date"
  val UPDATED_DATE = "updated_date"
  val DATE         = "date"

  // Identity
  val USER_ID         = "user_id"
  val CONSUMER_ID     = "consumer_id"
  val BANK_ID         = "bank_id"
  val ACCOUNT_ID      = "account_id"
  val CUSTOMER_ID     = "customer_id"
  val CARD_ID         = "card_id"
  val TRANSACTION_ID  = "transaction_id"
  val COUNTERPARTY_ID = "counterparty_id"
  val CONSENT_ID      = "consent_id"
  val CORRELATION_ID  = "correlation_id"

  // Descriptive
  val USERNAME        = "username"
  val EMAIL           = "email"
  val PROVIDER        = "provider"
  val NAME            = "name"
  val SHORT_NAME      = "short_name"
  val FULL_NAME       = "full_name"
  val LABEL           = "label"
  val STATUS          = "status"
  val APP_NAME        = "app_name"
  val DEVELOPER_EMAIL = "developer_email"

  // Financial
  val AMOUNT   = "amount"
  val CURRENCY = "currency"
  val BALANCE  = "balance"

  // Request / metrics
  val URL                             = "url"
  val VERB                            = "verb"
  val HTTP_STATUS_CODE                = "http_status_code"
  val DURATION                        = "duration"
  val IMPLEMENTED_BY_PARTIAL_FUNCTION = "implemented_by_partial_function"
  val IMPLEMENTED_IN_VERSION          = "implemented_in_version"

  val All: Set[String] = Set(
    CREATED_DATE, UPDATED_DATE, DATE,
    USER_ID, CONSUMER_ID, BANK_ID, ACCOUNT_ID, CUSTOMER_ID, CARD_ID,
    TRANSACTION_ID, COUNTERPARTY_ID, CONSENT_ID, CORRELATION_ID,
    USERNAME, EMAIL, PROVIDER, NAME, SHORT_NAME, FULL_NAME, LABEL, STATUS,
    APP_NAME, DEVELOPER_EMAIL,
    AMOUNT, CURRENCY, BALANCE,
    URL, VERB, HTTP_STATUS_CODE, DURATION,
    IMPLEMENTED_BY_PARTIAL_FUNCTION, IMPLEMENTED_IN_VERSION
  )
}
