/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

import code.api.util.ApiVersionUtils
import code.api.util.ApiVersionUtils.versions
import code.api.v4_0_0.V400ServerSetup

class ApiVersionUtilsTest extends V400ServerSetup {
  feature("test ApiVersionUtils.valueOf ") {
    scenario("support both fullyQualifiedVersion and apiShortVersion") {
    ApiVersionUtils.valueOf("v4.0.0")
    ApiVersionUtils.valueOf("OBPv4.0.0")

    ApiVersionUtils.valueOf("v1.3")
    ApiVersionUtils.valueOf("BGv1.3")
    ApiVersionUtils.valueOf("dynamic-endpoint")
    ApiVersionUtils.valueOf("dynamic-entity")

    
    versions.map(version => ApiVersionUtils.valueOf(version.apiShortVersion))
    versions.map(version => ApiVersionUtils.valueOf(version.fullyQualifiedVersion))

    //NOTE, when we added the new version, better fix this number manually. and also check the versions
    // 26 -> 20: removed Lift standards STET v1.4, Polish v2.1.1.1, AUOpenBanking v1.0.0,
    // BahrainOBF v1.0.0, MxOF v1.0.0 and CNBV9 v1.0.0 (6 scanned versions).
    // 20 -> 21: added UK Open Banking Read/Write v4.0.1 (OBP_UKOpenBanking_401).
    versions.length shouldBe(21)
  }}
}