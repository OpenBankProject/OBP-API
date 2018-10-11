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
import code.api.util.APIUtil


import code.api.v1_2_1.{AccountRoutingJsonV121, AmountOfMoneyJsonV121}
import code.api.v1_4_0.JSONFactory1_4_0.{BranchRoutingJsonV141, CustomerFaceImageJson}
import code.api.v2_1_0.{CustomerCreditRatingJSON, ResourceUserJSON}

import code.api.v2_2_0._
import code.loginattempts.BadLoginAttempt
import code.metrics.{TopApi, TopConsumer}
import code.model.{Consumer, User}
import code.webhook.AccountWebHook
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


case class CreditLimitRequestJson(
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
case class ConsumersJson(consumers: List[ConsumerJson])

case class AccountWebHookJson(account_web_hook_id: String,
                              bank_id: String,
                              account_id: String,
                              trigger_name: String,
                              url: String,
                              http_method: String,
                              created_by_user_id: String,
                              is_active: Boolean
                             )

case class AccountWebHookPostJson(account_id: String,
                                  trigger_name: String,
                                  url: String,
                                  http_method: String,
                                  is_active: String
                                  )
case class AccountWebHookPutJson(account_web_hook_id: String,
                                 is_active: String
                                 )

case class AccountWebHooksJson(web_hooks: List[AccountWebHookJson])

case class ConfigurationJsonV310(default_bank_id: String, akka: AkkaJSON, elastic_search: ElasticSearchJSON, cache: List[CachedFunctionJSON])

case class PostCustomerJsonV310(
                                 number: String,
                                 customer_number : String,
                                 legal_name : String,
                                 mobile_phone_number : String,
                                 email : String,
                                 face_image : CustomerFaceImageJson,
                                 date_of_birth: Date,
                                 relationship_status: String,
                                 dependants: Int,
                                 dob_of_dependants: List[Date],
                                 credit_rating: CustomerCreditRatingJSON,
                                 credit_limit: AmountOfMoneyJsonV121,
                                 highest_education_attained: String,
                                 employment_status: String,
                                 kyc_status: Boolean,
                                 last_ok_date: Date
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

    code.api.v3_1_0.ConsumerJson(consumer_id=c.consumerId.get,
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

  def createConsumersJson(consumers: List[Consumer], user: Box[User]): ConsumersJson = {
    val c = consumers.map(createConsumerJSON(_, user))
    ConsumersJson(c)
  }

  def createConsumersJson(consumers: List[Consumer], users: List[User]): ConsumersJson = {
    val cs = consumers.map(
      c => createConsumerJSON(c, users.filter(_.userId==c.createdByUserId.get).headOption)
    )
    ConsumersJson(cs)
  }

  def createAccountWebHookJson(wh: AccountWebHook) = {
    AccountWebHookJson(
      account_web_hook_id = wh.accountWebHookId,
      bank_id = wh.bankId,
      account_id = wh.accountId,
      trigger_name = wh.triggerName,
      url = wh.url,
      http_method = wh.httpMethod,
      created_by_user_id = wh.createdByUserId,
      is_active = wh.isActive()
    )
  }

  def createAccountWebHooksJson(whs: List[AccountWebHook]) = {
    AccountWebHooksJson(whs.map(createAccountWebHookJson(_)))
  }
  
  def getConfigInfoJSON(): ConfigurationJsonV310 = {
    val configurationJson: ConfigurationJSON = JSONFactory220.getConfigInfoJSON()
    val defaultBankId= APIUtil.defaultBankId
    ConfigurationJsonV310(defaultBankId,configurationJson.akka,configurationJson.elastic_search, configurationJson.cache)
  }

}