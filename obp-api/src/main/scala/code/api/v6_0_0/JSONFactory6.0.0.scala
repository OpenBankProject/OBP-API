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
import code.api.v2_0_0.{EntitlementJSONs, JSONFactory200}
import code.api.v3_0_0.{UserJsonV300, ViewJSON300, ViewsJSON300}
import code.api.v3_1_0.{RateLimit, RedisCallLimitJson}
import code.entitlement.Entitlement
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._

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

case class PostBankJson600(
                            bank_id: String,
                            bank_code: String,
                            full_name: Option[String],
                            logo: Option[String],
                            website: Option[String],
                            bank_routings: Option[List[BankRoutingJsonV121]]
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
}