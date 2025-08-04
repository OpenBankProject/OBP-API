/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.util

import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.UKOpenBanking.v3_1_0.APIMethods_AccountAccessApi
import code.api.berlin.group.ConstantsBG
import code.api.builder.AccountInformationServiceAISApi.APIMethods_AccountInformationServiceAISApi
import code.api.util.APIUtil.OBPEndpoint
import code.api.util._
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.api.v4_0_0.{OBPAPI4_0_0, V400ServerSetup}
import code.setup.PropsReset
import code.views.system.ViewDefinition
import com.openbankproject.commons.util.ApiVersion

class APIUtilHeavyTest extends V400ServerSetup  with PropsReset {

  val bgVersion = ConstantsBG.berlinGroupVersion1.apiShortVersion
  
  feature("test APIUtil.versionIsAllowed method") {
    //This mean, we are only disabled the v4.0.0, all other versions should be enabled
    setPropsValues(
      "api_disabled_versions" -> "[v4.0.0]",
      "api_enabled_versions" -> "[]"
    )
    APIUtil.versionIsAllowed(ApiVersion.v4_0_0) should be(false)
    val allEnabledVersions= ApiVersionUtils.versions.filterNot(_ == ApiVersion.v4_0_0).map(APIUtil.versionIsAllowed)
    allEnabledVersions.contains(false) should be (false)

    setPropsValues(
      "api_disabled_versions" -> "[OBPv4.0.0]",
      "api_enabled_versions" -> "[]"
    )
    APIUtil.versionIsAllowed(ApiVersion.v4_0_0) should be(false)
    val allEnabledVersions2: List[Boolean] = ApiVersionUtils.versions.filterNot(_ == ApiVersion.v4_0_0).map(APIUtil.versionIsAllowed)
    allEnabledVersions2.contains(false) should be (false)

    setPropsValues(
      "api_disabled_versions" -> "[OBPv4.0.0,v3.1.0,v3.0.0,UKv3.1,UKv2.0]",
      "api_enabled_versions" -> "[]"
    )
    APIUtil.versionIsAllowed(ApiVersion.v4_0_0) should be(false)
    APIUtil.versionIsAllowed(ApiVersion.v3_1_0) should be(false)
    APIUtil.versionIsAllowed(ApiVersion.v3_0_0) should be(false)
    APIUtil.versionIsAllowed(ApiVersionUtils.versions.find(_.fullyQualifiedVersion=="UKv3.1").head) should be(false)
    APIUtil.versionIsAllowed(ApiVersionUtils.versions.find(_.fullyQualifiedVersion=="UKv2.0").head) should be(false)
    val allEnabledVersions3: List[Boolean] = ApiVersionUtils.versions
      .filterNot(_.fullyQualifiedVersion == ApiVersion.v4_0_0.fullyQualifiedVersion)
      .filterNot(_.fullyQualifiedVersion == ApiVersion.v3_1_0.fullyQualifiedVersion)
      .filterNot(_.fullyQualifiedVersion == ApiVersion.v3_0_0.fullyQualifiedVersion)
      .filterNot(_.fullyQualifiedVersion == "UKv3.1")
      .filterNot(_.fullyQualifiedVersion == "UKv2.0")
      .map(APIUtil.versionIsAllowed)
    allEnabledVersions3.contains(false) should be (false)
    
    
    When("we set OBPv4.0.0 both in enabled and disabled props, it should be disabled")
    setPropsValues(
      "api_disabled_versions" -> "[OBPv4.0.0]",
      "api_enabled_versions" -> "[OBPv4.0.0]"
    )
    APIUtil.versionIsAllowed(ApiVersion.v4_0_0) should be(false)
    APIUtil.versionIsAllowed(ApiVersion.v3_1_0) should be(false)


    When("we set OBPv4.0.0 both in enabled props, it will only enable one version, all other version will be disabled ")
    setPropsValues(
      "api_disabled_versions" -> "[]",
      "api_enabled_versions" -> "[OBPv4.0.0]"
    )
    APIUtil.versionIsAllowed(ApiVersion.v4_0_0) should be(true)
    APIUtil.versionIsAllowed(ApiVersion.v3_1_0) should be(false)
    APIUtil.versionIsAllowed(ApiVersion.v3_0_0) should be(false)
  }


  feature("test APIUtil.getAllowedEndpoints method") {
    scenario(s"Test the APIUtil.getAllowedEndpoints method") {
      val obpEndpointsV400: List[OBPEndpoint] = OBPAPI4_0_0.endpointsOf4_0_0.toList
      val obpAllResourceDocsV400 = Implementations4_0_0.resourceDocs

      val allowedEndpoints: List[APIUtil.ResourceDoc] = APIUtil.getAllowedResourceDocs(obpEndpointsV400, obpAllResourceDocsV400).toList

      val allowedOperationIds = allowedEndpoints.map(_.operationId)

      allowedOperationIds contains("OBPv4.0.0-getLogoutLink") should be (true)


      setPropsValues(
        "api_disabled_endpoints" -> "[OBPv4.0.0-getLogoutLink,OBPv4.0.0-getMapperDatabaseInfo,OBPv4.0.0-callsLimit,OBPv4.0.0-getBanks,OBPv4.0.0-ibanChecker]",
        "api_enabled_endpoints" -> "[]"
      )
      val allowedEndpoints2: List[APIUtil.ResourceDoc] = APIUtil.getAllowedResourceDocs(obpEndpointsV400, obpAllResourceDocsV400).toList

      val allowedOperationIds2 = allowedEndpoints2.map(_.operationId)

      allowedOperationIds2 contains("OBPv4.0.0-getLogoutLink") should be (false)
      allowedOperationIds2 contains("OBPv4.0.0-getMapperDatabaseInfo") should be (false)
      allowedOperationIds2 contains("OBPv4.0.0-callsLimit") should be (false)


      val bgResourceDocsV13 = APIMethods_AccountInformationServiceAISApi.resourceDocs
      val bgEndpointsV13 = APIMethods_AccountInformationServiceAISApi.endpoints

      setPropsValues(
        "api_disabled_endpoints" -> s"[BG${bgVersion}-createConsent,BG${bgVersion}-deleteConsent]",
        "api_enabled_endpoints" -> "[]"
      )

      val allowedEndpoints3: List[APIUtil.ResourceDoc] = APIUtil.getAllowedResourceDocs(bgEndpointsV13, bgResourceDocsV13).toList
      val allowedOperationIds3 = allowedEndpoints3.map(_.operationId)

      allowedOperationIds3 contains(s"BG${bgVersion}-getCardAccountTransactionList") should be (true)
      allowedOperationIds3 contains(s"BG${bgVersion}-createConsent") should be (false)
      allowedOperationIds3 contains(s"BG${bgVersion}-deleteConsent") should be (false)

      val ukResourceDocsV31 = APIMethods_AccountAccessApi.resourceDocs
      val ukEndpointsV31 = APIMethods_AccountAccessApi.endpoints

      setPropsValues(
        "api_disabled_endpoints" -> "[UKv3.1-createAccountAccessConsents,UKv3.1-deleteConsent]",
        "api_enabled_endpoints" -> "[]"
      )

      val allowedEndpoints4: List[APIUtil.ResourceDoc] = APIUtil.getAllowedResourceDocs(ukEndpointsV31, ukResourceDocsV31).toList
      val allowedOperationIds4 = allowedEndpoints4.map(_.operationId)

      allowedOperationIds4 contains("UKv3.1-getAccountAccessConsentsConsentId") should be (true)
      allowedOperationIds4 contains("UKv3.1-createAccountAccessConsents") should be (false)
      allowedOperationIds4 contains("UKv3.1-deleteConsent") should be (false)

      setPropsValues(
        "api_disabled_endpoints" -> "[]",
        "api_enabled_endpoints" -> "[UKv3.1-createAccountAccessConsents]"
      )

      val allowedEndpoints5: List[APIUtil.ResourceDoc] = APIUtil.getAllowedResourceDocs(ukEndpointsV31, ukResourceDocsV31).toList
      val allowedOperationIds5 = allowedEndpoints5.map(_.operationId)

      allowedOperationIds5.length should be (1)
      allowedOperationIds5 contains("UKv3.1-createAccountAccessConsents") should be (true)
      allowedOperationIds5 contains("UKv3.1-deleteConsent") should be (false)
    }
  }

  feature("test APIUtil.getPermissionPairFromViewDefinition method") {

    scenario(s"Test the getPermissionPairFromViewDefinition method") {

      val subList = List(
        "can_see_transaction_request_types",
        "can_see_available_views_for_bank_account",
        "can_see_views_with_permissions_for_all_users",
        "can_see_views_with_permissions_for_one_user",
        "can_see_transaction_this_bank_account",
        "can_see_transaction_other_bank_account",
        "can_see_transaction_description",
        "can_see_transaction_start_date",
        "can_see_transaction_finish_date",
        "can_see_bank_account_national_identifier",
        "can_see_bank_account_swift_bic",
        "can_see_transaction_status"
      ).toSet
      val systemOwnerView = getOrCreateSystemView(SYSTEM_OWNER_VIEW_ID)
      val permissions = systemOwnerView.asInstanceOf[ViewDefinition].allowed_actions.toSet

      subList.subsetOf(permissions)
    }
    
  }
  
}