/**
  * Open Bank Project - API
  * Copyright (C) 2011-2019, TESOBE GmbH
  * *
  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Affero General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  * *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Affero General Public License for more details.
  * *
  * You should have received a copy of the GNU Affero General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
  * *
  * Email: contact@tesobe.com
  * TESOBE GmbH
  * Osloerstrasse 16/17
  * Berlin 13359, Germany
  * *
  * This product includes software developed at
  * TESOBE (http://www.tesobe.com/)
  *
  */
package code.api.v4_0_0

import java.text.SimpleDateFormat
import java.util.Date

import code.api.attributedefinition.AttributeDefinition
import code.api.util.APIUtil
import code.api.util.APIUtil.{DateWithDay, DateWithSeconds, stringOptionOrNull, stringOrNull}
import code.api.v1_2_1.JSONFactory.{createAmountOfMoneyJSON, createOwnersJSON}
import code.api.v1_2_1.{BankRoutingJsonV121, JSONFactory, UserJSONV121, ViewJSONV121}
import code.api.v1_4_0.JSONFactory1_4_0.{LocationJsonV140, MetaJsonV140, TransactionRequestAccountJsonV140, transformToLocationFromV140, transformToMetaFromV140}
import code.api.v2_0_0.JSONFactory200.UserJsonV200
import code.api.v2_0_0.{EntitlementJSONs, JSONFactory200, TransactionRequestChargeJsonV200}
import code.api.v2_1_0.{IbanJson, JSONFactory210, PostCounterpartyBespokeJson, ResourceUserJSON}
import code.api.v2_2_0.CounterpartyMetadataJson
import code.api.v3_0_0.JSONFactory300._
import code.api.v3_0_0._
import code.api.v3_1_0.JSONFactory310.{createAccountAttributeJson, createProductAttributesJson}
import code.api.v3_1_0.{AccountAttributeResponseJson, PostHistoricalTransactionResponseJson, ProductAttributeResponseWithoutBankIdJson, RedisCallLimitJson}
import code.apicollection.ApiCollectionTrait
import code.apicollectionendpoint.ApiCollectionEndpointTrait
import code.atms.Atms.Atm
import code.bankattribute.BankAttribute
import code.consent.MappedConsent
import code.entitlement.Entitlement
import code.model.dataAccess.ResourceUser
import code.model.{Consumer, ModeratedBankAccount, ModeratedBankAccountCore}
import code.ratelimiting.RateLimiting
import code.standingorders.StandingOrderTrait
import code.transactionrequests.TransactionRequests.TransactionChallengeTypes
import code.userlocks.UserLocks
import code.users.{UserAgreement, UserInvitation}
import com.openbankproject.commons.model.{DirectDebitTrait, ProductFeeTrait, _}
import net.liftweb.common.{Box, Full}
import net.liftweb.json.JValue
import net.liftweb.mapper.By

import scala.collection.immutable.List
import scala.math.BigDecimal
import scala.util.Try


case class CallLimitPostJsonV400(
                                  from_date : Date,
                                  to_date : Date,
                                  api_version: Option[String],
                                  api_name: Option[String],
                                  bank_id: Option[String],
                                  per_second_call_limit : String,
                                  per_minute_call_limit : String,
                                  per_hour_call_limit : String,
                                  per_day_call_limit : String,
                                  per_week_call_limit : String,
                                  per_month_call_limit : String
                                )

case class CallLimitJsonV400(
                             from_date: Date,
                             to_date: Date,
                             api_version: Option[String],
                             api_name: Option[String],
                             bank_id: Option[String],
                             per_second_call_limit: String,
                             per_minute_call_limit: String,
                             per_hour_call_limit: String,
                             per_day_call_limit: String,
                             per_week_call_limit: String,
                             per_month_call_limit: String,
                             current_state: Option[RedisCallLimitJson]
                           )

case class BankJson400(
                        id: String,
                        short_name: String,
                        full_name: String,
                        logo: String,
                        website: String,
                        bank_routings: List[BankRoutingJsonV121],
                        attributes: Option[List[BankAttributeBankResponseJsonV400]]
                      )

case class BanksJson400(banks: List[BankJson400])


case class ChallengeJsonV400(
                              id: String,
                              user_id: String,
                              allowed_attempts : Int,
                              challenge_type: String,
                              link: String
                             )

case class TransactionRequestWithChargeJSON400(
                                                id: String,
                                                `type`: String,
                                                from: TransactionRequestAccountJsonV140,
                                                details: TransactionRequestBodyAllTypes,
                                                transaction_ids: List[String],
                                                status: String,
                                                start_date: Date,
                                                end_date: Date,
                                                challenges: List[ChallengeJsonV400],
                                                charge : TransactionRequestChargeJsonV200
                                              )
case class PostHistoricalTransactionAtBankJson(
                                                from_account_id: String,
                                                to_account_id: String,
                                                value: AmountOfMoneyJsonV121,
                                                description: String,
                                                posted: String,
                                                completed: String,
                                                `type`: String,
                                                charge_policy: String
                                              )
case class HistoricalTransactionAccountJsonV400(
                                                 bank_id: String,
                                                 account_id : String
                                               )
case class PostHistoricalTransactionResponseJsonV400(
                                                  transaction_id: String,
                                                  from: HistoricalTransactionAccountJsonV400,
                                                  to: HistoricalTransactionAccountJsonV400,
                                                  value: AmountOfMoneyJsonV121,
                                                  description: String,
                                                  posted: Date,
                                                  completed: Date,
                                                  transaction_request_type: String,
                                                  charge_policy: String
                                                )
case class PostResetPasswordUrlJsonV400(username: String, email: String, user_id: String)
case class ResetPasswordUrlJsonV400(reset_password_url: String)

case class PostUserInvitationAnonymousJsonV400(secret_key: Long)
case class PostUserInvitationJsonV400(first_name: String, 
                                      last_name: String, 
                                      email: String, 
                                      company: String, 
                                      country: String,
                                      purpose: String)
case class UserInvitationJsonV400(first_name: String,
                                  last_name: String,
                                  email: String,
                                  company: String,
                                  country: String,
                                  purpose: String,
                                  status: String)
case class UserInvitationsJsonV400(user_invitations: List[UserInvitationJsonV400])

case class UserIdJsonV400(user_id: String)

case class APIInfoJson400(
                        version : String,
                        version_status: String,
                        git_commit : String,
                        connector : String,
                        hosted_by : HostedBy400,
                        hosted_at : HostedAt400,
                        energy_source : EnergySource400,
                        resource_docs_requires_role: Boolean
                      )
case class HostedBy400(
                     organisation : String,
                     email : String,
                     phone : String,
                     organisation_website: String
                   )
case class HostedAt400(
                     organisation : String,
                     organisation_website: String
                   )
case class EnergySource400(
                         organisation : String,
                         organisation_website: String
                       )

case class ModeratedCoreAccountJsonV400(
                                         id: String,
                                         bank_id: String,
                                         label: String,
                                         number: String,
                                         product_code: String,
                                         balance: AmountOfMoneyJsonV121,
                                         account_routings: List[AccountRoutingJsonV121],
                                         views_basic: List[String]
                                       )

case class ModeratedFirehoseAccountJsonV400(
                                             id: String,
                                             bank_id: String,
                                             label: String,
                                             number: String,
                                             owners: List[UserJSONV121],
                                             product_code: String,
                                             balance: AmountOfMoneyJsonV121,
                                             account_routings: List[AccountRoutingJsonV121],
                                             account_rules: List[AccountRuleJsonV300],
                                             account_attributes: Option[List[AccountAttributeResponseJson]] = None
                                           )

case class ModeratedFirehoseAccountsJsonV400(
                                              accounts: List[ModeratedFirehoseAccountJsonV400]
                                            )

case class FastFirehoseAccountJsonV400(
  id: String,
  bank_id: String,
  label: String,
  number: String,
  owners: String,
  product_code: String,
  balance: AmountOfMoneyJsonV121,
  account_routings: String ,
  account_attributes: String
)

case class FastFirehoseAccountsJsonV400(
  accounts: List[FastFirehoseAccountJsonV400]
)

case class ModeratedAccountJSON400(
                                    id : String,
                                    label : String,
                                    number : String,
                                    owners : List[UserJSONV121],
                                    product_code : String,
                                    balance : AmountOfMoneyJsonV121,
                                    views_available : List[ViewJSONV121],
                                    bank_id : String,
                                    account_routings :List[AccountRoutingJsonV121],
                                    account_attributes: List[AccountAttributeResponseJson],
                                    tags: List[AccountTagJSON]
                                  )

case class ModeratedAccountsJSON400(
                                     accounts: List[ModeratedAccountJSON400]
                                   )

case class AccountTagJSON(
                           id : String,
                           value : String,
                           date : Date,
                           user : UserJSONV121
                         )

case class EntitlementJsonV400(
                                entitlement_id: String,
                                role_name: String,
                                bank_id: String,
                                user_id: String
                              )

case class EntitlementsJsonV400(
                                list: List[EntitlementJsonV400]
                              )

case class AccountTagsJSON(
                            tags: List[AccountTagJSON]
                          )
case class PostAccountTagJSON(
                               value : String
                             )

case class UpdateAccountJsonV400(label : String)

case class AccountsBalancesJsonV400(accounts:List[AccountBalanceJsonV400])

case class BalanceJsonV400(`type`: String, currency: String, amount: String)

case class AccountBalanceJsonV400(
                                   account_id: String,
                                   bank_id: String,
                                   account_routings: List[AccountRouting],
                                   label: String,
                                   balances: List[BalanceJsonV400]
                                 )

case class AccountBalancesJsonV400(
  account_id: String,
  bank_id: String,
  account_routings: List[AccountRouting],
  label: String,
  balances: List[BalanceJsonV400],
  
)

case class PostCustomerPhoneNumberJsonV400(mobile_phone_number: String)
case class PostDirectDebitJsonV400(customer_id: String,
                                   user_id: String,
                                   counterparty_id: String,
                                   date_signed: Option[Date],
                                   date_starts: Date, 
                                   date_expires: Option[Date]
                                  )

case class DirectDebitJsonV400(direct_debit_id: String,
                               bank_id: String,
                               account_id: String,
                               customer_id: String,
                               user_id: String,
                               counterparty_id: String,
                               date_signed: Date,
                               date_starts: Date,
                               date_expires: Date,
                               date_cancelled: Date,
                               active: Boolean)
case class When(frequency: String, detail: String)
case class PostStandingOrderJsonV400(customer_id: String,
                                     user_id: String,
                                     counterparty_id: String,
                                     amount : AmountOfMoneyJsonV121,
                                     when: When,
                                     date_signed: Option[Date],
                                     date_starts: Date,
                                     date_expires: Option[Date]
                                    )

case class StandingOrderJsonV400(standing_order_id: String,
                                 bank_id: String,
                                 account_id: String,
                                 customer_id: String,
                                 user_id: String,
                                 counterparty_id: String,
                                 amount: AmountOfMoneyJsonV121,
                                 when: When,
                                 date_signed: Date,
                                 date_starts: Date,
                                 date_expires: Date,
                                 date_cancelled: Date,
                                 active: Boolean)
case class PostViewJsonV400(view_id: String, is_system: Boolean)
case class PostAccountAccessJsonV400(user_id: String, view: PostViewJsonV400)
case class PostRevokeGrantAccountAccessJsonV400(views: List[String])
case class RevokedJsonV400(revoked: Boolean)

case class ConsentJsonV400(consent_id: String, jwt: String, status: String, api_standard: String, api_version: String)
case class ConsentsJsonV400(consents: List[ConsentJsonV400])
case class ConsentInfoJsonV400(consent_id: String, 
                               consumer_id: String,
                               created_by_user_id: String, 
                               last_action_date: String, 
                               last_usage_date: String, 
                               status: String, 
                               api_standard: String, 
                               api_version: String)
case class ConsentInfosJsonV400(consents: List[ConsentInfoJsonV400])

case class TransactionRequestBodySEPAJsonV400(
                                               value: AmountOfMoneyJsonV121,
                                               to: IbanJson,
                                               description: String,
                                               charge_policy: String,
                                               future_date: Option[String] = None,
                                               reasons: Option[List[TransactionRequestReasonJsonV400]] = None
                                             ) extends TransactionRequestCommonBodyJSON

case class TransactionRequestReasonJsonV400(
                                             code: String,
                                             document_number: Option[String],
                                             amount: Option[String],
                                             currency: Option[String],
                                             description: Option[String]
                                           ) {
  def transform: TransactionRequestReason = {
    TransactionRequestReason(
      code = this.code,
      documentNumber = this.document_number,
      currency = this.currency,
      amount = this.amount,
      description = this.description
    )
  }
}
// the data from endpoint, extract as valid JSON
case class TransactionRequestBodyRefundJsonV400(
  to: Option[TransactionRequestRefundTo],
  from: Option[TransactionRequestRefundFrom],
  value: AmountOfMoneyJsonV121,
  description: String,
  refund: RefundJson
) extends TransactionRequestCommonBodyJSON

case class TransactionRequestRefundTo(
                                       bank_id: Option[String],
                                       account_id : Option[String],
                                       counterparty_id: Option[String]
                                     )

case class TransactionRequestRefundFrom(
                                         counterparty_id: String
                                       )

case class RefundJson(
  transaction_id: String,
  reason_code: String
)

case class CustomerAttributeJsonV400(
  name: String,
  `type`: String,
  value: String,
)

case class CustomerAttributesResponseJson(
  customer_attributes: List[CustomerAttributeResponseJsonV300]
)
case class TransactionAttributeJsonV400(
  name: String,
  `type`: String,
  value: String,
)

case class TransactionAttributeResponseJson(
  transaction_attribute_id: String,
  name: String,
  `type`: String,
  value: String
)

case class TransactionAttributesResponseJson(
  transaction_attributes: List[TransactionAttributeResponseJson]
)

case class TransactionRequestAttributeJsonV400(
  name: String,
  `type`: String,
  value: String,
)

case class TransactionRequestAttributeResponseJson(
  transaction_request_attribute_id: String,
  name: String,
  `type`: String,
  value: String
)

case class TransactionRequestAttributesResponseJson(
  transaction_request_attributes: List[TransactionRequestAttributeResponseJson]
)

case class ConsumerJson(consumer_id: String,
                        key: String,
                        secret: String,
                        app_name: String,
                        app_type: String,
                        description: String,
                        client_certificate: String,
                        developer_email: String,
                        redirect_url: String,
                        created_by_user_id: String,
                        created_by_user: ResourceUserJSON,
                        enabled: Boolean,
                        created: Date
                       )

case class AttributeDefinitionJsonV400(
                                        name: String,
                                        category: String,
                                        `type`: String,
                                        description: String,
                                        alias: String,
                                        can_be_seen_on_views: List[String],
                                        is_active: Boolean
                                      )

case class AttributeDefinitionResponseJsonV400(attribute_definition_id: String,
                                               bank_id: String,
                                               name: String,
                                               category: String,
                                               `type`: String,
                                               description: String,
                                               alias: String,
                                               can_be_seen_on_views: List[String],
                                               is_active: Boolean
                                              )

case class AttributeDefinitionsResponseJsonV400(
                                                 attributes: List[AttributeDefinitionResponseJsonV400]
                                               )
case class UserLockStatusJson(
                               user_id : String,
                               type_of_lock: String,
                               last_lock_date : Date
                             )

case class LogoutLinkJson(link: String)

case class DatabaseInfoJson(product_name: String, product_version: String)

case class ChallengeJson(
  challenge_id: String,
  transaction_request_id: String,
  expected_user_id: String
)

case class SettlementAccountRequestJson(
                                         user_id: String,
                                         payment_system: String,
                                         balance: AmountOfMoneyJsonV121,
                                         label: String,
                                         branch_id : String,
                                         account_routings: List[AccountRoutingJsonV121]
                                        )

case class SettlementAccountResponseJson(
                                         account_id: String,
                                         user_id: String,
                                         payment_system: String,
                                         balance: AmountOfMoneyJsonV121,
                                         label: String,
                                         branch_id : String,
                                         account_routings: List[AccountRoutingJsonV121],
                                         account_attributes: List[AccountAttributeResponseJson]
                                        )

case class SettlementAccountJson(
                                  account_id: String,
                                  payment_system: String,
                                  balance: AmountOfMoneyJsonV121,
                                  label: String,
                                  branch_id : String,
                                  account_routings: List[AccountRoutingJsonV121],
                                  account_attributes: List[AccountAttributeResponseJson]
                                )

case class SettlementAccountsJson(
                                  settlement_accounts: List[SettlementAccountJson]
                                 )

case class CounterpartyWithMetadataJson400(
                                         name: String,
                                         description: String,
                                         currency: String,
                                         created_by_user_id: String,
                                         this_bank_id: String,
                                         this_account_id: String,
                                         this_view_id: String,
                                         counterparty_id: String,
                                         other_bank_routing_scheme: String,
                                         other_bank_routing_address: String,
                                         other_branch_routing_scheme: String,
                                         other_branch_routing_address: String,
                                         other_account_routing_scheme: String,
                                         other_account_routing_address: String,
                                         other_account_secondary_routing_scheme: String,
                                         other_account_secondary_routing_address: String,
                                         is_beneficiary: Boolean,
                                         bespoke:List[PostCounterpartyBespokeJson],
                                         metadata: CounterpartyMetadataJson
                                       )

case class PostCounterpartyJson400(
                                    name: String,
                                    description: String,
                                    currency: String,
                                    other_account_routing_scheme: String,
                                    other_account_routing_address: String,
                                    other_account_secondary_routing_scheme: String,
                                    other_account_secondary_routing_address: String,
                                    other_bank_routing_scheme: String,
                                    other_bank_routing_address: String,
                                    other_branch_routing_scheme: String,
                                    other_branch_routing_address: String,
                                    is_beneficiary: Boolean,
                                    bespoke: List[PostCounterpartyBespokeJson]
                                  )
case class DynamicEndpointHostJson400(
  host: String
)

case class EndpointTagJson400(
  tag_name: String,
)

case class SystemLevelEndpointTagResponseJson400(
  endpoint_tag_id: String,
  operation_id: String,
  tag_name: String
)

case class BankLevelEndpointTagResponseJson400(
  bank_id: String,
  endpoint_tag_id: String,
  operation_id: String,
  tag_name: String
)

case class MySpaces(
  bank_ids: List[String],
)

case class ProductJsonV400(
  bank_id: String,
  product_code: String,
  parent_product_code: String,
  name: String,
  more_info_url: String,
  terms_and_conditions_url: String,
  description: String,
  meta: MetaJsonV140,
  attributes: Option[List[ProductAttributeResponseWithoutBankIdJson]],
  fees: Option[List[ProductFeeJsonV400]]
)

case class ProductsJsonV400(products: List[ProductJsonV400])

case class PutProductJsonV400(
  parent_product_code: String,
  name: String,
  more_info_url: String,
  terms_and_conditions_url: String,
  description: String,
  meta: MetaJsonV140,
)
case class CounterpartyJson400(
                                 name: String,
                                 description: String,
                                 currency: String,
                                 created_by_user_id: String,
                                 this_bank_id: String,
                                 this_account_id: String,
                                 this_view_id: String,
                                 counterparty_id: String,
                                 other_bank_routing_scheme: String,
                                 other_bank_routing_address: String,
                                 other_branch_routing_scheme: String,
                                 other_branch_routing_address: String,
                                 other_account_routing_scheme: String,
                                 other_account_routing_address: String,
                                 other_account_secondary_routing_scheme: String,
                                 other_account_secondary_routing_address: String,
                                 is_beneficiary: Boolean,
                                 bespoke:List[PostCounterpartyBespokeJson]
                               )

case class CounterpartiesJson400(
                                   counterparties: List[CounterpartyJson400]
                                 )

case class PutConsentStatusJsonV400(status: String)
case class PutConsentUserJsonV400(user_id: String)
case class BankAccountRoutingJson(
                                 bank_id: Option[String],
                                 account_routing: AccountRoutingJsonV121
                                 )

case class ChallengeAnswerJson400 (
                                 id: String,
                                 answer: String,
                                 reason_code: Option[String] = None,
                                 additional_information: Option[String] = None
                               )

case class ApiCollectionJson400 (
  api_collection_id: String,
  user_id: String,
  api_collection_name: String,
  is_sharable: Boolean,
  description: String
)
case class ApiCollectionsJson400 (
  api_collections: List[ApiCollectionJson400] 
)

case class PostApiCollectionJson400(
  api_collection_name: String,
  is_sharable: Boolean,
  description: String
)

case class ApiCollectionEndpointJson400 (
  api_collection_endpoint_id: String,
  api_collection_id: String,
  operation_id: String
)

case class ApiCollectionEndpointsJson400(
  api_collection_endpoints: List[ApiCollectionEndpointJson400]
)

case class PostApiCollectionEndpointJson400(
  operation_id: String
)
// Validation related START
case class JsonSchemaV400(
    $schema: String,
    description: String,
    title: String,
    required: List[String],
    `type`: String,
    properties: Properties,
    additionalProperties: Boolean
  )
  case class Properties (xxx_id: XxxId)
  case class XxxId (
    `type`: String,
    minLength: Int,
    maxLength: Int,
    examples: List[String]
  )
case class JsonValidationV400(operation_id: String, json_schema: JsonSchemaV400)
// Validation related END

case class ProductAttributeJsonV400(
                                     name: String,
                                     `type`: String,
                                     value: String,
                                     is_active: Option[Boolean]
                                   )
case class ProductAttributeResponseJsonV400(
                                         bank_id: String,
                                         product_code: String,
                                         product_attribute_id: String,
                                         name: String,
                                         `type`: String,
                                         value: String,
                                         is_active: Option[Boolean]
                                       )
case class ProductAttributeResponseWithoutBankIdJsonV400(
                                                      product_code: String,
                                                      product_attribute_id: String,
                                                      name: String,
                                                      `type`: String,
                                                      value: String,
                                                      is_active: Option[Boolean]
                                                    )

case class BankAttributeJsonV400(
                                  name: String,
                                  `type`: String,
                                  value: String,
                                  is_active: Option[Boolean])

case class BankAttributeResponseJsonV400(
                                          bank_id: String,
                                          bank_attribute_id: String,
                                          name: String,
                                          `type`: String,
                                          value: String,
                                          is_active: Option[Boolean]
                                        )
case class BankAttributesResponseJsonV400(bank_attributes: List[BankAttributeResponseJsonV400])
case class BankAttributeBankResponseJsonV400(name: String,
                                             value: String)
case class BankAttributesResponseJson(list: List[BankAttributeBankResponseJsonV400])

case class ProductFeeValueJsonV400(
  currency: String,
  amount: BigDecimal,
  frequency: String,
  `type`: String,
)

case class ProductFeeJsonV400(
  product_fee_id:Option[String],
  name: String,
  is_active: Boolean,
  more_info: String,
  value:ProductFeeValueJsonV400,
)

case class ProductFeeResponseJsonV400(
  bank_id: String,
  product_code: String,
  product_fee_id: String,
  name: String,
  is_active: Boolean,
  more_info: String,
  value:ProductFeeValueJsonV400,
)

case class ProductFeesResponseJsonV400(
  product_fees: List[ProductFeeResponseJsonV400]
)

case class IbanCheckerJsonV400(
                                is_valid: Boolean,
                                details: Option[IbanDetailsJsonV400]
                              )

case class AttributeJsonV400(name: String, value: String)
case class IbanDetailsJsonV400(bank_routings: List[BankRoutingJsonV121],
                               bank: String,
                               branch: String,
                               address: String,
                               city: String,
                               postcode: String,
                               phone: String,
                               country: String,
                               attributes: List[AttributeJsonV400]
                              )

case class DoubleEntryTransactionJson(
                                         transaction_request: TransactionRequestBankAccountJson,
                                         debit_transaction: TransactionBankAccountJson,
                                         credit_transaction: TransactionBankAccountJson
                                         )

case class TransactionRequestBankAccountJson(
                                        bank_id: String,
                                        account_id: String,
                                        transaction_request_id: String
                                        )

case class TransactionBankAccountJson(
                                  bank_id: String,
                                  account_id: String,
                                  transaction_id: String
                                  )

case class ResourceDocFragment(
                                requestVerb: String,
                                requestUrl: String,
                                exampleRequestBody: Option[JValue],
                                successResponseBody: Option[JValue]
                           ) extends JsonFieldReName

case class SupportedCurrenciesJson(
  supported_currencies: List[String]
)

case class AtmSupportedCurrenciesJson(
  atm_id: String,
  supported_currencies: List[String]
)

case class SupportedLanguagesJson(
  supported_languages: List[String]
)

case class AtmSupportedLanguagesJson(
  atm_id: String,
  supported_languages: List[String]
)

case class AccessibilityFeaturesJson(
  accessibility_features: List[String]
)

case class AtmAccessibilityFeaturesJson(
  atm_id: String,
  accessibility_features: List[String]
)

case class AtmServicesJsonV400(
  services: List[String]
)

case class AtmServicesResponseJsonV400(
  atm_id: String,
  services: List[String]
)

case class AtmNotesJsonV400(
  notes: List[String]
)

case class AtmNotesResponseJsonV400(
  atm_id: String,
  notes: List[String]
)

case class AtmLocationCategoriesJsonV400(
  location_categories: List[String]
)

case class AtmLocationCategoriesResponseJsonV400(
  atm_id: String,
  location_categories: List[String]
)

case class AtmJsonV400 (
  id : Option[String],
  bank_id : String,
  name : String,
  address: AddressJsonV300,
  location: LocationJsonV140,
  meta: MetaJsonV140,

  monday: OpeningTimesV300,
  tuesday: OpeningTimesV300,
  wednesday: OpeningTimesV300,
  thursday: OpeningTimesV300,
  friday: OpeningTimesV300,
  saturday: OpeningTimesV300,
  sunday: OpeningTimesV300,

  is_accessible : String,
  located_at : String,
  more_info : String,
  has_deposit_capability : String,
  
  supported_languages: List[String],
  services: List[String],
  accessibility_features: List[String],
  supported_currencies: List[String],
  notes: List[String],
  location_categories: List[String],
  minimum_withdrawal: String,
  branch_identification: String,
  site_identification: String,
  site_name: String,
  cash_withdrawal_national_fee: String,
  cash_withdrawal_international_fee: String,
  balance_inquiry_fee: String
)

case class AtmsJsonV400(atms : List[AtmJsonV400])

case class UserAgreementJson(`type`: String, text: String)
case class UserJsonV400(
                         user_id: String,
                         email : String,
                         provider_id: String,
                         provider : String,
                         username : String,
                         entitlements : EntitlementJSONs,
                         views: Option[ViewsJSON300],
                         agreements: Option[List[UserAgreementJson]],
                         is_deleted: Boolean,
                         last_marketing_agreement_signed_date: Option[Date]
                       )
case class UsersJsonV400(users: List[UserJsonV400])

object JSONFactory400 {

  def createUserInfoJSON(user : User, entitlements: List[Entitlement], agreements: Option[List[UserAgreement]]) : UserJsonV400 = {
    UserJsonV400(
      user_id = user.userId,
      email = user.emailAddress,
      username = stringOrNull(user.name),
      provider_id = user.idGivenByProvider,
      provider = stringOrNull(user.provider),
      entitlements = JSONFactory200.createEntitlementJSONs(entitlements),
      views = None,
      agreements = agreements.map(_.map( i => 
        UserAgreementJson(`type` = i.agreementType, text = i.agreementText))
      ),
      is_deleted = user.isDeleted.getOrElse(false),
      last_marketing_agreement_signed_date = user.lastMarketingAgreementSignedDate
    )
  }

  def createUsersJson(users : List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]) : UsersJsonV400 = {
    UsersJsonV400(
      users.map(t => 
        createUserInfoJSON(
          t._1, 
          t._2.getOrElse(Nil),
          t._3
        )
      )
    )
  }

  def createCallsLimitJson(rateLimiting: RateLimiting) : CallLimitJsonV400 = {
    CallLimitJsonV400(
      rateLimiting.fromDate,
      rateLimiting.toDate,
      rateLimiting.apiVersion,
      rateLimiting.apiName,
      rateLimiting.bankId,
      rateLimiting.perSecondCallLimit.toString,
      rateLimiting.perMinuteCallLimit.toString,
      rateLimiting.perHourCallLimit.toString,
      rateLimiting.perDayCallLimit.toString,
      rateLimiting.perWeekCallLimit.toString,
      rateLimiting.perMonthCallLimit.toString,
      None
    )

  }
  
  def createBankJSON400(bank: Bank, attributes: List[BankAttribute] = Nil): BankJson400 = {
    val obp = BankRoutingJsonV121("OBP", bank.bankId.value)
    val bic = BankRoutingJsonV121("BIC", bank.swiftBic)
    val routings = bank.bankRoutingScheme match {
      case "OBP" => bic :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
      case "BIC" => obp :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
      case _ => obp :: bic :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
    }
    new BankJson400(
      stringOrNull(bank.bankId.value),
      stringOrNull(bank.shortName),
      stringOrNull(bank.fullName),
      stringOrNull(bank.logoUrl),
      stringOrNull(bank.websiteUrl),
      routings.filter(a => stringOrNull(a.address) != null),
      Option(
        attributes.filter(_.isActive == Some(true)).map(a => BankAttributeBankResponseJsonV400(
          name = a.name, 
          value = a.value)
        )
      )
    )
  }

  def createBanksJson(l: List[Bank]): BanksJson400 = {
    BanksJson400(l.map(i => createBankJSON400(i, Nil)))
  }

  def createUserIdInfoJson(user : User) : UserIdJsonV400 = {
    UserIdJsonV400(user_id = user.userId)
  }

  def createSettlementAccountJson(userId: String, account: BankAccount, accountAttributes: List[AccountAttribute]): SettlementAccountResponseJson =
    SettlementAccountResponseJson(
      account_id = account.accountId.value,
      user_id = userId,
      label = account.label,
      payment_system = account.accountId.value.split("_SETTLEMENT_ACCOUNT").headOption.getOrElse(""),
      balance = AmountOfMoneyJsonV121(
        account.currency,
        account.balance.toString()
      ),
      branch_id = account.branchId,
      account_routings = account.accountRoutings.map(r => AccountRoutingJsonV121(r.scheme, r.address)),
      account_attributes = accountAttributes.map(createAccountAttributeJson)
    )

  def getSettlementAccountJson(account: BankAccount, accountAttributes: List[AccountAttribute]): SettlementAccountJson =
    SettlementAccountJson(
      account_id = account.accountId.value,
      label = account.label,
      payment_system = account.accountId.value.split("_SETTLEMENT_ACCOUNT").headOption.getOrElse(""),
      balance = AmountOfMoneyJsonV121(
        account.currency,
        account.balance.toString()
      ),
      branch_id = account.branchId,
      account_routings = account.accountRoutings.map(r => AccountRoutingJsonV121(r.scheme, r.address)),
      account_attributes = accountAttributes.map(createAccountAttributeJson)
    )

  def createTransactionRequestWithChargeJSON(tr : TransactionRequest, challenges: List[ChallengeJson]) : TransactionRequestWithChargeJSON400 = {
    new TransactionRequestWithChargeJSON400(
      id = stringOrNull(tr.id.value),
      `type` = stringOrNull(tr.`type`),
      from = try{TransactionRequestAccountJsonV140 (
        bank_id = stringOrNull(tr.from.bank_id),
        account_id = stringOrNull(tr.from.account_id)
      )} catch {case _ : Throwable => null},
      details = try{tr.body} catch {case _ : Throwable => null},
      transaction_ids = tr.transaction_ids::Nil,
      status = stringOrNull(tr.status),
      start_date = tr.start_date,
      end_date = tr.end_date,
      // Some (mapped) data might not have the challenge. TODO Make this nicer
      challenges = {
        try {
          val otpViaWebFormPath = APIUtil.getPropsValue("hostname", "") + List(
            "/otp?flow=transaction_request&bankId=",
            stringOrNull(tr.from.bank_id),
            "&accountId=",
            stringOrNull(tr.from.account_id),
            "&viewId=owner",
            "&transactionRequestType=",
            stringOrNull(tr.`type`),
            "&transactionRequestId=",
            stringOrNull(tr.id.value),
            "&id=",
            stringOrNull(tr.challenge.id)
          ).mkString("")
          
          val otpViaApiPath = APIUtil.getPropsValue("hostname", "") + List(
            "/obp/v4.0.0/banks/",
            stringOrNull(tr.from.bank_id),
            "/accounts/",
            stringOrNull(tr.from.account_id),
            "/owner",
            "/transaction-request-types/",
            stringOrNull(tr.`type`),
            "/transaction-requests/",
            stringOrNull(tr.id.value),
            "/challenge").mkString("")
          val link = tr.challenge.challenge_type match  {
            case challengeType if challengeType == TransactionChallengeTypes.OTP_VIA_WEB_FORM.toString => otpViaWebFormPath
            case challengeType if challengeType == TransactionChallengeTypes.OTP_VIA_API.toString => otpViaApiPath
            case _ => ""
          }
          challenges.map(
            e => ChallengeJsonV400(id = stringOrNull(e.challenge_id), user_id = e.expected_user_id, allowed_attempts = tr.challenge.allowed_attempts, challenge_type = stringOrNull(tr.challenge.challenge_type), link = link)
          )
        }
        // catch { case _ : Throwable => ChallengeJSON (id = "", allowed_attempts = 0, challenge_type = "")}
        catch { case _ : Throwable => null}
      },
      charge = try {TransactionRequestChargeJsonV200 (summary = stringOrNull(tr.charge.summary),
        value = AmountOfMoneyJsonV121(currency = stringOrNull(tr.charge.value.currency),
          amount = stringOrNull(tr.charge.value.amount))
      )} catch {case _ : Throwable => null}
    )
  }

  
  def createNewCoreBankAccountJson(account : ModeratedBankAccountCore, 
                                   availableViews: List[View]) : ModeratedCoreAccountJsonV400 =  {
    ModeratedCoreAccountJsonV400 (
      account.accountId.value,
      stringOrNull(account.bankId.value),
      stringOptionOrNull(account.label),
      stringOptionOrNull(account.number),
      stringOptionOrNull(account.accountType),
      createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance.getOrElse("")),
      createAccountRoutingsJSON(account.accountRoutings),
      views_basic = availableViews.map(view => view.viewId.value)
    )
  }


  def createBankAccountJSON(account : ModeratedBankAccountCore,
                            viewsAvailable : List[ViewJSONV121],
                            accountAttributes: List[AccountAttribute],
                            tags: List[TransactionTag]) : ModeratedAccountJSON400 =  {
    new ModeratedAccountJSON400(
      account.accountId.value,
      stringOptionOrNull(account.label),
      stringOptionOrNull(account.number),
      createOwnersJSON(account.owners.getOrElse(Set()), ""),
      stringOptionOrNull(account.accountType),
      createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance.getOrElse("")),
      viewsAvailable,
      stringOrNull(account.bankId.value),
      createAccountRoutingsJSON(account.accountRoutings),
      accountAttributes.map(createAccountAttributeJson),
      tags.map(createAccountTagJSON)
    )
  }


  def createAccountTagsJSON(tags : List[TransactionTag]) : AccountTagsJSON = {
    new AccountTagsJSON(tags.map(createAccountTagJSON))
  }
  def createAccountTagJSON(tag : TransactionTag) : AccountTagJSON = {
    new AccountTagJSON(
      id = tag.id_,
      value = tag.value,
      date = tag.datePosted,
      user = JSONFactory.createUserJSON(tag.postedBy)
    )
  }

  def createFirehoseCoreBankAccountJSON(accounts : List[ModeratedBankAccount], accountAttributes: Option[List[AccountAttribute]] = None) : ModeratedFirehoseAccountsJsonV400 =  {
    def getAttributes(bankId: BankId, accountId: AccountId): Option[List[AccountAttributeResponseJson]] = accountAttributes match {
      case Some(v) =>
        val attributes: List[AccountAttributeResponseJson] =
          v.filter(attr => attr.bankId == bankId && attr.accountId == accountId)
            .map(createAccountAttributeJson)
        Some(attributes)
      case None => None
    }
    ModeratedFirehoseAccountsJsonV400(
      accounts.map(
        account =>
          ModeratedFirehoseAccountJsonV400 (
            account.accountId.value,
            stringOrNull(account.bankId.value),
            stringOptionOrNull(account.label),
            stringOptionOrNull(account.number),
            createOwnersJSON(account.owners.getOrElse(Set()), account.bankName.getOrElse("")),
            stringOptionOrNull(account.accountType),
            createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance),
            createAccountRoutingsJSON(account.accountRoutings),
            createAccountRulesJSON(account.accountRules),
            account_attributes = getAttributes(account.bankId, account.accountId)
          )
      )
    )
  }
  def createFirehoseBankAccountJSON(firehoseAccounts : List[FastFirehoseAccount]) : FastFirehoseAccountsJsonV400 =  {
    FastFirehoseAccountsJsonV400(
      firehoseAccounts.map(
        account =>
          FastFirehoseAccountJsonV400(
            account.id,
            account.bankId,
            account.label,
            account.number,
            account.owners,
            account.productCode,
            AmountOfMoneyJsonV121(account.balance.currency, account.balance.amount),
            account.accountRoutings,
            account.accountAttributes
          )
      )
    )
  }


  def createEntitlementJSONs(entitlements: List[Entitlement]): EntitlementsJsonV400 = {
    EntitlementsJsonV400(entitlements.map(entitlement => EntitlementJsonV400(
      entitlement_id = entitlement.entitlementId,
      role_name = entitlement.roleName,
      bank_id = entitlement.bankId,
      user_id = entitlement.userId
    )))
  }

  def createDirectDebitJSON(directDebit: DirectDebitTrait): DirectDebitJsonV400 = {
    DirectDebitJsonV400(direct_debit_id = directDebit.directDebitId,
      bank_id = directDebit.bankId,
      account_id = directDebit.accountId,
      customer_id = directDebit.customerId,
      user_id = directDebit.userId,
      counterparty_id = directDebit.counterpartyId,
      date_signed = directDebit.dateSigned,
      date_cancelled = directDebit.dateCancelled,
      date_starts = directDebit.dateStarts,
      date_expires = directDebit.dateExpires,
      active = directDebit.active)
  }
  def createStandingOrderJSON(standingOrder: StandingOrderTrait): StandingOrderJsonV400 = {
    StandingOrderJsonV400(standing_order_id = standingOrder.standingOrderId,
      bank_id = standingOrder.bankId,
      account_id = standingOrder.accountId,
      customer_id = standingOrder.customerId,
      user_id = standingOrder.userId,
      counterparty_id = standingOrder.counterpartyId,
      amount = AmountOfMoneyJsonV121(standingOrder.amountValue.toString(), standingOrder.amountCurrency),
      when = When(frequency = standingOrder.whenFrequency, detail = standingOrder.whenDetail),
      date_signed = standingOrder.dateSigned,
      date_cancelled = standingOrder.dateCancelled,
      date_starts = standingOrder.dateStarts,
      date_expires = standingOrder.dateExpires,
      active = standingOrder.active)
  }

  def createCustomerAttributeJson(customerAttribute: CustomerAttribute) : CustomerAttributeResponseJsonV300 = {
    CustomerAttributeResponseJsonV300(
      customer_attribute_id = customerAttribute.customerAttributeId,
      name = customerAttribute.name,
      `type` = customerAttribute.attributeType.toString,
      value = customerAttribute.value
    )
  }

  def createCustomerAttributesJson(customerAttributes: List[CustomerAttribute]) : CustomerAttributesResponseJson = {
    CustomerAttributesResponseJson (customerAttributes.map( customerAttribute => CustomerAttributeResponseJsonV300(
      customer_attribute_id = customerAttribute.customerAttributeId,
      name = customerAttribute.name,
      `type` = customerAttribute.attributeType.toString,
      value = customerAttribute.value
    )))
  }

  def createTransactionAttributeJson(transactionAttribute: TransactionAttribute) : TransactionAttributeResponseJson = {
    TransactionAttributeResponseJson(
      transaction_attribute_id = transactionAttribute.transactionAttributeId,
      name = transactionAttribute.name,
      `type` = transactionAttribute.attributeType.toString,
      value = transactionAttribute.value
    )
  }

  def createTransactionAttributesJson(transactionAttributes: List[TransactionAttribute]) : TransactionAttributesResponseJson = {
    TransactionAttributesResponseJson (transactionAttributes.map( transactionAttribute => TransactionAttributeResponseJson(
      transaction_attribute_id = transactionAttribute.transactionAttributeId,
      name = transactionAttribute.name,
      `type` = transactionAttribute.attributeType.toString,
      value = transactionAttribute.value
    )))
  }

  def createTransactionRequestAttributeJson(transactionRequestAttribute: TransactionRequestAttributeTrait) : TransactionRequestAttributeResponseJson = {
    TransactionRequestAttributeResponseJson(
      transaction_request_attribute_id = transactionRequestAttribute.transactionRequestAttributeId,
      name = transactionRequestAttribute.name,
      `type` = transactionRequestAttribute.attributeType.toString,
      value = transactionRequestAttribute.value
    )
  }

  def createTransactionRequestAttributesJson(transactionRequestAttributes: List[TransactionRequestAttributeTrait]) : TransactionRequestAttributesResponseJson = {
    TransactionRequestAttributesResponseJson (transactionRequestAttributes.map( transactionRequestAttribute => TransactionRequestAttributeResponseJson(
      transaction_request_attribute_id = transactionRequestAttribute.transactionRequestAttributeId,
      name = transactionRequestAttribute.name,
      `type` = transactionRequestAttribute.attributeType.toString,
      value = transactionRequestAttribute.value
    )))
  }

  def createConsumerJSON(c: Consumer, user: Box[User]): ConsumerJson = {

    val resourceUserJSON = user match {
      case Full(resourceUser) => ResourceUserJSON(
        user_id = resourceUser.userId,
        email = resourceUser.emailAddress,
        provider_id = resourceUser.idGivenByProvider,
        provider = resourceUser.provider,
        username = resourceUser.name
      )
      case _ => null
    }

    ConsumerJson(consumer_id=c.consumerId.get,
      key=c.key.get,
      secret=c.secret.get,
      app_name=c.name.get,
      app_type=c.appType.toString(),
      description=c.description.get,
      developer_email=c.developerEmail.get,
      redirect_url=c.redirectURL.get,
      created_by_user_id =c.createdByUserId.get,
      created_by_user =resourceUserJSON,
      enabled=c.isActive.get,
      created=c.createdAt.get,
      client_certificate=c.clientCertificate.get
    )
  }


  def createAttributeDefinitionJson(attributeDefinition: AttributeDefinition) : AttributeDefinitionResponseJsonV400 = {
    AttributeDefinitionResponseJsonV400(
      attribute_definition_id = attributeDefinition.attributeDefinitionId,
      bank_id = attributeDefinition.bankId.value,
      name = attributeDefinition.name,
      category = attributeDefinition.category.toString,
      `type` = attributeDefinition.`type`.toString,
      description = attributeDefinition.description,
      can_be_seen_on_views = attributeDefinition.canBeSeenOnViews,
      alias = attributeDefinition.alias,
      is_active = attributeDefinition.isActive,
    )
  }

  def createAttributeDefinitionsJson(attributeDefinitions: List[AttributeDefinition]) : AttributeDefinitionsResponseJsonV400 = {
    AttributeDefinitionsResponseJsonV400(attributeDefinitions.map(createAttributeDefinitionJson))
  }

  def createUserLockStatusJson(userLock: UserLocks) : UserLockStatusJson = {
    UserLockStatusJson(
      userLock.userId,
      userLock.typeOfLock,
      userLock.lastLockDate)
  }

  def createCounterpartyWithMetadataJson400(counterparty: CounterpartyTrait, counterpartyMetadata: CounterpartyMetadata): CounterpartyWithMetadataJson400 = {
    CounterpartyWithMetadataJson400(
      name = counterparty.name,
      description = counterparty.description,
      currency = counterparty.currency,
      created_by_user_id = counterparty.createdByUserId,
      this_bank_id = counterparty.thisBankId,
      this_account_id = counterparty.thisAccountId,
      this_view_id = counterparty.thisViewId,
      counterparty_id = counterparty.counterpartyId,
      other_bank_routing_scheme = counterparty.otherBankRoutingScheme,
      other_bank_routing_address = counterparty.otherBankRoutingAddress,
      other_account_routing_scheme = counterparty.otherAccountRoutingScheme,
      other_account_routing_address = counterparty.otherAccountRoutingAddress,
      other_account_secondary_routing_scheme = counterparty.otherAccountSecondaryRoutingScheme,
      other_account_secondary_routing_address = counterparty.otherAccountSecondaryRoutingAddress,
      other_branch_routing_scheme = counterparty.otherBranchRoutingScheme,
      other_branch_routing_address =counterparty.otherBranchRoutingAddress,
      is_beneficiary = counterparty.isBeneficiary,
      bespoke = counterparty.bespoke.map(bespoke =>PostCounterpartyBespokeJson(bespoke.key,bespoke.value)),
      metadata=CounterpartyMetadataJson(
        public_alias = counterpartyMetadata.getPublicAlias,
        more_info = counterpartyMetadata.getMoreInfo,
        url = counterpartyMetadata.getUrl,
        image_url = counterpartyMetadata.getImageURL,
        open_corporates_url = counterpartyMetadata.getOpenCorporatesURL,
        corporate_location = JSONFactory210.createLocationJSON(counterpartyMetadata.getCorporateLocation),
        physical_location = JSONFactory210.createLocationJSON(counterpartyMetadata.getPhysicalLocation),
        private_alias = counterpartyMetadata.getPrivateAlias
      )
    )
  }

  def createCounterpartyJson400(counterparty: CounterpartyTrait): CounterpartyJson400 = {
    CounterpartyJson400(
      name = counterparty.name,
      description = counterparty.description,
      currency = counterparty.currency,
      created_by_user_id = counterparty.createdByUserId,
      this_bank_id = counterparty.thisBankId,
      this_account_id = counterparty.thisAccountId,
      this_view_id = counterparty.thisViewId,
      counterparty_id = counterparty.counterpartyId,
      other_bank_routing_scheme = counterparty.otherBankRoutingScheme,
      other_bank_routing_address = counterparty.otherBankRoutingAddress,
      other_account_routing_scheme = counterparty.otherAccountRoutingScheme,
      other_account_routing_address = counterparty.otherAccountRoutingAddress,
      other_account_secondary_routing_scheme = counterparty.otherAccountSecondaryRoutingScheme,
      other_account_secondary_routing_address = counterparty.otherAccountSecondaryRoutingAddress,
      other_branch_routing_scheme = counterparty.otherBranchRoutingScheme,
      other_branch_routing_address =counterparty.otherBranchRoutingAddress,
      is_beneficiary = counterparty.isBeneficiary,
      bespoke = counterparty.bespoke.map(bespoke =>PostCounterpartyBespokeJson(bespoke.key,bespoke.value))
    )
  }

  def createCounterpartiesJson400(counterparties: List[CounterpartyTrait]): CounterpartiesJson400 =
    CounterpartiesJson400(counterparties.map(createCounterpartyJson400))

  def createUserInvitationJson(userInvitation: UserInvitation): UserInvitationJsonV400 = {
    UserInvitationJsonV400(
      first_name = userInvitation.firstName,
      last_name = userInvitation.lastName,
      email = userInvitation.email,
      company = userInvitation.company,
      country = userInvitation.country,
      purpose = userInvitation.purpose,
      status = userInvitation.status
    )
  }

  def createUserInvitationJson(userInvitations: List[UserInvitation]): UserInvitationsJsonV400 = {
    UserInvitationsJsonV400(userInvitations.map(createUserInvitationJson))
  }
  
  def createBalancesJson(accountsBalances: AccountsBalances) = {
    AccountsBalancesJsonV400(
      accounts = accountsBalances.accounts.map(
        account => AccountBalanceJsonV400(
          account_id = account.id,
          bank_id = account.bankId,
          account_routings = account.accountRoutings,
          label = account.label,
          balances = List(
            BalanceJsonV400(`type` = "OpeningBooked", currency = account.balance.currency, amount = account.balance.amount)
          )
        )
      )
    )
  }
  
  def createAccountBalancesJson(accountBalances: AccountBalances) = {
     AccountBalanceJsonV400(
       account_id = accountBalances.id, 
       bank_id = accountBalances.bankId, 
       account_routings = accountBalances.accountRoutings, 
       label = accountBalances.label, 
       balances = accountBalances.balances.map( balance => 
         BalanceJsonV400(`type`=balance.balanceType, currency = balance.balance.currency, amount = balance.balance.amount)
       )
     )
  }

  def createConsentsJsonV400(consents: List[MappedConsent]): ConsentsJsonV400= {
    ConsentsJsonV400(consents.map(c => ConsentJsonV400(c.consentId, c.jsonWebToken, c.status, c.apiStandard, c.apiVersion)))
  }
  def createConsentInfosJsonV400(consents: List[MappedConsent]): ConsentInfosJsonV400= {
    ConsentInfosJsonV400(consents.map(c => 
      ConsentInfoJsonV400(
        c.consentId,
        c.consumerId,
        c.userId,
        if(c.lastActionDate!=null) new SimpleDateFormat(DateWithDay).format(c.lastActionDate) else null, 
        if(c.usesSoFarTodayCounterUpdatedAt!=null) new SimpleDateFormat(DateWithSeconds).format(c.usesSoFarTodayCounterUpdatedAt) else null, 
        c.status, 
        c.apiStandard, 
        c.apiVersion))
    )
  }

  def createApiCollectionJsonV400(apiCollection: ApiCollectionTrait) = {
      ApiCollectionJson400(
        apiCollection.apiCollectionId,
        apiCollection.userId,
        apiCollection.apiCollectionName,
        apiCollection.isSharable,
        apiCollection.description
      )
  }
  def createIbanCheckerJson(iban: IbanChecker): IbanCheckerJsonV400 = {
    val details = iban.details.map(
      i =>
        IbanDetailsJsonV400(
          bank_routings = List(BankRoutingJsonV121("BIC", i.bic)),
          bank = i.bank,
          branch = i.branch,
          address = i.address,
          city = i.city,
          postcode = i.zip,
          phone = i.phone,
          country = i.country,
          attributes = List(
            AttributeJsonV400("country_iso", i.countryIso),
            AttributeJsonV400("sepa_credit_transfer", i.sepaDirectDebit),
            AttributeJsonV400("sepa_direct_debit", i.sepaSddCore),
            AttributeJsonV400("sepa_sdd_core", i.sepaSddCore),
            AttributeJsonV400("sepa_b2b", i.sepaB2b),
            AttributeJsonV400("sepa_card_clearing", i.sepaCardClearing),
          )
        )
    )
    IbanCheckerJsonV400(
      iban.isValid,
      details
    )
  }

  def createDoubleEntryTransactionJson(doubleEntryBookTransaction: DoubleEntryTransaction): DoubleEntryTransactionJson =
    DoubleEntryTransactionJson(
      transaction_request = (for {
        transactionRequestBankId <- doubleEntryBookTransaction.transactionRequestBankId
        transactionRequestAccountId <- doubleEntryBookTransaction.transactionRequestAccountId
        transactionRequestId <- doubleEntryBookTransaction.transactionRequestId
      } yield TransactionRequestBankAccountJson(
        transactionRequestBankId.value,
        transactionRequestAccountId.value,
        transactionRequestId.value
      )).orNull,
      debit_transaction = TransactionBankAccountJson(
        doubleEntryBookTransaction.debitTransactionBankId.value,
        doubleEntryBookTransaction.debitTransactionAccountId.value,
        doubleEntryBookTransaction.debitTransactionId.value
      ),
      credit_transaction = TransactionBankAccountJson(
        doubleEntryBookTransaction.creditTransactionBankId.value,
        doubleEntryBookTransaction.creditTransactionAccountId.value,
        doubleEntryBookTransaction.creditTransactionId.value
      )
    )
  
  def createApiCollectionsJsonV400(apiCollections: List[ApiCollectionTrait]) = {
    ApiCollectionsJson400(apiCollections.map(apiCollection => createApiCollectionJsonV400(apiCollection)))
  }

  def createApiCollectionEndpointJsonV400(apiCollectionEndpoint: ApiCollectionEndpointTrait) = {
    ApiCollectionEndpointJson400(
      apiCollectionEndpoint.apiCollectionEndpointId,
      apiCollectionEndpoint.apiCollectionId,
      apiCollectionEndpoint.operationId
    )
  }


  def createProductAttributeJson(productAttribute: ProductAttribute): ProductAttributeResponseJsonV400 =
    ProductAttributeResponseJsonV400(
      bank_id = productAttribute.bankId.value,
      product_code = productAttribute.productCode.value,
      product_attribute_id = productAttribute.productAttributeId,
      name = productAttribute.name,
      `type` = productAttribute.attributeType.toString,
      value = productAttribute.value,
      is_active = productAttribute.isActive
    )
  def createBankAttributeJson(bankAttribute: BankAttribute): BankAttributeResponseJsonV400 =
    BankAttributeResponseJsonV400(
      bank_id = bankAttribute.bankId.value,
      bank_attribute_id = bankAttribute.bankAttributeId,
      name = bankAttribute.name,
      `type` = bankAttribute.attributeType.toString,
      value = bankAttribute.value,
      is_active = bankAttribute.isActive
    )
  def createBankAttributesJson(bankAttributes: List[BankAttribute]): BankAttributesResponseJsonV400 =
    BankAttributesResponseJsonV400(bankAttributes.map(createBankAttributeJson))
  
    
  def createProductFeeJson(productFee: ProductFeeTrait): ProductFeeResponseJsonV400 =
    ProductFeeResponseJsonV400(
      bank_id = productFee.bankId.value,
      product_code = productFee.productCode.value,
      product_fee_id = productFee.productFeeId,
      name = productFee.name,
      is_active = productFee.isActive,
      more_info = productFee.moreInfo,
      value = ProductFeeValueJsonV400(
        currency = productFee.currency,
        amount = productFee.amount,
        frequency= productFee.frequency,
        `type`= productFee.`type`
      )
    )

  def createProductFeesJson(productFees: List[ProductFeeTrait]): ProductFeesResponseJsonV400 =
    ProductFeesResponseJsonV400(productFees.map(createProductFeeJson))
    

  def createApiCollectionEndpointsJsonV400(apiCollectionEndpoints: List[ApiCollectionEndpointTrait]) = {
    ApiCollectionEndpointsJson400(apiCollectionEndpoints.map(apiCollectionEndpoint => createApiCollectionEndpointJsonV400(apiCollectionEndpoint)))
  }
  def createAtmJsonV400(atm: AtmT): AtmJsonV400 = {
    AtmJsonV400(
      id= Some(atm.atmId.value),
      bank_id= atm.bankId.value,
      name= atm.name,
      AddressJsonV300(atm.address.line1,
        atm.address.line2,
        atm.address.line3,
        atm.address.city,
        atm.address.county.getOrElse(""),
        atm.address.state,
        atm.address.postCode,
        atm.address.countryCode),
      createLocationJson(atm.location),
      createMetaJson(atm.meta),
      monday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnMonday.getOrElse(""),
        closing_time = atm.ClosingTimeOnMonday.getOrElse("")),
      tuesday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnTuesday.getOrElse(""),
        closing_time = atm.ClosingTimeOnTuesday.getOrElse("")),
      wednesday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnWednesday.getOrElse(""),
        closing_time = atm.ClosingTimeOnWednesday.getOrElse("")),
      thursday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnThursday.getOrElse(""),
        closing_time = atm.ClosingTimeOnThursday.getOrElse("")),
      friday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnFriday.getOrElse(""),
        closing_time = atm.ClosingTimeOnFriday.getOrElse("")),
      saturday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnSaturday.getOrElse(""),
        closing_time = atm.ClosingTimeOnSaturday.getOrElse("")),
      sunday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnSunday.getOrElse(""),
        closing_time = atm.ClosingTimeOnSunday.getOrElse("")),
      is_accessible = atm.isAccessible.map(_.toString).getOrElse(""),
      located_at = atm.locatedAt.getOrElse(""),
      more_info = atm.moreInfo.getOrElse(""),
      has_deposit_capability = atm.hasDepositCapability.map(_.toString).getOrElse(""),
      supported_languages = atm.supportedLanguages.getOrElse(Nil),
      services = atm.services.getOrElse(Nil),
      accessibility_features = atm.accessibilityFeatures.getOrElse(Nil),
      supported_currencies = atm.supportedCurrencies.getOrElse(Nil),
      notes = atm.notes.getOrElse(Nil),
      location_categories = atm.locationCategories.getOrElse(Nil),
      minimum_withdrawal = atm.minimumWithdrawal.getOrElse(""),
      branch_identification = atm.branchIdentification.getOrElse(""),
      site_identification = atm.siteIdentification.getOrElse(""),
      site_name = atm.siteName.getOrElse(""),
      cash_withdrawal_national_fee = atm.cashWithdrawalNationalFee.getOrElse(""),
      cash_withdrawal_international_fee = atm.cashWithdrawalInternationalFee.getOrElse(""),
      balance_inquiry_fee = atm.balanceInquiryFee.getOrElse(""),
    )
  }
  
  def createAtmsJsonV400(atmList: List[AtmT]): AtmsJsonV400 = {
      AtmsJsonV400(atmList.map(createAtmJsonV400))
  }
  
  def transformToAtmFromV400(atmJsonV400: AtmJsonV400): Atm = {
    val address : Address = transformToAddressFromV300(atmJsonV400.address) // Note the address in V220 is V140
    val location: Location =  transformToLocationFromV140(atmJsonV400.location)  // Note the location is V140
    val meta: Meta =  transformToMetaFromV140(atmJsonV400.meta)  // Note the meta  is V140
    val isAccessible: Boolean = Try(atmJsonV400.is_accessible.toBoolean).getOrElse(false)
    val hdc: Boolean = Try(atmJsonV400.has_deposit_capability.toBoolean).getOrElse(false)
    
    Atm(
      atmId = AtmId(atmJsonV400.id.getOrElse("")),
      bankId = BankId(atmJsonV400.bank_id),
      name = atmJsonV400.name,
      address = address,
      location = location,
      meta = meta,
      OpeningTimeOnMonday = Some(atmJsonV400.monday.opening_time),
      ClosingTimeOnMonday = Some(atmJsonV400.monday.closing_time),

      OpeningTimeOnTuesday = Some(atmJsonV400.tuesday.opening_time),
      ClosingTimeOnTuesday = Some(atmJsonV400.tuesday.closing_time),

      OpeningTimeOnWednesday = Some(atmJsonV400.wednesday.opening_time),
      ClosingTimeOnWednesday = Some(atmJsonV400.wednesday.closing_time),

      OpeningTimeOnThursday = Some(atmJsonV400.thursday.opening_time),
      ClosingTimeOnThursday = Some(atmJsonV400.thursday.closing_time),

      OpeningTimeOnFriday = Some(atmJsonV400.friday.opening_time),
      ClosingTimeOnFriday = Some(atmJsonV400.friday.closing_time),

      OpeningTimeOnSaturday = Some(atmJsonV400.saturday.opening_time),
      ClosingTimeOnSaturday = Some(atmJsonV400.saturday.closing_time),

      OpeningTimeOnSunday = Some(atmJsonV400.sunday.opening_time),
      ClosingTimeOnSunday = Some(atmJsonV400.sunday.closing_time),
      // Easy access for people who use wheelchairs etc. true or false ""=Unknown
      isAccessible = Some(isAccessible),
      locatedAt = Some(atmJsonV400.located_at),
      moreInfo = Some(atmJsonV400.more_info),
      hasDepositCapability = Some(hdc),

      supportedLanguages = Some(atmJsonV400.supported_languages),
      services = Some(atmJsonV400.services),
      accessibilityFeatures = Some(atmJsonV400.accessibility_features),
      supportedCurrencies = Some(atmJsonV400.supported_currencies),
      notes = Some(atmJsonV400.notes),
      minimumWithdrawal = Some(atmJsonV400.minimum_withdrawal ),
      branchIdentification = Some(atmJsonV400.branch_identification),
      locationCategories = Some(atmJsonV400.location_categories ),
      siteIdentification = Some(atmJsonV400.site_identification),
      siteName = Some(atmJsonV400.site_name),
      cashWithdrawalNationalFee = Some(atmJsonV400.cash_withdrawal_national_fee),
      cashWithdrawalInternationalFee = Some(atmJsonV400.cash_withdrawal_international_fee),
      balanceInquiryFee = Some(atmJsonV400.balance_inquiry_fee)
    )
  }

  def createProductJson(product: Product) : ProductJsonV400 = {
    ProductJsonV400(
      bank_id = product.bankId.toString,
      product_code = product.code.value,
      parent_product_code = product.parentProductCode.value,
      name = product.name,
      more_info_url = product.moreInfoUrl,
      terms_and_conditions_url = product.termsAndConditionsUrl,
      description = product.description,
      meta = createMetaJson(product.meta),
      None,
      None
    )
  }
  def createProductsJson(productsList: List[Product]) : ProductsJsonV400 = {
    ProductsJsonV400(productsList.map(createProductJson))}

  def createProductJson(product: Product, productAttributes: List[ProductAttribute], productFees:List[ProductFeeTrait]) : ProductJsonV400 = {
    ProductJsonV400(
      bank_id = product.bankId.toString,
      product_code = product.code.value,
      parent_product_code = product.parentProductCode.value,
      name = product.name,
      more_info_url = product.moreInfoUrl,
      terms_and_conditions_url = product.termsAndConditionsUrl,
      description = product.description,
      meta = createMetaJson(product.meta),
      attributes = Some(createProductAttributesJson(productAttributes)),
      fees = Some(productFees.map(productFee =>ProductFeeJsonV400(
        product_fee_id= Some(productFee.productFeeId),
        name = productFee.name,
        is_active = productFee.isActive,
        more_info = productFee.moreInfo,
        value = ProductFeeValueJsonV400(
        currency = productFee.currency,
        amount = productFee.amount,
        frequency = productFee.frequency,
        `type` = productFee.`type`
      ))))
    )
  }



  def createPostHistoricalTransactionResponseJson(
                                                   bankId: BankId,
                                                   transactionId: TransactionId,
                                                   fromAccountId: AccountId,
                                                   toAccountId: AccountId,
                                                   value: AmountOfMoneyJsonV121,
                                                   description: String,
                                                   posted: Date,
                                                   completed: Date,
                                                   transactionRequestType: String,
                                                   chargePolicy: String
                                                 ) : PostHistoricalTransactionResponseJsonV400 = {
    PostHistoricalTransactionResponseJsonV400(
      transaction_id = transactionId.value,
      from = HistoricalTransactionAccountJsonV400(bankId.value, fromAccountId.value),
      to = HistoricalTransactionAccountJsonV400(bankId.value, toAccountId.value),
      value: AmountOfMoneyJsonV121,
      description: String,
      posted: Date,
      completed: Date,
      transaction_request_type = transactionRequestType,
      chargePolicy: String
    )
  }
  
  
  
  
  
}

