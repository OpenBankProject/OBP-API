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

import code.api.Constant
import code.api.util.APIUtil.stringOrNull
import code.metrics.ConnectorTrace
import code.api.util.RateLimitingPeriod.LimitCallPeriod
import code.api.util._
import code.api.v1_2_1.{AccountHolderJSON, BankRoutingJsonV121, OtherAccountMetadataJSON, TransactionDetailsJSON, TransactionMetadataJSON, UserJSONV121}
import code.api.v1_4_0.JSONFactory1_4_0.{CustomerFaceImageJson, MetaJsonV140, createMetaJson}
import code.api.v2_0_0.{BasicViewJson, EntitlementJSONs, JSONFactory200}
import code.api.v2_1_0.CustomerCreditRatingJSON
import code.api.v3_0_0.{
  CustomerAttributeResponseJsonV300,
  ModeratedTransactionWithAttributes,
  UserJsonV300,
  ViewJSON300,
  ViewsJSON300
}
import code.api.v3_1_0.{AccountAttributeResponseJson, ProductAttributeResponseWithoutBankIdJson, RateLimit, RedisCallLimitJson}
import code.api.v3_1_0.JSONFactory310.createProductAttributesJson
import code.api.v4_0_0.{AccountTagJSON, BankAttributeBankResponseJsonV400, ProductFeeJsonV400, ProductFeeValueJsonV400, TransactionAttributeResponseJson, UserAgreementJson}
import code.entitlement.Entitlement
import code.apiproduct.ApiProductTrait
import code.apiproductattribute.ApiProductAttributeTrait
import code.featuredapicollection.FeaturedApiCollectionTrait
import code.loginattempts.LoginAttempt
import code.model.ModeratedBankAccountCore
import code.model.dataAccess.{AuthUser, ResourceUser}
import code.users.UserAgreement
import net.liftweb.mapper.By
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{
  AmountOfMoneyJsonV121,
  CustomerAttribute,
  _
}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box

import java.util.Date
import org.json4s._
import com.openbankproject.commons.util.JsonAliases._

case class FeaturesJsonV600(
  allow_public_views: Boolean,
  allow_abac_account_access: Boolean,
  allow_account_firehose: Boolean,
  allow_customer_firehose: Boolean,
  allow_direct_login: Boolean,
  allow_gateway_login: Boolean,
  allow_oauth2_login: Boolean,
  allow_dauth: Boolean,
  allow_sandbox_account_creation: Boolean,
  allow_sandbox_data_import: Boolean,
  allow_account_deletion: Boolean,
  allow_just_in_time_entitlements: Boolean
)

case class CounterpartyAttributeRequestJsonV600(
  name: String,
  attribute_type: String,
  value: String,
  is_active: Option[Boolean]
)

case class CounterpartyAttributeResponseJsonV600(
  counterparty_id: String,
  counterparty_attribute_id: String,
  name: String,
  attribute_type: String,
  value: String,
  is_active: Option[Boolean]
)

case class CounterpartyAttributesJsonV600(
  attributes: List[CounterpartyAttributeResponseJsonV600]
)

case class PostCustomerLinkJsonV600(
  customer_id: String,
  other_bank_id: String,
  other_customer_id: String,
  relationship_to: String
)

case class PutCustomerLinkJsonV600(
  relationship_to: String
)

case class CustomerLinkJsonV600(
  customer_link_id: String,
  bank_id: String,
  customer_id: String,
  other_bank_id: String,
  other_customer_id: String,
  relationship_to: String,
  date_inserted: Date,
  date_updated: Date
)

case class CustomerLinksJsonV600(
  customer_links: List[CustomerLinkJsonV600]
)

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

// Full Consumer details for management endpoints (V600)
case class ConsumerJsonV600(
    consumer_id: String,
    consumer_key: String,
    app_name: String,
    app_type: String,
    description: String,
    developer_email: String,
    company: String,
    redirect_url: String,
    certificate_pem: String,
    certificate_info: Option[code.api.v5_1_0.CertificateInfoJsonV510],
    created_by_user: code.api.v2_1_0.ResourceUserJSON,
    enabled: Boolean,
    created: Date,
    logo_url: Option[String],
    active_rate_limits: ActiveRateLimitsJsonV600,
    call_counters: RedisCallCountersJsonV600
)

// OIDC Client Verification models (V600)
case class VerifyOidcClientRequestJsonV600(
    client_id: String,
    client_secret: String
)

case class VerifyOidcClientResponseJsonV600(
    valid: Boolean,
    client_id: Option[String] = None,
    consumer_id: Option[String] = None,
    redirect_uris: Option[List[String]] = None
)

// OIDC Client Get (metadata lookup without secret verification)
case class GetOidcClientResponseJsonV600(
    client_id: String,
    client_name: String,
    consumer_id: String,
    redirect_uris: List[String],
    enabled: Boolean
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

// v6 entitlement JSON carries created_by_process ("manual",
// "create_just_in_time_entitlements", or "super_admin_user_ids" /
// "oidc_operator_user_ids" for the virtual entitlements merged into
// GET /users/current) — older versions' EntitlementJSON omits it.
case class EntitlementJsonV600(
    entitlement_id: String,
    role_name: String,
    bank_id: String,
    created_by_process: String,
    // Set when the entitlement was granted off an entitlement request —
    // links the grant back to who asked for it.
    entitlement_request_id: Option[String],
    // user_id of the granter when a person made the grant (directly or as a
    // self-grant); absent for system-process grants, virtual entitlements,
    // and rows created before the field existed (2026-08-09).
    granted_by_user_id: Option[String]
)
case class EntitlementsJsonV600(list: List[EntitlementJsonV600])

case class UserJsonV600(
    user_id: String,
    email: String,
    provider_id: String,
    provider: String,
    username: String,
    entitlements: EntitlementsJsonV600,
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
    first_name: String,
    last_name: String,
    entitlements: EntitlementJSONs,
    views: Option[ViewsJSON300],
    agreements: Option[List[UserAgreementJson]],
    is_deleted: Boolean,
    last_marketing_agreement_signed_date: Option[Date],
    is_locked: Boolean,
    created_date: Option[Date],
    updated_date: Option[Date],
    email_validated: Option[Boolean],
    last_used_locale: Option[String]
)

case class UsersInfoJsonV600(users: List[UserInfoJsonV600])

case class UserInfoDetailJsonV600(
    user_id: String,
    email: String,
    provider_id: String,
    provider: String,
    username: String,
    first_name: String,
    last_name: String,
    entitlements: EntitlementJSONs,
    views: Option[ViewsJSON300],
    agreements: Option[List[UserAgreementJson]],
    is_deleted: Boolean,
    last_marketing_agreement_signed_date: Option[Date],
    is_locked: Boolean,
    created_date: Option[Date],
    updated_date: Option[Date],
    email_validated: Option[Boolean],
    last_used_locale: Option[String],
    last_activity_date: Option[Date],
    recent_operation_ids: List[String]
)

case class CreateUserJsonV600(
    email: String,
    username: String,
    password: String,
    first_name: String,
    last_name: String
)

case class PostVerifyUserCredentialsJsonV600(
    username: String,
    password: String,
    provider: String
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

case class ConnectorInfoJsonV600(
  connector_name: String,
  is_available_in_method_routing: Boolean
)

case class ConnectorsJsonV600(connectors: List[ConnectorInfoJsonV600])

// Basic Account with account_id instead of id for v6.0.0 consistency
case class BasicAccountJsonV600(
  account_id: String,
  bank_id: String,
  label: String,
  views_available: List[BasicViewJson]
)

case class BasicAccountsJsonV600(
  accounts: List[BasicAccountJsonV600]
)

// Moderated Core Account with account_id instead of id for v6.0.0 consistency
case class ModeratedCoreAccountJsonV600(
  account_id: String,
  bank_id: String,
  label: String,
  number: String,
  product_code: String,
  balance: AmountOfMoneyJsonV121,
  account_routings: List[AccountRoutingJsonV121],
  views_basic: List[String]
)

case class TopApiJsonV600(
    count: Int,
    implemented_by_partial_function: String,
    implemented_in_version: String,
    operation_id: String
)

case class TopApisJsonV600(top_apis: List[TopApiJsonV600])


case class MetricJsonV600(
    user_id: String,
    url: String,
    date: Date,
    username: String,
    app_name: String,
    developer_email: String,
    implemented_by_partial_function: String,
    implemented_in_version: String,
    consumer_id: String,
    verb: String,
    correlation_id: String,
    duration: Long,
    source_ip: String,
    target_ip: String,
    response_body: org.json4s.JValue,
    status_code: Int,
    operation_id: String,
    api_instance_id: String,
    consent_reference_id: Option[String],
    // Authentication scheme of the call: "Consent", "OAuth2", "OAuth1", "DirectLogin",
    // "GatewayLogin", "DAuth", "Anonymous", "Other". Absent on rows written before the
    // auth_type column existed.
    auth_type: Option[String],
    // How the caller's certificate was established: "direct", "forwarded" or "none";
    // absent when the request carried no certificate material. See PeerTrust.Resolution.
    certificate_trust: Option[String],
    // The forwarding proxy's subject DN, or the reason no caller was identified.
    certificate_trust_detail: Option[String]
)
case class MetricsJsonV600(metrics: List[MetricJsonV600])

case class AggregateMetricJsonV600(
    count: Int,
    average_response_time: Double,
    minimum_response_time: Double,
    maximum_response_time: Double,
    // Distinct humans: consent-borne calls are attributed to the granting (on-behalf-of)
    // user via the consent table, not to the consent's technical shadow user.
    distinct_user_count: Int,
    distinct_consumer_count: Int,
    // Calls made under a consent, and the number of distinct consents exercised.
    consent_call_count: Int,
    distinct_consent_count: Int
)

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

case class DatabasePoolInfoJsonV600(
    pool_name: String,
    active_connections: Int,
    idle_connections: Int,
    total_connections: Int,
    threads_awaiting_connection: Int,
    maximum_pool_size: Int,
    minimum_idle: Int,
    connection_timeout_ms: Long,
    idle_timeout_ms: Long,
    max_lifetime_ms: Long,
    keepalive_time_ms: Long
)

case class StoredProcedureConnectorHealthJsonV600(
    status: String,
    server_name: Option[String],
    server_ip: Option[String],
    database_name: Option[String],
    response_time_ms: Long,
    error_message: Option[String]
)

case class BankJsonV600(
    bank_id: String,
    bank_code: String,
    full_name: String,
    logo: String,
    website: String,
    bank_routings: List[BankRoutingJsonV121],
    attributes: Option[List[BankAttributeBankResponseJsonV400]]
)

case class BanksJsonV600(banks: List[BankJsonV600])

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
    name_suffix: Option[String] = None,
    customer_type: Option[String] = None,
    parent_customer_id: Option[String] = None
)

case class PostRetailCustomerJsonV600(
    legal_name: String,
    customer_number: Option[String] = None,
    mobile_phone_number: String,
    email: Option[String] = None,
    face_image: Option[CustomerFaceImageJson] = None,
    date_of_birth: Option[String] = None,
    relationship_status: Option[String] = None,
    dependants: Option[Int] = None,
    dob_of_dependants: Option[List[String]] = None,
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

case class PostCorporateCustomerJsonV600(
    legal_name: String,
    customer_number: Option[String] = None,
    mobile_phone_number: String,
    email: Option[String] = None,
    credit_rating: Option[CustomerCreditRatingJSON] = None,
    credit_limit: Option[AmountOfMoneyJsonV121] = None,
    kyc_status: Option[Boolean] = None,
    last_ok_date: Option[Date] = None,
    branch_id: Option[String] = None,
    customer_type: Option[String] = None,
    parent_customer_id: Option[String] = None
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
    name_suffix: String,
    customer_type: String,
    parent_customer_id: String
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
    customer_type: String,
    parent_customer_id: String,
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

// Mandate JSON case classes

case class CreateMandateJsonV600(
    customer_id: String,
    mandate_name: String,
    mandate_reference: String,
    legal_text: String,
    description: String,
    status: String,
    valid_from: String,
    valid_to: String
)

case class UpdateMandateJsonV600(
    mandate_name: String,
    mandate_reference: String,
    legal_text: String,
    description: String,
    status: String,
    valid_from: String,
    valid_to: String
)

case class MandateJsonV600(
    mandate_id: String,
    bank_id: String,
    account_id: String,
    customer_id: String,
    mandate_name: String,
    mandate_reference: String,
    legal_text: String,
    description: String,
    status: String,
    valid_from: String,
    valid_to: String,
    created_by_user_id: String,
    updated_by_user_id: String
)

case class MandatesJsonV600(mandates: List[MandateJsonV600])

// Mandate Provision JSON case classes

case class SignatoryRequirementJsonV600(
    panel_id: String,
    required_count: Int
)

case class CreateMandateProvisionJsonV600(
    provision_name: String,
    provision_description: String,
    legal_reference: String,
    provision_type: String,
    conditions: String,
    signatory_requirements: List[SignatoryRequirementJsonV600],
    linked_view_id: Option[String],
    linked_abac_rule_id: Option[String],
    linked_challenge_type: Option[String],
    is_active: Boolean,
    sort_order: Int
)

case class UpdateMandateProvisionJsonV600(
    provision_name: String,
    provision_description: String,
    legal_reference: String,
    provision_type: String,
    conditions: String,
    signatory_requirements: List[SignatoryRequirementJsonV600],
    linked_view_id: Option[String],
    linked_abac_rule_id: Option[String],
    linked_challenge_type: Option[String],
    is_active: Boolean,
    sort_order: Int
)

case class MandateProvisionJsonV600(
    provision_id: String,
    mandate_id: String,
    provision_name: String,
    provision_description: String,
    legal_reference: String,
    provision_type: String,
    conditions: String,
    signatory_requirements: List[SignatoryRequirementJsonV600],
    linked_view_id: String,
    linked_abac_rule_id: String,
    linked_challenge_type: String,
    is_active: Boolean,
    sort_order: Int
)

case class MandateProvisionsJsonV600(provisions: List[MandateProvisionJsonV600])

// Signatory Panel JSON case classes

case class CreateSignatoryPanelJsonV600(
    panel_name: String,
    description: String,
    user_ids: List[String]
)

case class UpdateSignatoryPanelJsonV600(
    panel_name: String,
    description: String,
    user_ids: List[String]
)

case class SignatoryPanelJsonV600(
    panel_id: String,
    mandate_id: String,
    panel_name: String,
    description: String,
    user_ids: List[String]
)

case class SignatoryPanelsJsonV600(signatory_panels: List[SignatoryPanelJsonV600])

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

case class ValidateDynamicResourceDocSuccessJsonV600(
    valid: Boolean,
    message: String
)

case class ValidateDynamicResourceDocErrorDetailsJsonV600(
    error_type: String
)

case class ValidateDynamicResourceDocFailureJsonV600(
    valid: Boolean,
    error: String,
    message: String,
    details: ValidateDynamicResourceDocErrorDetailsJsonV600
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

// Transaction JSON structures for v6.0.0 - with bank_id included directly
case class ThisAccountJsonV600(
    bank_id: String,
    account_id: String,
    bank_routing: BankRoutingJsonV121,
    account_routings: List[AccountRoutingJsonV121],
    holders: List[AccountHolderJSON]
)

case class OtherAccountJsonV600(
    bank_id: String,
    account_id: String,
    holder: AccountHolderJSON,
    bank_routing: BankRoutingJsonV121,
    account_routings: List[AccountRoutingJsonV121],
    metadata: OtherAccountMetadataJSON
)

case class TransactionJsonV600(
    transaction_id: String,
    this_account: ThisAccountJsonV600,
    other_account: OtherAccountJsonV600,
    details: TransactionDetailsJSON,
    metadata: TransactionMetadataJSON,
    transaction_attributes: List[TransactionAttributeResponseJson]
)

case class TransactionsJsonV600(
    transactions: List[TransactionJsonV600]
)

// HATEOAS-style links for dynamic entity discoverability
case class RelatedLinkJsonV600(rel: String, href: String, method: String)
case class DynamicEntityLinksJsonV600(
    related: List[RelatedLinkJsonV600]
)

// Dynamic Entity definition with fully predictable structure (v6.0.0 format)
// No dynamic keys - entity name is an explicit field, schema describes the structure
case class DynamicEntityDefinitionJsonV600(
    dynamic_entity_id: String,
    entity_name: String,
    user_id: String,
    bank_id: Option[String],
    has_personal_entity: Boolean,
    has_public_access: Boolean = false,
    has_community_access: Boolean = false,
    personal_requires_role: Boolean = false,
    use_row_level_access: Boolean = false,
    schema: org.json4s.JsonAST.JObject,
    _links: Option[DynamicEntityLinksJsonV600] = None
)

case class MyDynamicEntitiesJsonV600(
    dynamic_entities: List[DynamicEntityDefinitionJsonV600]
)

// Management version includes record_count for admin visibility
case class DynamicEntityDefinitionWithCountJsonV600(
    dynamic_entity_id: String,
    entity_name: String,
    user_id: String,
    bank_id: Option[String],
    has_personal_entity: Boolean,
    has_public_access: Boolean = false,
    has_community_access: Boolean = false,
    personal_requires_role: Boolean = false,
    use_row_level_access: Boolean = false,
    schema: org.json4s.JsonAST.JObject,
    record_count: Long,
    _links: Option[DynamicEntityLinksJsonV600] = None
)

case class DynamicEntitiesWithCountJsonV600(
    dynamic_entities: List[DynamicEntityDefinitionWithCountJsonV600]
)

// Request format for creating a dynamic entity (v6.0.0 with snake_case)
case class CreateDynamicEntityRequestJsonV600(
    entity_name: String,
    has_personal_entity: Option[Boolean],  // defaults to true if not provided
    has_public_access: Option[Boolean] = None,  // defaults to false if not provided
    has_community_access: Option[Boolean] = None,  // defaults to false if not provided
    personal_requires_role: Option[Boolean] = None,  // defaults to false if not provided
    use_row_level_access: Option[Boolean] = None,  // defaults to false if not provided
    schema: org.json4s.JsonAST.JObject
)

// Request format for updating a dynamic entity (v6.0.0 with snake_case)
case class UpdateDynamicEntityRequestJsonV600(
    entity_name: String,
    has_personal_entity: Option[Boolean],
    has_public_access: Option[Boolean] = None,
    has_community_access: Option[Boolean] = None,
    personal_requires_role: Option[Boolean] = None,
    use_row_level_access: Option[Boolean] = None,
    schema: org.json4s.JsonAST.JObject
)

// Featured API Collections (v6.0.0)
case class PostFeaturedApiCollectionJsonV600(
    api_collection_id: String,
    sort_order: Int
)

case class PutFeaturedApiCollectionJsonV600(
    sort_order: Int
)

case class FeaturedApiCollectionJsonV600(
    featured_api_collection_id: String,
    api_collection_id: String,
    sort_order: Int
)

case class FeaturedApiCollectionsJsonV600(
    featured_api_collections: List[FeaturedApiCollectionJsonV600]
)

// Response for popular API endpoints (operation IDs only)
case class PopularApisJsonV600(
    operation_ids: List[String]
)

case class ConnectorCountJsonV600(
  connector_name: String,
  method_name: String,
  per_hour_outbound_count: Long,
  per_hour_inbound_success_count: Long,
  per_hour_inbound_failure_count: Long,
  ttl_seconds: Long
)

case class ConnectorCountsJsonV600(
  enabled: Boolean,
  connector_counts: List[ConnectorCountJsonV600]
)

// Api Product (independent of CBS)
case class PostPutApiProductJsonV600(
  parent_api_product_code: Option[String],
  name: String,
  category: Option[String],
  more_info_url: Option[String],
  terms_and_conditions_url: Option[String],
  description: Option[String],
  collection_id: Option[String],
  monthly_subscription_currency: Option[String],
  monthly_subscription_amount: Option[String],
  per_second_call_limit: Option[Long],
  per_minute_call_limit: Option[Long],
  per_hour_call_limit: Option[Long],
  per_day_call_limit: Option[Long],
  per_week_call_limit: Option[Long],
  per_month_call_limit: Option[Long],
  tags: Option[List[String]]
)

case class ApiProductJsonV600(
  api_product_id: String,
  bank_id: String,
  api_product_code: String,
  parent_api_product_code: String,
  name: String,
  category: String,
  more_info_url: String,
  terms_and_conditions_url: String,
  description: String,
  collection_id: String,
  monthly_subscription_currency: String,
  monthly_subscription_amount: String,
  per_second_call_limit: Long,
  per_minute_call_limit: Long,
  per_hour_call_limit: Long,
  per_day_call_limit: Long,
  per_week_call_limit: Long,
  per_month_call_limit: Long,
  tags: List[String],
  attributes: Option[List[ApiProductAttributeResponseJsonV600]]
)

case class ApiProductsJsonV600(api_products: List[ApiProductJsonV600])

// Financial Product (v6.0.0) — adds `tags` on top of the v4.0.0 shape.
case class ProductJsonV600(
  bank_id: String,
  product_code: String,
  parent_product_code: String,
  name: String,
  more_info_url: String,
  terms_and_conditions_url: String,
  description: String,
  meta: MetaJsonV140,
  tags: List[String],
  attributes: Option[List[ProductAttributeResponseWithoutBankIdJson]],
  fees: Option[List[ProductFeeJsonV400]]
)

case class ProductsJsonV600(products: List[ProductJsonV600])

case class ProductTagsJsonV600(tags: List[String])

case class ApiProductAttributeJsonV600(
  name: String,
  `type`: String,
  value: String,
  is_active: Option[Boolean]
)

case class ApiProductAttributeResponseJsonV600(
  bank_id: String,
  api_product_code: String,
  api_product_attribute_id: String,
  name: String,
  `type`: String,
  value: String,
  is_active: Option[Boolean]
)

case class ConnectorTraceJsonV600(
  connector_trace_id: Long,
  correlation_id: String,
  connector_name: String,
  function_name: String,
  bank_id: String,
  outbound_message: String,
  inbound_message: String,
  date: Date,
  duration: Long,
  is_successful: Boolean,
  user_id: String,
  http_verb: String,
  url: String
)

case class ConnectorTracesJsonV600(
  connector_traces: List[ConnectorTraceJsonV600]
)

case class ConfigPropJsonV600(name: String, value: String)

// Signal Channels case classes (Redis-backed ephemeral messaging channels)
case class PostSignalMessageJsonV600(
    payload: org.json4s.JsonAST.JValue,
    message_type: Option[String] = None,
    to_user_id: Option[String] = None
)

case class SignalMessageJsonV600(
    message_id: String,
    channel_name: String,
    sender_consumer_id: String,
    sender_user_id: String,
    to_user_id: Option[String],
    timestamp: String,
    message_type: String,
    payload: org.json4s.JsonAST.JValue
)

case class SignalMessagesJsonV600(
    channel_name: String,
    messages: List[SignalMessageJsonV600],
    total_count: Long,
    has_more: Boolean
)

case class SignalMessagePublishedJsonV600(
    message_id: String,
    channel_name: String,
    timestamp: String,
    channel_message_count: Long
)

case class SignalChannelInfoJsonV600(
    channel_name: String,
    message_count: Long,
    ttl_seconds: Long
)

case class SignalChannelsJsonV600(
    channels: List[SignalChannelInfoJsonV600]
)

case class SignalStatsJsonV600(
    total_channels: Int,
    total_messages: Long,
    channels: List[SignalChannelInfoJsonV600]
)

case class SignalChannelDeletedJsonV600(
    channel_name: String,
    deleted: Boolean
)

// Investigation Report
case class InvestigationTransactionJsonV600(
  transaction_id: String,
  account_id: String,
  amount: String,
  currency: String,
  transaction_type: String,
  description: String,
  start_date: java.util.Date,
  finish_date: java.util.Date,
  counterparty_name: String,
  counterparty_account: String,
  counterparty_bank_name: String
)

case class InvestigationAccountJsonV600(
  account_id: String,
  bank_id: String,
  currency: String,
  balance: String,
  account_name: String,
  account_type: String,
  transactions: List[InvestigationTransactionJsonV600]
)

case class InvestigationCustomerLinkJsonV600(
  customer_link_id: String,
  other_customer_id: String,
  other_bank_id: String,
  relationship: String,
  other_legal_name: String
)

case class InvestigationReportJsonV600(
  customer_id: String,
  legal_name: String,
  bank_id: String,
  accounts: List[InvestigationAccountJsonV600],
  related_customers: List[InvestigationCustomerLinkJsonV600],
  from_date: java.util.Date,
  to_date: java.util.Date,
  data_source: String
)

// Chat / Messaging API case classes
case class PostChatRoomJsonV600(name: String, description: String)
case class PutChatRoomJsonV600(name: Option[String], description: Option[String])
case class ChatRoomSearchRequestJsonV600(
  with_user_ids: List[String],
  exact_participants: Option[Boolean] = Some(false)
)
case class PostParticipantJsonV600(user_id: Option[String], consumer_id: Option[String], permissions: Option[List[String]], webhook_url: Option[String])
case class PutParticipantPermissionsJsonV600(permissions: List[String])
case class PostChatMessageJsonV600(content: String, message_type: Option[String], mentioned_user_ids: Option[List[String]], reply_to_message_id: Option[String], thread_id: Option[String])
case class PutChatMessageJsonV600(content: String)
case class PostReactionJsonV600(emoji: String)

case class ChatRoomJsonV600(
  chat_room_id: String,
  bank_id: String,
  name: String,
  description: String,
  joining_key: String,
  created_by_user_id: String,
  created_by_username: String,
  created_by_provider: String,
  is_open_room: Boolean,
  is_archived: Boolean,
  last_message_at: Option[java.util.Date],
  last_message_preview: Option[String],
  last_message_sender_username: Option[String],
  unread_count: Option[Long],
  created_at: java.util.Date,
  updated_at: java.util.Date,
  participant_count: Long = 0L
)
case class ChatRoomsJsonV600(chat_rooms: List[ChatRoomJsonV600])

case class ParticipantJsonV600(
  participant_id: String,
  chat_room_id: String,
  user_id: String,
  username: String,
  provider: String,
  consumer_id: String,
  consumer_name: String,
  permissions: List[String],
  webhook_url: String,
  joined_at: java.util.Date,
  last_read_at: java.util.Date,
  is_muted: Boolean
)
case class ParticipantsJsonV600(participants: List[ParticipantJsonV600])

case class ChatMessageJsonV600(
  chat_message_id: String,
  chat_room_id: String,
  sender_user_id: String,
  sender_consumer_id: String,
  sender_username: String,
  sender_provider: String,
  sender_consumer_name: String,
  content: String,
  message_type: String,
  mentioned_user_ids: List[String],
  reply_to_message_id: String,
  thread_id: String,
  is_deleted: Boolean,
  created_at: java.util.Date,
  updated_at: java.util.Date,
  reactions: List[ReactionSummaryJsonV600]
)
case class ChatMessagesJsonV600(messages: List[ChatMessageJsonV600])

case class ReactionJsonV600(
  reaction_id: String,
  chat_message_id: String,
  user_id: String,
  username: String,
  provider: String,
  emoji: String,
  created_at: java.util.Date
)
case class ReactionsJsonV600(reactions: List[ReactionJsonV600])
case class ReactionSummaryJsonV600(emoji: String, count: Int, user_ids: List[String])

case class TypingUserJsonV600(user_id: String, username: String, provider: String)
case class TypingUsersJsonV600(users: List[TypingUserJsonV600])

case class UnreadCountJsonV600(chat_room_id: String, unread_count: Long)
case class UnreadCountsJsonV600(unread_counts: List[UnreadCountJsonV600])

case class MessageReactionsJsonV600(chat_message_id: String, reactions: List[ReactionSummaryJsonV600])
case class BulkReactionsJsonV600(message_reactions: List[MessageReactionsJsonV600])

case class JoiningKeyJsonV600(joining_key: String)

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

  def createConsumerJsonV600(
      c: code.model.Consumer,
      certificateInfo: Option[code.api.v5_1_0.CertificateInfoJsonV510],
      activeRateLimits: ActiveRateLimitsJsonV600,
      callCounters: RedisCallCountersJsonV600
  ): ConsumerJsonV600 = {
    val resourceUserJSON = code.users.Users.users.vend.getUserByUserId(c.createdByUserId.toString()) match {
      case net.liftweb.common.Full(resourceUser) => code.api.v2_1_0.ResourceUserJSON(
        user_id = resourceUser.userId,
        email = resourceUser.emailAddress,
        provider_id = resourceUser.idGivenByProvider,
        provider = resourceUser.provider,
        username = resourceUser.name
      )
      case _ => null
    }

    ConsumerJsonV600(
      consumer_id = c.consumerId.get,
      consumer_key = c.key.get,
      app_name = c.name.get,
      app_type = c.appType.toString(),
      description = c.description.get,
      developer_email = c.developerEmail.get,
      company = c.company.get,
      redirect_url = c.redirectURL.get,
      certificate_pem = c.clientCertificate.get,
      certificate_info = certificateInfo,
      created_by_user = resourceUserJSON,
      enabled = c.isActive.get,
      created = c.createdAt.get,
      logo_url = if (c.logoUrl.get == null || c.logoUrl.get.isEmpty) None else Some(c.logoUrl.get),
      active_rate_limits = activeRateLimits,
      call_counters = callCounters
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
      entitlements = EntitlementsJsonV600(
        current_user.entitlements.map(e =>
          EntitlementJsonV600(
            e.entitlementId,
            e.roleName,
            e.bankId,
            e.createdByProcess,
            e.entitlementRequestId,
            e.grantedByUserId
          )
        )
      ),
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
      firstName: String,
      lastName: String,
      entitlements: List[Entitlement],
      agreements: Option[List[UserAgreement]],
      isLocked: Boolean,
      lastActivityDate: Option[Date],
      recentOperationIds: List[String]
  ): UserInfoDetailJsonV600 = {
    val authUser = AuthUser.find(By(AuthUser.user, user.userPrimaryKey.value))
    UserInfoDetailJsonV600(
      user_id = user.userId,
      email = user.emailAddress,
      username = stringOrNull(user.name),
      provider_id = user.idGivenByProvider,
      provider = stringOrNull(user.provider),
      first_name = firstName,
      last_name = lastName,
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
      created_date = authUser.map(_.createdAt.get),
      updated_date = authUser.map(_.updatedAt.get),
      email_validated = authUser.map(_.validated.get),
      last_used_locale = user.lastUsedLocale,
      last_activity_date = lastActivityDate,
      recent_operation_ids = recentOperationIds
    )
  }

  /**
   * Build UsersInfoJsonV600 from Doobie-joined rows (single-SQL path).
   *
   * The LEFT JOIN in DoobieUserQueries.searchUsers already gave us
   * first_name / last_name / is_locked / metadata dates in a single
   * round-trip, so there are no per-user AuthUser lookups here.
   */
  def createUsersInfoJsonV600(
      users: List[
        (code.users.DoobieUserQueries.UserSearchRow, List[Entitlement], List[UserAgreement])
      ]
  ): UsersInfoJsonV600 = {
    UsersInfoJsonV600(
      users.map { case (row, entitlements, agreements) =>
        UserInfoJsonV600(
          user_id = row.userId,
          email = row.email.getOrElse(""),
          username = stringOrNull(row.username.orNull),
          provider_id = row.providerId.getOrElse(""),
          provider = stringOrNull(row.provider.orNull),
          first_name = row.firstName.getOrElse(""),
          last_name = row.lastName.getOrElse(""),
          entitlements = JSONFactory200.createEntitlementJSONs(entitlements),
          views = None,
          agreements = Some(agreements.map(a => UserAgreementJson(`type` = a.agreementType, text = a.agreementText))),
          is_deleted = row.isDeleted.getOrElse(false),
          last_marketing_agreement_signed_date = row.lastMarketingAgreementSignedDate.map(d => new Date(d.getTime)),
          is_locked = row.isLocked,
          created_date = row.createdDate.map(t => new Date(t.getTime)),
          updated_date = row.updatedDate.map(t => new Date(t.getTime)),
          email_validated = row.emailValidated,
          last_used_locale = row.lastUsedLocale
        )
      }
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

  def createConnectorsJson(
      connectorInfos: List[ConnectorInfoJsonV600]
  ): ConnectorsJsonV600 = {
    ConnectorsJsonV600(connectorInfos.sortBy(_.connector_name))
  }

  def createBasicAccountJsonV600(account: BankAccount, viewsAvailable: List[BasicViewJson]): BasicAccountJsonV600 = {
    BasicAccountJsonV600(
      account_id = account.accountId.value,
      bank_id = account.bankId.value,
      label = account.label,
      views_available = viewsAvailable
    )
  }

  def createBasicAccountsJsonV600(accounts: List[BasicAccountJsonV600]): BasicAccountsJsonV600 = {
    BasicAccountsJsonV600(accounts)
  }

  def createModeratedCoreAccountJsonV600(
    account: ModeratedBankAccountCore,
    availableViews: List[View]
  ): ModeratedCoreAccountJsonV600 = {
    ModeratedCoreAccountJsonV600(
      account_id = account.accountId.value,
      bank_id = account.bankId.value,
      label = account.label.getOrElse(""),
      number = account.number.getOrElse(""),
      product_code = account.accountType.getOrElse(""),
      balance = AmountOfMoneyJsonV121(
        account.currency.getOrElse(""),
        account.balance.getOrElse("").toString
      ),
      account_routings = Constant.accountRoutingsWithImplicitOBP(
        account.accountId.value,
        account.accountRoutings.map(r => AccountRoutingJsonV121(scheme = r.scheme, address = r.address))
      ),
      views_basic = availableViews.map(_.viewId.value)
    )
  }

  def createTopApisJsonV600(
      topApis: List[TopApiJsonV600]
  ): TopApisJsonV600 = {
    TopApisJsonV600(topApis)
  }

  def createMetricJsonV600(metric: code.metrics.APIMetric, lookupMap: Map[String, String]): MetricJsonV600 = {
    val operationId = lookupMap.getOrElse(
      metric.getImplementedByPartialFunction(),
      scala.util.Try(code.api.util.APIUtil.buildOperationId(code.api.util.ApiVersionUtils.valueOf(metric.getImplementedInVersion()), metric.getImplementedByPartialFunction()))
        .getOrElse(s"${metric.getImplementedInVersion()}-${metric.getImplementedByPartialFunction()}")
    )
    MetricJsonV600(
      user_id = metric.getUserId(),
      username = metric.getUserName(),
      developer_email = metric.getDeveloperEmail(),
      app_name = metric.getAppName(),
      url = metric.getUrl(),
      date = metric.getDate(),
      consumer_id = metric.getConsumerId(),
      verb = metric.getVerb(),
      implemented_in_version = metric.getImplementedInVersion(),
      implemented_by_partial_function = metric.getImplementedByPartialFunction(),
      correlation_id = metric.getCorrelationId(),
      duration = metric.getDuration(),
      source_ip = metric.getSourceIp(),
      target_ip = metric.getTargetIp(),
      response_body = com.openbankproject.commons.util.JsonAliases.parseOpt(metric.getResponseBody()).getOrElse(org.json4s.JString("Not enabled")),
      status_code = metric.getHttpCode(),
      operation_id = operationId,
      api_instance_id = metric.getApiInstanceId(),
      consent_reference_id = Option(metric.getConsentReferenceId()).filter(_.nonEmpty),
      auth_type = Option(metric.getAuthType()).filter(_.nonEmpty),
      certificate_trust = Option(metric.getCertificateTrust()).filter(_.nonEmpty),
      certificate_trust_detail = Option(metric.getCertificateTrustDetail()).filter(_.nonEmpty)
    )
  }

  def createMetricsJsonV600(metrics: List[code.metrics.APIMetric], lookupMap: Map[String, String]): MetricsJsonV600 = {
    MetricsJsonV600(metrics.map(createMetricJsonV600(_, lookupMap)))
  }

  // Overload that builds the partialFunctionName -> operationId lookup itself —
  // the shared path for endpoints returning raw metric rows.
  def createMetricsJsonV600(metrics: List[code.metrics.APIMetric]): MetricsJsonV600 = {
    val lookupMap = code.api.util.APIUtil.getAllResourceDocs.map(d => d.partialFunctionName -> d.operationId).toMap
    createMetricsJsonV600(metrics, lookupMap)
  }

  // Same list shape as JSONFactory300.createAggregateMetricJson (a single-element array),
  // extended with the distinct/consent counts introduced in v6.0.0.
  def createAggregateMetricJsonV600(aggregateMetrics: List[code.metrics.AggregateMetrics]): List[AggregateMetricJsonV600] = {
    aggregateMetrics.map(aggregateMetric =>
      AggregateMetricJsonV600(
        aggregateMetric.totalCount,
        aggregateMetric.avgResponseTime,
        aggregateMetric.minResponseTime,
        aggregateMetric.maxResponseTime,
        aggregateMetric.distinctUserCount,
        aggregateMetric.distinctConsumerCount,
        aggregateMetric.consentCallCount,
        aggregateMetric.distinctConsentCount
      )
    )
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
      name_suffix = cInfo.nameSuffix,
      customer_type = cInfo.customerType.getOrElse("INDIVIDUAL"),
      parent_customer_id = cInfo.parentCustomerId.getOrElse("")
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
      customer_type = cInfo.customerType.getOrElse("INDIVIDUAL"),
      parent_customer_id = cInfo.parentCustomerId.getOrElse(""),
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

  case class OrphanedDynamicEntityJsonV600(
      entity_name: String,
      bank_id: String,
      record_count: Long
  )

  case class DynamicEntityDiagnosticsJsonV600(
      scanned_entities: List[String],
      issues: List[DynamicEntityIssueJsonV600],
      total_issues: Int,
      orphaned_entities: List[OrphanedDynamicEntityJsonV600]
  )

  case class CleanupOrphanedDynamicEntityResponseJsonV600(
      deleted_orphaned_entities: List[OrphanedDynamicEntityJsonV600],
      total_records_deleted: Long
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
      // The row's stored provenance, verbatim: "GROUP_MEMBERSHIP" for rows granted since
      // provenance moved to created_by_process; legacy group rows show "manual".
      created_by_process: String
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

  case class ResetPasswordEmailSentJsonV600(status: String, to: String)

  case class PostResetPasswordUrlAnonymousJsonV600(
      username: String,
      email: String
  )

  case class ResetPasswordUrlAnonymousResponseJsonV600(message: String)

  case class PostResetPasswordCompleteJsonV600(
      token: String,
      new_password: String
  )

  case class ResetPasswordCompleteResponseJsonV600(message: String)

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
      bank_id: String,
      account_id: String,
      view_id: String,
      view_name: String,
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
      bank_id = view.bankId.value,
      account_id = view.accountId.value,
      view_id = view.viewId.value,
      view_name = view.name,
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

  // Mandate conversion functions
  private val dateFormatter = {
    val df = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    df.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    df
  }

  def createMandateJsonV600(mandate: code.mandate.MandateTrait): MandateJsonV600 = {
    MandateJsonV600(
      mandate_id = mandate.mandateId,
      bank_id = mandate.bankId,
      account_id = mandate.accountId,
      customer_id = mandate.customerId,
      mandate_name = mandate.mandateName,
      mandate_reference = mandate.mandateReference,
      legal_text = mandate.legalText,
      description = mandate.description,
      status = mandate.status,
      valid_from = if (mandate.validFrom != null) dateFormatter.format(mandate.validFrom) else "",
      valid_to = if (mandate.validTo != null) dateFormatter.format(mandate.validTo) else "",
      created_by_user_id = mandate.createdByUserId,
      updated_by_user_id = mandate.updatedByUserId
    )
  }

  def createMandatesJsonV600(mandates: List[code.mandate.MandateTrait]): MandatesJsonV600 = {
    MandatesJsonV600(mandates.map(createMandateJsonV600))
  }

  private def parseSignatoryRequirements(json: String): List[SignatoryRequirementJsonV600] = {
    if (json == null || json.isEmpty) Nil
    else {
      try {
        import org.json4s._
        import com.openbankproject.commons.util.JsonAliases._
        implicit val formats: Formats = DefaultFormats
        com.openbankproject.commons.util.JsonAliases.parse(json).extract[List[SignatoryRequirementJsonV600]]
      } catch {
        case _: Exception => Nil
      }
    }
  }

  def createMandateProvisionJsonV600(provision: code.mandate.MandateProvisionTrait): MandateProvisionJsonV600 = {
    MandateProvisionJsonV600(
      provision_id = provision.provisionId,
      mandate_id = provision.mandateId,
      provision_name = provision.provisionName,
      provision_description = provision.provisionDescription,
      legal_reference = provision.legalReference,
      provision_type = provision.provisionType,
      conditions = provision.conditions,
      signatory_requirements = parseSignatoryRequirements(provision.signatoryRequirements),
      linked_view_id = provision.linkedViewId,
      linked_abac_rule_id = provision.linkedAbacRuleId,
      linked_challenge_type = provision.linkedChallengeType,
      is_active = provision.isActive,
      sort_order = provision.sortOrder
    )
  }

  def createMandateProvisionsJsonV600(provisions: List[code.mandate.MandateProvisionTrait]): MandateProvisionsJsonV600 = {
    MandateProvisionsJsonV600(provisions.map(createMandateProvisionJsonV600))
  }

  def createSignatoryPanelJsonV600(panel: code.mandate.SignatoryPanelTrait): SignatoryPanelJsonV600 = {
    val userIdList = if (panel.userIds == null || panel.userIds.isEmpty) Nil
                     else panel.userIds.split(",").map(_.trim).filter(_.nonEmpty).toList
    SignatoryPanelJsonV600(
      panel_id = panel.panelId,
      mandate_id = panel.mandateId,
      panel_name = panel.panelName,
      description = panel.description,
      user_ids = userIdList
    )
  }

  def createSignatoryPanelsJsonV600(panels: List[code.mandate.SignatoryPanelTrait]): SignatoryPanelsJsonV600 = {
    SignatoryPanelsJsonV600(panels.map(createSignatoryPanelJsonV600))
  }

  def createFeaturedApiCollectionJsonV600(
      featuredApiCollection: FeaturedApiCollectionTrait
  ): FeaturedApiCollectionJsonV600 = {
    FeaturedApiCollectionJsonV600(
      featured_api_collection_id = featuredApiCollection.featuredApiCollectionId,
      api_collection_id = featuredApiCollection.apiCollectionId,
      sort_order = featuredApiCollection.sortOrder
    )
  }

  def createFeaturedApiCollectionsJsonV600(
      featuredApiCollections: List[FeaturedApiCollectionTrait]
  ): FeaturedApiCollectionsJsonV600 = {
    FeaturedApiCollectionsJsonV600(
      featuredApiCollections.map(createFeaturedApiCollectionJsonV600)
    )
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
      Constant.ABAC_RULE_NAMESPACE -> ("ABAC rule cache", "Authorization"),
      Constant.FINANCIAL_PRODUCTS_NAMESPACE -> ("Financial product list (bank-scoped and all-banks)", "Products"),
      Constant.API_PRODUCTS_NAMESPACE -> ("Api product list (all banks)", "Products")
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

  def createDatabasePoolInfoJsonV600(): DatabasePoolInfoJsonV600 = {
    import code.api.util.APIUtil

    val ds = APIUtil.vendor.HikariDatasource.ds
    val config = APIUtil.vendor.HikariDatasource.config
    val pool = ds.getHikariPoolMXBean

    DatabasePoolInfoJsonV600(
      pool_name = ds.getPoolName,
      active_connections = if (pool != null) pool.getActiveConnections else -1,
      idle_connections = if (pool != null) pool.getIdleConnections else -1,
      total_connections = if (pool != null) pool.getTotalConnections else -1,
      threads_awaiting_connection = if (pool != null) pool.getThreadsAwaitingConnection else -1,
      maximum_pool_size = config.getMaximumPoolSize,
      minimum_idle = config.getMinimumIdle,
      connection_timeout_ms = config.getConnectionTimeout,
      idle_timeout_ms = config.getIdleTimeout,
      max_lifetime_ms = config.getMaxLifetime,
      keepalive_time_ms = config.getKeepaliveTime
    )
  }

  def createBankJsonV600(bank: Bank, attributes: List[BankAttributeTrait] = Nil): BankJsonV600 = {
    val bic = BankRoutingJsonV121("BIC", bank.swiftBic)
    val stored = BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress)
    val nonObpRoutings =
      if (bank.bankRoutingScheme == "BIC") List(stored)
      else List(bic, stored)
    val routings = Constant.bankRoutingsWithImplicitOBP(bank.bankId.value, nonObpRoutings)
    BankJsonV600(
      bank_id = stringOrNull(bank.bankId.value),
      bank_code = stringOrNull(bank.shortName),
      full_name = stringOrNull(bank.fullName),
      logo = stringOrNull(bank.logoUrl),
      website = stringOrNull(bank.websiteUrl),
      bank_routings = routings.filter(a => stringOrNull(a.address) != null),
      attributes = Option(
        attributes.filter(_.isActive == Some(true)).map(a => BankAttributeBankResponseJsonV400(
          name = a.name,
          value = a.value)
        )
      )
    )
  }

  def createBanksJsonV600(banks: List[Bank]): BanksJsonV600 = {
    BanksJsonV600(banks.map(bank => createBankJsonV600(bank, Nil)))
  }

  /**
   * Create v6.0.0 response for GET /my/dynamic-entities
   *
   * Fully predictable structure with no dynamic keys.
   * Entity name is an explicit field, schema describes the structure.
   *
   * Response format:
   * {
   *   "dynamic_entities": [
   *     {
   *       "dynamic_entity_id": "abc-123",
   *       "entity_name": "CustomerPreferences",
   *       "user_id": "user-456",
   *       "bank_id": null,
   *       "has_personal_entity": true,
   *       "schema": { ... }
   *     }
   *   ]
   * }
   */
  private def buildDynamicEntityLinks(entity: code.dynamicEntity.DynamicEntityCommons): DynamicEntityLinksJsonV600 = {
    val entityName = entity.entityName
    val idPlaceholder = net.liftweb.util.StringHelpers.snakify(entityName + "Id").toUpperCase()
    val bankPrefix = entity.bankId match {
      case Some(bankId) => s"/obp/${ApiVersion.`dynamic-entity`}/banks/$bankId"
      case None => s"/obp/${ApiVersion.`dynamic-entity`}"
    }

    val personalLinks = if (entity.hasPersonalEntity) {
      val baseUrl = s"$bankPrefix/my/$entityName"
      List(
        RelatedLinkJsonV600("personal-list", baseUrl, "GET"),
        RelatedLinkJsonV600("personal-create", baseUrl, "POST"),
        RelatedLinkJsonV600("personal-read", s"$baseUrl/$idPlaceholder", "GET"),
        RelatedLinkJsonV600("personal-update", s"$baseUrl/$idPlaceholder", "PUT"),
        RelatedLinkJsonV600("personal-delete", s"$baseUrl/$idPlaceholder", "DELETE")
      )
    } else Nil

    val publicLinks = if (entity.hasPublicAccess) {
      val baseUrl = s"$bankPrefix/public/$entityName"
      List(
        RelatedLinkJsonV600("public-list", baseUrl, "GET"),
        RelatedLinkJsonV600("public-read", s"$baseUrl/$idPlaceholder", "GET")
      )
    } else Nil

    val communityLinks = if (entity.hasCommunityAccess) {
      val baseUrl = s"$bankPrefix/community/$entityName"
      List(
        RelatedLinkJsonV600("community-list", baseUrl, "GET"),
        RelatedLinkJsonV600("community-read", s"$baseUrl/$idPlaceholder", "GET")
      )
    } else Nil

    DynamicEntityLinksJsonV600(
      related = personalLinks ++ publicLinks ++ communityLinks
    )
  }

  def createMyDynamicEntitiesJson(dynamicEntities: List[code.dynamicEntity.DynamicEntityCommons]): MyDynamicEntitiesJsonV600 = {
    import com.openbankproject.commons.util.JsonAliases.parse
    import net.liftweb.util.StringHelpers

    MyDynamicEntitiesJsonV600(
      dynamic_entities = dynamicEntities.map { entity =>
        // metadataJson contains the full internal format: { "EntityName": { schema }, "hasPersonalEntity": true }
        // We need to extract just the schema part using the entity name as key
        val fullJson = parse(entity.metadataJson).asInstanceOf[JObject]
        val schemaOption = fullJson.obj.find(_.name == entity.entityName).map(_.value.asInstanceOf[JObject])

        // Validate that the dynamic key matches entity_name
        val knownFlagFields = Set("hasPersonalEntity", "hasPublicAccess", "hasCommunityAccess", "personalRequiresRole", "useRowLevelAccess")
        val dynamicKeyName = fullJson.obj.find(f => !knownFlagFields.contains(f.name)).map(_.name)
        if (dynamicKeyName.exists(_ != entity.entityName)) {
          throw new IllegalStateException(
            s"Dynamic entity key mismatch: stored entityName='${entity.entityName}' but dynamic key='${dynamicKeyName.getOrElse("none")}'"
          )
        }

        val schemaObj = schemaOption.getOrElse(
          throw new IllegalStateException(s"Could not extract schema for entity '${entity.entityName}' from metadataJson")
        )

        val links = buildDynamicEntityLinks(entity)

        DynamicEntityDefinitionJsonV600(
          dynamic_entity_id = entity.dynamicEntityId.getOrElse(""),
          entity_name = entity.entityName,
          user_id = entity.userId,
          bank_id = entity.bankId,
          has_personal_entity = entity.hasPersonalEntity,
          has_public_access = entity.hasPublicAccess,
          has_community_access = entity.hasCommunityAccess,
          personal_requires_role = entity.personalRequiresRole,
          use_row_level_access = entity.useRowLevelAccess,
          schema = schemaObj,
          _links = Some(links)
        )
      }
    )
  }

  /**
   * Create v6.0.0 response for management GET endpoints (includes record_count)
   */
  def createDynamicEntitiesWithCountJson(
    entitiesWithCounts: List[(code.dynamicEntity.DynamicEntityCommons, Long)]
  ): DynamicEntitiesWithCountJsonV600 = {
    import com.openbankproject.commons.util.JsonAliases.parse

    DynamicEntitiesWithCountJsonV600(
      dynamic_entities = entitiesWithCounts.map { case (entity, recordCount) =>
        // metadataJson contains the full internal format: { "EntityName": { schema }, "hasPersonalEntity": true }
        // We need to extract just the schema part using the entity name as key
        val fullJson = parse(entity.metadataJson).asInstanceOf[JObject]
        val schemaOption = fullJson.obj.find(_.name == entity.entityName).map(_.value.asInstanceOf[JObject])

        // Validate that the dynamic key matches entity_name
        val knownFlagFields = Set("hasPersonalEntity", "hasPublicAccess", "hasCommunityAccess", "personalRequiresRole", "useRowLevelAccess")
        val dynamicKeyName = fullJson.obj.find(f => !knownFlagFields.contains(f.name)).map(_.name)
        if (dynamicKeyName.exists(_ != entity.entityName)) {
          throw new IllegalStateException(
            s"Dynamic entity key mismatch: stored entityName='${entity.entityName}' but dynamic key='${dynamicKeyName.getOrElse("none")}'"
          )
        }

        val schema = schemaOption.getOrElse(
          throw new IllegalStateException(s"Could not extract schema for entity '${entity.entityName}' from metadataJson")
        )

        val links = buildDynamicEntityLinks(entity)

        DynamicEntityDefinitionWithCountJsonV600(
          dynamic_entity_id = entity.dynamicEntityId.getOrElse(""),
          entity_name = entity.entityName,
          user_id = entity.userId,
          bank_id = entity.bankId,
          has_personal_entity = entity.hasPersonalEntity,
          has_public_access = entity.hasPublicAccess,
          has_community_access = entity.hasCommunityAccess,
          personal_requires_role = entity.personalRequiresRole,
          use_row_level_access = entity.useRowLevelAccess,
          schema = schema,
          record_count = recordCount,
          _links = Some(links)
        )
      }
    )
  }

  /**
   * Convert v6.0.0 request format to the internal JObject format expected by DynamicEntityCommons.apply
   *
   * Input (v6.0.0):
   * {
   *   "entity_name": "CustomerPreferences",
   *   "has_personal_entity": true,
   *   "schema": { ... }
   * }
   *
   * Output (internal):
   * {
   *   "CustomerPreferences": { ... schema ... },
   *   "hasPersonalEntity": true
   * }
   */
  def convertV600RequestToInternal(request: CreateDynamicEntityRequestJsonV600): org.json4s.JsonAST.JObject = {
    import org.json4s.JsonDSL._

    val hasPersonalEntity = request.has_personal_entity.getOrElse(true)
    val hasPublicAccess = request.has_public_access.getOrElse(false)
    val hasCommunityAccess = request.has_community_access.getOrElse(false)
    val personalRequiresRole = request.personal_requires_role.getOrElse(false)
    val useRowLevelAccess = request.use_row_level_access.getOrElse(false)

    // Build the internal format: entity name as dynamic key + flags
    JObject(
      JField(request.entity_name, request.schema) ::
      JField("hasPersonalEntity", JBool(hasPersonalEntity)) ::
      JField("hasPublicAccess", JBool(hasPublicAccess)) ::
      JField("hasCommunityAccess", JBool(hasCommunityAccess)) ::
      JField("personalRequiresRole", JBool(personalRequiresRole)) ::
      JField("useRowLevelAccess", JBool(useRowLevelAccess)) ::
      Nil
    )
  }

  def convertV600UpdateRequestToInternal(request: UpdateDynamicEntityRequestJsonV600): org.json4s.JsonAST.JObject = {
    import org.json4s.JsonDSL._

    val hasPersonalEntity = request.has_personal_entity.getOrElse(true)
    val hasPublicAccess = request.has_public_access.getOrElse(false)
    val hasCommunityAccess = request.has_community_access.getOrElse(false)
    val personalRequiresRole = request.personal_requires_role.getOrElse(false)
    val useRowLevelAccess = request.use_row_level_access.getOrElse(false)

    // Build the internal format: entity name as dynamic key + flags
    JObject(
      JField(request.entity_name, request.schema) ::
      JField("hasPersonalEntity", JBool(hasPersonalEntity)) ::
      JField("hasPublicAccess", JBool(hasPublicAccess)) ::
      JField("hasCommunityAccess", JBool(hasCommunityAccess)) ::
      JField("personalRequiresRole", JBool(personalRequiresRole)) ::
      JField("useRowLevelAccess", JBool(useRowLevelAccess)) ::
      Nil
    )
  }

  // Transaction v6.0.0 factory methods

  import code.api.util.APIUtil.stringOptionOrNull
  import code.api.v1_2_1.JSONFactory.{createAmountOfMoneyJSON, createTransactionCommentJSON, createTransactionTagJSON, createTransactionImageJSON, createLocationJSON, createAccountHolderJSON}
  import code.api.v3_0_0.JSONFactory300.createOtherAccountMetaDataJSON
  import code.api.v4_0_0.JSONFactory400.createTransactionAttributeJson
  import code.model.{ModeratedBankAccount, ModeratedOtherBankAccount, ModeratedTransaction, ModeratedTransactionMetadata}

  def createTransactionsJsonV600(moderatedTransactionsWithAttributes: List[ModeratedTransactionWithAttributes]): TransactionsJsonV600 = {
    TransactionsJsonV600(moderatedTransactionsWithAttributes.map(t => createTransactionJsonV600(t.transaction, t.transactionAttributes)))
  }

  def createTransactionJsonV600(transaction: ModeratedTransaction, transactionAttributes: List[TransactionAttribute]): TransactionJsonV600 = {
    TransactionJsonV600(
      transaction_id = transaction.id.value,
      this_account = transaction.bankAccount.map(createThisAccountJsonV600).getOrElse(null),
      other_account = transaction.otherBankAccount.map(createOtherAccountJsonV600).getOrElse(null),
      details = createTransactionDetailsJsonV600(transaction),
      metadata = transaction.metadata.map(createTransactionMetadataJsonV600).getOrElse(null),
      transaction_attributes = transactionAttributes.map(createTransactionAttributeJson)
    )
  }

  def createThisAccountJsonV600(bankAccount: ModeratedBankAccount): ThisAccountJsonV600 = {
    ThisAccountJsonV600(
      bank_id = bankAccount.bankId.value,
      account_id = bankAccount.accountId.value,
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.bankRoutingScheme), stringOptionOrNull(bankAccount.bankRoutingAddress)),
      account_routings = Constant.accountRoutingsWithImplicitOBP(
        bankAccount.accountId.value,
        List(AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme), stringOptionOrNull(bankAccount.accountRoutingAddress)))
      ),
      holders = bankAccount.owners.map(x => x.toList.map(holder => AccountHolderJSON(name = holder.name, is_alias = false))).getOrElse(null)
    )
  }

  def createOtherAccountJsonV600(bankAccount: ModeratedOtherBankAccount): OtherAccountJsonV600 = {
    // Extract bank_id from bank_routing when scheme is "OBP", otherwise use the address as best effort
    val bankId = bankAccount.bankRoutingScheme match {
      case Some("OBP") => stringOptionOrNull(bankAccount.bankRoutingAddress)
      case _ => stringOptionOrNull(bankAccount.bankRoutingAddress) // Best effort - use address
    }

    OtherAccountJsonV600(
      bank_id = bankId,
      account_id = bankAccount.id,
      holder = createAccountHolderJSON(bankAccount.label.display, bankAccount.isAlias),
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.bankRoutingScheme), stringOptionOrNull(bankAccount.bankRoutingAddress)),
      account_routings = Constant.accountRoutingsWithImplicitOBP(
        bankAccount.id,
        List(AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme), stringOptionOrNull(bankAccount.accountRoutingAddress)))
      ),
      metadata = bankAccount.metadata.map(createOtherAccountMetaDataJSON).getOrElse(null)
    )
  }

  def createTransactionDetailsJsonV600(transaction: ModeratedTransaction): TransactionDetailsJSON = {
    TransactionDetailsJSON(
      `type` = stringOptionOrNull(transaction.transactionType),
      description = stringOptionOrNull(transaction.description),
      posted = transaction.startDate.getOrElse(null),
      completed = transaction.finishDate.getOrElse(null),
      new_balance = createAmountOfMoneyJSON(transaction.currency, transaction.balance),
      value = createAmountOfMoneyJSON(transaction.currency, transaction.amount.map(_.toString))
    )
  }

  def createTransactionMetadataJsonV600(metadata: ModeratedTransactionMetadata): TransactionMetadataJSON = {
    TransactionMetadataJSON(
      narrative = stringOptionOrNull(metadata.ownerComment),
      comments = metadata.comments.map(_.map(createTransactionCommentJSON)).getOrElse(null),
      tags = metadata.tags.map(_.map(createTransactionTagJSON)).getOrElse(null),
      images = metadata.images.map(_.map(createTransactionImageJSON)).getOrElse(null),
      where = metadata.whereTag.map(createLocationJSON).getOrElse(null)
    )
  }

  def createApiProductAttributeResponseJsonV600(
    attribute: ApiProductAttributeTrait
  ): ApiProductAttributeResponseJsonV600 = {
    ApiProductAttributeResponseJsonV600(
      bank_id = attribute.bankId,
      api_product_code = attribute.apiProductCode,
      api_product_attribute_id = attribute.apiProductAttributeId,
      name = attribute.name,
      `type` = attribute.attributeType,
      value = attribute.value,
      is_active = attribute.isActive
    )
  }

  def createApiProductJsonV600(
    product: ApiProductTrait,
    attributes: Option[List[ApiProductAttributeTrait]]
  ): ApiProductJsonV600 = {
    ApiProductJsonV600(
      api_product_id = product.apiProductId,
      bank_id = product.bankId,
      api_product_code = product.apiProductCode,
      parent_api_product_code = product.parentApiProductCode,
      name = product.name,
      category = product.category,
      more_info_url = product.moreInfoUrl,
      terms_and_conditions_url = product.termsAndConditionsUrl,
      description = product.description,
      collection_id = product.collectionId,
      monthly_subscription_currency = product.monthlySubscriptionCurrency,
      monthly_subscription_amount = product.monthlySubscriptionAmount,
      per_second_call_limit = product.perSecondCallLimit,
      per_minute_call_limit = product.perMinuteCallLimit,
      per_hour_call_limit = product.perHourCallLimit,
      per_day_call_limit = product.perDayCallLimit,
      per_week_call_limit = product.perWeekCallLimit,
      per_month_call_limit = product.perMonthCallLimit,
      tags = product.tags,
      attributes = attributes.map(_.map(createApiProductAttributeResponseJsonV600))
    )
  }

  def createApiProductsJsonV600(
    products: List[ApiProductTrait]
  ): ApiProductsJsonV600 = {
    ApiProductsJsonV600(products.map(p => createApiProductJsonV600(p, None)))
  }

  def createProductJsonV600(product: Product, tags: List[String]): ProductJsonV600 = {
    ProductJsonV600(
      bank_id = product.bankId.value,
      product_code = product.code.value,
      parent_product_code = product.parentProductCode.value,
      name = product.name,
      more_info_url = product.moreInfoUrl,
      terms_and_conditions_url = product.termsAndConditionsUrl,
      description = product.description,
      meta = createMetaJson(product.meta),
      tags = tags,
      attributes = None,
      fees = None
    )
  }

  def createProductsJsonV600(products: List[Product], tagsByCode: Map[String, List[String]]): ProductsJsonV600 = {
    ProductsJsonV600(products.map(p => createProductJsonV600(p, tagsByCode.getOrElse(p.code.value, Nil))))
  }

  def createProductTagsJsonV600(tags: List[String]): ProductTagsJsonV600 = {
    ProductTagsJsonV600(tags = tags)
  }

  def createConnectorTraceJsonV600(trace: ConnectorTrace): ConnectorTraceJsonV600 = {
    ConnectorTraceJsonV600(
      connector_trace_id = trace.id.get,
      correlation_id = trace.correlationId.get,
      connector_name = trace.connectorName.get,
      function_name = trace.functionName.get,
      bank_id = trace.bankId.get,
      outbound_message = trace.outboundMessage.get,
      inbound_message = trace.inboundMessage.get,
      date = trace.date.get,
      duration = trace.duration.get,
      is_successful = trace.isSuccessful.get,
      user_id = trace.userId.get,
      http_verb = trace.httpVerb.get,
      url = trace.url.get
    )
  }

  def createConnectorTracesJsonV600(traces: List[ConnectorTrace]): ConnectorTracesJsonV600 = {
    ConnectorTracesJsonV600(traces.map(createConnectorTraceJsonV600))
  }

  // Account Access Request JSON case classes
  case class PostAccountAccessRequestJsonV600(
    target_user_id: String,
    view_id: String,
    is_system_view: Boolean,
    business_justification: String
  )

  case class PostApproveAccountAccessRequestJsonV600(
    comment: Option[String]
  )

  case class PostRejectAccountAccessRequestJsonV600(
    comment: String
  )

  case class AccountAccessRequestJsonV600(
    account_access_request_id: String,
    bank_id: String,
    account_id: String,
    view_id: String,
    is_system_view: Boolean,
    requestor_user_id: String,
    target_user_id: String,
    business_justification: String,
    status: String,
    checker_user_id: String,
    checker_comment: String,
    created: java.util.Date,
    updated: java.util.Date
  )

  case class AccountAccessRequestsJsonV600(
    account_access_requests: List[AccountAccessRequestJsonV600]
  )

  def createAccountAccessRequestJsonV600(r: code.accountaccessrequest.AccountAccessRequestTrait): AccountAccessRequestJsonV600 = {
    AccountAccessRequestJsonV600(
      account_access_request_id = r.accountAccessRequestId,
      bank_id = r.bankId,
      account_id = r.accountId,
      view_id = r.viewId,
      is_system_view = r.isSystemView,
      requestor_user_id = r.requestorUserId,
      target_user_id = r.targetUserId,
      business_justification = r.businessJustification,
      status = r.status,
      checker_user_id = r.checkerUserId,
      checker_comment = r.checkerComment,
      created = r.created,
      updated = r.updated
    )
  }

  def createAccountAccessRequestsJsonV600(requests: List[code.accountaccessrequest.AccountAccessRequestTrait]): AccountAccessRequestsJsonV600 = {
    AccountAccessRequestsJsonV600(requests.map(createAccountAccessRequestJsonV600))
  }

  case class AccountDirectoryItemJsonV600(
    account_id: String,
    bank_id: String,
    label: String,
    account_number: String,
    account_type: String,
    branch_id: String,
    account_routings: List[AccountRoutingJsonV121],
    account_attributes: List[FastFirehoseAttributes],
    view_ids: List[String]
  )

  case class AccountDirectoryJsonV600(
    accounts: List[AccountDirectoryItemJsonV600]
  )

  def createAccountDirectoryJsonV600(
    accounts: List[AccountDirectoryItem],
    viewsPerAccount: Map[BankIdAccountId, List[String]]
  ): AccountDirectoryJsonV600 = {
    AccountDirectoryJsonV600(
      accounts.map { a =>
        AccountDirectoryItemJsonV600(
          account_id = a.id,
          bank_id = a.bankId,
          label = a.label,
          account_number = a.number,
          account_type = a.productCode,
          branch_id = a.branchId,
          account_routings = Constant.accountRoutingsWithImplicitOBP(
            a.id,
            a.accountRoutings.map(r => AccountRoutingJsonV121(r.scheme, r.address))
          ),
          account_attributes = a.accountAttributes,
          view_ids = viewsPerAccount.getOrElse(BankIdAccountId(BankId(a.bankId), AccountId(a.id)), Nil)
        )
      }
    )
  }

  case class HasAccountAccessJsonV600(
    has_account_access: Boolean,
    access_source: String,
    account_access_id: String,
    abac_rule_id: String
  )

  case class UserWithViewAccessJsonV600(
    user_id: String,
    username: String,
    email: String,
    provider: String,
    access_source: String  // "ACCOUNT_ACCESS" or "ABAC"
  )

  case class UsersWithViewAccessJsonV600(
    users: List[UserWithViewAccessJsonV600]
  )

  case class ModeratedAccountJSON600(
    id: String,
    label: String,
    number: String,
    owners: List[UserJSONV121],
    product_code: String,
    balance: AmountOfMoneyJsonV121,
    views_available: List[ViewJsonV600],
    bank_id: String,
    account_routings: List[AccountRoutingJsonV121],
    account_attributes: List[AccountAttributeResponseJson],
    tags: List[AccountTagJSON]
  )

  def createBankAccountJSON600(
    account: ModeratedBankAccountCore,
    viewsAvailable: List[ViewJsonV600],
    accountAttributes: List[AccountAttribute],
    tags: List[TransactionTag]
  ): ModeratedAccountJSON600 = {
    import code.api.v1_2_1.JSONFactory.{createAmountOfMoneyJSON, createOwnersJSON}
    import code.api.v3_0_0.JSONFactory300.createAccountRoutingsJSON
    import code.api.v3_1_0.JSONFactory310.createAccountAttributeJson
    import code.api.v4_0_0.JSONFactory400.createAccountTagJSON
    ModeratedAccountJSON600(
      id = account.accountId.value,
      label = stringOptionOrNull(account.label),
      number = stringOptionOrNull(account.number),
      owners = createOwnersJSON(account.owners.getOrElse(Set()), ""),
      product_code = stringOptionOrNull(account.accountType),
      balance = createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance.getOrElse("")),
      views_available = viewsAvailable,
      bank_id = stringOrNull(account.bankId.value),
      account_routings = Constant.accountRoutingsWithImplicitOBP(
        account.accountId.value,
        createAccountRoutingsJSON(account.accountRoutings)
      ),
      account_attributes = accountAttributes.map(createAccountAttributeJson),
      tags = tags.map(createAccountTagJSON)
    )
  }

  def createCounterpartyAttributeJson(attribute: CounterpartyAttributeTrait): CounterpartyAttributeResponseJsonV600 = {
    CounterpartyAttributeResponseJsonV600(
      counterparty_id = attribute.counterpartyId.value,
      counterparty_attribute_id = attribute.counterpartyAttributeId,
      name = attribute.name,
      attribute_type = attribute.attributeType.toString,
      value = attribute.value,
      is_active = attribute.isActive
    )
  }

  def createCounterpartyAttributesJson(attributes: List[CounterpartyAttributeTrait]): CounterpartyAttributesJsonV600 = {
    CounterpartyAttributesJsonV600(
      attributes.map(createCounterpartyAttributeJson)
    )
  }

  def createCustomerLinkJson(customerLink: code.customerlinks.CustomerLinkTrait): CustomerLinkJsonV600 = {
    CustomerLinkJsonV600(
      customer_link_id = customerLink.customerLinkId,
      bank_id = customerLink.bankId,
      customer_id = customerLink.customerId,
      other_bank_id = customerLink.otherBankId,
      other_customer_id = customerLink.otherCustomerId,
      relationship_to = customerLink.relationshipTo,
      date_inserted = customerLink.dateInserted,
      date_updated = customerLink.dateUpdated
    )
  }

  def createCustomerLinksJson(customerLinks: List[code.customerlinks.CustomerLinkTrait]): CustomerLinksJsonV600 = {
    CustomerLinksJsonV600(
      customerLinks.map(createCustomerLinkJson)
    )
  }

  def createInvestigationReportJson(
    customer: code.investigation.DoobieInvestigationQueries.CustomerRow,
    bankId: String,
    accounts: List[code.investigation.DoobieInvestigationQueries.AccountRow],
    transactions: List[code.investigation.DoobieInvestigationQueries.TransactionRow],
    customerLinks: List[code.investigation.DoobieInvestigationQueries.CustomerLinkRow],
    fromDate: java.util.Date,
    toDate: java.util.Date
  ): InvestigationReportJsonV600 = {
    val transactionsByAccount = transactions.groupBy(_.accountId)

    val accountJsons = accounts.map { acc =>
      val txns = transactionsByAccount.getOrElse(acc.accountId, Nil)
      InvestigationAccountJsonV600(
        account_id = acc.accountId,
        bank_id = acc.bankId,
        currency = acc.currency,
        balance = acc.balance.toString,
        account_name = acc.accountName,
        account_type = acc.accountType,
        transactions = txns.map { t =>
          InvestigationTransactionJsonV600(
            transaction_id = t.transactionId,
            account_id = t.accountId,
            amount = t.amount.toString,
            currency = t.currency,
            transaction_type = t.transactionType,
            description = t.description,
            start_date = t.startDate,
            finish_date = t.finishDate,
            counterparty_name = t.counterpartyName,
            counterparty_account = t.counterpartyAccount,
            counterparty_bank_name = t.counterpartyBankName
          )
        }
      )
    }

    val relatedCustomerJsons = customerLinks.map { cl =>
      InvestigationCustomerLinkJsonV600(
        customer_link_id = cl.customerLinkId,
        other_customer_id = cl.otherCustomerId,
        other_bank_id = cl.otherBankId,
        relationship = cl.relationship,
        other_legal_name = cl.otherLegalName
      )
    }

    InvestigationReportJsonV600(
      customer_id = customer.customerId,
      legal_name = customer.legalName,
      bank_id = bankId,
      accounts = accountJsons,
      related_customers = relatedCustomerJsons,
      from_date = fromDate,
      to_date = toDate,
      data_source = "mapped_database"
    )
  }

  // Chat / Messaging factory functions
  def createChatRoomJson(
    room: code.chat.ChatRoomTrait,
    unreadCount: Option[Long] = None,
    participantCount: Long = 0L
  ): ChatRoomJsonV600 = {
    val creator = code.users.Users.users.vend.getUserByUserId(room.createdByUserId)
    val hasLastMessage = room.lastMessageAt.isDefined
    ChatRoomJsonV600(
      chat_room_id = room.chatRoomId,
      bank_id = room.bankId,
      name = room.name,
      description = room.description,
      joining_key = room.joiningKey,
      created_by_user_id = room.createdByUserId,
      created_by_username = creator.map(_.name).getOrElse(""),
      created_by_provider = creator.map(_.provider).getOrElse(""),
      is_open_room = room.isOpenRoom,
      is_archived = room.isArchived,
      last_message_at = room.lastMessageAt,
      last_message_preview = if (hasLastMessage) Some(room.lastMessagePreview) else None,
      last_message_sender_username = if (hasLastMessage) Some(room.lastMessageSenderUsername) else None,
      unread_count = unreadCount,
      created_at = room.createdDate,
      updated_at = room.updatedDate,
      participant_count = participantCount
    )
  }
  def createChatRoomsJson(
    rooms: List[code.chat.ChatRoomTrait],
    unreadCounts: Map[String, Long] = Map.empty,
    participantCounts: Map[String, Long] = Map.empty
  ): ChatRoomsJsonV600 = {
    ChatRoomsJsonV600(rooms.map(r =>
      createChatRoomJson(r, unreadCounts.get(r.chatRoomId), participantCounts.getOrElse(r.chatRoomId, 0L))
    ))
  }

  def createParticipantJson(p: code.chat.ParticipantTrait): ParticipantJsonV600 = {
    val user = code.users.Users.users.vend.getUserByUserId(p.userId)
    val consumerName = if (p.consumerId.nonEmpty)
      code.model.Consumer.find(By(code.model.Consumer.consumerId, p.consumerId)).map(_.name.get).getOrElse("")
    else ""
    ParticipantJsonV600(
      participant_id = p.participantId,
      chat_room_id = p.chatRoomId,
      user_id = p.userId,
      username = user.map(_.name).getOrElse(""),
      provider = user.map(_.provider).getOrElse(""),
      consumer_id = p.consumerId,
      consumer_name = consumerName,
      permissions = p.permissions,
      webhook_url = p.webhookUrl,
      joined_at = p.joinedAt,
      last_read_at = p.lastReadAt,
      is_muted = p.isMuted
    )
  }
  def createParticipantsJson(participants: List[code.chat.ParticipantTrait]): ParticipantsJsonV600 = {
    ParticipantsJsonV600(participants.map(createParticipantJson))
  }

  def createChatMessageJson(msg: code.chat.ChatMessageTrait, reactions: List[code.chat.ReactionTrait]): ChatMessageJsonV600 = {
    val reactionSummaries = reactions.groupBy(_.emoji).map { case (emoji, rs) =>
      ReactionSummaryJsonV600(emoji = emoji, count = rs.size, user_ids = rs.map(_.userId))
    }.toList
    val user = code.users.Users.users.vend.getUserByUserId(msg.senderUserId)
    val consumerAppName = if (msg.senderConsumerId.nonEmpty)
      code.model.Consumer.find(By(code.model.Consumer.consumerId, msg.senderConsumerId)).map(_.name.get).getOrElse("")
    else ""
    ChatMessageJsonV600(
      chat_message_id = msg.chatMessageId,
      chat_room_id = msg.chatRoomId,
      sender_user_id = msg.senderUserId,
      sender_consumer_id = msg.senderConsumerId,
      sender_username = user.map(_.name).getOrElse(""),
      sender_provider = user.map(_.provider).getOrElse(""),
      sender_consumer_name = consumerAppName,
      content = if (msg.isDeleted) "" else msg.content,
      message_type = msg.messageType,
      mentioned_user_ids = msg.mentionedUserIds,
      reply_to_message_id = msg.replyToMessageId,
      thread_id = msg.threadId,
      is_deleted = msg.isDeleted,
      created_at = msg.createdDate,
      updated_at = msg.updatedDate,
      reactions = reactionSummaries
    )
  }
  def createChatMessagesJson(messages: List[code.chat.ChatMessageTrait], allReactions: Map[String, List[code.chat.ReactionTrait]]): ChatMessagesJsonV600 = {
    ChatMessagesJsonV600(messages.map(msg => createChatMessageJson(msg, allReactions.getOrElse(msg.chatMessageId, List.empty))))
  }
  def createBulkReactionsJson(allReactions: Map[String, List[code.chat.ReactionTrait]], messageIds: List[String]): BulkReactionsJsonV600 = {
    BulkReactionsJsonV600(
      message_reactions = messageIds.map { msgId =>
        val reactions = allReactions.getOrElse(msgId, List.empty)
        val summaries = reactions.groupBy(_.emoji).map { case (emoji, rs) =>
          ReactionSummaryJsonV600(emoji = emoji, count = rs.size, user_ids = rs.map(_.userId))
        }.toList
        MessageReactionsJsonV600(chat_message_id = msgId, reactions = summaries)
      }
    )
  }

  def createChatMessagesJsonFromRows(
    messages: List[code.chat.DoobieChatMessageQueries.ChatMessageRow],
    allReactions: Map[String, List[code.chat.DoobieChatMessageQueries.ReactionRow]]
  ): ChatMessagesJsonV600 = {
    ChatMessagesJsonV600(messages.map { msg =>
      val reactions = allReactions.getOrElse(msg.chatMessageId, List.empty)
      val reactionSummaries = reactions.groupBy(_.emoji).map { case (emoji, rs) =>
        ReactionSummaryJsonV600(emoji = emoji, count = rs.size, user_ids = rs.map(_.userId))
      }.toList
      val mentionedIds = msg.mentionedUserIds match {
        case Some(ids) if ids.nonEmpty => ids.split(",").map(_.trim).filter(_.nonEmpty).toList
        case _ => List.empty
      }
      ChatMessageJsonV600(
        chat_message_id = msg.chatMessageId,
        chat_room_id = msg.chatRoomId,
        sender_user_id = msg.senderUserId,
        sender_consumer_id = msg.senderConsumerId,
        sender_username = msg.senderUsername,
        sender_provider = msg.senderProvider,
        sender_consumer_name = msg.senderConsumerName,
        content = if (msg.isDeleted) "" else msg.content,
        message_type = msg.messageType,
        mentioned_user_ids = mentionedIds,
        reply_to_message_id = msg.replyToMessageId,
        thread_id = msg.threadId,
        is_deleted = msg.isDeleted,
        created_at = msg.createdAt,
        updated_at = msg.updatedAt,
        reactions = reactionSummaries
      )
    })
  }

  def createReactionJson(r: code.chat.ReactionTrait): ReactionJsonV600 = {
    val user = code.users.Users.users.vend.getUserByUserId(r.userId)
    ReactionJsonV600(
      reaction_id = r.reactionId,
      chat_message_id = r.chatMessageId,
      user_id = r.userId,
      username = user.map(_.name).getOrElse(""),
      provider = user.map(_.provider).getOrElse(""),
      emoji = r.emoji,
      created_at = r.createdDate
    )
  }
  def createReactionsJson(reactions: List[code.chat.ReactionTrait]): ReactionsJsonV600 = {
    ReactionsJsonV600(reactions.map(createReactionJson))
  }

}
