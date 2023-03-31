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
import code.api.util.APIUtil
import code.api.util.APIUtil.gitCommit
import code.api.v4_0_0.{EnergySource400, HostedAt400, HostedBy400}
import code.atmattribute.AtmAttribute
import code.views.system.{AccountAccess, ViewDefinition}
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}

import scala.collection.immutable.List


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
case class CurrencyJsonV510(alphanumeric_code: String)
case class CurrenciesJsonV510(currencies: List[CurrencyJsonV510])



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
case class AtmAttributesResponseJsonV510(bank_attributes: List[AtmAttributeResponseJsonV510])
case class AtmAttributeBankResponseJsonV510(name: String,
                                             value: String)
case class AtmAttributesResponseJson(list: List[AtmAttributeBankResponseJsonV510])



object JSONFactory510 {
  
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
  
  def createAtmAttributesJson(bankAttributes: List[AtmAttribute]): AtmAttributesResponseJsonV510 =
    AtmAttributesResponseJsonV510(bankAttributes.map(createAtmAttributeJson))

  
}

