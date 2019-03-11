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

import code.accountapplication.AccountApplication
import code.accountattribute.AccountAttribute.AccountAttribute
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.RateLimitPeriod.LimitCallPeriod
import code.api.util.{APIUtil, RateLimitPeriod}
import code.api.v1_2_1.{AccountRoutingJsonV121, AmountOfMoneyJsonV121, RateLimiting}
import code.api.v1_4_0.JSONFactory1_4_0.{BranchRoutingJsonV141, CustomerFaceImageJson, MetaJsonV140}
import code.api.v2_0_0.{MeetingJson, MeetingKeysJson, MeetingPresentJson}
import code.api.v2_1_0.JSONFactory210.createLicenseJson
import code.api.v2_1_0.{CustomerCreditRatingJSON, ResourceUserJSON}
import code.api.v2_2_0._
import code.bankconnectors.ObpApiLoopback
import code.common.Meta
import code.context.UserAuthContext
import code.customeraddress.CustomerAddress
import code.entitlement.Entitlement
import code.loginattempts.BadLoginAttempt
import code.meetings.Meeting
import code.metrics.{TopApi, TopConsumer}
import code.model.{Consumer, User}
import code.productattribute.ProductAttribute.ProductAttribute
import code.productcollection.ProductCollection
import code.productcollectionitem.ProductCollectionItem
import code.products.Products.Product
import code.taxresidence.TaxResidence
import code.webhook.AccountWebhook
import com.openbankproject.commons.model.{Customer, User}
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

case class CallLimitPostJson(
                          per_second_call_limit : String,
                          per_minute_call_limit : String,
                          per_hour_call_limit : String,
                          per_day_call_limit : String,
                          per_week_call_limit : String,
                          per_month_call_limit : String
                        )
case class RateLimit(calls_made: Option[Long], reset_in_seconds: Option[Long])
case class RedisCallLimitJson(
                          per_second : Option[RateLimit],
                          per_minute : Option[RateLimit],
                          per_hour :  Option[RateLimit],
                          per_day :  Option[RateLimit],
                          per_week:  Option[RateLimit],
                          per_month :  Option[RateLimit]
                        )
case class CallLimitJson(
                          per_second_call_limit : String,
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
                              http_protocol: String,
                              created_by_user_id: String,
                              is_active: Boolean
                             )

case class AccountWebhookPostJson(account_id: String,
                                  trigger_name: String,
                                  url: String,
                                  http_method: String,
                                  http_protocol: String,
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
                                        tags: List[String],
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
                                    tags: List[String],
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

case class ProductAttributeJson(
  name: String,
  `type`: String,
  value: String,
)

case class ProductAttributeResponseJson(
  bank_id: String,
  product_code: String,
  product_attribute_id: String,
  name: String,
  `type`: String,
  value: String,
)
case class ProductAttributeResponseWithoutBankIdJson(
  product_code: String,
  product_attribute_id: String,
  name: String,
  `type`: String,
  value: String,
)

case class AccountApplicationJson(
  product_code: String,
  user_id: Option[String],
  customer_id: Option[String]
 )

case class AccountApplicationResponseJson(
  account_application_id: String,
  product_code: String,
  user: ResourceUserJSON,
  customer: CustomerJsonV310,
  date_of_application: Date,
  status: String
)

case class AccountAttributeJson(
  bank_id: String,
  account_id: String,
  name: String,
  `type`: String,
  value: String,
)


case class AccountAttributeResponseJson(
  bank_id: String,
  account_id: String,
  product_code: String,
  account_attribute_id: String,
  name: String,
  `type`: String,
  value: String
)
case class AccountAttributesResponseJson(list: List[AccountAttributeResponseJson])

case class AccountApplicationUpdateStatusJson(status: String)

case class AccountApplicationsJsonV310(account_applications: List[AccountApplicationResponseJson])


case class RateLimitingInfoV310(enabled: Boolean, technology: String, service_available: Boolean, is_active: Boolean)

case class PostPutProductJsonV310(bank_id: String,
                                  name : String,
                                  parent_product_code : String,
                                  category: String,
                                  family : String,
                                  super_family : String,
                                  more_info_url: String,
                                  details: String,
                                  description: String,
                                  meta : MetaJsonV140)
case class ProductJsonV310(bank_id: String,
                           code : String,
                           parent_product_code : String,
                           name : String,
                           category: String,
                           family : String,
                           super_family : String,
                           more_info_url: String,
                           details: String,
                           description: String,
                           meta : MetaJsonV140,
                           product_attributes: Option[List[ProductAttributeResponseWithoutBankIdJson]])
case class ProductsJsonV310 (products : List[ProductJsonV310])
case class ProductTreeJsonV310(bank_id: String,
                               code : String,
                               name : String,
                               category: String,
                               family : String,
                               super_family : String,
                               more_info_url: String,
                               details: String,
                               description: String,
                               meta : MetaJsonV140,
                               parent_product: Option[ProductTreeJsonV310],
                                 )
case class PutProductCollectionsV310(parent_product_code: String, children_product_codes: List[String])


case class ProductCollectionItemJsonV310(member_product_code: String)
case class ProductCollectionJsonV310(collection_code: String, 
                                     product_code: String,
                                     items: List[ProductCollectionItemJsonV310])
case class ProductCollectionsJsonV310(product_collection : List[ProductCollectionJsonV310])

case class ProductCollectionJsonTreeV310(collection_code: String,
                                         products: List[ProductJsonV310])

case class ContactDetailsJson(
  name: String,
  mobile_phone: String,
  email_addresse: String
)

case class InviteeJson(
  contact_details: ContactDetailsJson,
  status: String
)

case class CreateMeetingJsonV310(
  provider_id: String,
  purpose_id: String,
  date: Date,
  creator: ContactDetailsJson,
  invitees: List[InviteeJson]
)

case class MeetingJsonV310(
  meeting_id: String,
  provider_id: String,
  purpose_id: String,
  bank_id: String,
  present: MeetingPresentJson,
  keys: MeetingKeysJson,
  when: Date,
  creator: ContactDetailsJson,
  invitees: List[InviteeJson]
)

case class MeetingsJsonV310(
  meetings: List[MeetingJsonV310]
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
            getInfo(RateLimitPeriod.PER_SECOND),
            getInfo(RateLimitPeriod.PER_MINUTE),
            getInfo(RateLimitPeriod.PER_HOUR),
            getInfo(RateLimitPeriod.PER_DAY),
            getInfo(RateLimitPeriod.PER_WEEK),
            getInfo(RateLimitPeriod.PER_MONTH)
          )
        )
    }

    CallLimitJson(
      consumer.perSecondCallLimit.get.toString,
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
      http_protocol = wh.httpProtocol,
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
      tags = address.tags.split(",").toList,
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
  
   def createProductAttributeJson(productAttribute: ProductAttribute): ProductAttributeResponseJson =
     ProductAttributeResponseJson(
       bank_id = productAttribute.bankId.value,
       product_code = productAttribute.productCode.value,
       product_attribute_id = productAttribute.productAttributeId,
       name = productAttribute.name,
       `type` = productAttribute.attributeType.toString,
       value = productAttribute.value,
       )
  def createProductAttributesJson(productAttributes: List[ProductAttribute]): List[ProductAttributeResponseWithoutBankIdJson] = {
    productAttributes.map(
      productAttribute => 
      ProductAttributeResponseWithoutBankIdJson(
        product_code = productAttribute.productCode.value,
        product_attribute_id = productAttribute.productAttributeId,
        name = productAttribute.name,
        `type` = productAttribute.attributeType.toString,
        value = productAttribute.value,
      )
    )
  }
  
  def createAccountApplicationJson(accountApplication: AccountApplication, user: Box[User], customer: Box[Customer]): AccountApplicationResponseJson = {

    val userJson = user.map(u => ResourceUserJSON(
      user_id = u.userId,
      email = u.emailAddress,
      provider_id = u.idGivenByProvider,
      provider = u.provider,
      username = u.name
    )).orNull

    val customerJson = customer.map(createCustomerJson).orNull

    AccountApplicationResponseJson(
      account_application_id = accountApplication.accountApplicationId,
      product_code = accountApplication.productCode.value,
      user = userJson,
      customer = customerJson,
      date_of_application =accountApplication.dateOfApplication,
      status = accountApplication.status
    )
  }

  def createAccountApplications(accountApplications: List[AccountApplication], users: List[User], customers: List[Customer]): AccountApplicationsJsonV310 = {
    val applicationList = accountApplications.map { x =>
      val user = Box(users.find(it => it.userId == x.userId))
      val customer = Box(customers.find(it => it.customerId == x.customerId))
      createAccountApplicationJson(x, user, customer)
    }
    AccountApplicationsJsonV310(applicationList)
  }

  def createMetaJson(meta: Meta) : MetaJsonV140 = {
    MetaJsonV140(createLicenseJson(meta.license))
  }
  def createProductJson(product: Product, productAttributes: List[ProductAttribute]) : ProductJsonV310 = {
    ProductJsonV310(
      bank_id = product.bankId.toString,
      code = product.code.value,
      parent_product_code = product.parentProductCode.value,
      name = product.name,
      category = product.category,
      family = product.family,
      super_family = product.superFamily,
      more_info_url = product.moreInfoUrl,
      details = product.details,
      description = product.description,
      meta = createMetaJson(product.meta),
      product_attributes = Some(createProductAttributesJson(productAttributes))
    )
  }
  def createProductJson(product: Product) : ProductJsonV310 = {
    ProductJsonV310(
      bank_id = product.bankId.toString,
      code = product.code.value,
      parent_product_code = product.parentProductCode.value,
      name = product.name,
      category = product.category,
      family = product.family,
      super_family = product.superFamily,
      more_info_url = product.moreInfoUrl,
      details = product.details,
      description = product.description,
      meta = createMetaJson(product.meta),
      None)
  }
  def createProductsJson(productsList: List[Product]) : ProductsJsonV310 = {
    ProductsJsonV310(productsList.map(createProductJson))
  }

  def createProductTreeJson(productsList: List[Product], rootProductCode: String): ProductTreeJsonV310 = {
    def getProductTree(list: List[Product], code: String): Option[ProductTreeJsonV310] = {
      productsList.filter(_.code.value == code) match {
       case x :: Nil =>
         Some(
           ProductTreeJsonV310(
             bank_id = x.bankId.toString,
             code = x.code.value,
             parent_product = getProductTree(productsList, x.parentProductCode.value),
             name = x.name,
             category = x.category,
             family = x.family,
             super_family = x.superFamily,
             more_info_url = x.moreInfoUrl,
             details = x.details,
             description = x.description,
             meta = createMetaJson(x.meta)
           )
         )
        case Nil =>
          None
      }
    }

    val rootElement = productsList.filter(_.code.value == rootProductCode).head
    ProductTreeJsonV310(
      bank_id = rootElement.bankId.toString,
      code = rootElement.code.value,
      parent_product = getProductTree(productsList, rootElement.parentProductCode.value),
      name = rootElement.name,
      category = rootElement.category,
      family = rootElement.family,
      super_family = rootElement.superFamily,
      more_info_url = rootElement.moreInfoUrl,
      details = rootElement.details,
      description = rootElement.description,
      meta = createMetaJson(rootElement.meta)
    )
  }


  def createProductCollectionsJson(productsList: List[ProductCollection], 
                                   productCollectionItems: List[ProductCollectionItem]): ProductCollectionsJsonV310 = {
    ProductCollectionsJsonV310(
      productsList.map(
        pc => 
          ProductCollectionJsonV310(
            pc.collectionCode, 
            pc.productCode,
            productCollectionItems.map(y => ProductCollectionItemJsonV310(y.memberProductCode))
          )
      )
    )
  }
  def createProductCollectionsTreeJson(list: List[(ProductCollectionItem, Product, List[ProductAttribute])]): ProductCollectionJsonTreeV310 = {
    val products = list.map(pc => createProductJson(pc._2, pc._3))
    val collectionCode = list.map(_._1.collectionCode).headOption.getOrElse("")
    ProductCollectionJsonTreeV310(
      collectionCode,
      products
    )
  }

  def createAccountAttributeJson(accountAttribute: AccountAttribute) : AccountAttributeResponseJson = {
    AccountAttributeResponseJson(
      bank_id = accountAttribute.bankId.value,
      account_id = accountAttribute.accountId.value,
      product_code = accountAttribute.productCode.value,
      account_attribute_id = accountAttribute.accountAttributeId,
      name = accountAttribute.name,
      `type` = accountAttribute.attributeType.toString,
      value = accountAttribute.value
    )
  }
  def createAccountAttributesJson(productsList: List[AccountAttribute]) : AccountAttributesResponseJson = {
    AccountAttributesResponseJson(productsList.map(createAccountAttributeJson))
  }
  def createMeetingJson(meeting : Meeting) : MeetingJsonV310 = {
    MeetingJsonV310(
      meeting_id = meeting.meetingId,
      provider_id = meeting.providerId,
      purpose_id = meeting.purposeId,
      bank_id = meeting.bankId,
      present = MeetingPresentJson(
        staff_user_id = meeting.present.staffUserId,
        customer_user_id = meeting.present.customerUserId
      ),
      keys = MeetingKeysJson(
        session_id = meeting.keys.sessionId,
        staff_token = meeting.keys.staffToken,
        customer_token = meeting.keys.customerToken
      ),
      when = meeting.when,
      creator = ContactDetailsJson(meeting.creator.name, meeting.creator.phone, meeting.creator.email),
      invitees = meeting.invitees.map(
        invitee =>
          InviteeJson(
            ContactDetailsJson(
              invitee.contactDetails.name,
              invitee.contactDetails.phone,
              invitee.contactDetails.email),
            invitee.status)) 
    )
  }
  
  def createMeetingsJson(meetings : List[Meeting]) : MeetingsJsonV310 = {
    MeetingsJsonV310(meetings.map(createMeetingJson))
  }

}

