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
package code.api.v5_0_0

import java.lang
import java.util.Date

import code.api.util.APIUtil.{stringOptionOrNull, stringOrNull}
import code.api.v1_2_1.BankRoutingJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.{CustomerFaceImageJson, MetaJsonV140}
import code.api.v1_3_0.JSONFactory1_3_0.{cardActionsToString, createAccountJson, createPinResetJson, createReplacementJson}
import code.api.v1_3_0.{PinResetJSON, ReplacementJSON}
import code.api.v1_4_0.JSONFactory1_4_0.CustomerFaceImageJson
import code.api.v2_1_0.CustomerCreditRatingJSON
import code.api.v3_0_0.{CustomerAttributeResponseJsonV300, JSONFactory300}
import code.api.v3_1_0.{AccountAttributeResponseJson, AccountBasicV310, CustomerWithAttributesJsonV310, PhysicalCardWithAttributesJsonV310, PostConsentEntitlementJsonV310}
import code.api.v4_0_0.BankAttributeBankResponseJsonV400
import code.bankattribute.BankAttribute
import com.openbankproject.commons.model.{AccountAttribute, AccountRouting, AccountRoutingJsonV121, AmountOfMoneyJsonV121, Bank, BankAccount, CardAttribute, Customer, CustomerAttribute, PhysicalCardTrait, User, UserAuthContext, UserAuthContextUpdate, View, ViewBasic}
import net.liftweb.json.JsonAST.JValue

import scala.collection.immutable.List

case class PostBankJson500(
    id: Option[String],
    bank_code: String,
    full_name: Option[String],
    logo: Option[String],
    website: Option[String],
    bank_routings: Option[List[BankRoutingJsonV121]],
    attributes: Option[List[BankAttributeBankResponseJsonV400]]
)

case class BankJson500(
    id: String,
    bank_code: String,
    full_name: String,
    logo: String,
    website: String,
    bank_routings: List[BankRoutingJsonV121],
    attributes: Option[List[BankAttributeBankResponseJsonV400]]
)

case class CreateAccountRequestJsonV500(
    user_id : Option[String],
    label   : String,
    product_code : String,
    balance : Option[AmountOfMoneyJsonV121],
    branch_id : Option[String],
    account_routings: Option[List[AccountRoutingJsonV121]]
)

case class PostCustomerJsonV500(
   legal_name: String,
   mobile_phone_number: String,
   email: Option[String] = None,
   face_image: Option[CustomerFaceImageJson] = None,
   date_of_birth: Option[Date] = None,
   relationship_status: Option[String] = None,
   dependants: Option[Int] = None,
   dob_of_dependants: Option[List[Date]] = None,
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

case class PostCustomerOverviewJsonV500(customer_number: String)

case class CustomerWithAttributesJsonV500(
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
   branch_id: String,
   name_suffix: String,
   customer_attributes: List[CustomerAttributeResponseJsonV300],
   accounts: List[AccountResponseJson500])

case class AccountAttributeResponseJson500(
   contract_code: Option[String],
   product_code: String,
   account_attribute_id: String,
   name: String,
   `type`: String,
   value: String
 )

case class AccountResponseJson500(account_id: String,
                                  label: String,
                                  product_code: String,
                                  balance : AmountOfMoneyJsonV121,
                                  branch_id: String,
                                  account_routings: List[AccountRouting],
                                  account_attributes: List[AccountAttributeResponseJson500]
                                 )

case class PutProductJsonV500(
   parent_product_code: String, 
   name: String, 
   more_info_url: Option[String] = None, 
   terms_and_conditions_url: Option[String] = None, 
   description: Option[String] = None, 
   meta: Option[MetaJsonV140] = None,
)

case class UserAuthContextJsonV500(
  user_auth_context_id: String,
  user_id: String,
  key: String,
  value: String,
  time_stamp: Date,
  consumer_id: String,
)

case class UserAuthContextsJsonV500(
  user_auth_contexts: List[UserAuthContextJsonV500]
)

case class UserAuthContextUpdateJsonV500(
  user_auth_context_update_id: String,
  user_id: String,
  key: String,
  value: String,
  status: String,
  consumer_id: String,
)


case class PostConsentRequestResponseJson(consentRequestId: String)

case class ConsentRequestResponseJson(
  consent_request_id: String, 
  payload : JValue, 
  consumer_id : String
)
case class AccountAccessV500(
//  bank_routing: Option[BankRoutingJsonV121],
//  branch_routing: Option[BranchRoutingJsonV141],
  account_routing: AccountRoutingJsonV121,
  view_id: String
)

case class PostConsentRequestJsonV500(
  everything: Boolean,
  account_access: List[AccountAccessV500],
  entitlements: Option[List[PostConsentEntitlementJsonV310]],
  consumer_id: Option[String],
  email: Option[String],
  phone_number: Option[String],
  valid_from: Option[Date],
  time_to_live: Option[Long]
)

case class ConsentJsonV500(consent_id: String, jwt: String, status: String, consent_request_id: Option[String])

case class CreatePhysicalCardJsonV500(
  card_number: String,
  card_type: String,
  name_on_card: String,
  issue_number: String,
  serial_number: String,
  valid_from_date: Date,
  expires_date: Date,
  enabled: Boolean,
  technology: String,
  networks: List[String],
  allows: List[String],
  account_id: String,
  replacement: Option[ReplacementJSON],
  pin_reset: List[PinResetJSON],
  collected: Option[Date],
  posted: Option[Date],
  customer_id: String,
  brand: String
)

case class PhysicalCardJsonV500(
  card_id: String,
  bank_id: String,
  card_number: String,
  card_type: String,
  name_on_card: String,
  issue_number: String,
  serial_number: String,
  valid_from_date: Date,
  expires_date: Date,
  enabled: Boolean,
  cancelled: Boolean,
  on_hot_list: Boolean,
  technology: String,
  networks: List[String],
  allows: List[String],
  account: code.api.v1_2_1.AccountJSON,
  replacement: ReplacementJSON,
  pin_reset: List[PinResetJSON],
  collected: Date,
  posted: Date,
  customer_id: String,
  cvv: String,
  brand: String
)

case class UpdatedPhysicalCardJsonV500(
  card_id: String,
  bank_id: String,
  card_number: String,
  card_type: String,
  name_on_card: String,
  issue_number: String,
  serial_number: String,
  valid_from_date: Date,
  expires_date: Date,
  enabled: Boolean,
  cancelled: Boolean,
  on_hot_list: Boolean,
  technology: String,
  networks: List[String],
  allows: List[String],
  account: code.api.v1_2_1.AccountJSON,
  replacement: ReplacementJSON,
  pin_reset: List[PinResetJSON],
  collected: Date,
  posted: Date,
  customer_id: String,
  brand: String
)

case class PhysicalCardWithAttributesJsonV500(
  card_id: String,
  bank_id: String,
  card_number: String,
  card_type: String,
  name_on_card: String,
  issue_number: String,
  serial_number: String,
  valid_from_date: Date,
  expires_date: Date,
  enabled: Boolean,
  cancelled: Boolean,
  on_hot_list: Boolean,
  technology: String,
  networks: List[String],
  allows: List[String],
  account: AccountBasicV310,
  replacement: ReplacementJSON,
  pin_reset: List[PinResetJSON],
  collected: Date,
  posted: Date,
  customer_id: String,
  card_attributes: List[CardAttribute],
  brand: String
)

case class UpdatePhysicalCardJsonV500(
  card_type: String,
  name_on_card: String,
  issue_number: String,
  serial_number: String,
  valid_from_date: Date,
  expires_date: Date,
  enabled: Boolean,
  technology: String,
  networks: List[String],
  allows: List[String],
  account_id: String,
  replacement: ReplacementJSON,
  pin_reset: List[PinResetJSON],
  collected: Date,
  posted: Date,
  customer_id: String,
  brand: String
)

object JSONFactory500 {

  def createUserAuthContextJson(userAuthContext: UserAuthContext): UserAuthContextJsonV500 = {
    UserAuthContextJsonV500(
      user_auth_context_id= userAuthContext.userAuthContextId,
      user_id = userAuthContext.userId,
      key = userAuthContext.key,
      value = userAuthContext.value,
      time_stamp = userAuthContext.timeStamp,
      consumer_id = userAuthContext.consumerId,
    )
  }
  
  def createUserAuthContextsJson(userAuthContext: List[UserAuthContext]): UserAuthContextsJsonV500 = {
    UserAuthContextsJsonV500(userAuthContext.map(createUserAuthContextJson))
  }

  def createUserAuthContextUpdateJson(userAuthContextUpdate: UserAuthContextUpdate): UserAuthContextUpdateJsonV500 = {
    UserAuthContextUpdateJsonV500(
      user_auth_context_update_id= userAuthContextUpdate.userAuthContextUpdateId,
      user_id = userAuthContextUpdate.userId,
      key = userAuthContextUpdate.key,
      value = userAuthContextUpdate.value,
      status = userAuthContextUpdate.status,
      consumer_id = userAuthContextUpdate.consumerId
    )
  }

  def createBankJSON500(bank: Bank, attributes: List[BankAttribute] = Nil): BankJson500 = {
    val obp = BankRoutingJsonV121("OBP", bank.bankId.value)
    val bic = BankRoutingJsonV121("BIC", bank.swiftBic)
    val routings = bank.bankRoutingScheme match {
      case "OBP" => bic :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
      case "BIC" => obp :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
      case _ => obp :: bic :: BankRoutingJsonV121(bank.bankRoutingScheme, bank.bankRoutingAddress) :: Nil
    }
    new BankJson500(
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

  def createCustomerWithAttributesJson(cInfo : Customer, 
                                       customerAttributes: List[CustomerAttribute], 
                                       accounts: List[(BankAccount, List[AccountAttribute])]) : CustomerWithAttributesJsonV500 = {
    CustomerWithAttributesJsonV500(
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
      branch_id = cInfo.branchId,
      name_suffix = cInfo.nameSuffix,
      customer_attributes = customerAttributes.map(JSONFactory300.createCustomerAttributeJson),
      accounts = createAccounts(accounts)
    )
  }
  
  def createAccounts(accounts: List[(BankAccount, List[AccountAttribute])]): List[AccountResponseJson500] = {
    accounts.map{ account =>
      AccountResponseJson500(
        account_id = account._1.accountId.value,
        label = account._1.label,
        product_code = account._1.accountType,
        balance = AmountOfMoneyJsonV121(account._1.balance.toString(), account._1.currency),
        branch_id = account._1.branchId,
        account_routings = account._1.accountRoutings,
        account_attributes = account._2.map{ attribute => 
          AccountAttributeResponseJson500(
            contract_code = attribute.productInstanceCode,
            product_code = attribute.productCode.value,
            account_attribute_id = attribute.accountAttributeId,
            name = attribute.name,
            `type` = attribute.attributeType.toString,
            value = attribute.value
          )
        }
      )
    }
  }

  def createPhysicalCardWithAttributesJson(card: PhysicalCardTrait, cardAttributes: List[CardAttribute],user : User, views: List[View]): PhysicalCardWithAttributesJsonV500 = {
    PhysicalCardWithAttributesJsonV500(
      card_id = stringOrNull(card.cardId),
      bank_id = stringOrNull(card.bankId),
      card_number = stringOrNull(card.bankCardNumber),
      card_type = stringOrNull(card.cardType),
      name_on_card = stringOrNull(card.nameOnCard),
      issue_number = stringOrNull(card.issueNumber),
      serial_number = stringOrNull(card.serialNumber),
      valid_from_date = card.validFrom,
      expires_date = card.expires,
      enabled = card.enabled,
      cancelled = card.cancelled,
      on_hot_list = card.onHotList,
      technology = stringOrNull(card.technology),
      networks = card.networks,
      allows = card.allows.map(cardActionsToString).toList,
      account = AccountBasicV310(
        card.account.accountId.value,
        card.account.label,
        views.map(view => ViewBasic(view.viewId.value, view.name, view.description)),
        card.account.bankId.value),
      replacement = card.replacement.map(createReplacementJson).getOrElse(null),
      pin_reset = card.pinResets.map(createPinResetJson),
      collected = card.collected.map(_.date).getOrElse(null),
      posted = card.posted.map(_.date).getOrElse(null),
      customer_id = stringOrNull(card.customerId),
      card_attributes = cardAttributes,
      brand = stringOptionOrNull(card.brand),
    )
  }
  def createPhysicalCardJson(card: PhysicalCardTrait, user : User): PhysicalCardJsonV500 = {
    PhysicalCardJsonV500(
      card_id = stringOrNull(card.cardId),
      bank_id = stringOrNull(card.bankId),
      card_number = stringOrNull(card.bankCardNumber),
      card_type = stringOrNull(card.cardType),
      name_on_card = stringOrNull(card.nameOnCard),
      issue_number = stringOrNull(card.issueNumber),
      serial_number = stringOrNull(card.serialNumber),
      valid_from_date = card.validFrom,
      expires_date = card.expires,
      enabled = card.enabled,
      cancelled = card.cancelled,
      on_hot_list = card.onHotList,
      technology = stringOrNull(card.technology),
      networks = card.networks,
      allows = card.allows.map(cardActionsToString).toList,
      account = createAccountJson(card.account, user),
      replacement = card.replacement.map(createReplacementJson).getOrElse(null),
      pin_reset = card.pinResets.map(createPinResetJson),
      collected = card.collected.map(_.date).getOrElse(null),
      posted = card.posted.map(_.date).getOrElse(null),
      customer_id = stringOrNull(card.customerId),
      cvv = stringOptionOrNull(card.cvv),
      brand = stringOptionOrNull(card.brand)
    )
  }
}

