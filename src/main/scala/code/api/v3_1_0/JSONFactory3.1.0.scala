/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)

 */
package code.api.v3_1_0

import java.util.Date

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.v1_2_1.AccountRoutingJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.BranchRoutingJsonV141
import code.api.v2_1_0.ResourceUserJSON
import code.loginattempts.BadLoginAttempt
import code.metrics.{TopApi, TopConsumer}
import code.model.{Consumer, User}
import net.liftweb.common.{Box, Full}

import scala.collection.immutable.List

case class CheckbookOrdersJson(
  account: AccountV310Json ,
  orders: List[OrderJson]
)

case class AccountV310Json(
  bank_id: String ,
  account_id: String ,
  account_type : String,
  account_routings: List[AccountRoutingJsonV121],
  branch_routings: List[BranchRoutingJsonV141]
)

case class OrderJson(order: OrderObjectJson)

case class OrderObjectJson(
  order_id: String,
  order_date: String,
  number_of_checkbooks: String,
  distribution_channel: String,
  status: String,
  first_check_number: String,
  shipping_code: String
)

case class CardObjectJson(
  card_type: String,
  card_description: String,
  use_type: String
)

case class CreditCardOrderStatusResponseJson(
  cards: List[CardObjectJson] ,
)


case class CreditLimitOrderRequestJson(
  requested_current_rate_amount1: String,
  requested_current_rate_amount2: String,
  requested_current_valid_end_date: String,
  current_credit_documentation: String,
  temporary_requested_current_amount: String,
  requested_temporary_valid_end_date: String,
  temporary_credit_documentation: String
)

case class CreditLimitOrderResponseJson(
  execution_time: String,
  execution_date: String,
  token: String,
  short_reference: String
)

case class CreditLimitOrderJson(
  rank_amount_1: String,
  nominal_interest_1: String,
  rank_amount_2: String,
  nominal_interest_2: String
)


case class TopApiJson(
  count: Int,
  Implemented_by_partial_function: String,
  implemented_in_version: String
)

case class TopApisJson(top_apis : List[TopApiJson])

case class TopConsumerJson(
  count: Int,
  consumer_id: String,
  app_name: String,
  developer_email: String
)

case class TopConsumersJson(top_consumers : List[TopConsumerJson])

case class BadLoginStatusJson(
  username : String,
  bad_attempts_since_last_success_or_reset: Int,
  last_failure_date : Date
)

case class CallLimitJson(
                          per_minute_call_limit : String,
                          per_hour_call_limit : String,
                          per_day_call_limit : String,
                          per_week_call_limit : String,
                          per_month_call_limit : String
                        )
case class CheckFundsAvailableJson(answer: String,
                                   date: Date,
                                   available_funds_request_id: String)

case class ConsumerJson(consumer_id: String,
                        app_name: String,
                        app_type: String,
                        description: String,
                        developer_email: String,
                        redirect_url: String,
                        created_by_user: ResourceUserJSON,
                        enabled: Boolean,
                        created: Date
                       )

object JSONFactory310{
  def createCheckbookOrdersJson(checkbookOrders: CheckbookOrdersJson): CheckbookOrdersJson =
    checkbookOrders

  def createStatisOfCreditCardJson(cards: List[CardObjectJson]): CreditCardOrderStatusResponseJson =
    CreditCardOrderStatusResponseJson(cards)

  def createCreditLimitOrderResponseJson(): CreditLimitOrderResponseJson =
    SwaggerDefinitionsJSON.creditLimitOrderResponseJson

  def getCreditLimitOrderResponseJson(): CreditLimitOrderJson =
    SwaggerDefinitionsJSON.creditLimitOrderJson

  def getCreditLimitOrderByRequestIdResponseJson(): CreditLimitOrderJson =
    SwaggerDefinitionsJSON.creditLimitOrderJson

  def createTopApisJson(topApis: List[TopApi]): TopApisJson ={
    TopApisJson(topApis.map(topApi => TopApiJson(topApi.count, topApi.ImplementedByPartialFunction, topApi.implementedInVersion)))
  }

  def createTopConsumersJson(topConsumers: List[TopConsumer]): TopConsumersJson ={
    TopConsumersJson(topConsumers.map(topConsumer => TopConsumerJson(topConsumer.count, topConsumer.consumerId, topConsumer.appName, topConsumer.developerEmail)))
  }

  def createBadLoginStatusJson(badLoginStatus: BadLoginAttempt) : BadLoginStatusJson = {
    BadLoginStatusJson(badLoginStatus.username,badLoginStatus.badAttemptsSinceLastSuccessOrReset, badLoginStatus.lastFailureDate)
  }
  def createCallLimitJson(consumer: Consumer) : CallLimitJson = {
    CallLimitJson(
      consumer.perMinuteCallLimit.get.toString,
      consumer.perHourCallLimit.get.toString,
      consumer.perDayCallLimit.get.toString,
      consumer.perWeekCallLimit.get.toString,
      consumer.perMonthCallLimit.get.toString
    )
  }
  def createCheckFundsAvailableJson(fundsAvailable : String, availableFundsRequestId: String) : CheckFundsAvailableJson = {
    CheckFundsAvailableJson(fundsAvailable,new Date(), availableFundsRequestId)
  }

  def createConsumerJSON(c: Consumer, user: Box[User]): ConsumerJson = {
    val resourceUserJSON =  user match {
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
      app_name=c.name.get,
      app_type=c.appType.toString(),
      description=c.description.get,
      developer_email=c.developerEmail.get,
      redirect_url=c.redirectURL.get,
      created_by_user =resourceUserJSON,
      enabled=c.isActive.get,
      created=c.createdAt.get
    )
  }

  def createConsumersJson(consumers: List[Consumer], user: Box[User]): List[ConsumerJson] = {
    consumers.map(createConsumerJSON(_, user))
  }

}