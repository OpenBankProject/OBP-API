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

import code.api.Constant
import code.api.Constant._
import code.api.util.APIUtil
import code.api.util.APIUtil.{gitCommit, nullToString, stringOptionOrNull, stringOrNull}
import code.api.util.ErrorMessages.MandatoryPropertyIsNotSet
import code.api.v1_2_1.BankRoutingJsonV121
import code.api.v1_3_0.JSONFactory1_3_0.{cardActionsToString, createAccountJson, createPinResetJson, createReplacementJson}
import code.api.v1_3_0.{PinResetJSON, ReplacementJSON}
import code.api.v1_4_0.JSONFactory1_4_0.{CustomerFaceImageJson, MetaJsonV140}
import code.api.v2_1_0.CustomerCreditRatingJSON
import code.api.v3_0_0.{CustomerAttributeResponseJsonV300, JSONFactory300}
import code.api.v3_1_0.{AccountBasicV310, PostConsentEntitlementJsonV310}
import code.api.v4_0_0._
import code.consent.ConsentRequest
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JsonAST.JValue
import net.liftweb.util.Helpers

import java.lang
import java.util.Date

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
  bank_id: Option[String],
  account_access: List[AccountAccessV500],
  entitlements: Option[List[PostConsentEntitlementJsonV310]],
  consumer_id: Option[String],
  email: Option[String],
  phone_number: Option[String],
  valid_from: Option[Date],
  time_to_live: Option[Long]
)
case class HelperInfoJson(
  counterparty_ids:List[String]
)

case class ConsentAccountAccessJson(
  bank_id:String,
  account_id:String,
  view_id:String,
  helper_info: Option[HelperInfoJson]
)


case class ConsentJsonV500(
  consent_id: String, 
  jwt: String, 
  status: String, 
  consent_request_id: Option[String], 
  account_access:Option[ConsentAccountAccessJson] = None
)

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

  def getApiInfoJSON(apiVersion : ApiVersion, apiVersionStatus : String) = {
    val organisation = APIUtil.getPropsValue("hosted_by.organisation", "TESOBE")
    val email = APIUtil.getPropsValue("hosted_by.email", "contact@tesobe.com")
    val phone = APIUtil.getPropsValue("hosted_by.phone", "+49 (0)30 8145 3994")
    val organisationWebsite = APIUtil.getPropsValue("organisation_website", "https://www.tesobe.com")
    val hostedBy = new HostedBy400(organisation, email, phone, organisationWebsite)

    val organisationHostedAt = APIUtil.getPropsValue("hosted_at.organisation", "")
    val organisationWebsiteHostedAt = APIUtil.getPropsValue("hosted_at.organisation_website", "")
    val hostedAt = new HostedAt400(organisationHostedAt, organisationWebsiteHostedAt)

    val organisationEnergySource = APIUtil.getPropsValue("energy_source.organisation", "")
    val organisationWebsiteEnergySource = APIUtil.getPropsValue("energy_source.organisation_website", "")
    val energySource = new EnergySource400(organisationEnergySource, organisationWebsiteEnergySource)

    val connector = code.api.Constant.CONNECTOR.openOrThrowException(s"$MandatoryPropertyIsNotSet. The missing prop is `connector` ")
    val resourceDocsRequiresRole = APIUtil.getPropsAsBoolValue("resource_docs_requires_role", false)

    APIInfoJson400(
      apiVersion.vDottedApiVersion,
      apiVersionStatus,
      gitCommit,
      connector,
      Constant.HostName,
      Constant.localIdentityProvider,
      hostedBy,
      hostedAt,
      energySource,
      resourceDocsRequiresRole
    )
  }

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

  def createBankJSON500(bank: Bank, attributes: List[BankAttributeTrait] = Nil): BankJson500 = {
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
      routings,
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

  def createConsentRequestResponseJson(createdConsentRequest: ConsentRequest): ConsentRequestResponseJson = {
    ConsentRequestResponseJson(
      createdConsentRequest.consentRequestId,
      net.liftweb.json.parse(createdConsentRequest.payload),
      createdConsentRequest.consumerId,
    )
  }

  def createViewJsonV500(view : View) : ViewJsonV500 = {
    val allowed_actions = view.allowed_actions
    
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
      is_firehose = Some(view.isFirehose),
      alias = alias,
      hide_metadata_if_alias_used = view.hideOtherAccountMetadataIfAlias,
      can_add_comment = allowed_actions.exists(_ == CAN_ADD_COMMENT),
      can_add_corporate_location = allowed_actions.exists(_ == CAN_ADD_CORPORATE_LOCATION),
      can_add_image = allowed_actions.exists(_ == CAN_ADD_IMAGE),
      can_add_image_url = allowed_actions.exists(_ == CAN_ADD_IMAGE_URL),
      can_add_more_info = allowed_actions.exists(_ == CAN_ADD_MORE_INFO),
      can_add_open_corporates_url = allowed_actions.exists(_ == CAN_ADD_OPEN_CORPORATES_URL),
      can_add_physical_location = allowed_actions.exists(_ == CAN_ADD_PHYSICAL_LOCATION),
      can_add_private_alias = allowed_actions.exists(_ == CAN_ADD_PRIVATE_ALIAS),
      can_add_public_alias = allowed_actions.exists(_ == CAN_ADD_PUBLIC_ALIAS),
      can_add_tag = allowed_actions.exists(_ == CAN_ADD_TAG),
      can_add_url = allowed_actions.exists(_ == CAN_ADD_URL),
      can_add_where_tag = allowed_actions.exists(_ == CAN_ADD_WHERE_TAG),
      can_delete_comment = allowed_actions.exists(_ == CAN_DELETE_COMMENT),
      can_add_counterparty = allowed_actions.exists(_ == CAN_ADD_COUNTERPARTY),
      can_delete_corporate_location = allowed_actions.exists(_ == CAN_DELETE_CORPORATE_LOCATION),
      can_delete_image = allowed_actions.exists(_ == CAN_DELETE_IMAGE),
      can_delete_physical_location = allowed_actions.exists(_ == CAN_DELETE_PHYSICAL_LOCATION),
      can_delete_tag = allowed_actions.exists(_ == CAN_DELETE_TAG),
      can_delete_where_tag = allowed_actions.exists(_ == CAN_DELETE_WHERE_TAG),
      can_edit_owner_comment = allowed_actions.exists(_ == CAN_EDIT_OWNER_COMMENT),
      can_see_bank_account_balance = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_BALANCE),
      can_query_available_funds = allowed_actions.exists(_ == CAN_QUERY_AVAILABLE_FUNDS),
      can_see_bank_account_bank_name = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_BANK_NAME),
      can_see_bank_account_currency = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_CURRENCY),
      can_see_bank_account_iban = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_IBAN),
      can_see_bank_account_label = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_LABEL),
      can_see_bank_account_national_identifier = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER),
      can_see_bank_account_number = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_NUMBER),
      can_see_bank_account_owners = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_OWNERS),
      can_see_bank_account_swift_bic = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_SWIFT_BIC),
      can_see_bank_account_type = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_TYPE),
      can_see_comments = allowed_actions.exists(_ == CAN_SEE_COMMENTS),
      can_see_corporate_location = allowed_actions.exists(_ == CAN_SEE_CORPORATE_LOCATION),
      can_see_image_url = allowed_actions.exists(_ == CAN_SEE_IMAGE_URL),
      can_see_images = allowed_actions.exists(_ == CAN_SEE_IMAGES),
      can_see_more_info = allowed_actions.exists(_ == CAN_SEE_MORE_INFO),
      can_see_open_corporates_url = allowed_actions.exists(_ == CAN_SEE_OPEN_CORPORATES_URL),
      can_see_other_account_bank_name = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_BANK_NAME),
      can_see_other_account_iban = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_IBAN),
      can_see_other_account_kind = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_KIND),
      can_see_other_account_metadata = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_METADATA),
      can_see_other_account_national_identifier = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER),
      can_see_other_account_number = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_NUMBER),
      can_see_other_account_swift_bic = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC),
      can_see_owner_comment = allowed_actions.exists(_ == CAN_SEE_OWNER_COMMENT),
      can_see_physical_location = allowed_actions.exists(_ == CAN_SEE_PHYSICAL_LOCATION),
      can_see_private_alias = allowed_actions.exists(_ == CAN_SEE_PRIVATE_ALIAS),
      can_see_public_alias = allowed_actions.exists(_ == CAN_SEE_PUBLIC_ALIAS),
      can_see_tags = allowed_actions.exists(_ == CAN_SEE_TAGS),
      can_see_transaction_amount = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_AMOUNT),
      can_see_transaction_balance = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_BALANCE),
      can_see_transaction_currency = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_CURRENCY),
      can_see_transaction_description = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_DESCRIPTION),
      can_see_transaction_finish_date = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_FINISH_DATE),
      can_see_transaction_metadata = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_METADATA),
      can_see_transaction_other_bank_account = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT),
      can_see_transaction_start_date = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_START_DATE),
      can_see_transaction_this_bank_account = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT),
      can_see_transaction_type = allowed_actions.exists(_ == CAN_SEE_TRANSACTION_TYPE),
      can_see_url = allowed_actions.exists(_ == CAN_SEE_URL),
      can_see_where_tag = allowed_actions.exists(_ == CAN_SEE_WHERE_TAG),
      //V300 new
      can_see_bank_routing_scheme         = allowed_actions.exists(_ == CAN_SEE_BANK_ROUTING_SCHEME),
      can_see_bank_routing_address        = allowed_actions.exists(_ == CAN_SEE_BANK_ROUTING_ADDRESS),
      can_see_bank_account_routing_scheme  = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME),
      can_see_bank_account_routing_address = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS),
      can_see_other_bank_routing_scheme    = allowed_actions.exists(_ == CAN_SEE_OTHER_BANK_ROUTING_SCHEME),
      can_see_other_bank_routing_address   = allowed_actions.exists(_ == CAN_SEE_OTHER_BANK_ROUTING_ADDRESS),
      can_see_other_account_routing_scheme = allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME),
      can_see_other_account_routing_address= allowed_actions.exists(_ == CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS),
      can_add_transaction_request_to_own_account = allowed_actions.exists(_ == CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT), //added following two for payments
      can_add_transaction_request_to_any_account = allowed_actions.exists(_ == CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT),
      can_see_bank_account_credit_limit = allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_CREDIT_LIMIT),
      can_create_direct_debit = allowed_actions.exists(_ == CAN_CREATE_DIRECT_DEBIT),
      can_create_standing_order = allowed_actions.exists(_ == CAN_CREATE_STANDING_ORDER),
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

