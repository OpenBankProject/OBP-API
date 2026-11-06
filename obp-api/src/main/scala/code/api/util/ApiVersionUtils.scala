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

package code.api.util

import com.openbankproject.commons.util.ApiVersion._
import com.openbankproject.commons.util.ScannedApiVersion

object ApiVersionUtils {

  val scannedApis = ScannedApis.versionMapScannedApis.keysIterator.toList
  val versions = (
      v1_2_1 ::
      v1_3_0 ::
      v1_4_0 ::
      v2_0_0 ::
      v2_1_0 ::
      v2_2_0 ::
      v3_0_0 ::
      v3_1_0 ::
      v4_0_0 ::
      v5_0_0 ::
      v5_1_0 ::
      v6_0_0 ::
      v7_0_0 ::
      `dynamic-endpoint` ::
      `dynamic-entity` ::
      scannedApis
    ).distinct

  def valueOf(value: String): ScannedApiVersion = {

    //This `match` is used for compatibility. Previously we did not take care about BerlinGroup and UKOpenBanking versions carefully (since they didn't exist back in the day).
    // eg: v1 ==BGv1, v1.3 ==BGv1.3, v2.0 == UKv2.0
    // Now, we use the BerlinGroup standard version in OBP. But we need still make sure old version system is working.
    val compatibilityVersion = value match {
      case v1_2_1.fullyQualifiedVersion | v1_2_1.apiShortVersion => v1_2_1
      case v1_3_0.fullyQualifiedVersion | v1_3_0.apiShortVersion => v1_3_0
      case v1_4_0.fullyQualifiedVersion | v1_4_0.apiShortVersion => v1_4_0
      case v2_0_0.fullyQualifiedVersion | v2_0_0.apiShortVersion => v2_0_0
      case v2_1_0.fullyQualifiedVersion | v2_1_0.apiShortVersion => v2_1_0
      case v2_2_0.fullyQualifiedVersion | v2_2_0.apiShortVersion => v2_2_0
      case v3_0_0.fullyQualifiedVersion | v3_0_0.apiShortVersion => v3_0_0
      case v3_1_0.fullyQualifiedVersion | v3_1_0.apiShortVersion => v3_1_0
      case v4_0_0.fullyQualifiedVersion | v4_0_0.apiShortVersion => v4_0_0
      case v5_0_0.fullyQualifiedVersion | v5_0_0.apiShortVersion => v5_0_0
      case v5_1_0.fullyQualifiedVersion | v5_1_0.apiShortVersion => v5_1_0
      case v6_0_0.fullyQualifiedVersion | v6_0_0.apiShortVersion => v6_0_0
      case v7_0_0.fullyQualifiedVersion | v7_0_0.apiShortVersion => v7_0_0
      case `dynamic-endpoint`.fullyQualifiedVersion | `dynamic-endpoint`.apiShortVersion => `dynamic-endpoint`
      case `dynamic-entity`.fullyQualifiedVersion | `dynamic-entity`.apiShortVersion => `dynamic-entity`
      case version if(scannedApis.map(_.fullyQualifiedVersion).contains(version))
        =>scannedApis.filter(_.fullyQualifiedVersion==version).head
      case version if(scannedApis.map(_.apiShortVersion).contains(version))
        =>scannedApis.filter(_.apiShortVersion==version).head  
      case _=> throw new IllegalArgumentException("Incorrect ApiVersion value: " + value) // There is no Role  
    }                                 

    versions.filter(_ == compatibilityVersion) match {
      case x :: Nil => x // We find exactly one Role
      case x :: _ => throw new Exception("Duplicated version: " + x) // We find more than one Role
      case _ => throw new IllegalArgumentException("Incorrect ApiVersion value: " + value) // There is no Role
    }
  }


}