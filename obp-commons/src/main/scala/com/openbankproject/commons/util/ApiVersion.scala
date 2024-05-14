package com.openbankproject.commons.util

import com.openbankproject.commons.util.ApiShortVersions.Value

import java.util.concurrent.ConcurrentHashMap
import net.liftweb.json.{Formats, JField, JObject, JString, JsonAST}

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
  val `dynamic-endpoint` = Value("dynamic-endpoint")
  val `dynamic-entity` = Value("dynamic-entity")
}

object ApiVersionStatus extends Enumeration {
  type Status = Value
  val STABLE, BLEEDING_EDGE,DRAFT  = Value
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
      `dynamic-endpoint` :: 
      `dynamic-entity`::
      Nil
      
  //This is other standard versions
  
  val berlinGroupV13 = ScannedApiVersion("berlin-group", "BG", "v1.3")
  val mxofV100 = ScannedApiVersion("mxof", "MXOF", "v1.0.0")
  val cnbv9 = ScannedApiVersion("CNBV9", "CNBV9", "v1.0.0")

  /**
   * the ApiPathZero value must be got by obp-api project, so here is a workaround, let obp-api project modify this value
   * and affect the follow OBP Standard versions.
   * @param apiPathZero
   */
  def setUrlPrefix(apiPathZero: String): Unit =
    standardVersions.foreach(ReflectUtils.setField(_, "urlPrefix", apiPathZero))
}
