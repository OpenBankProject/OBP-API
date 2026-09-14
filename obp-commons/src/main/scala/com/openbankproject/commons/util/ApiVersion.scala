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

package com.openbankproject.commons.util

import org.json4s._
import com.openbankproject.commons.util.JsonAliases._

import java.util.concurrent.ConcurrentHashMap

object ApiStandards extends Enumeration {
  type ApiStandards = Value
  val obp = Value
}

object ApiShortVersions extends Enumeration {
  type ApiShortVersions = Value
  val `v1.2.1` = Value("v1.2.1")
  val `v1.3.0` = Value("v1.3.0")
  val `v1.4.0` = Value("v1.4.0")
  val `v2.0.0` = Value("v2.0.0")
  val `v2.1.0` = Value("v2.1.0")
  val `v2.2.0` = Value("v2.2.0")
  val `v3.0.0` = Value("v3.0.0")
  val `v3.1.0` = Value("v3.1.0")
  val `v4.0.0` = Value("v4.0.0")
  val `v5.0.0` = Value("v5.0.0")
  val `v5.1.0` = Value("v5.1.0")
  val `v6.0.0` = Value("v6.0.0")
  val `v7.0.0` = Value("v7.0.0")
  val `dynamic-endpoint` = Value("dynamic-endpoint")
  val `dynamic-entity` = Value("dynamic-entity")
}

object ApiVersionStatus extends Enumeration {
  type Status = Value
  val STABLE, BLEEDING_EDGE,DRAFT, DEPRECATED = Value
}

sealed trait ApiVersion {
  lazy val dottedApiVersion: String = this.toString.replace("_", ".").replace("v","")
  lazy val vDottedApiVersion: String = this.toString.replace("_", ".")
  lazy val noV: String = this.toString.replace("v", "").replace("V","")
  override def toString() = {
    val (head, tail) = getClass().getSimpleName.splitAt(1)
    head.toLowerCase() + tail
  }
}

/**
 * We need more fields for the versions. now, we support many standards: UKOpenBanking, BerlinGroup.
 * For each standard, we need its own `fullyQualifiedVersion`
 * @param urlPrefix : eg: `obp` or 'berlin`-group``
 * @param apiStandard eg: obp or `BG` or `UK`
 * @param apiShortVersion eg: `v1.2.1` or `v2.0`
 */
@SerialVersionUID(2319477438367593617L)
case class ScannedApiVersion(urlPrefix: String, apiStandard: String, apiShortVersion: String) extends ApiVersion with JsonAble {
  // record all scanned api versions
  ApiVersion.allScannedApiVersion.add(this)

  val fullyQualifiedVersion = s"${apiStandard.toUpperCase}$apiShortVersion"

  override def toString() = apiShortVersion

  // The deserialization instance is just for FrozenClassTest, to do check Frozen type whether be modified.
  // urlPrefix maybe changed by code.api.Constant.ApiPathZero, that is count as modify, So equals and hashCode not omit urlPrefix field
  def canEqual(other: Any): Boolean = other.isInstanceOf[ScannedApiVersion]

  override def equals(other: Any): Boolean = other match {
    case that: ScannedApiVersion =>
      (that canEqual this) &&
        apiStandard == that.apiStandard &&
        apiShortVersion == that.apiShortVersion
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(apiStandard, apiShortVersion)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toJValue(implicit format: Formats): JsonAST.JValue = {
    val jFields = JField("urlPrefix", JString(urlPrefix)) ::
      JField("apiStandard", JString(apiStandard)) ::
      JField("apiShortVersion", JString(apiShortVersion)) ::
      JField("API_VERSION", JString(this.vDottedApiVersion)) ::
      Nil

    JObject(jFields)
  }
}

object ApiVersion {
  //Special version: this has its own props: openid_connect.enabled
  case class OpenIdConnect1() extends ApiVersion
  lazy val openIdConnect1 = OpenIdConnect1()

  val allScannedApiVersion = ConcurrentHashMap.newKeySet[ScannedApiVersion]()

  /**
   * this version is for OBPRequired, match any ApiVersion
   */
  val allVersion = new ApiVersion {
    override def toString: String = "allVersion"
  }

  val urlPrefix: String = ApiStandards.obp.toString
  //OBP Standard
  val v1_2_1 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v1.2.1`.toString)
  val v1_3_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v1.3.0`.toString)
  val v1_4_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v1.4.0`.toString)
  val v2_0_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v2.0.0`.toString)
  val v2_1_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v2.1.0`.toString)
  val v2_2_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v2.2.0`.toString)
  val v3_0_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v3.0.0`.toString)
  val v3_1_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v3.1.0`.toString)
  val v4_0_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v4.0.0`.toString)
  val v5_0_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v5.0.0`.toString)
  val v5_1_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v5.1.0`.toString)
  val v6_0_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v6.0.0`.toString)
  val v7_0_0 = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`v7.0.0`.toString)
  val `dynamic-endpoint` = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`dynamic-endpoint`.toString)
  val `dynamic-entity` = ScannedApiVersion(urlPrefix,ApiStandards.obp.toString,ApiShortVersions.`dynamic-entity`.toString)
  
  //This is OBP standard version:
  val standardVersions = 
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
      `dynamic-entity`::
      Nil
      
  //This is other standard versions
  
  val berlinGroupV13 = ScannedApiVersion("berlin-group", "BG", "v1.3")
  val ukOpenBankingV20 = ScannedApiVersion("open-banking", "UK", "v2.0")
  val ukOpenBankingV31 = ScannedApiVersion("open-banking", "UK", "v3.1")
  val ukOpenBankingV401 = ScannedApiVersion("open-banking", "UK", "v4.0.1")

  // STET v1.4, Polish v2.1.1.1, CDS-AU v1.0.0, BAHRAIN-OBF v1.0.0, MxOF v1.0.0 and CNBV9 v1.0.0
  // were removed with the Lift teardown — code.api.STET, code.api.Polish, code.api.AUOpenBanking,
  // code.api.BahrainOBF and code.api.MxOF no longer exist, and RetiredApiStandardsTest keeps them
  // that way. Their constants lived on here, and because a ScannedApiVersion registers itself in
  // `allScannedApiVersion` when it is constructed, GET /obp/{v}/api/versions kept advertising six
  // standards this API cannot serve: asking for their resource docs answers OBP-00027. Do not
  // reinstate a constant without the implementation behind it.

  /**
   * the ApiPathZero value must be got by obp-api project, so here is a workaround, let obp-api project modify this value
   * and affect the follow OBP Standard versions.
   * @param apiPathZero
   */
  def setUrlPrefix(apiPathZero: String): Unit =
    standardVersions.foreach(ReflectUtils.setField(_, "urlPrefix", apiPathZero))
}
