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

import java.lang
import java.util.Date

import code.customeraddress.CustomerAddress
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.RateLimitPeriod.LimitCallPeriod
import code.api.util.{APIUtil, RateLimitPeriod}
import code.api.v1_2_1.{AccountRoutingJsonV121, AmountOfMoneyJsonV121, RateLimiting}
import code.api.v1_4_0.JSONFactory1_4_0.{BranchRoutingJsonV141, CustomerFaceImageJson}
import code.api.v2_1_0.{CustomerCreditRatingJSON, CustomerJsonV210, ResourceUserJSON}
import code.api.v2_2_0._
import code.bankconnectors.ObpApiLoopback
import code.common.Address
import code.loginattempts.BadLoginAttempt
import code.metrics.{TopApi, TopConsumer}
import code.model.{Consumer, User}
import code.webhook.AccountWebhook
import net.liftweb.common.{Box, Full}

import scala.collection.immutable.List
import code.customer.Customer
import code.context.UserAuthContext
import code.entitlement.Entitlement
import code.taxresidence.TaxResidence

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

case class CallLimitPostJson(
                          per_minute_call_limit : String,
                          per_hour_call_limit : String,
                          per_day_call_limit : String,
                          per_week_call_limit : String,
                          per_month_call_limit : String
                        )
case class RateLimit(calls_made: Option[Long], reset_in_seconds: Option[Long])
case class RedisCallLimitJson(
                          per_minute : Option[RateLimit],
                          per_hour :  Option[RateLimit],
                          per_day :  Option[RateLimit],
                          per_week:  Option[RateLimit],
                          per_month :  Option[RateLimit]
                        )
case class CallLimitJson(
                          per_minute_call_limit : String,
                          per_hour_call_limit : String,
                          per_day_call_limit : String,
                          per_week_call_limit : String,
                          per_month_call_limit : String,
                          current_state: Option[RedisCallLimitJson]
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

case class AccountWebhookJson(account_webhook_id: String,
                              bank_id: String,
                              account_id: String,
                              trigger_name: String,
                              url: String,
                              http_method: String,
                              created_by_user_id: String,
                              is_active: Boolean
                             )

case class AccountWebhookPostJson(account_id: String,
                                  trigger_name: String,
                                  url: String,
                                  http_method: String,
                                  is_active: String
                                  )
case class AccountWebhookPutJson(account_webhook_id: String,
                                 is_active: String
                                 )

case class AccountWebhooksJson(web_hooks: List[AccountWebhookJson])

case class ConfigurationJsonV310(default_bank_id: String, akka: AkkaJSON, elastic_search: ElasticSearchJSON, cache: List[CachedFunctionJSON])


case class PostCustomerJsonV310(
  legal_name: String,
  mobile_phone_number: String,
  email: String,
  face_image: CustomerFaceImageJson,
  date_of_birth: Date,
  relationship_status: String,
  dependants: Int,
  dob_of_dependants: List[Date],
  credit_rating: CustomerCreditRatingJSON,
  credit_limit: AmountOfMoneyJsonV121,
  highest_education_attained: String,
  employment_status: String,
  kyc_status: Boolean,
  last_ok_date: Date,
  title: String,
  branchId: String,
  nameSuffix: String
)

case class CustomerJsonV310(
  bank_id: String,
  customer_id: String,
  customer_number : String,
  legal_name : String,
  mobile_phone_number : String,
  email : String,
  face_image : CustomerFaceImageJson,
  date_of_birth: Date,
  relationship_status: String,
  dependants: Integer,
  dob_of_dependants: List[Date],
  credit_rating: Option[CustomerCreditRatingJSON],
  credit_limit: Option[AmountOfMoneyJsonV121],
  highest_education_attained: String,
  employment_status: String,
  kyc_status: lang.Boolean,
  last_ok_date: Date,
  title: String,
  branchId: String,
  nameSuffix: String
)

case class PostCustomerResponseJsonV310(messages: List[String])

case class PostCustomerNumberJsonV310(customer_number: String)

case class PostUserAuthContextJson(
  key: String,
  value: String
)

case class UserAuthContextJson(
  user_auth_context_id: String,
  user_id: String,
  key: String,
  value: String
)

case class UserAuthContextsJson(
  user_auth_contexts: List[UserAuthContextJson]
)

case class TaxResidenceV310(domain: String, tax_number: String, tax_residence_id: String)
case class PostTaxResidenceJsonV310(domain: String, tax_number: String)
case class TaxResidenceJsonV310(tax_residence: List[TaxResidenceV310])

case class EntitlementJsonV310(entitlement_id: String, role_name: String, bank_id: String, user_id: String, username: String)
case class EntitlementJSonsV310(list: List[EntitlementJsonV310])


case class PostCustomerAddressJsonV310(
                                line_1: String,
                                line_2: String,
                                line_3: String,
                                city: String,
                                county: String,
                                state: String,
                                postcode: String,
                                //ISO_3166-1_alpha-2
                                country_code: String,
                                status: String
                              )

case class CustomerAddressJsonV310(
                            customer_address_id: String,
                            customer_id: String,
                            line_1: String,
                            line_2: String,
                            line_3: String,
                            city: String,
                            county: String,
                            state: String,
                            postcode: String,
                            //ISO_3166-1_alpha-2
                            country_code: String,
                            status: String,
                            insert_date: Date
                          )
case class CustomerAddressesJsonV310(addresses: List[CustomerAddressJsonV310])
case class ObpApiLoopbackJson(
  connector_version: String,
  git_commit: String,
  duration_time: String
)

case class RefreshUserJson(
  duration_time: String
)

case class RateLimitingInfoV310(enabled: Boolean, technology: String, service_available: Boolean, is_active: Boolean)

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
  def createCallLimitJson(consumer: Consumer, rateLimits: List[((Option[Long], Option[Long]), LimitCallPeriod)]) : CallLimitJson = {
    val redisRateLimit = rateLimits match {
      case Nil => None
      case _   =>
        def getInfo(period: RateLimitPeriod.Value): Option[RateLimit] = {
          rateLimits.filter(_._2 == period) match {
            case x :: Nil =>
              x._1 match {
                case (Some(x), Some(y)) => Some(RateLimit(Some(x), Some(y)))
                case _                  => None

              }
            case _ => None
          }
        }
        Some(
          RedisCallLimitJson(
            getInfo(RateLimitPeriod.PER_MINUTE),
            getInfo(RateLimitPeriod.PER_HOUR),
            getInfo(RateLimitPeriod.PER_DAY),
            getInfo(RateLimitPeriod.PER_WEEK),
            getInfo(RateLimitPeriod.PER_MONTH)
          )
        )
    }

    CallLimitJson(
      consumer.perMinuteCallLimit.get.toString,
      consumer.perHourCallLimit.get.toString,
      consumer.perDayCallLimit.get.toString,
      consumer.perWeekCallLimit.get.toString,
      consumer.perMonthCallLimit.get.toString,
      redisRateLimit
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

  def createAccountWebhookJson(wh: AccountWebhook) = {
    AccountWebhookJson(
      account_webhook_id = wh.accountWebhookId,
      bank_id = wh.bankId,
      account_id = wh.accountId,
      trigger_name = wh.triggerName,
      url = wh.url,
      http_method = wh.httpMethod,
      created_by_user_id = wh.createdByUserId,
      is_active = wh.isActive()
    )
  }

  def createAccountWebhooksJson(whs: List[AccountWebhook]) = {
    AccountWebhooksJson(whs.map(createAccountWebhookJson(_)))
  }

  def getConfigInfoJSON(): ConfigurationJsonV310 = {
    val configurationJson: ConfigurationJSON = JSONFactory220.getConfigInfoJSON()
    val defaultBankId= APIUtil.defaultBankId
    ConfigurationJsonV310(defaultBankId,configurationJson.akka,configurationJson.elastic_search, configurationJson.cache)
  }

  def createCustomerJson(cInfo : Customer) : CustomerJsonV310 = {
    CustomerJsonV310(
      bank_id = cInfo.bankId.toString,
      customer_id = cInfo.customerId,
      customer_number = cInfo.number,
      legal_name = cInfo.legalName,
      mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email,
      face_image = CustomerFaceImageJson(url = cInfo.faceImage.url,
        date = cInfo.faceImage.date),
      date_of_birth = cInfo.dateOfBirth,
      relationship_status = cInfo.relationshipStatus,
      dependants = cInfo.dependents,
      dob_of_dependants = cInfo.dobOfDependents,
      credit_rating = Option(CustomerCreditRatingJSON(rating = cInfo.creditRating.rating, source = cInfo.creditRating.source)),
      credit_limit = Option(AmountOfMoneyJsonV121(currency = cInfo.creditLimit.currency, amount = cInfo.creditLimit.amount)),
      highest_education_attained = cInfo.highestEducationAttained,
      employment_status = cInfo.employmentStatus,
      kyc_status = cInfo.kycStatus,
      last_ok_date = cInfo.lastOkDate,
      title = cInfo.title,
      branchId = cInfo.branchId,
      nameSuffix = cInfo.nameSuffix
    )
  }
  
  def createUserAuthContextJson(userAuthContext: UserAuthContext): UserAuthContextJson = {
    UserAuthContextJson(
      user_auth_context_id= userAuthContext.userAuthContextId,
      user_id = userAuthContext.userId,
      key = userAuthContext.key,
      value = userAuthContext.value
    )
  }
  
  def createUserAuthContextsJson(userAuthContext: List[UserAuthContext]): UserAuthContextsJson = {
    UserAuthContextsJson(userAuthContext.map(createUserAuthContextJson))
  }

  def createTaxResidence(tr: List[TaxResidence]) = TaxResidenceJsonV310(
    tr.map(
      i =>
        TaxResidenceV310(
          domain = i.domain,
          tax_number = i.taxNumber,
          tax_residence_id = i.taxResidenceId
        )
    )
  )
  def createAddress(address: CustomerAddress): CustomerAddressJsonV310 =
    CustomerAddressJsonV310(
      customer_address_id = address.customerAddressId,
      customer_id = address.customerId,
      line_1 = address.line1,
      line_2 = address.line2,
      line_3 = address.line3,
      city = address.city,
      county = address.county,
      state = address.state,
      postcode = address.postcode,
      country_code = address.countryCode,
      status = address.status,
      insert_date = address.insertDate
    )

  def createAddresses(addresses: List[CustomerAddress]): CustomerAddressesJsonV310 =
    CustomerAddressesJsonV310(addresses.map(createAddress(_)))

  def createObpApiLoopbackJson(obpApiLoopback: ObpApiLoopback): ObpApiLoopbackJson =
    ObpApiLoopbackJson(
      obpApiLoopback.connectorVersion,
      obpApiLoopback.gitCommit,
      s"${obpApiLoopback.durationTime} ms"
    )

  def createRefreshUserJson(durationTime: Long): RefreshUserJson =
    RefreshUserJson(s" $durationTime ms")
  
  def createEntitlementJsonsV310(tr: List[Entitlement]) = {
    val idToUser: Map[String, Box[String]] = tr.map(_.userId).distinct.map {
     userId => (userId, User.findByUserId(userId).map(_.name))
    } toMap;

    EntitlementJSonsV310(
      tr.map(e =>
        EntitlementJsonV310(
          entitlement_id = e.entitlementId,
          role_name = e.roleName,
          bank_id = e.bankId,
          user_id = e.userId,
          username = idToUser(e.userId).openOrThrowException("not user exists for userId: " + e.userId)
        )
      )
    )
  }
  
  def createRateLimitingInfo(info: RateLimiting): RateLimitingInfoV310 = 
    RateLimitingInfoV310(
      enabled = info.enabled, 
      technology = info.technology, 
      service_available = info.service_available, 
      is_active = info.is_active
    )

}