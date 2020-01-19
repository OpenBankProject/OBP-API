package com.openbankproject.commons.util

object ApiStandards extends Enumeration {
  type ApiStandards = Value
  val obp = Value
  val `api-builder` = Value("api-builder")
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
  val b1 = Value
}

sealed trait ApiVersion {
  def dottedApiVersion() : String = this.toString.replace("_", ".").replace("v","")
  def vDottedApiVersion() : String = this.toString.replace("_", ".")
  def noV() : String = this.toString.replace("v", "").replace("V","")
  override def toString() = {
    val (head, tail) = getClass().getSimpleName.splitAt(1)
    head.toLowerCase() + tail
  }
}

/**
 * We need more fields for the versions. now, we support many standards: UKOpenBanking, BerlinGroup.
 * For each standard, we need its own `fullyQualifiedVersion`
 * @param urlPrefixFn : eg: `obp` or 'berlin`-group``
 * @param apiStandard eg: obp or `BG` or `UK`
 * @param apiShortVersion eg: `v1.2.1` or `v2.0`
 * note: why not use case class? because case class can't have call by name parameter. this is work around for
 */
class ScannedApiVersion(urlPrefixFn: => String, val apiStandard: String, val apiShortVersion: String) extends ApiVersion{

  def urlPrefix: String = urlPrefixFn

  val fullyQualifiedVersion = s"${apiStandard.toUpperCase}$apiShortVersion"

  override def toString() = apiShortVersion
}

object ScannedApiVersion {

  def apply(urlPrefix: => String, apiStandard: String, apiShortVersion: String) = new ScannedApiVersion(urlPrefix, apiStandard, apiShortVersion)

  def unapply(version: ScannedApiVersion): Option[(String, String, String)] =
    Option(version).map(v => (v.urlPrefix, v.apiStandard, v.apiShortVersion))
}

object ApiVersion {
  //Special versions
  case class ImporterApi() extends ApiVersion
  lazy val importerApi = ImporterApi()
  case class AccountsApi() extends ApiVersion
  lazy val accountsApi = AccountsApi()
  case class BankMockApi() extends ApiVersion
  lazy val bankMockApi = BankMockApi()

  case class OpenIdConnect1() extends ApiVersion
  lazy val openIdConnect1 = OpenIdConnect1()
  case class Sandbox() extends ApiVersion
  lazy val sandbox = Sandbox()

  /**
   * this version is for OBPRequired, match any ApiVersion
   */
  val allVersion = new ApiVersion {
    override def toString: String = "allVersion"
  }

  //Fixed the apiBuild apis as `api-builder` standard .
  lazy val apiBuilder = ScannedApiVersion("api-builder",ApiStandards.`api-builder`.toString, ApiShortVersions.b1.toString)

  // the ApiPathZero value must get by obp-api project, so here is a workaround, let obp-api project modify this value
  // and affect the follow OBP Standard versions
  var apiPathZero: String = ApiStandards.obp.toString
  //OBP Standard
  val v1_2_1 = ScannedApiVersion({apiPathZero},ApiStandards.obp.toString,ApiShortVersions.`v1.2.1`.toString)
  val v1_3_0 = ScannedApiVersion({apiPathZero},ApiStandards.obp.toString,ApiShortVersions.`v1.3.0`.toString)
  val v1_4_0 = ScannedApiVersion({apiPathZero},ApiStandards.obp.toString,ApiShortVersions.`v1.4.0`.toString)
  val v2_0_0 = ScannedApiVersion({apiPathZero},ApiStandards.obp.toString,ApiShortVersions.`v2.0.0`.toString)
  val v2_1_0 = ScannedApiVersion({apiPathZero},ApiStandards.obp.toString,ApiShortVersions.`v2.1.0`.toString)
  val v2_2_0 = ScannedApiVersion({apiPathZero},ApiStandards.obp.toString,ApiShortVersions.`v2.2.0`.toString)
  val v3_0_0 = ScannedApiVersion({apiPathZero},ApiStandards.obp.toString,ApiShortVersions.`v3.0.0`.toString)
  val v3_1_0 = ScannedApiVersion({apiPathZero},ApiStandards.obp.toString,ApiShortVersions.`v3.1.0`.toString)
  val v4_0_0 = ScannedApiVersion({apiPathZero},ApiStandards.obp.toString,ApiShortVersions.`v4.0.0`.toString)
}
