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

import java.util.Date

import code.api.attributedefinition.AttributeDefinition
import code.api.util.APIUtil
import code.api.util.APIUtil.{stringOptionOrNull, stringOrNull}
import code.api.v1_2_1.JSONFactory.{createAmountOfMoneyJSON, createOwnersJSON}
import code.api.v1_2_1.{BankRoutingJsonV121, JSONFactory, UserJSONV121, ViewJSONV121}
import code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJsonV140
import code.api.v2_0_0.TransactionRequestChargeJsonV200
import code.api.v2_1_0.{IbanJson, JSONFactory210, PostCounterpartyBespokeJson, ResourceUserJSON}
import code.api.v2_2_0.CounterpartyMetadataJson
import code.api.v3_0_0.JSONFactory300.{createAccountRoutingsJSON, createAccountRulesJSON}
import code.api.v3_0_0.{AccountRuleJsonV300, CustomerAttributeResponseJsonV300}
import code.api.v3_1_0.{AccountAttributeResponseJson, RedisCallLimitJson}
import code.api.v3_1_0.JSONFactory310.createAccountAttributeJson
import code.entitlement.Entitlement
import code.model.{Consumer, ModeratedBankAccount, ModeratedBankAccountCore}
import code.apicollectionendpoint.ApiCollectionEndpointTrait
import code.apicollection.ApiCollectionTrait
import code.ratelimiting.RateLimiting
import code.standingorders.StandingOrderTrait
import code.transactionrequests.TransactionRequests.TransactionChallengeTypes
import code.userlocks.UserLocks
import com.openbankproject.commons.model.{DirectDebitTrait, _}
import net.liftweb.common.{Box, Full}

import scala.collection.immutable.List


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
                        bank_routings: List[BankRoutingJsonV121]
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
case class PostResetPasswordUrlJsonV400(username: String, email: String, user_id: String)
case class ResetPasswordUrlJsonV400(reset_password_url: String)

case class APIInfoJson400(
                        version : String,
                        version_status: String,
                        git_commit : String,
                        connector : String,
                        hosted_by : HostedBy400,
                        hosted_at : HostedAt400,
                        energy_source : EnergySource400
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
  is_sharable: Boolean
)
case class ApiCollectionsJson400 (
  api_collections: List[ApiCollectionJson400] 
)

case class PostApiCollectionJson400(
  api_collection_name: String,
  is_sharable: Boolean
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

object JSONFactory400 {

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
  
  def createBankJSON400(bank: Bank): BankJson400 = {
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
      routings.filter(a => stringOrNull(a.address) != null)
    )
  }

  def createBanksJson(l: List[Bank]): BanksJson400 = {
    BanksJson400(l.map(createBankJSON400))
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

  def createBalancesJson(accountsBalances: AccountsBalances) = {
    AccountsBalancesJsonV400(
      accounts = accountsBalances.accounts.map(
        account => AccountBalanceJsonV400(
          account_id = account.id,
          bank_id = account.bankId,
          account_routings = account.accountRoutings,
          label = account.label,
          balances = List(
            BalanceJsonV400(`type` = "", currency = account.balance.currency, amount = account.balance.amount)
          )
        )
      )
    )
  }

  def createApiCollectionJsonV400(apiCollection: ApiCollectionTrait) = {
      ApiCollectionJson400(
        apiCollection.apiCollectionId,
        apiCollection.userId,
        apiCollection.apiCollectionName,
        apiCollection.isSharable,
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

  def createApiCollectionEndpointsJsonV400(apiCollectionEndpoints: List[ApiCollectionEndpointTrait]) = {
    ApiCollectionEndpointsJson400(apiCollectionEndpoints.map(apiCollectionEndpoint => createApiCollectionEndpointJsonV400(apiCollectionEndpoint)))
  }
  
}

