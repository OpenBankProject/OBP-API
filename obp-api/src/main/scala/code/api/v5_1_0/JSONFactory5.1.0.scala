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
package code.api.v5_1_0

import code.api.Constant
import code.api.util.{APIUtil, ConsentJWT, CustomJsonFormats, JwtUtil, Role}
import code.api.util.APIUtil.{gitCommit, stringOrNull}
import code.api.v1_2_1.BankRoutingJsonV121
import code.api.v1_4_0.JSONFactory1_4_0.{LocationJsonV140, MetaJsonV140, transformToLocationFromV140, transformToMetaFromV140}
import code.api.v2_1_0.ResourceUserJSON
import code.api.v3_0_0.JSONFactory300.{createLocationJson, createMetaJson, transformToAddressFromV300}
import code.api.v3_0_0.{AccountIdJson, AccountsIdsJsonV300, AddressJsonV300, OpeningTimesV300, ViewJsonV300}
import code.api.v4_0_0.{EnergySource400, HostedAt400, HostedBy400, PostViewJsonV400}
import code.api.v5_0_0.{PostConsentRequestJsonV500}
import code.atmattribute.AtmAttribute
import code.atms.Atms.Atm
import code.users.{UserAttribute, Users}
import code.views.system.{AccountAccess, ViewDefinition}
import com.openbankproject.commons.model.{AccountRoutingJsonV121, Address, AtmId, AtmT, BankId, BankIdAccountId, BranchRoutingJsonV141, CreateViewJson, Customer, Location, Meta, RegulatedEntityTrait, UpdateViewJSON, View}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}

import java.util.Date
import code.consent.MappedConsent
import code.metrics.APIMetric
import code.model.Consumer
import net.liftweb.common.{Box, Full}
import net.liftweb.json
import net.liftweb.json.{JString, JValue, parse, parseOpt}

import scala.collection.immutable.List
import scala.util.Try


case class SuggestedSessionTimeoutV510(timeout_in_seconds: String)
case class APIInfoJsonV510(
                           version : String,
                           version_status: String,
                           git_commit : String,
                           stage : String,
                           connector : String,
                           hostname : String,
                           local_identity_provider : String,
                           hosted_by : HostedBy400,
                           hosted_at : HostedAt400,
                           energy_source : EnergySource400,
                           resource_docs_requires_role: Boolean
                         )

case class RegulatedEntityJsonV510(
                                    entity_id: String,
                                    certificate_authority_ca_owner_id: String,
                                    entity_certificate_public_key: String,
                                    entity_name: String,
                                    entity_code: String,
                                    entity_type: String,
                                    entity_address: String,
                                    entity_town_city: String,
                                    entity_post_code: String,
                                    entity_country: String,
                                    entity_web_site: String,
                                    services: JValue
                                  )
case class RegulatedEntityPostJsonV510(
                                      certificate_authority_ca_owner_id: String,
                                      entity_certificate_public_key: String,
                                      entity_name: String,
                                      entity_code: String,
                                      entity_type: String,
                                      entity_address: String,
                                      entity_town_city: String,
                                      entity_post_code: String,
                                      entity_country: String,
                                      entity_web_site: String,
                                      services: JValue
                                    )
case class RegulatedEntitiesJsonV510(entities: List[RegulatedEntityJsonV510])

case class WaitingForGodotJsonV510(sleep_in_milliseconds: Long)

case class CertificateInfoJsonV510(
                                    subject_domain_name: String,
                                    issuer_domain_name: String,
                                    not_before: String,
                                    not_after: String,
                                    roles: Option[List[String]],
                                    roles_info: Option[String] = None
                                  )

case class CheckSystemIntegrityJsonV510(
  success: Boolean,
  debug_info: Option[String] = None
)

case class ConsentJsonV510(consent_id: String, 
                           jwt: String, 
                           status: String,
                           consent_request_id: Option[String],
                           scopes: Option[List[Role]])

case class CurrencyJsonV510(alphanumeric_code: String)
case class CurrenciesJsonV510(currencies: List[CurrencyJsonV510])

case class PostAtmJsonV510 (
  id : Option[String],
  bank_id : String,
  name : String,
  address: AddressJsonV300,
  location: LocationJsonV140,
  meta: MetaJsonV140,

  monday: OpeningTimesV300,
  tuesday: OpeningTimesV300,
  wednesday: OpeningTimesV300,
  thursday: OpeningTimesV300,
  friday: OpeningTimesV300,
  saturday: OpeningTimesV300,
  sunday: OpeningTimesV300,

  is_accessible : String,
  located_at : String,
  more_info : String,
  has_deposit_capability : String,

  supported_languages: List[String],
  services: List[String],
  accessibility_features: List[String],
  supported_currencies: List[String],
  notes: List[String],
  location_categories: List[String],
  minimum_withdrawal: String,
  branch_identification: String,
  site_identification: String,
  site_name: String,
  cash_withdrawal_national_fee: String,
  cash_withdrawal_international_fee: String,
  balance_inquiry_fee: String,
  atm_type: String,
  phone: String
)

case class PostCounterpartyLimitV510(
  currency: String,
  max_single_amount: Int,
  max_monthly_amount: Int,
  max_number_of_monthly_transactions: Int,
  max_yearly_amount: Int,
  max_number_of_yearly_transactions: Int
)

case class CounterpartyLimitV510(
  counterparty_limit_id: String,
  bank_id: String,
  account_id: String,
  view_id: String,
  counterparty_id: String,
  max_single_amount: Int,
  max_monthly_amount: Int,
  max_number_of_monthly_transactions: Int,
  max_yearly_amount: Int,
  max_number_of_yearly_transactions: Int
)

case class AtmJsonV510 (
  id : Option[String],
  bank_id : String,
  name : String,
  address: AddressJsonV300,
  location: LocationJsonV140,
  meta: MetaJsonV140,

  monday: OpeningTimesV300,
  tuesday: OpeningTimesV300,
  wednesday: OpeningTimesV300,
  thursday: OpeningTimesV300,
  friday: OpeningTimesV300,
  saturday: OpeningTimesV300,
  sunday: OpeningTimesV300,

  is_accessible : String,
  located_at : String,
  more_info : String,
  has_deposit_capability : String,

  supported_languages: List[String],
  services: List[String],
  accessibility_features: List[String],
  supported_currencies: List[String],
  notes: List[String],
  location_categories: List[String],
  minimum_withdrawal: String,
  branch_identification: String,
  site_identification: String,
  site_name: String,
  cash_withdrawal_national_fee: String,
  cash_withdrawal_international_fee: String,
  balance_inquiry_fee: String,
  atm_type: String,
  phone: String,
  attributes: Option[List[AtmAttributeResponseJsonV510]]
)

case class AtmsJsonV510(atms : List[AtmJsonV510])

case class ProductAttributeJsonV510(
                                     name: String,
                                     `type`: String,
                                     value: String,
                                     is_active: Option[Boolean]
                                   )
case class ProductAttributeResponseJsonV510(
                                             bank_id: String,
                                             product_code: String,
                                             product_attribute_id: String,
                                             name: String,
                                             `type`: String,
                                             value: String,
                                             is_active: Option[Boolean]
                                           )
case class ProductAttributeResponseWithoutBankIdJsonV510(
                                                          product_code: String,
                                                          product_attribute_id: String,
                                                          name: String,
                                                          `type`: String,
                                                          value: String,
                                                          is_active: Option[Boolean]
                                                        )

case class AtmAttributeJsonV510(
                                name: String,
                                `type`: String,
                                value: String,
                                is_active: Option[Boolean])

case class AtmAttributeResponseJsonV510(
                                        bank_id: String,
                                        atm_id: String,
                                        atm_attribute_id: String,
                                        name: String,
                                        `type`: String,
                                        value: String,
                                        is_active: Option[Boolean]
                                      )
case class AtmAttributesResponseJsonV510(atm_attributes: List[AtmAttributeResponseJsonV510])

case class UserAttributeResponseJsonV510(
  user_attribute_id: String,
  name: String,
  `type`: String,
  value: String,
  is_personal: Boolean,
  insert_date: Date
)

case class UserAttributeJsonV510(
  name: String,
  `type`: String,
  value: String
)

case class UserAttributesResponseJsonV510(
  user_attributes: List[UserAttributeResponseJsonV510]
)

case class CustomerIdJson(id: String)
case class CustomersIdsJsonV510(customers: List[CustomerIdJson])

case class MetricJsonV510(
                       user_id: String,
                       url: String,
                       date: Date,
                       user_name: String,
                       app_name: String,
                       developer_email: String,
                       implemented_by_partial_function: String,
                       implemented_in_version: String,
                       consumer_id: String,
                       verb: String,
                       correlation_id: String,
                       duration: Long,
                       source_ip: String,
                       target_ip: String,
                       response_body: JValue
                     )
case class MetricsJsonV510(metrics: List[MetricJsonV510])


case class ConsumerJwtPostJsonV510(jwt: String)
case class ConsumerPostJsonV510(app_name: Option[String],
                                app_type: Option[String],
                                description: String,
                                developer_email: Option[String],
                                redirect_url: Option[String],
                               )
case class ConsumerJsonV510(consumer_id: String,
                            consumer_key: String,
                            consumer_secret: String,
                            app_name: String,
                            app_type: String,
                            description: String,
                            developer_email: String,
                            company: String,
                            redirect_url: String,
                            certificate_pem: String,
                            certificate_info: Option[CertificateInfoJsonV510],
                            created_by_user: ResourceUserJSON,
                            enabled: Boolean,
                            created: Date
                           )

case class PostCreateUserAccountAccessJsonV510(username: String, provider:String, view_id:String)

case class PostAccountAccessJsonV510(user_id: String, view_id: String)

case class CreateCustomViewJson(
  name: String,
  description: String,
  metadata_view: String,
  is_public: Boolean,
  which_alias_to_use: String,
  hide_metadata_if_alias_used: Boolean,
  allowed_permissions : List[String],
) {
  def toCreateViewJson = CreateViewJson(
    name: String,
    description: String,
    metadata_view: String,
    is_public: Boolean,
    which_alias_to_use: String,
    hide_metadata_if_alias_used: Boolean,
    allowed_actions = allowed_permissions,
  )
}

case class UpdateCustomViewJson(
  description: String,
  metadata_view: String,
  is_public: Boolean,
  which_alias_to_use: String,
  hide_metadata_if_alias_used: Boolean,
  allowed_permissions: List[String]
) {
  def toUpdateViewJson = UpdateViewJSON(
    description: String,
    metadata_view: String,
    is_public: Boolean,
    is_firehose= None,
    which_alias_to_use: String,
    hide_metadata_if_alias_used: Boolean,
    allowed_actions = allowed_permissions
  )
}

case class CustomViewJsonV510(
  id: String,
  name: String,
  description: String,
  metadata_view: String,
  is_public: Boolean,
  alias: String,
  hide_metadata_if_alias_used: Boolean,
  allowed_permissions: List[String]
)

case class ConsentRequestFromAccountJson(
  bank_routing: BankRoutingJsonV121,
  account_routing: AccountRoutingJsonV121,
  branch_routing: BranchRoutingJsonV141
)

case class ConsentRequestToAccountJson(
  bank_routing: BankRoutingJsonV121,
  account_routing: AccountRoutingJsonV121,
  branch_routing: BranchRoutingJsonV141,
  limit: PostCounterpartyLimitV510
)

case class PostConsentRequestJsonV510(
  from_account:ConsentRequestFromAccountJson,
  to_account:ConsentRequestToAccountJson,
  valid_from: Option[Date],
  time_to_live: Option[Long]
){
  def toPostConsentRequestJsonV500 = {
    PostConsentRequestJsonV500(
      everything = false,
      bank_id = Some(from_account.bank_routing.address),
      account_access = Nil,
      entitlements = None,
      consumer_id = None,
      email = None,
      phone_number = None,
      valid_from = valid_from,
      time_to_live = time_to_live
    )
  }
}

object JSONFactory510 extends CustomJsonFormats {

  def createViewJson(view: View): CustomViewJsonV510 = {
    val alias =
      if (view.usePublicAliasIfOneExists)
        "public"
      else if (view.usePrivateAliasIfOneExists)
        "private"
      else
        ""
    CustomViewJsonV510(
      id = view.viewId.value,
      name = stringOrNull(view.name),
      description = stringOrNull(view.description),
      metadata_view = view.metadataView,
      is_public = view.isPublic,
      alias = alias,
      hide_metadata_if_alias_used = view.hideOtherAccountMetadataIfAlias,
      allowed_permissions = APIUtil.getViewPermissions(view.asInstanceOf[ViewDefinition]).toList
    )
  }
  def createCustomersIds(customers :  List[Customer]): CustomersIdsJsonV510 =
    CustomersIdsJsonV510(customers.map(x => CustomerIdJson(x.customerId)))

  def waitingForGodot(sleep: Long): WaitingForGodotJsonV510 = WaitingForGodotJsonV510(sleep)

  def createAtmsJsonV510(atmAndAttributesTupleList: List[(AtmT, List[AtmAttribute])] ): AtmsJsonV510 = {
    AtmsJsonV510(atmAndAttributesTupleList.map(
      atmAndAttributesTuple =>
        createAtmJsonV510(atmAndAttributesTuple._1,atmAndAttributesTuple._2)
    ))
  }

  def createAtmJsonV510(atm: AtmT, atmAttributes:List[AtmAttribute]): AtmJsonV510 = {
    AtmJsonV510(
      id = Some(atm.atmId.value),
      bank_id = atm.bankId.value,
      name = atm.name,
      AddressJsonV300(atm.address.line1,
        atm.address.line2,
        atm.address.line3,
        atm.address.city,
        atm.address.county.getOrElse(""),
        atm.address.state,
        atm.address.postCode,
        atm.address.countryCode),
      createLocationJson(atm.location),
      createMetaJson(atm.meta),
      monday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnMonday.getOrElse(""),
        closing_time = atm.ClosingTimeOnMonday.getOrElse("")),
      tuesday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnTuesday.getOrElse(""),
        closing_time = atm.ClosingTimeOnTuesday.getOrElse("")),
      wednesday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnWednesday.getOrElse(""),
        closing_time = atm.ClosingTimeOnWednesday.getOrElse("")),
      thursday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnThursday.getOrElse(""),
        closing_time = atm.ClosingTimeOnThursday.getOrElse("")),
      friday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnFriday.getOrElse(""),
        closing_time = atm.ClosingTimeOnFriday.getOrElse("")),
      saturday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnSaturday.getOrElse(""),
        closing_time = atm.ClosingTimeOnSaturday.getOrElse("")),
      sunday = OpeningTimesV300(
        opening_time = atm.OpeningTimeOnSunday.getOrElse(""),
        closing_time = atm.ClosingTimeOnSunday.getOrElse("")),
      is_accessible = atm.isAccessible.map(_.toString).getOrElse(""),
      located_at = atm.locatedAt.getOrElse(""),
      more_info = atm.moreInfo.getOrElse(""),
      has_deposit_capability = atm.hasDepositCapability.map(_.toString).getOrElse(""),
      supported_languages = atm.supportedLanguages.getOrElse(Nil),
      services = atm.services.getOrElse(Nil),
      accessibility_features = atm.accessibilityFeatures.getOrElse(Nil),
      supported_currencies = atm.supportedCurrencies.getOrElse(Nil),
      notes = atm.notes.getOrElse(Nil),
      location_categories = atm.locationCategories.getOrElse(Nil),
      minimum_withdrawal = atm.minimumWithdrawal.getOrElse(""),
      branch_identification = atm.branchIdentification.getOrElse(""),
      site_identification = atm.siteIdentification.getOrElse(""),
      site_name = atm.siteName.getOrElse(""),
      cash_withdrawal_national_fee = atm.cashWithdrawalNationalFee.getOrElse(""),
      cash_withdrawal_international_fee = atm.cashWithdrawalInternationalFee.getOrElse(""),
      balance_inquiry_fee = atm.balanceInquiryFee.getOrElse(""),
      atm_type = atm.atmType.getOrElse(""),
      phone = atm.phone.getOrElse(""),
      attributes = Some(atmAttributes.map(createAtmAttributeJson))
    )
  }

  def transformToAtmFromV510(postAtmJsonV510: PostAtmJsonV510): Atm = {
    val json = AtmJsonV510(
      id = postAtmJsonV510.id,
      bank_id = postAtmJsonV510.bank_id,
      name = postAtmJsonV510.name,
      address = postAtmJsonV510.address,
      location = postAtmJsonV510.location,
      meta = postAtmJsonV510.meta,
      monday = postAtmJsonV510.monday,
      tuesday = postAtmJsonV510.tuesday,
      wednesday = postAtmJsonV510.wednesday,
      thursday = postAtmJsonV510.thursday,
      friday = postAtmJsonV510.friday,
      saturday = postAtmJsonV510.saturday,
      sunday = postAtmJsonV510.sunday,
      is_accessible = postAtmJsonV510.is_accessible,
      located_at = postAtmJsonV510.located_at,
      more_info = postAtmJsonV510.more_info,
      has_deposit_capability = postAtmJsonV510.has_deposit_capability,
      supported_languages = postAtmJsonV510.supported_languages,
      services = postAtmJsonV510.services,
      accessibility_features =postAtmJsonV510.accessibility_features,
      supported_currencies = postAtmJsonV510.supported_currencies,
      notes = postAtmJsonV510.notes,
      location_categories = postAtmJsonV510.location_categories,
      minimum_withdrawal = postAtmJsonV510.minimum_withdrawal,
      branch_identification = postAtmJsonV510.branch_identification,
      site_identification = postAtmJsonV510.site_identification,
      site_name = postAtmJsonV510.site_name,
      cash_withdrawal_national_fee = postAtmJsonV510.cash_withdrawal_national_fee,
      cash_withdrawal_international_fee = postAtmJsonV510.cash_withdrawal_international_fee,
      balance_inquiry_fee = postAtmJsonV510.balance_inquiry_fee,
      atm_type = postAtmJsonV510.atm_type,
      phone = postAtmJsonV510.phone,
      attributes = None
    )
    transformToAtmFromV510(json)
  }
  def transformToAtmFromV510(atmJsonV510: AtmJsonV510): Atm = {
    val address: Address = transformToAddressFromV300(atmJsonV510.address) // Note the address in V220 is V140
    val location: Location = transformToLocationFromV140(atmJsonV510.location) // Note the location is V140
    val meta: Meta = transformToMetaFromV140(atmJsonV510.meta) // Note the meta  is V140
    val isAccessible: Boolean = Try(atmJsonV510.is_accessible.toBoolean).getOrElse(false)
    val hdc: Boolean = Try(atmJsonV510.has_deposit_capability.toBoolean).getOrElse(false)

    Atm(
      atmId = AtmId(atmJsonV510.id.getOrElse("")),
      bankId = BankId(atmJsonV510.bank_id),
      name = atmJsonV510.name,
      address = address,
      location = location,
      meta = meta,
      OpeningTimeOnMonday = Some(atmJsonV510.monday.opening_time),
      ClosingTimeOnMonday = Some(atmJsonV510.monday.closing_time),

      OpeningTimeOnTuesday = Some(atmJsonV510.tuesday.opening_time),
      ClosingTimeOnTuesday = Some(atmJsonV510.tuesday.closing_time),

      OpeningTimeOnWednesday = Some(atmJsonV510.wednesday.opening_time),
      ClosingTimeOnWednesday = Some(atmJsonV510.wednesday.closing_time),

      OpeningTimeOnThursday = Some(atmJsonV510.thursday.opening_time),
      ClosingTimeOnThursday = Some(atmJsonV510.thursday.closing_time),

      OpeningTimeOnFriday = Some(atmJsonV510.friday.opening_time),
      ClosingTimeOnFriday = Some(atmJsonV510.friday.closing_time),

      OpeningTimeOnSaturday = Some(atmJsonV510.saturday.opening_time),
      ClosingTimeOnSaturday = Some(atmJsonV510.saturday.closing_time),

      OpeningTimeOnSunday = Some(atmJsonV510.sunday.opening_time),
      ClosingTimeOnSunday = Some(atmJsonV510.sunday.closing_time),
      // Easy access for people who use wheelchairs etc. true or false ""=Unknown
      isAccessible = Some(isAccessible),
      locatedAt = Some(atmJsonV510.located_at),
      moreInfo = Some(atmJsonV510.more_info),
      hasDepositCapability = Some(hdc),

      supportedLanguages = Some(atmJsonV510.supported_languages),
      services = Some(atmJsonV510.services),
      accessibilityFeatures = Some(atmJsonV510.accessibility_features),
      supportedCurrencies = Some(atmJsonV510.supported_currencies),
      notes = Some(atmJsonV510.notes),
      minimumWithdrawal = Some(atmJsonV510.minimum_withdrawal),
      branchIdentification = Some(atmJsonV510.branch_identification),
      locationCategories = Some(atmJsonV510.location_categories),
      siteIdentification = Some(atmJsonV510.site_identification),
      siteName = Some(atmJsonV510.site_name),
      cashWithdrawalNationalFee = Some(atmJsonV510.cash_withdrawal_national_fee),
      cashWithdrawalInternationalFee = Some(atmJsonV510.cash_withdrawal_international_fee),
      balanceInquiryFee = Some(atmJsonV510.balance_inquiry_fee),
      atmType = Some(atmJsonV510.atm_type),
      phone = Some(atmJsonV510.phone)
    )
  }

  def getCustomViewNamesCheck(views: List[ViewDefinition]): CheckSystemIntegrityJsonV510 = {
    val success = views.size == 0
    val debugInfo = if(success) None else Some(s"Incorrect custom views: ${views.map(_.viewId.value).mkString(",")}")
    CheckSystemIntegrityJsonV510(
      success = success,
      debug_info = debugInfo
    )
  }
  def getSystemViewNamesCheck(views: List[ViewDefinition]): CheckSystemIntegrityJsonV510 = {
    val success = views.size == 0
    val debugInfo = if(success) None else Some(s"Incorrect system views: ${views.map(_.viewId.value).mkString(",")}")
    CheckSystemIntegrityJsonV510(
      success = success,
      debug_info = debugInfo
    )
  }
  def getAccountAccessUniqueIndexCheck(groupedRows: Map[String, List[AccountAccess]]): CheckSystemIntegrityJsonV510 = {
    val success = groupedRows.size == 0
    val debugInfo = if(success) None else Some(s"Incorrect system views: ${groupedRows.map(_._1).mkString(",")}")
    CheckSystemIntegrityJsonV510(
      success = success,
      debug_info = debugInfo
    )
  }
  def getSensibleCurrenciesCheck(bankCurrencies: List[String], accountCurrencies: List[String]): CheckSystemIntegrityJsonV510 = {
    val incorrectCurrencies: List[String] = bankCurrencies.filterNot(c => accountCurrencies.contains(c))
    val success = incorrectCurrencies.size == 0
    val debugInfo = if(success) None else Some(s"Incorrect currencies: ${incorrectCurrencies.mkString(",")}")
    CheckSystemIntegrityJsonV510(
      success = success,
      debug_info = debugInfo
    )
  }
  def getOrphanedAccountsCheck(orphanedAccounts: List[String]): CheckSystemIntegrityJsonV510 = {
    val success = orphanedAccounts.size == 0
    val debugInfo = if(success) None else Some(s"Orphaned account's ids: ${orphanedAccounts.mkString(",")}")
    CheckSystemIntegrityJsonV510(
      success = success,
      debug_info = debugInfo
    )
  }

  def getConsentInfoJson(consent: MappedConsent): ConsentJsonV510 = {
    val jsonWebTokenAsJValue: Box[ConsentJWT] = JwtUtil.getSignedPayloadAsJson(consent.jsonWebToken).map(parse(_).extract[ConsentJWT])
    ConsentJsonV510(
      consent.consentId,
      consent.jsonWebToken,
      consent.status,
      Some(consent.consentRequestId),
      jsonWebTokenAsJValue.map(_.entitlements).toOption
    )
  }

  def getApiInfoJSON(apiVersion : ApiVersion, apiVersionStatus: String) = {
    val organisation = APIUtil.getPropsValue("hosted_by.organisation", "TESOBE")
    val email = APIUtil.getPropsValue("hosted_by.email", "contact@tesobe.com")
    val phone = APIUtil.getPropsValue("hosted_by.phone", "+49 (0)30 8145 3994")
    val organisationWebsite = APIUtil.getPropsValue("organisation_website", "https://www.tesobe.com")
    val hostedBy = new HostedBy400(organisation, email, phone, organisationWebsite)

    val organisationHostedAt = APIUtil.getPropsValue("hosted_at.organisation", "")
    val organisationWebsiteHostedAt = APIUtil.getPropsValue("hosted_at.organisation_website", "")
    val hostedAt = HostedAt400(organisationHostedAt, organisationWebsiteHostedAt)

    val organisationEnergySource = APIUtil.getPropsValue("energy_source.organisation", "")
    val organisationWebsiteEnergySource = APIUtil.getPropsValue("energy_source.organisation_website", "")
    val energySource = EnergySource400(organisationEnergySource, organisationWebsiteEnergySource)

    val connector = APIUtil.getPropsValue("connector").openOrThrowException("no connector set")
    val resourceDocsRequiresRole = APIUtil.getPropsAsBoolValue("resource_docs_requires_role", false)

    APIInfoJsonV510(
      version = apiVersion.vDottedApiVersion,
      version_status = apiVersionStatus,
      git_commit = gitCommit,
      connector = connector,
      hostname = Constant.HostName,
      stage = System.getProperty("run.mode"),
      local_identity_provider = Constant.localIdentityProvider,
      hosted_by = hostedBy,
      hosted_at = hostedAt,
      energy_source = energySource,
      resource_docs_requires_role = resourceDocsRequiresRole
    )
  }

  def createAtmAttributeJson(atmAttribute: AtmAttribute): AtmAttributeResponseJsonV510 =
    AtmAttributeResponseJsonV510(
      bank_id = atmAttribute.bankId.value,
      atm_id = atmAttribute.atmId.value,
      atm_attribute_id = atmAttribute.atmAttributeId,
      name = atmAttribute.name,
      `type` = atmAttribute.attributeType.toString,
      value = atmAttribute.value,
      is_active = atmAttribute.isActive
    )

  def createAtmAttributesJson(atmAttributes: List[AtmAttribute]): AtmAttributesResponseJsonV510 =
    AtmAttributesResponseJsonV510(atmAttributes.map(createAtmAttributeJson))

  def createUserAttributeJson(userAttribute: UserAttribute): UserAttributeResponseJsonV510 = {
    UserAttributeResponseJsonV510(
      user_attribute_id = userAttribute.userAttributeId,
      name = userAttribute.name,
      `type` = userAttribute.attributeType.toString,
      value = userAttribute.value,
      insert_date = userAttribute.insertDate,
      is_personal = userAttribute.isPersonal
    )
  }

  def createUserAttributesJson(userAttribute: List[UserAttribute]): UserAttributesResponseJsonV510 = {
    UserAttributesResponseJsonV510(userAttribute.map(createUserAttributeJson))
  }

  def createRegulatedEntityJson(entity: RegulatedEntityTrait): RegulatedEntityJsonV510 = {
    RegulatedEntityJsonV510(
      entity_id = entity.entityId,
      certificate_authority_ca_owner_id = entity.certificateAuthorityCaOwnerId,
      entity_certificate_public_key = entity.entityCertificatePublicKey,
      entity_name = entity.entityName,
      entity_code = entity.entityCode,
      entity_type = entity.entityType,
      entity_address = entity.entityAddress,
      entity_town_city = entity.entityTownCity,
      entity_post_code = entity.entityPostCode,
      entity_country = entity.entityCountry,
      entity_web_site = entity.entityWebSite,
      services = json.parse(entity.services)
    )
  }
  def createRegulatedEntitiesJson(entities: List[RegulatedEntityTrait]): RegulatedEntitiesJsonV510 = {
    RegulatedEntitiesJsonV510(entities.map(createRegulatedEntityJson))
  }

  def createMetricJson(metric: APIMetric): MetricJsonV510 = {
    MetricJsonV510(
      user_id = metric.getUserId(),
      user_name = metric.getUserName(),
      developer_email = metric.getDeveloperEmail(),
      app_name = metric.getAppName(),
      url = metric.getUrl(),
      date = metric.getDate(),
      consumer_id = metric.getConsumerId(),
      verb = metric.getVerb(),
      implemented_in_version = metric.getImplementedInVersion(),
      implemented_by_partial_function = metric.getImplementedByPartialFunction(),
      correlation_id = metric.getCorrelationId(),
      duration = metric.getDuration(),
      source_ip = metric.getSourceIp(),
      target_ip = metric.getTargetIp(),
      response_body = parseOpt(metric.getResponseBody()).getOrElse(JString("Not enabled"))
    )
  }

  def createMetricsJson(metrics: List[APIMetric]): MetricsJsonV510 = {
    MetricsJsonV510(metrics.map(createMetricJson))
  }

  def createConsumerJSON(c: Consumer, certificateInfo: Option[CertificateInfoJsonV510] = None): ConsumerJsonV510 = {

    val resourceUserJSON = Users.users.vend.getUserByUserId(c.createdByUserId.toString()) match {
      case Full(resourceUser) => ResourceUserJSON(
        user_id = resourceUser.userId,
        email = resourceUser.emailAddress,
        provider_id = resourceUser.idGivenByProvider,
        provider = resourceUser.provider,
        username = resourceUser.name
      )
      case _ => null
    }

    ConsumerJsonV510(
      consumer_id = c.consumerId.get,
      consumer_key = c.key.get,
      consumer_secret = c.secret.get,
      app_name = c.name.get,
      app_type = c.appType.toString(),
      description = c.description.get,
      developer_email = c.developerEmail.get,
      company = c.company.get,
      redirect_url = c.redirectURL.get,
      certificate_pem = c.clientCertificate.get,
      certificate_info = certificateInfo,
      created_by_user = resourceUserJSON,
      enabled = c.isActive.get,
      created = c.createdAt.get
    )
  }


}

