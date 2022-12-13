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

import code.api.util.APIUtil.{nullToString, stringOptionOrNull, stringOrNull}
import code.api.v1_2_1.BankRoutingJsonV121
import code.api.v1_3_0.JSONFactory1_3_0.{cardActionsToString, createAccountJson, createPinResetJson, createReplacementJson}
import code.api.v1_3_0.{PinResetJSON, ReplacementJSON}
import code.api.v1_4_0.JSONFactory1_4_0.{CustomerFaceImageJson, MetaJsonV140}
import code.api.v2_1_0.CustomerCreditRatingJSON
import code.api.v3_0_0.{AdapterInfoJsonV300, CustomerAttributeResponseJsonV300, JSONFactory300}
import code.api.v3_1_0.{AccountAttributeResponseJson, AccountBasicV310, CustomerWithAttributesJsonV310, PhysicalCardWithAttributesJsonV310, PostConsentEntitlementJsonV310}
import code.api.v4_0_0.BankAttributeBankResponseJsonV400
import code.bankattribute.BankAttribute
import code.customeraccountlinks.CustomerAccountLinkTrait
import com.openbankproject.commons.model.{AccountAttribute, AccountRouting, AccountRoutingJsonV121, AmountOfMoneyJsonV121, Bank, BankAccount, CardAttribute, CreateViewJson, Customer, CustomerAttribute, InboundAdapterInfoInternal, InboundStatusMessage, PhysicalCardTrait, UpdateViewJSON, User, UserAuthContext, UserAuthContextUpdate, View, ViewBasic}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers

import scala.collection.immutable.List

case class PostBankJson500(
    id: Option[String],
    bank_code: String,
    full_name: Option[String],
    logo: Option[String],
    website: Option[String],
    bank_routings: Option[List[BankRoutingJsonV121]]
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
   customer_number: Option[String] = None,
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

case class CustomerOverviewJsonV500(
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

case class CustomerOverviewFlatJsonV500(
   bank_id: String,
   customer_id: String,
   customer_number : String,
   legal_name : String,
   mobile_phone_number : String,
   email : String,
   date_of_birth: Date,
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

case class ContractJsonV500(product_code: String,
                            contract_code: String,
                            product_description: Option[String] = None,
                            issuance_amount: Option[String] = None,
                            interest_rate: Option[String] = None,
                            term: Option[String] = None,
                            form_of_payment: Option[String] = None,
                            interest_amount: Option[String] = None,
                            branch_code: Option[String] = None,
                            payment_method: Option[String] = None,
                            opening_date: Option[String] = None,
                            maturity_date: Option[String] = None,
                            renewal_date: Option[String] = None,
                            cancellation_date: Option[String] = None,
                            instrument_status_code: Option[String] = None,
                            instrument_status_definition: Option[String] = None,
                            is_substituted: Option[String] = None
                           )
case class AccountResponseJson500(account_id: String,
                                  label: String,
                                  product_code: String,
                                  balance : AmountOfMoneyJsonV121,
                                  branch_id: String,
                                  contracts: Option[List[ContractJsonV500]] = None,
                                  account_routings: List[AccountRoutingJsonV121],
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

case class CreateCustomerAccountLinkJson(
  customer_id: String,
  bank_id: String,
  account_id: String,
  relationship_type: String
)

case class UpdateCustomerAccountLinkJson(
  relationship_type: String
)

case class CustomerAccountLinkJson(
  customer_account_link_id: String,
  customer_id: String,
  bank_id: String,
  account_id: String,
  relationship_type: String
)

case class CustomerAccountLinksJson(
  links:List[CustomerAccountLinkJson]
)

case class AdapterInfoJsonV500(
  name: String,
  version: String,
  git_commit: String,
  date: String,
  total_duration: BigDecimal,
  backend_messages: List[InboundStatusMessage],
)


case class CreateViewJsonV500(
                               name: String,
                               description: String,
                               metadata_view: String,
                               is_public: Boolean,
                               which_alias_to_use: String,
                               hide_metadata_if_alias_used: Boolean,
                               allowed_actions : List[String],
                               can_grant_access_to_views : Option[List[String]] = None,
                               can_revoke_access_to_views : Option[List[String]] = None
                             ) {
  def toCreateViewJson = CreateViewJson(
    name = this.name,
    description = this.description,
    metadata_view = this.metadata_view,
    is_public = this.is_public,
    which_alias_to_use = this.which_alias_to_use,
    hide_metadata_if_alias_used = this.hide_metadata_if_alias_used,
    allowed_actions = this.allowed_actions,
    can_grant_access_to_views = this.can_grant_access_to_views,
    can_revoke_access_to_views = this.can_revoke_access_to_views
  )
}
case class UpdateViewJsonV500(
                               description: String,
                               metadata_view: String,
                               is_public: Boolean,
                               is_firehose: Option[Boolean] = None,
                               which_alias_to_use: String,
                               hide_metadata_if_alias_used: Boolean,
                               allowed_actions: List[String],
                               can_grant_access_to_views : Option[List[String]] = None,
                               can_revoke_access_to_views : Option[List[String]] = None
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
case class ViewsJsonV500(views : List[ViewJsonV500])

case class ViewIdJsonV500(id: String)
case class ViewsIdsJsonV500(views : List[ViewIdJsonV500])

case class ViewJsonV500(
                         val id: String,
                         val short_name: String,
                         val description: String,
                         val metadata_view: String,
                         val is_public: Boolean,
                         val is_system: Boolean,
                         val is_firehose: Option[Boolean] = None,
                         val alias: String,
                         val hide_metadata_if_alias_used: Boolean,
                         val can_grant_access_to_views : List[String],
                         val can_revoke_access_to_views : List[String],
                         val can_add_comment : Boolean,
                         val can_add_corporate_location : Boolean,
                         val can_add_image : Boolean,
                         val can_add_image_url: Boolean,
                         val can_add_more_info: Boolean,
                         val can_add_open_corporates_url : Boolean,
                         val can_add_physical_location : Boolean,
                         val can_add_private_alias : Boolean,
                         val can_add_public_alias : Boolean,
                         val can_add_tag : Boolean,
                         val can_add_url: Boolean,
                         val can_add_where_tag : Boolean,
                         val can_delete_comment: Boolean,
                         val can_add_counterparty : Boolean,
                         val can_delete_corporate_location : Boolean,
                         val can_delete_image : Boolean,
                         val can_delete_physical_location : Boolean,
                         val can_delete_tag : Boolean,
                         val can_delete_where_tag : Boolean,
                         val can_edit_owner_comment: Boolean,
                         val can_see_bank_account_balance: Boolean,
                         val can_query_available_funds: Boolean,
                         val can_see_bank_account_bank_name: Boolean,
                         val can_see_bank_account_currency: Boolean,
                         val can_see_bank_account_iban: Boolean,
                         val can_see_bank_account_label: Boolean,
                         val can_see_bank_account_national_identifier: Boolean,
                         val can_see_bank_account_number: Boolean,
                         val can_see_bank_account_owners: Boolean,
                         val can_see_bank_account_swift_bic: Boolean,
                         val can_see_bank_account_type: Boolean,
                         val can_see_comments: Boolean,
                         val can_see_corporate_location: Boolean,
                         val can_see_image_url: Boolean,
                         val can_see_images: Boolean,
                         val can_see_more_info: Boolean,
                         val can_see_open_corporates_url: Boolean,
                         val can_see_other_account_bank_name: Boolean,
                         val can_see_other_account_iban: Boolean,
                         val can_see_other_account_kind: Boolean,
                         val can_see_other_account_metadata: Boolean,
                         val can_see_other_account_national_identifier: Boolean,
                         val can_see_other_account_number: Boolean,
                         val can_see_other_account_swift_bic: Boolean,
                         val can_see_owner_comment: Boolean,
                         val can_see_physical_location: Boolean,
                         val can_see_private_alias: Boolean,
                         val can_see_public_alias: Boolean,
                         val can_see_tags: Boolean,
                         val can_see_transaction_amount: Boolean,
                         val can_see_transaction_balance: Boolean,
                         val can_see_transaction_currency: Boolean,
                         val can_see_transaction_description: Boolean,
                         val can_see_transaction_finish_date: Boolean,
                         val can_see_transaction_metadata: Boolean,
                         val can_see_transaction_other_bank_account: Boolean,
                         val can_see_transaction_start_date: Boolean,
                         val can_see_transaction_this_bank_account: Boolean,
                         val can_see_transaction_type: Boolean,
                         val can_see_url: Boolean,
                         val can_see_where_tag: Boolean,
                         //V300 new 
                         val can_see_bank_routing_scheme: Boolean,
                         val can_see_bank_routing_address: Boolean,
                         val can_see_bank_account_routing_scheme: Boolean,
                         val can_see_bank_account_routing_address: Boolean,
                         val can_see_other_bank_routing_scheme: Boolean,
                         val can_see_other_bank_routing_address: Boolean,
                         val can_see_other_account_routing_scheme: Boolean,
                         val can_see_other_account_routing_address: Boolean,
                         val can_add_transaction_request_to_own_account: Boolean, //added following two for payments
                         val can_add_transaction_request_to_any_account: Boolean,
                         val can_see_bank_account_credit_limit: Boolean,
                         val can_create_direct_debit: Boolean,
                         val can_create_standing_order: Boolean
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
                                       accounts: List[(BankAccount, List[AccountAttribute])]) : CustomerOverviewJsonV500 = {
    CustomerOverviewJsonV500(
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
  def createCustomerOverviewFlatJson(cInfo : Customer, 
                                     customerAttributes: List[CustomerAttribute], 
                                     accounts: List[(BankAccount, List[AccountAttribute])]) : CustomerOverviewFlatJsonV500 = {
    CustomerOverviewFlatJsonV500(
      bank_id = cInfo.bankId,
      customer_id = cInfo.customerId,
      customer_number = cInfo.number,
      legal_name = cInfo.legalName,
      mobile_phone_number = cInfo.mobileNumber,
      email = cInfo.email,
      date_of_birth = cInfo.dateOfBirth,
      title = cInfo.title,
      branch_id = cInfo.branchId,
      name_suffix = cInfo.nameSuffix,
      customer_attributes = customerAttributes.map(JSONFactory300.createCustomerAttributeJson),
      accounts = createAccounts(accounts)
    )
  }
  
  def createContracts(list: List[AccountAttribute]) : Option[List[ContractJsonV500]] = {
    def getOptionalValue(key: String): Option[String] = {
      list.filter(_.name == key).map(_.value).headOption
    }
    val grouped: Map[String, List[AccountAttribute]] = list.filter(_.productInstanceCode.isDefined).groupBy(_.productInstanceCode.getOrElse("None"))
    val result = grouped.filter(i => i._1.trim().isEmpty == false).map(x => 
      ContractJsonV500(
        contract_code = x._1, 
        product_code = x._2.map(_.productCode.value).distinct.headOption.getOrElse(""),
        product_description = getOptionalValue("product_description"),
        issuance_amount = getOptionalValue("issuance_amount"),
        interest_rate = getOptionalValue("interest_rate"),
        term = getOptionalValue("term"),
        form_of_payment = getOptionalValue("form_of_payment"),
        interest_amount = getOptionalValue("interest_amount"),
        branch_code = getOptionalValue("branch_code"),
        payment_method = getOptionalValue("payment_method"),
        opening_date = getOptionalValue("opening_date"),
        maturity_date = getOptionalValue("maturity_date"),
        renewal_date = getOptionalValue("renewal_date"),
        cancellation_date = getOptionalValue("cancellation_date"),
        instrument_status_code = getOptionalValue("instrument_status_code"),
        instrument_status_definition = getOptionalValue("instrument_status_definition"),
        is_substituted = getOptionalValue("is_substituted")
      )
    ).toList
    Some(result)
  }
  
  def createAccounts(accounts: List[(BankAccount, List[AccountAttribute])]): List[AccountResponseJson500] = {
    accounts.map{ account =>
      AccountResponseJson500(
        account_id = account._1.accountId.value,
        label = account._1.label,
        product_code = account._1.accountType,
        balance = AmountOfMoneyJsonV121(account._1.balance.toString(), account._1.currency),
        branch_id = account._1.branchId,
        contracts = createContracts(account._2),
        account_routings = account._1.accountRoutings.map(i => AccountRoutingJsonV121(scheme = i.scheme, address = i.address)),
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
      technology = nullToString(card.technology),
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

  def createCustomerAccountLinkJson(customerAccountLink: CustomerAccountLinkTrait): CustomerAccountLinkJson ={
    CustomerAccountLinkJson(
    customerAccountLink.customerAccountLinkId,
    customerAccountLink.customerId,
    customerAccountLink.bankId,
    customerAccountLink.accountId,
    customerAccountLink.relationshipType
    )
  }
  
  def createCustomerAccountLinksJon(customerAccountLinks: List[CustomerAccountLinkTrait]): CustomerAccountLinksJson = {
    CustomerAccountLinksJson(customerAccountLinks.map(createCustomerAccountLinkJson))
  }



  def createViewJsonV500(view : View) : ViewJsonV500 = {
    val alias =
      if(view.usePublicAliasIfOneExists)
        "public"
      else if(view.usePrivateAliasIfOneExists)
        "private"
      else
        ""

    ViewJsonV500(
      id = view.viewId.value,
      short_name = stringOrNull(view.name),
      description = stringOrNull(view.description),
      metadata_view= view.metadataView,
      is_public = view.isPublic,
      is_system = view.isSystem,
      alias = alias,
      hide_metadata_if_alias_used = view.hideOtherAccountMetadataIfAlias,
      can_add_comment = view.canAddComment,
      can_add_corporate_location = view.canAddCorporateLocation,
      can_add_image = view.canAddImage,
      can_add_image_url = view.canAddImageURL,
      can_add_more_info = view.canAddMoreInfo,
      can_add_open_corporates_url = view.canAddOpenCorporatesUrl,
      can_add_physical_location = view.canAddPhysicalLocation,
      can_add_private_alias = view.canAddPrivateAlias,
      can_add_public_alias = view.canAddPublicAlias,
      can_add_tag = view.canAddTag,
      can_add_url = view.canAddURL,
      can_add_where_tag = view.canAddWhereTag,
      can_delete_comment = view.canDeleteComment,
      can_add_counterparty = view.canAddCounterparty,
      can_delete_corporate_location = view.canDeleteCorporateLocation,
      can_delete_image = view.canDeleteImage,
      can_delete_physical_location = view.canDeletePhysicalLocation,
      can_delete_tag = view.canDeleteTag,
      can_delete_where_tag = view.canDeleteWhereTag,
      can_edit_owner_comment = view.canEditOwnerComment,
      can_see_bank_account_balance = view.canSeeBankAccountBalance,
      can_query_available_funds = view.canQueryAvailableFunds,
      can_see_bank_account_bank_name = view.canSeeBankAccountBankName,
      can_see_bank_account_currency = view.canSeeBankAccountCurrency,
      can_see_bank_account_iban = view.canSeeBankAccountIban,
      can_see_bank_account_label = view.canSeeBankAccountLabel,
      can_see_bank_account_national_identifier = view.canSeeBankAccountNationalIdentifier,
      can_see_bank_account_number = view.canSeeBankAccountNumber,
      can_see_bank_account_owners = view.canSeeBankAccountOwners,
      can_see_bank_account_swift_bic = view.canSeeBankAccountSwift_bic,
      can_see_bank_account_type = view.canSeeBankAccountType,
      can_see_comments = view.canSeeComments,
      can_see_corporate_location = view.canSeeCorporateLocation,
      can_see_image_url = view.canSeeImageUrl,
      can_see_images = view.canSeeImages,
      can_see_more_info = view.canSeeMoreInfo,
      can_see_open_corporates_url = view.canSeeOpenCorporatesUrl,
      can_see_other_account_bank_name = view.canSeeOtherAccountBankName,
      can_see_other_account_iban = view.canSeeOtherAccountIBAN,
      can_see_other_account_kind = view.canSeeOtherAccountKind,
      can_see_other_account_metadata = view.canSeeOtherAccountMetadata,
      can_see_other_account_national_identifier = view.canSeeOtherAccountNationalIdentifier,
      can_see_other_account_number = view.canSeeOtherAccountNumber,
      can_see_other_account_swift_bic = view.canSeeOtherAccountSWIFT_BIC,
      can_see_owner_comment = view.canSeeOwnerComment,
      can_see_physical_location = view.canSeePhysicalLocation,
      can_see_private_alias = view.canSeePrivateAlias,
      can_see_public_alias = view.canSeePublicAlias,
      can_see_tags = view.canSeeTags,
      can_see_transaction_amount = view.canSeeTransactionAmount,
      can_see_transaction_balance = view.canSeeTransactionBalance,
      can_see_transaction_currency = view.canSeeTransactionCurrency,
      can_see_transaction_description = view.canSeeTransactionDescription,
      can_see_transaction_finish_date = view.canSeeTransactionFinishDate,
      can_see_transaction_metadata = view.canSeeTransactionMetadata,
      can_see_transaction_other_bank_account = view.canSeeTransactionOtherBankAccount,
      can_see_transaction_start_date = view.canSeeTransactionStartDate,
      can_see_transaction_this_bank_account = view.canSeeTransactionThisBankAccount,
      can_see_transaction_type = view.canSeeTransactionType,
      can_see_url = view.canSeeUrl,
      can_see_where_tag = view.canSeeWhereTag,
      //V300 new
      can_see_bank_routing_scheme         = view.canSeeBankRoutingScheme,
      can_see_bank_routing_address        = view.canSeeBankRoutingAddress,
      can_see_bank_account_routing_scheme  = view.canSeeBankAccountRoutingScheme,
      can_see_bank_account_routing_address = view.canSeeBankAccountRoutingAddress,
      can_see_other_bank_routing_scheme    = view.canSeeOtherBankRoutingScheme,
      can_see_other_bank_routing_address   = view.canSeeOtherBankRoutingAddress,
      can_see_other_account_routing_scheme = view.canSeeOtherAccountRoutingScheme,
      can_see_other_account_routing_address= view.canSeeOtherAccountRoutingAddress,
      can_add_transaction_request_to_own_account = view.canAddTransactionRequestToOwnAccount, //added following two for payments
      can_add_transaction_request_to_any_account = view.canAddTransactionRequestToAnyAccount,
      can_see_bank_account_credit_limit = view.canSeeBankAccountCreditLimit,
      can_create_direct_debit = view.canCreateDirectDebit,
      can_create_standing_order = view.canCreateStandingOrder,
      // Version 5.0.0
      can_grant_access_to_views = view.canGrantAccessToViews.getOrElse(Nil),
      can_revoke_access_to_views = view.canRevokeAccessToViews.getOrElse(Nil),
    )
  }
  def createViewsJsonV500(views : List[View]) : ViewsJsonV500 = {
    ViewsJsonV500(views.map(createViewJsonV500))
  }


  def createViewsIdsJsonV500(views : List[View]) : ViewsIdsJsonV500 = {
    ViewsIdsJsonV500(views.map(i => ViewIdJsonV500(i.viewId.value)))
  }
  
  def createAdapterInfoJson(inboundAdapterInfoInternal: InboundAdapterInfoInternal, startTime: Long): AdapterInfoJsonV500 = {
    AdapterInfoJsonV500(
      name = inboundAdapterInfoInternal.name,
      version = inboundAdapterInfoInternal.version,
      git_commit = inboundAdapterInfoInternal.git_commit,
      date = inboundAdapterInfoInternal.date,
      total_duration = BigDecimal(Helpers.now.getTime - startTime)/1000,
      backend_messages = inboundAdapterInfoInternal.backendMessages
    )
  }
  
}

