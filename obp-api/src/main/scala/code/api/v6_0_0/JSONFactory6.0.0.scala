/** Open Bank Project - API Copyright (C) 2011-2019, TESOBE GmbH * This program
  * is free software: you can redistribute it and/or modify it under the terms
  * of the GNU Affero General Public License as published by the Free Software
  * Foundation, either version 3 of the License, or (at your option) any later
  * version. * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
  * General Public License for more details. * You should have received a copy
  * of the GNU Affero General Public License along with this program. If not,
  * see <http://www.gnu.org/licenses/>. * Email: contact@tesobe.com TESOBE GmbH
  * Osloerstrasse 16/17 Berlin 13359, Germany * This product includes software
  * developed at TESOBE (http://www.tesobe.com/)
  */
package code.api.v6_0_0

import code.api.util.APIUtil.stringOrNull
import code.api.util.RateLimitingPeriod.LimitCallPeriod
import code.api.util._
import code.api.v1_2_1.BankRoutingJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.CustomerFaceImageJson
import code.api.v2_0_0.{EntitlementJSONs, JSONFactory200}
import code.api.v2_1_0.CustomerCreditRatingJSON
import code.api.v3_0_0.{
  CustomerAttributeResponseJsonV300,
  UserJsonV300,
  ViewJSON300,
  ViewsJSON300
}
import code.api.v3_1_0.{RateLimit, RedisCallLimitJson}
import code.api.v4_0_0.{BankAttributeBankResponseJsonV400, UserAgreementJson}
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.model.dataAccess.ResourceUser
import code.users.UserAgreement
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{
  AmountOfMoneyJsonV121,
  CustomerAttribute,
  _
}
import net.liftweb.common.Box

import java.util.Date

case class CardanoPaymentJsonV600(
    address: String,
    amount: CardanoAmountJsonV600,
    assets: Option[List[CardanoAssetJsonV600]] = None
)

case class CardanoAmountJsonV600(
    quantity: Long,
    unit: String // "lovelace"
)

case class CardanoAssetJsonV600(
    policy_id: String,
    asset_name: String,
    quantity: Long
)

case class CardanoMetadataStringJsonV600(
    string: String
)

case class TokenJSON(
    token: String
)

case class CurrentConsumerJsonV600(
    app_name: String,
    app_type: String,
    description: String,
    consumer_id: String,
    active_rate_limits: ActiveRateLimitsJsonV600,
    call_counters: RedisCallCountersJsonV600
)

case class CallLimitPostJsonV600(
    from_date: java.util.Date,
    to_date: java.util.Date,
    api_version: Option[String] = None,
    api_name: Option[String] = None,
    bank_id: Option[String] = None,
    per_second_call_limit: String,
    per_minute_call_limit: String,
    per_hour_call_limit: String,
    per_day_call_limit: String,
    per_week_call_limit: String,
    per_month_call_limit: String
)

case class CallLimitJsonV600(
    rate_limiting_id: String,
    from_date: java.util.Date,
    to_date: java.util.Date,
    api_version: Option[String],
    api_name: Option[String],
    bank_id: Option[String],
    per_second_call_limit: String,
    per_minute_call_limit: String,
    per_hour_call_limit: String,
    per_day_call_limit: String,
    per_week_call_limit: String,
    per_month_call_limit: String,
    created_at: java.util.Date,
    updated_at: java.util.Date
)

case class ActiveRateLimitsJsonV600(
    considered_rate_limit_ids: List[String],
    active_at_date: java.util.Date,
    active_per_second_rate_limit: Long,
    active_per_minute_rate_limit: Long,
    active_per_hour_rate_limit: Long,
    active_per_day_rate_limit: Long,
    active_per_week_rate_limit: Long,
    active_per_month_rate_limit: Long
)

case class RateLimitV600(
    calls_made: Option[Long],
    reset_in_seconds: Option[Long],
    status: String
)

case class RedisCallCountersJsonV600(
    per_second: RateLimitV600,
    per_minute: RateLimitV600,
    per_hour: RateLimitV600,
    per_day: RateLimitV600,
    per_week: RateLimitV600,
    per_month: RateLimitV600
)

case class TransactionRequestBodyCardanoJsonV600(
    to: CardanoPaymentJsonV600,
    value: AmountOfMoneyJsonV121,
    passphrase: String,
    description: String,
    metadata: Option[Map[String, CardanoMetadataStringJsonV600]] = None
) extends TransactionRequestCommonBodyJSON

// ---------------- Ethereum models (V600) ----------------
case class TransactionRequestBodyEthereumJsonV600(
    params: Option[String] = None, // This is for eth_sendRawTransaction
    to: String, // this is for eth_sendTransaction eg: 0x addressk
    value: AmountOfMoneyJsonV121, // currency should be "ETH"; amount string (decimal)
    description: String
) extends TransactionRequestCommonBodyJSON

// This is only for the request JSON body; we will construct `TransactionRequestBodyEthereumJsonV600` for OBP.
case class TransactionRequestBodyEthSendRawTransactionJsonV600(
    params: String, // eth_sendRawTransaction params field.
    description: String
)

// ---------------- HOLD models (V600) ----------------
case class TransactionRequestBodyHoldJsonV600(
    value: AmountOfMoneyJsonV121,
    description: String
) extends TransactionRequestCommonBodyJSON

case class UserJsonV600(
    user_id: String,
    email: String,
    provider_id: String,
    provider: String,
    username: String,
    entitlements: EntitlementJSONs,
    views: Option[ViewsJSON300],
    on_behalf_of: Option[UserJsonV300]
)

case class UserV600(
    user: User,
    entitlements: List[Entitlement],
    views: Option[Permission]
)
case class UsersJsonV600(current_user: UserV600, on_behalf_of_user: UserV600)

case class UserInfoJsonV600(
    user_id: String,
    email: String,
    provider_id: String,
    provider: String,
    username: String,
    entitlements: EntitlementJSONs,
    views: Option[ViewsJSON300],
    agreements: Option[List[UserAgreementJson]],
    is_deleted: Boolean,
    last_marketing_agreement_signed_date: Option[Date],
    is_locked: Boolean,
    last_activity_date: Option[Date],
    recent_operation_ids: List[String]
)

case class UsersInfoJsonV600(users: List[UserInfoJsonV600])

case class CreateUserJsonV600(
    email: String,
    username: String,
    password: String,
    first_name: String,
    last_name: String,
    validating_application: Option[String] = None
)

case class MigrationScriptLogJsonV600(
    migration_script_log_id: String,
    name: String,
    commit_id: String,
    is_successful: Boolean,
    start_date: Long,
    end_date: Long,
    duration_in_ms: Long,
    remark: String,
    created_at: Date,
    updated_at: Date
)

case class MigrationScriptLogsJsonV600(
    migration_script_logs: List[MigrationScriptLogJsonV600]
)

case class PostBankJson600(
    bank_id: String,
    bank_code: String,
    full_name: Option[String],
    logo: Option[String],
    website: Option[String],
    bank_routings: Option[List[BankRoutingJsonV121]]
)

case class BankJson600(
    bank_id: String,
    bank_code: String,
    full_name: String,
    logo: String,
    website: String,
    bank_routings: List[BankRoutingJsonV121],
    attributes: Option[List[BankAttributeBankResponseJsonV400]]
)

case class ProvidersJsonV600(providers: List[String])

case class ConnectorMethodNamesJsonV600(connector_method_names: List[String])

case class CacheNamespaceJsonV600(
    prefix: String,
    description: String,
    ttl_seconds: String,
    category: String,
    key_count: Int,
    example_key: String
)

case class CacheNamespacesJsonV600(namespaces: List[CacheNamespaceJsonV600])

case class InvalidateCacheNamespaceJsonV600(
    namespace_id: String
)

case class InvalidatedCacheNamespaceJsonV600(
    namespace_id: String,
    old_version: Long,
    new_version: Long,
    status: String
)

case class RedisCacheStatusJsonV600(
    available: Boolean,
    url: String,
    port: Int,
    use_ssl: Boolean
)

case class InMemoryCacheStatusJsonV600(
    available: Boolean,
    current_size: Long
)

case class CacheConfigJsonV600(
    redis_status: RedisCacheStatusJsonV600,
    in_memory_status: InMemoryCacheStatusJsonV600,
    instance_id: String,
    environment: String,
    global_prefix: String
)

case class CacheNamespaceInfoJsonV600(
    namespace_id: String,
    prefix: String,
    current_version: Long,
    key_count: Int,
    description: String,
    category: String,
    storage_location: String,
    ttl_info: String
)

case class CacheInfoJsonV600(
    namespaces: List[CacheNamespaceInfoJsonV600],
    total_keys: Int,
    redis_available: Boolean
)

case class PostCustomerJsonV600(
    legal_name: String,
    customer_number: Option[String] = None,
    mobile_phone_number: String,
    email: Option[String] = None,
    face_image: Option[CustomerFaceImageJson] = None,
    date_of_birth: Option[String] = None, // YYYY-MM-DD format
    relationship_status: Option[String] = None,
    dependants: Option[Int] = None,
    dob_of_dependants: Option[List[String]] = None, // YYYY-MM-DD format
    credit_rating: Option[CustomerCreditRatingJSON] = None,
    credit_limit: Option[AmountOfMoneyJsonV121] = None,
    highest_education_attained: Option[String] = None,
    employment_status: Option[String] = None,
    kyc_status: Option[Boolean] = None,
    last_ok_date: Option[Date] = None,
    title: Option[String] = None,
    branch_id: Option[String] = None,
    name_suffix: Option[String] = None
)

case class CustomerJsonV600(
    bank_id: String,
    customer_id: String,
    customer_number: String,
    legal_name: String,
    mobile_phone_number: String,
    email: String,
    face_image: CustomerFaceImageJson,
    date_of_birth: String, // YYYY-MM-DD format
    relationship_status: String,
    dependants: Integer,
    dob_of_dependants: List[String], // YYYY-MM-DD format
    credit_rating: Option[CustomerCreditRatingJSON],
    credit_limit: Option[AmountOfMoneyJsonV121],
    highest_education_attained: String,
    employment_status: String,
    kyc_status: java.lang.Boolean,
    last_ok_date: Date,
    title: String,
    branch_id: String,
    name_suffix: String
)

case class CustomerJSONsV600(customers: List[CustomerJsonV600])

case class CustomerWithAttributesJsonV600(
    bank_id: String,
    customer_id: String,
    customer_number: String,
    legal_name: String,
    mobile_phone_number: String,
    email: String,
    face_image: CustomerFaceImageJson,
    date_of_birth: String, // YYYY-MM-DD format
    relationship_status: String,
    dependants: Integer,
    dob_of_dependants: List[String], // YYYY-MM-DD format
    credit_rating: Option[CustomerCreditRatingJSON],
    credit_limit: Option[AmountOfMoneyJsonV121],
    highest_education_attained: String,
    employment_status: String,
    kyc_status: java.lang.Boolean,
    last_ok_date: Date,
    title: String,
    branch_id: String,
    name_suffix: String,
    customer_attributes: List[CustomerAttributeResponseJsonV300]
)

// ABAC Rule JSON models
case class CreateAbacRuleJsonV600(
    rule_name: String,
    rule_code: String,
    description: String,
    policy: String,
    is_active: Boolean
)

case class UpdateAbacRuleJsonV600(
    rule_name: String,
    rule_code: String,
    description: String,
    policy: String,
    is_active: Boolean
)

case class AbacRuleJsonV600(
    abac_rule_id: String,
    rule_name: String,
    rule_code: String,
    is_active: Boolean,
    description: String,
    policy: String,
    created_by_user_id: String,
    updated_by_user_id: String
)

case class AbacRulesJsonV600(abac_rules: List[AbacRuleJsonV600])

case class ExecuteAbacRuleJsonV600(
    authenticated_user_id: Option[String],
    on_behalf_of_user_id: Option[String],
    user_id: Option[String],
    bank_id: Option[String],
    account_id: Option[String],
    view_id: Option[String],
    transaction_request_id: Option[String],
    transaction_id: Option[String],
    customer_id: Option[String]
)

case class AbacRuleResultJsonV600(
    result: Boolean
)

case class ValidateAbacRuleJsonV600(
    rule_code: String
)

case class ValidateAbacRuleSuccessJsonV600(
    valid: Boolean,
    message: String
)

case class ValidateAbacRuleErrorDetailsJsonV600(
    error_type: String
)

case class ValidateAbacRuleFailureJsonV600(
    valid: Boolean,
    error: String,
    message: String,
    details: ValidateAbacRuleErrorDetailsJsonV600
)

case class AbacParameterJsonV600(
    name: String,
    `type`: String,
    description: String,
    required: Boolean,
    category: String
)

case class AbacObjectPropertyJsonV600(
    name: String,
    `type`: String,
    description: String
)

case class AbacObjectTypeJsonV600(
    name: String,
    description: String,
    properties: List[AbacObjectPropertyJsonV600]
)

case class AbacRuleExampleJsonV600(
    rule_name: String,
    rule_code: String,
    description: String,
    policy: String,
    is_active: Boolean
)

case class AbacRuleSchemaJsonV600(
    parameters: List[AbacParameterJsonV600],
    object_types: List[AbacObjectTypeJsonV600],
    examples: List[AbacRuleExampleJsonV600],
    available_operators: List[String],
    notes: List[String]
)

case class AbacPolicyJsonV600(
    policy: String,
    description: String
)

case class AbacPoliciesJsonV600(
    policies: List[AbacPolicyJsonV600]
)

object JSONFactory600 extends CustomJsonFormats with MdcLoggable {

  def createRedisCallCountersJson(
    // Convert list to map for easy lookup by period
      rateLimits: List[((Option[Long], Option[Long], String), LimitCallPeriod)]
  ): RedisCallCountersJsonV600 = {
    val grouped: Map[LimitCallPeriod, (Option[Long], Option[Long], String)] =
      rateLimits.map { case (limits, period) => period -> limits }.toMap

    def getCallCounterForPeriod(period: RateLimitingPeriod.Value): RateLimitV600 =
      grouped.get(period) match {
        // Use status calculated by RateLimitingUtil (ACTIVE, NO_COUNTER, EXPIRED, REDIS_UNAVAILABLE)
        case Some((calls, ttl, status)) =>
          RateLimitV600(calls, ttl, status)
        case _ =>
          RateLimitV600(None, None, "DATA_MISSING")
      }

    RedisCallCountersJsonV600(
      getCallCounterForPeriod(RateLimitingPeriod.PER_SECOND),
      getCallCounterForPeriod(RateLimitingPeriod.PER_MINUTE),
      getCallCounterForPeriod(RateLimitingPeriod.PER_HOUR),
      getCallCounterForPeriod(RateLimitingPeriod.PER_DAY),
      getCallCounterForPeriod(RateLimitingPeriod.PER_WEEK),
      getCallCounterForPeriod(RateLimitingPeriod.PER_MONTH)
    )
  }

  def createUserInfoJSON(
      current_user: UserV600,
      onBehalfOfUser: Option[UserV600]
  ): UserJsonV600 = {
    UserJsonV600(
      user_id = current_user.user.userId,
      email = current_user.user.emailAddress,
      username = stringOrNull(current_user.user.name),
      provider_id = current_user.user.idGivenByProvider,
      provider = stringOrNull(current_user.user.provider),
      entitlements =
        JSONFactory200.createEntitlementJSONs(current_user.entitlements),
      views = current_user.views.map(y =>
        ViewsJSON300(
          y.views.map(
            (
                v =>
                  ViewJSON300(v.bankId.value, v.accountId.value, v.viewId.value)
            )
          )
        )
      ),
      on_behalf_of = onBehalfOfUser.map { obu =>
        UserJsonV300(
          user_id = obu.user.userId,
          email = obu.user.emailAddress,
          username = stringOrNull(obu.user.name),
          provider_id = obu.user.idGivenByProvider,
          provider = stringOrNull(obu.user.provider),
          entitlements =
            JSONFactory200.createEntitlementJSONs(obu.entitlements),
          views = obu.views.map(y =>
            ViewsJSON300(
              y.views.map(
                (
                    v =>
                      ViewJSON300(
                        v.bankId.value,
                        v.accountId.value,
                        v.viewId.value
                      )
                )
              )
            )
          )
        )
      }
    )
  }

  def createUserInfoJsonV600(
      user: User,
      entitlements: List[Entitlement],
      agreements: Option[List[UserAgreement]],
      isLocked: Boolean,
      lastActivityDate: Option[Date],
      recentOperationIds: List[String]
  ): UserInfoJsonV600 = {
    UserInfoJsonV600(
      user_id = user.userId,
      email = user.emailAddress,
      username = stringOrNull(user.name),
      provider_id = user.idGivenByProvider,
      provider = stringOrNull(user.provider),
      entitlements = JSONFactory200.createEntitlementJSONs(entitlements),
      views = None,
      agreements = agreements.map(
        _.map(i =>
          UserAgreementJson(`type` = i.agreementType, text = i.agreementText)
        )
      ),
      is_deleted = user.isDeleted.getOrElse(false),
      last_marketing_agreement_signed_date =
        user.lastMarketingAgreementSignedDate,
      is_locked = isLocked,
      last_activity_date = lastActivityDate,
      recent_operation_ids = recentOperationIds
    )
  }

  def createUsersInfoJsonV600(
      users: List[
        (ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])
      ]
  ): UsersInfoJsonV600 = {
    UsersInfoJsonV600(
      users.map(t =>
        createUserInfoJsonV600(
          t._1,
          t._2.getOrElse(Nil),
          t._3,
          LoginAttempt.userIsLocked(t._1.provider, t._1.name),
          None,
          List.empty
        )
      )
    )
  }

  def createMigrationScriptLogJsonV600(
      migrationLog: code.migration.MigrationScriptLogTrait
  ): MigrationScriptLogJsonV600 = {
    MigrationScriptLogJsonV600(
      migration_script_log_id = migrationLog.migrationScriptLogId,
      name = migrationLog.name,
      commit_id = migrationLog.commitId,
      is_successful = migrationLog.isSuccessful,
      start_date = migrationLog.startDate,
      end_date = migrationLog.endDate,
      duration_in_ms = migrationLog.endDate - migrationLog.startDate,
      remark = migrationLog.remark,
      created_at = new Date(migrationLog.startDate),
      updated_at = new Date(migrationLog.endDate)
    )
  }

  def createMigrationScriptLogsJsonV600(
      migrationLogs: List[code.migration.MigrationScriptLogTrait]
  ): MigrationScriptLogsJsonV600 = {
    MigrationScriptLogsJsonV600(
      migration_script_logs =
        migrationLogs.map(createMigrationScriptLogJsonV600)
    )
  }

  def createCallLimitJsonV600(
      rateLimiting: code.ratelimiting.RateLimiting
  ): CallLimitJsonV600 = {
    CallLimitJsonV600(
      rate_limiting_id = rateLimiting.rateLimitingId,
      from_date = rateLimiting.fromDate,
      to_date = rateLimiting.toDate,
      api_version = rateLimiting.apiVersion,
      api_name = rateLimiting.apiName,
      bank_id = rateLimiting.bankId,
      per_second_call_limit = rateLimiting.perSecondCallLimit.toString,
      per_minute_call_limit = rateLimiting.perMinuteCallLimit.toString,
      per_hour_call_limit = rateLimiting.perHourCallLimit.toString,
      per_day_call_limit = rateLimiting.perDayCallLimit.toString,
      per_week_call_limit = rateLimiting.perWeekCallLimit.toString,
      per_month_call_limit = rateLimiting.perMonthCallLimit.toString,
      created_at = rateLimiting.createdAt.get,
      updated_at = rateLimiting.updatedAt.get
    )
  }

  def createActiveRateLimitsJsonV600(
      rateLimitings: List[code.ratelimiting.RateLimiting],
      activeDate: java.util.Date
  ): ActiveRateLimitsJsonV600 = {
    val rateLimitIds = rateLimitings.map(_.rateLimitingId)
    ActiveRateLimitsJsonV600(
      considered_rate_limit_ids = rateLimitIds,
      active_at_date = activeDate,
      active_per_second_rate_limit = rateLimitings.map(_.perSecondCallLimit).sum,
      active_per_minute_rate_limit = rateLimitings.map(_.perMinuteCallLimit).sum,
      active_per_hour_rate_limit = rateLimitings.map(_.perHourCallLimit).sum,
      active_per_day_rate_limit = rateLimitings.map(_.perDayCallLimit).sum,
      active_per_week_rate_limit = rateLimitings.map(_.perWeekCallLimit).sum,
      active_per_month_rate_limit = rateLimitings.map(_.perMonthCallLimit).sum
    )
  }

  def createActiveRateLimitsJsonV600FromCallLimit(

      rateLimit: code.api.util.RateLimitingJson.CallLimit,
      rateLimitIds: List[String],
      activeDate: java.util.Date
  ): ActiveRateLimitsJsonV600 = {
    ActiveRateLimitsJsonV600(
      considered_rate_limit_ids = rateLimitIds,
      active_at_date = activeDate,
      active_per_second_rate_limit = rateLimit.per_second,
      active_per_minute_rate_limit = rateLimit.per_minute,
      active_per_hour_rate_limit = rateLimit.per_hour,
      active_per_day_rate_limit = rateLimit.per_day,
      active_per_week_rate_limit = rateLimit.per_week,
      active_per_month_rate_limit = rateLimit.per_month
    )
  }

  def createTokenJSON(token: String): TokenJSON = {
    TokenJSON(token)
  }

  def createProvidersJson(providers: List[String]): ProvidersJsonV600 = {
    ProvidersJsonV600(providers)
  }

  def createConnectorMethodNamesJson(
      methodNames: List[String]
  ): ConnectorMethodNamesJsonV600 = {
    ConnectorMethodNamesJsonV600(methodNames.sorted)
  }

  def createBankJSON600(
      bank: Bank,
      attributes: List[BankAttributeTrait] = Nil
  ): BankJson600 = {
    val obp = BankRoutingJsonV121("OBP", bank.bankId.value)
    val bic = BankRoutingJsonV121("BIC", bank.swiftBic)
    val routings = bank.bankRoutingScheme match {
      case "OBP" =>
        bic :: BankRoutingJsonV121(
          bank.bankRoutingScheme,
          bank.bankRoutingAddress
        ) :: Nil
      case "BIC" =>
        obp :: BankRoutingJsonV121(
          bank.bankRoutingScheme,
          bank.bankRoutingAddress
        ) :: Nil
      case _ =>
        obp :: bic :: BankRoutingJsonV121(
          bank.bankRoutingScheme,
          bank.bankRoutingAddress
        ) :: Nil
    }
    new BankJson600(
      stringOrNull(bank.bankId.value),
      stringOrNull(bank.shortName),
      stringOrNull(bank.fullName),
      stringOrNull(bank.logoUrl),
      stringOrNull(bank.websiteUrl),
      routings,
      Option(
        attributes
          .filter(_.isActive == Some(true))
          .map(a =>
            BankAttributeBankResponseJsonV400(name = a.name, value = a.value)
          )
      )
    )
  }

  def createCustomerJson(cInfo: Customer): CustomerJsonV600 = {
    import java.text.SimpleDateFormat
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

    CustomerJsonV600(
      bank_id = cInfo.bankId.toString,
      customer_id = cInfo.customerId,
      customer_number = cInfo.number,
      legal_name = cInfo.legalName,
      mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email,
      face_image = CustomerFaceImageJson(
        url = cInfo.faceImage.url,
        date = cInfo.faceImage.date
      ),
      date_of_birth =
        if (cInfo.dateOfBirth != null) dateFormat.format(cInfo.dateOfBirth)
        else "",
      relationship_status = cInfo.relationshipStatus,
      dependants = cInfo.dependents,
      dob_of_dependants = cInfo.dobOfDependents.map(d => dateFormat.format(d)),
      credit_rating = Option(
        CustomerCreditRatingJSON(
          rating = cInfo.creditRating.rating,
          source = cInfo.creditRating.source
        )
      ),
      credit_limit = Option(
        AmountOfMoneyJsonV121(
          currency = cInfo.creditLimit.currency,
          amount = cInfo.creditLimit.amount
        )
      ),
      highest_education_attained = cInfo.highestEducationAttained,
      employment_status = cInfo.employmentStatus,
      kyc_status = cInfo.kycStatus,
      last_ok_date = cInfo.lastOkDate,
      title = cInfo.title,
      branch_id = cInfo.branchId,
      name_suffix = cInfo.nameSuffix
    )
  }

  def createCustomersJson(customers: List[Customer]): CustomerJSONsV600 = {
    CustomerJSONsV600(customers.map(createCustomerJson))
  }

  def createCustomerWithAttributesJson(
      cInfo: Customer,
      customerAttributes: List[CustomerAttribute]
  ): CustomerWithAttributesJsonV600 = {
    import java.text.SimpleDateFormat
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

    CustomerWithAttributesJsonV600(
      bank_id = cInfo.bankId.toString,
      customer_id = cInfo.customerId,
      customer_number = cInfo.number,
      legal_name = cInfo.legalName,
      mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email,
      face_image = CustomerFaceImageJson(
        url = cInfo.faceImage.url,
        date = cInfo.faceImage.date
      ),
      date_of_birth =
        if (cInfo.dateOfBirth != null) dateFormat.format(cInfo.dateOfBirth)
        else "",
      relationship_status = cInfo.relationshipStatus,
      dependants = cInfo.dependents,
      dob_of_dependants = cInfo.dobOfDependents.map(d => dateFormat.format(d)),
      credit_rating = Option(
        CustomerCreditRatingJSON(
          rating = cInfo.creditRating.rating,
          source = cInfo.creditRating.source
        )
      ),
      credit_limit = Option(
        AmountOfMoneyJsonV121(
          currency = cInfo.creditLimit.currency,
          amount = cInfo.creditLimit.amount
        )
      ),
      highest_education_attained = cInfo.highestEducationAttained,
      employment_status = cInfo.employmentStatus,
      kyc_status = cInfo.kycStatus,
      last_ok_date = cInfo.lastOkDate,
      title = cInfo.title,
      branch_id = cInfo.branchId,
      name_suffix = cInfo.nameSuffix,
      customer_attributes = customerAttributes.map(customerAttribute =>
        CustomerAttributeResponseJsonV300(
          customer_attribute_id = customerAttribute.customerAttributeId,
          name = customerAttribute.name,
          `type` = customerAttribute.attributeType.toString,
          value = customerAttribute.value
        )
      )
    )
  }

  def createRoleWithEntitlementCountJson(
      role: String,
      count: Int
  ): RoleWithEntitlementCountJsonV600 = {
    // Check if the role requires a bank ID by looking it up in ApiRole
    val requiresBankId =
      try {
        code.api.util.ApiRole.valueOf(role).requiresBankId
      } catch {
        case _: IllegalArgumentException => false
      }
    RoleWithEntitlementCountJsonV600(
      role = role,
      requires_bank_id = requiresBankId,
      entitlement_count = count
    )
  }

  def createRolesWithEntitlementCountsJson(
      rolesWithCounts: List[(String, Int)]
  ): RolesWithEntitlementCountsJsonV600 = {
    RolesWithEntitlementCountsJsonV600(rolesWithCounts.map {
      case (role, count) =>
        createRoleWithEntitlementCountJson(role, count)
    })
  }

  case class ProvidersJsonV600(providers: List[String])

  case class DynamicEntityIssueJsonV600(
      entity_name: String,
      bank_id: String,
      field_name: String,
      example_value: String,
      error_message: String
  )

  case class DynamicEntityDiagnosticsJsonV600(
      scanned_entities: List[String],
      issues: List[DynamicEntityIssueJsonV600],
      total_issues: Int
  )

  case class ReferenceTypeJsonV600(
      type_name: String,
      example_value: String,
      description: String
  )

  case class ReferenceTypesJsonV600(
      reference_types: List[ReferenceTypeJsonV600]
  )

  case class ValidateUserEmailJsonV600(
      token: String
  )

  case class ValidateUserEmailResponseJsonV600(
      user_id: String,
      email: String,
      username: String,
      provider: String,
      validated: Boolean,
      message: String
  )

// Group JSON case classes
  case class PostGroupJsonV600(
      bank_id: Option[String],
      group_name: String,
      group_description: String,
      list_of_roles: List[String],
      is_enabled: Boolean
  )

  case class PutGroupJsonV600(
      group_name: Option[String],
      group_description: Option[String],
      list_of_roles: Option[List[String]],
      is_enabled: Option[Boolean]
  )

  case class GroupJsonV600(
      group_id: String,
      bank_id: Option[String],
      group_name: String,
      group_description: String,
      list_of_roles: List[String],
      is_enabled: Boolean
  )

  case class GroupsJsonV600(groups: List[GroupJsonV600])

  case class PostGroupMembershipJsonV600(
      group_id: String
  )

  case class AddUserToGroupResponseJsonV600(
      group_id: String,
      user_id: String,
      bank_id: Option[String],
      group_name: String,
      target_entitlements: List[String],
      entitlements_created: List[String],
      entitlements_skipped: List[String]
  )

  case class UserGroupMembershipJsonV600(
      group_id: String,
      user_id: String,
      bank_id: Option[String],
      group_name: String,
      list_of_entitlements: List[String]
  )

  case class UserGroupMembershipsJsonV600(
      group_entitlements: List[UserGroupMembershipJsonV600]
  )

  case class GroupEntitlementJsonV600(
      entitlement_id: String,
      role_name: String,
      bank_id: String,
      user_id: String,
      username: String,
      group_id: Option[String],
      process: Option[String]
  )

  case class GroupEntitlementsJsonV600(
      entitlements: List[GroupEntitlementJsonV600]
  )

  case class RoleWithEntitlementCountJsonV600(
      role: String,
      requires_bank_id: Boolean,
      entitlement_count: Int
  )

  case class RolesWithEntitlementCountsJsonV600(
      roles: List[RoleWithEntitlementCountJsonV600]
  )

  case class PostResetPasswordUrlJsonV600(
      username: String,
      email: String,
      user_id: String
  )

  case class ResetPasswordUrlJsonV600(reset_password_url: String)

  case class ScannedApiVersionJsonV600(
      url_prefix: String,
      api_standard: String,
      api_short_version: String,
      fully_qualified_version: String,
      is_active: Boolean
  )

  case class ViewPermissionJsonV600(
      permission: String,
      category: String
  )

  case class ViewPermissionsJsonV600(
      permissions: List[ViewPermissionJsonV600]
  )

  case class ViewJsonV600(
      view_id: String,
      short_name: String,
      description: String,
      metadata_view: String,
      is_public: Boolean,
      is_system: Boolean,
      is_firehose: Option[Boolean] = None,
      alias: String,
      hide_metadata_if_alias_used: Boolean,
      can_grant_access_to_views: List[String],
      can_revoke_access_to_views: List[String],
      allowed_actions: List[String]
  )

  case class ViewsJsonV600(views: List[ViewJsonV600])

  case class UpdateViewJsonV600(
      description: String,
      metadata_view: String,
      is_public: Boolean,
      is_firehose: Option[Boolean] = None,
      which_alias_to_use: String,
      hide_metadata_if_alias_used: Boolean,
      allowed_actions: List[String],
      can_grant_access_to_views: Option[List[String]] = None,
      can_revoke_access_to_views: Option[List[String]] = None
  ) {
    def toUpdateViewJson = UpdateViewJSON(
      description = this.description,
      metadata_view = this.metadata_view,
      is_public = this.is_public,
      is_firehose = this.is_firehose,
      which_alias_to_use = this.which_alias_to_use,
      hide_metadata_if_alias_used = this.hide_metadata_if_alias_used,
      allowed_actions = this.allowed_actions,
      can_grant_access_to_views = this.can_grant_access_to_views,
      can_revoke_access_to_views = this.can_revoke_access_to_views
    )
  }

  def createViewJsonV600(view: View): ViewJsonV600 = {
    val allowed_actions = view.allowed_actions

    val alias =
      if (view.usePublicAliasIfOneExists)
        "public"
      else if (view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

    ViewJsonV600(
      view_id = view.viewId.value,
      short_name = view.name,
      description = view.description,
      metadata_view = view.metadataView,
      is_public = view.isPublic,
      is_system = view.isSystem,
      is_firehose = Some(view.isFirehose),
      alias = alias,
      hide_metadata_if_alias_used = view.hideOtherAccountMetadataIfAlias,
      can_grant_access_to_views = view.canGrantAccessToViews.getOrElse(Nil),
      can_revoke_access_to_views = view.canRevokeAccessToViews.getOrElse(Nil),
      allowed_actions = allowed_actions
    )
  }

  def createViewsJsonV600(views: List[View]): ViewsJsonV600 = {
    ViewsJsonV600(views.map(createViewJsonV600))
  }

  def createAbacRuleJsonV600(
      rule: code.abacrule.AbacRuleTrait
  ): AbacRuleJsonV600 = {
    AbacRuleJsonV600(
      abac_rule_id = rule.abacRuleId,
      rule_name = rule.ruleName,
      rule_code = rule.ruleCode,
      is_active = rule.isActive,
      description = rule.description,
      policy = rule.policy,
      created_by_user_id = rule.createdByUserId,
      updated_by_user_id = rule.updatedByUserId
    )
  }

  def createAbacRulesJsonV600(
      rules: List[code.abacrule.AbacRuleTrait]
  ): AbacRulesJsonV600 = {
    AbacRulesJsonV600(rules.map(createAbacRuleJsonV600))
  }

  def createCacheNamespaceJsonV600(
      prefix: String,
      description: String,
      ttlSeconds: String,
      category: String,
      keyCount: Int,
      exampleKey: Option[String]
  ): CacheNamespaceJsonV600 = {
    CacheNamespaceJsonV600(
      prefix = prefix,
      description = description,
      ttl_seconds = ttlSeconds,
      category = category,
      key_count = keyCount,
      example_key = exampleKey.getOrElse("")
    )
  }

  def createCacheNamespacesJsonV600(
      namespaces: List[CacheNamespaceJsonV600]
  ): CacheNamespacesJsonV600 = {
    CacheNamespacesJsonV600(namespaces)
  }

  def createCacheConfigJsonV600(): CacheConfigJsonV600 = {
    import code.api.cache.{Redis, InMemory}
    import code.api.Constant
    import net.liftweb.util.Props

    val redisIsReady = try {
      Redis.isRedisReady
    } catch {
      case _: Throwable => false
    }

    val inMemorySize = try {
      InMemory.underlyingGuavaCache.size()
    } catch {
      case _: Throwable => 0L
    }

    val instanceId = code.api.util.APIUtil.getPropsValue("api_instance_id").getOrElse("obp")
    val environment = Props.mode match {
      case Props.RunModes.Production => "prod"
      case Props.RunModes.Staging => "staging"
      case Props.RunModes.Development => "dev"
      case Props.RunModes.Test => "test"
      case _ => "unknown"
    }

    val redisStatus = RedisCacheStatusJsonV600(
      available = redisIsReady,
      url = Redis.url,
      port = Redis.port,
      use_ssl = Redis.useSsl
    )

    val inMemoryStatus = InMemoryCacheStatusJsonV600(
      available = inMemorySize >= 0,
      current_size = inMemorySize
    )

    CacheConfigJsonV600(
      redis_status = redisStatus,
      in_memory_status = inMemoryStatus,
      instance_id = instanceId,
      environment = environment,
      global_prefix = Constant.getGlobalCacheNamespacePrefix
    )
  }

  def createCacheInfoJsonV600(): CacheInfoJsonV600 = {
    import code.api.cache.{Redis, InMemory}
    import code.api.Constant
    import code.api.JedisMethod

    val namespaceDescriptions = Map(
      Constant.CALL_COUNTER_NAMESPACE -> ("Rate limit call counters", "Rate Limiting"),
      Constant.RL_ACTIVE_NAMESPACE -> ("Active rate limit states", "Rate Limiting"),
      Constant.RD_LOCALISED_NAMESPACE -> ("Localized resource docs", "API Documentation"),
      Constant.RD_DYNAMIC_NAMESPACE -> ("Dynamic resource docs", "API Documentation"),
      Constant.RD_STATIC_NAMESPACE -> ("Static resource docs", "API Documentation"),
      Constant.RD_ALL_NAMESPACE -> ("All resource docs", "API Documentation"),
      Constant.SWAGGER_STATIC_NAMESPACE -> ("Static Swagger docs", "API Documentation"),
      Constant.CONNECTOR_NAMESPACE -> ("Connector cache", "Connector"),
      Constant.METRICS_STABLE_NAMESPACE -> ("Stable metrics data", "Metrics"),
      Constant.METRICS_RECENT_NAMESPACE -> ("Recent metrics data", "Metrics"),
      Constant.ABAC_RULE_NAMESPACE -> ("ABAC rule cache", "Authorization")
    )

    var redisAvailable = true
    var totalKeys = 0

    val namespaces = Constant.ALL_CACHE_NAMESPACES.map { namespaceId =>
      val version = Constant.getCacheNamespaceVersion(namespaceId)
      val prefix = Constant.getVersionedCachePrefix(namespaceId)
      val pattern = s"${prefix}*"

      // Dynamically determine storage location by checking where keys exist
      var redisKeyCount = 0
      var memoryKeyCount = 0
      var storageLocation = "unknown"
      var ttlInfo = "no keys to sample"

      try {
        redisKeyCount = Redis.countKeys(pattern)
        totalKeys += redisKeyCount

        // Sample keys to get TTL information
        if (redisKeyCount > 0) {
          val sampleKeys = Redis.scanKeys(pattern).take(5)
          val ttls = sampleKeys.flatMap { key =>
            Redis.use(JedisMethod.TTL, key, None, None).map(_.toLong)
          }

          if (ttls.nonEmpty) {
            val minTtl = ttls.min
            val maxTtl = ttls.max
            val avgTtl = ttls.sum / ttls.length.toLong

            ttlInfo = if (minTtl == maxTtl) {
              if (minTtl == -1) "no expiry"
              else if (minTtl == -2) "keys expired or missing"
              else s"${minTtl}s"
            } else {
              s"range ${minTtl}s to ${maxTtl}s (avg ${avgTtl}s)"
            }
          }
        }
      } catch {
        case _: Throwable =>
          redisAvailable = false
      }

      try {
        memoryKeyCount = InMemory.countKeys(pattern)
        totalKeys += memoryKeyCount

        if (memoryKeyCount > 0 && redisKeyCount == 0) {
          ttlInfo = "in-memory (no TTL in Guava cache)"
        }
      } catch {
        case _: Throwable =>
          // In-memory cache error (shouldn't happen, but handle gracefully)
      }

      // Determine storage based on where keys actually exist
      val keyCount = if (redisKeyCount > 0 && memoryKeyCount > 0) {
        storageLocation = "both"
        ttlInfo = s"redis: ${ttlInfo}, memory: in-memory cache"
        redisKeyCount + memoryKeyCount
      } else if (redisKeyCount > 0) {
        storageLocation = "redis"
        redisKeyCount
      } else if (memoryKeyCount > 0) {
        storageLocation = "memory"
        memoryKeyCount
      } else {
        // No keys found in either location - we don't know where they would be stored
        storageLocation = "unknown"
        0
      }

      val (description, category) = namespaceDescriptions.getOrElse(namespaceId, ("Unknown namespace", "Other"))

      CacheNamespaceInfoJsonV600(
        namespace_id = namespaceId,
        prefix = prefix,
        current_version = version,
        key_count = keyCount,
        description = description,
        category = category,
        storage_location = storageLocation,
        ttl_info = ttlInfo
      )
    }

    CacheInfoJsonV600(
      namespaces = namespaces,
      total_keys = totalKeys,
      redis_available = redisAvailable
    )
  }
}
