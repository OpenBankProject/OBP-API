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
package code.api.v6_0_0

import code.api.util.APIUtil.stringOrNull
import code.api.util.RateLimitingPeriod.LimitCallPeriod
import code.api.util._
import code.api.v1_2_1.BankRoutingJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.CustomerFaceImageJson
import code.api.v2_0_0.{EntitlementJSONs, JSONFactory200}
import code.api.v2_1_0.CustomerCreditRatingJSON
import code.api.v3_0_0.{CustomerAttributeResponseJsonV300, UserJsonV300, ViewJSON300, ViewsJSON300}
import code.api.v3_1_0.{RateLimit, RedisCallLimitJson}
import code.api.v4_0_0.{BankAttributeBankResponseJsonV400, UserAgreementJson}
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.model.dataAccess.ResourceUser
import code.users.UserAgreement
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AmountOfMoneyJsonV121, CustomerAttribute, _}
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

case class ActiveCallLimitsJsonV600(
  call_limits: List[CallLimitJsonV600],
  active_at_date: java.util.Date,
  total_per_second_call_limit: Long,
  total_per_minute_call_limit: Long,
  total_per_hour_call_limit: Long,
  total_per_day_call_limit: Long,
  total_per_week_call_limit: Long,
  total_per_month_call_limit: Long
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
  params: Option[String] = None,// This is for eth_sendRawTransaction
  to: String, // this is for eth_sendTransaction eg: 0x addressk
  value: AmountOfMoneyJsonV121,   // currency should be "ETH"; amount string (decimal)
  description: String
) extends TransactionRequestCommonBodyJSON

// This is only for the request JSON body; we will construct `TransactionRequestBodyEthereumJsonV600` for OBP.
case class TransactionRequestBodyEthSendRawTransactionJsonV600(
  params: String,            // eth_sendRawTransaction params field.
  description: String
)

// ---------------- HOLD models (V600) ----------------
case class TransactionRequestBodyHoldJsonV600(
  value: AmountOfMoneyJsonV121,
  description: String
) extends TransactionRequestCommonBodyJSON

case class UserJsonV600(
                         user_id: String,
                         email : String,
                         provider_id: String,
                         provider : String,
                         username : String,
                         entitlements : EntitlementJSONs,
                         views: Option[ViewsJSON300],
                         on_behalf_of: Option[UserJsonV300]
                       )

case class UserV600(user: User, entitlements: List[Entitlement], views: Option[Permission])
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

case class MigrationScriptLogsJsonV600(migration_script_logs: List[MigrationScriptLogJsonV600])

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
  customer_number : String,
  legal_name : String,
  mobile_phone_number : String,
  email : String,
  face_image : CustomerFaceImageJson,
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
  customer_number : String,
  legal_name : String,
  mobile_phone_number : String,
  email : String,
  face_image : CustomerFaceImageJson,
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

object JSONFactory600 extends CustomJsonFormats with MdcLoggable{

  def createCurrentUsageJson(rateLimits: List[((Option[Long], Option[Long]), LimitCallPeriod)]): Option[RedisCallLimitJson] = {
    if (rateLimits.isEmpty) None
    else {
      val grouped: Map[LimitCallPeriod, (Option[Long], Option[Long])] =
        rateLimits.map { case (limits, period) => period -> limits }.toMap

      def getInfo(period: RateLimitingPeriod.Value): Option[RateLimit] =
        grouped.get(period).collect {
          case (Some(x), Some(y)) => RateLimit(Some(x), Some(y))
        }

      Some(
        RedisCallLimitJson(
          getInfo(RateLimitingPeriod.PER_SECOND),
          getInfo(RateLimitingPeriod.PER_MINUTE),
          getInfo(RateLimitingPeriod.PER_HOUR),
          getInfo(RateLimitingPeriod.PER_DAY),
          getInfo(RateLimitingPeriod.PER_WEEK),
          getInfo(RateLimitingPeriod.PER_MONTH)
        )
      )
    }
  }



  def createUserInfoJSON(current_user: UserV600, onBehalfOfUser: Option[UserV600]): UserJsonV600 = {
    UserJsonV600(
      user_id = current_user.user.userId,
      email = current_user.user.emailAddress,
      username = stringOrNull(current_user.user.name),
      provider_id = current_user.user.idGivenByProvider,
      provider = stringOrNull(current_user.user.provider),
      entitlements = JSONFactory200.createEntitlementJSONs(current_user.entitlements),
      views = current_user.views.map(y => ViewsJSON300(y.views.map((v => ViewJSON300(v.bankId.value, v.accountId.value, v.viewId.value))))),
      on_behalf_of = onBehalfOfUser.map { obu =>
        UserJsonV300(
          user_id = obu.user.userId,
          email = obu.user.emailAddress,
          username = stringOrNull(obu.user.name),
          provider_id = obu.user.idGivenByProvider,
          provider = stringOrNull(obu.user.provider),
          entitlements = JSONFactory200.createEntitlementJSONs(obu.entitlements),
          views = obu.views.map(y => ViewsJSON300(y.views.map((v => ViewJSON300(v.bankId.value, v.accountId.value, v.viewId.value)))))
        )
      }
    )
  }

  def createUserInfoJsonV600(user: User, entitlements: List[Entitlement], agreements: Option[List[UserAgreement]], isLocked: Boolean, lastActivityDate: Option[Date], recentOperationIds: List[String]): UserInfoJsonV600 = {
    UserInfoJsonV600(
      user_id = user.userId,
      email = user.emailAddress,
      username = stringOrNull(user.name),
      provider_id = user.idGivenByProvider,
      provider = stringOrNull(user.provider),
      entitlements = JSONFactory200.createEntitlementJSONs(entitlements),
      views = None,
      agreements = agreements.map(_.map(i =>
        UserAgreementJson(`type` = i.agreementType, text = i.agreementText))
      ),
      is_deleted = user.isDeleted.getOrElse(false),
      last_marketing_agreement_signed_date = user.lastMarketingAgreementSignedDate,
      is_locked = isLocked,
      last_activity_date = lastActivityDate,
      recent_operation_ids = recentOperationIds
    )
  }

  def createUsersInfoJsonV600(users: List[(ResourceUser, Box[List[Entitlement]], Option[List[UserAgreement]])]): UsersInfoJsonV600 = {
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

  def createMigrationScriptLogJsonV600(migrationLog: code.migration.MigrationScriptLogTrait): MigrationScriptLogJsonV600 = {
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

  def createMigrationScriptLogsJsonV600(migrationLogs: List[code.migration.MigrationScriptLogTrait]): MigrationScriptLogsJsonV600 = {
    MigrationScriptLogsJsonV600(
      migration_script_logs = migrationLogs.map(createMigrationScriptLogJsonV600)
    )
  }

  def createCallLimitJsonV600(rateLimiting: code.ratelimiting.RateLimiting): CallLimitJsonV600 = {
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

  def createActiveCallLimitsJsonV600(rateLimitings: List[code.ratelimiting.RateLimiting], activeDate: java.util.Date): ActiveCallLimitsJsonV600 = {
    val callLimits = rateLimitings.map(createCallLimitJsonV600)
    ActiveCallLimitsJsonV600(
      call_limits = callLimits,
      active_at_date = activeDate,
      total_per_second_call_limit = rateLimitings.map(_.perSecondCallLimit).sum,
      total_per_minute_call_limit = rateLimitings.map(_.perMinuteCallLimit).sum,
      total_per_hour_call_limit = rateLimitings.map(_.perHourCallLimit).sum,
      total_per_day_call_limit = rateLimitings.map(_.perDayCallLimit).sum,
      total_per_week_call_limit = rateLimitings.map(_.perWeekCallLimit).sum,
      total_per_month_call_limit = rateLimitings.map(_.perMonthCallLimit).sum
    )
  }

  def createTokenJSON(token: String): TokenJSON = {
    TokenJSON(token)
  }

  def createProvidersJson(providers: List[String]): ProvidersJsonV600 = {
    ProvidersJsonV600(providers)
  }

  def createConnectorMethodNamesJson(methodNames: List[String]): ConnectorMethodNamesJsonV600 = {
    ConnectorMethodNamesJsonV600(methodNames.sorted)
  }

  def createBankJSON600(bank: Bank, attributes: List[BankAttributeTrait] = Nil): BankJson600 = {
    val obp = BankRoutingJsonV121("OBP", bank.bankId.value)
    val bic = BankRoutingJsonV121("BIC", bank.swiftBic)
    val routings = bank.bankRoutingScheme match {
      case "OBP" => bic :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
      case "BIC" => obp :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
      case _ => obp :: bic :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
    }
    new BankJson600(
      stringOrNull(bank.bankId.value),
      stringOrNull(bank.shortName),
      stringOrNull(bank.fullName),
      stringOrNull(bank.logoUrl),
      stringOrNull(bank.websiteUrl),
      routings,
      Option(
        attributes.filter(_.isActive == Some(true)).map(a => BankAttributeBankResponseJsonV400(
          name = a.name,
          value = a.value)
        )
      )
    )
  }

  def createCustomerJson(cInfo : Customer) : CustomerJsonV600 = {
    import java.text.SimpleDateFormat
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    
    CustomerJsonV600(
      bank_id = cInfo.bankId.toString,
      customer_id = cInfo.customerId,
      customer_number = cInfo.number,
      legal_name = cInfo.legalName,
      mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email,
      face_image = CustomerFaceImageJson(url = cInfo.faceImage.url,
        date = cInfo.faceImage.date),
      date_of_birth = if (cInfo.dateOfBirth != null) dateFormat.format(cInfo.dateOfBirth) else "",
      relationship_status = cInfo.relationshipStatus,
      dependants = cInfo.dependents,
      dob_of_dependants = cInfo.dobOfDependents.map(d => dateFormat.format(d)),
      credit_rating = Option(CustomerCreditRatingJSON(rating = cInfo.creditRating.rating, source = cInfo.creditRating.source)),
      credit_limit = Option(AmountOfMoneyJsonV121(currency = cInfo.creditLimit.currency, amount = cInfo.creditLimit.amount)),
      highest_education_attained = cInfo.highestEducationAttained,
      employment_status = cInfo.employmentStatus,
      kyc_status = cInfo.kycStatus,
      last_ok_date = cInfo.lastOkDate,
      title = cInfo.title,
      branch_id = cInfo.branchId,
      name_suffix = cInfo.nameSuffix
    )
  }

  def createCustomersJson(customers : List[Customer]) : CustomerJSONsV600 = {
    CustomerJSONsV600(customers.map(createCustomerJson))
  }

  def createCustomerWithAttributesJson(cInfo : Customer, customerAttributes: List[CustomerAttribute]) : CustomerWithAttributesJsonV600 = {
    import java.text.SimpleDateFormat
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    
    CustomerWithAttributesJsonV600(
      bank_id = cInfo.bankId.toString,
      customer_id = cInfo.customerId,
      customer_number = cInfo.number,
      legal_name = cInfo.legalName,
      mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email,
      face_image = CustomerFaceImageJson(url = cInfo.faceImage.url,
        date = cInfo.faceImage.date),
      date_of_birth = if (cInfo.dateOfBirth != null) dateFormat.format(cInfo.dateOfBirth) else "",
      relationship_status = cInfo.relationshipStatus,
      dependants = cInfo.dependents,
      dob_of_dependants = cInfo.dobOfDependents.map(d => dateFormat.format(d)),
      credit_rating = Option(CustomerCreditRatingJSON(rating = cInfo.creditRating.rating, source = cInfo.creditRating.source)),
      credit_limit = Option(AmountOfMoneyJsonV121(currency = cInfo.creditLimit.currency, amount = cInfo.creditLimit.amount)),
      highest_education_attained = cInfo.highestEducationAttained,
      employment_status = cInfo.employmentStatus,
      kyc_status = cInfo.kycStatus,
      last_ok_date = cInfo.lastOkDate,
      title = cInfo.title,
      branch_id = cInfo.branchId,
      name_suffix = cInfo.nameSuffix,
      customer_attributes = customerAttributes.map(customerAttribute => CustomerAttributeResponseJsonV300(
        customer_attribute_id = customerAttribute.customerAttributeId,
        name = customerAttribute.name,
        `type` = customerAttribute.attributeType.toString,
        value = customerAttribute.value
      ))
    )
  }

  def createRoleWithEntitlementCountJson(role: String, count: Int): RoleWithEntitlementCountJsonV600 = {
    // Check if the role requires a bank ID by looking it up in ApiRole
    val requiresBankId = try {
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

  def createRolesWithEntitlementCountsJson(rolesWithCounts: List[(String, Int)]): RolesWithEntitlementCountsJsonV600 = {
    RolesWithEntitlementCountsJsonV600(rolesWithCounts.map { case (role, count) => 
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

case class GroupMembershipJsonV600(
  group_id: String,
  user_id: String,
  bank_id: Option[String],
  group_name: String,
  list_of_roles: List[String]
)

case class GroupMembershipsJsonV600(group_memberships: List[GroupMembershipJsonV600])

case class RoleWithEntitlementCountJsonV600(
  role: String,
  requires_bank_id: Boolean,
  entitlement_count: Int
)

case class RolesWithEntitlementCountsJsonV600(roles: List[RoleWithEntitlementCountJsonV600])

case class PostResetPasswordUrlJsonV600(username: String, email: String, user_id: String)

case class ResetPasswordUrlJsonV600(reset_password_url: String)

}
