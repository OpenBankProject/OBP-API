package code.api.util

import com.openbankproject.commons.util.{ScannedApiVersion}
import com.openbankproject.commons.util.ApiVersion._

object ApiVersionUtils {

  val scannedApis = ScannedApis.versionMapScannedApis.keysIterator.toList
  val versions =
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
      `dynamic-endpoint` ::
      `dynamic-entity` ::
      b1::
      scannedApis

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
      case `dynamic-endpoint`.fullyQualifiedVersion | `dynamic-endpoint`.apiShortVersion => `dynamic-endpoint`
      case `dynamic-entity`.fullyQualifiedVersion | `dynamic-entity`.apiShortVersion => `dynamic-entity`
      case b1.fullyQualifiedVersion     |     b1.apiShortVersion => b1
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