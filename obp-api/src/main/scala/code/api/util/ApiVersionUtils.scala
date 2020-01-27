package code.api.util

import com.openbankproject.commons.util.ApiVersion
import com.openbankproject.commons.util.ApiVersion._

object ApiVersionUtils {

  val scannedApis = ScannedApis.versionMapScannedApis.keysIterator.toList
  private val versions =
      v1_2_1 ::
      v1_3_0 ::
      v1_4_0 ::
      v2_0_0 ::
      v2_1_0 ::
      v2_2_0 ::
      v3_0_0 ::
      v3_1_0 ::
      v4_0_0 ::
      importerApi ::
      accountsApi ::
      bankMockApi ::
      openIdConnect1 ::
      sandbox ::
      apiBuilder::
      scannedApis

  def valueOf(value: String): ApiVersion = {

    //This `match` is used for compatibility. Previously we did not take care about BerlinGroup and UKOpenBanking versions carefully (since they didn't exist back in the day).
    // eg: v1 ==BGv1, v1.3 ==BGv1.3, v2.0 == UKv2.0
    // Now, we use the BerlinGroup standard version in OBP. But we need still make sure old version system is working.
    val compatibilityVersion = value match {
      case v1_2_1.fullyQualifiedVersion => v1_2_1.apiShortVersion
      case v1_3_0.fullyQualifiedVersion => v1_3_0.apiShortVersion
      case v1_4_0.fullyQualifiedVersion => v1_4_0.apiShortVersion
      case v2_0_0.fullyQualifiedVersion => v2_0_0.apiShortVersion
      case v2_1_0.fullyQualifiedVersion => v2_1_0.apiShortVersion
      case v2_2_0.fullyQualifiedVersion => v2_2_0.apiShortVersion
      case v3_0_0.fullyQualifiedVersion => v3_0_0.apiShortVersion
      case v3_1_0.fullyQualifiedVersion => v3_1_0.apiShortVersion
      case v4_0_0.fullyQualifiedVersion => v4_0_0.apiShortVersion
      case apiBuilder.fullyQualifiedVersion => apiBuilder.apiShortVersion
      case version if(scannedApis.map(_.fullyQualifiedVersion).contains(version))
        =>scannedApis.filter(_.fullyQualifiedVersion==version).head.apiShortVersion
      case _=> value
    }

    versions.filter(_.vDottedApiVersion == compatibilityVersion) match {
      case x :: Nil => x // We find exactly one Role
      case x :: _ => throw new Exception("Duplicated version: " + x) // We find more than one Role
      case _ => throw new IllegalArgumentException("Incorrect ApiVersion value: " + value) // There is no Role
    }
  }


}