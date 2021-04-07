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

import code.api.util._
import code.api.v4_0_0.V400ServerSetup
import code.setup.PropsReset
import com.openbankproject.commons.util.ApiVersion


class APIUtilHeavyTest extends V400ServerSetup  with PropsReset {
  
  feature("test APIUtil.versionIsAllowed method - apiShortVersion") {
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
  
}