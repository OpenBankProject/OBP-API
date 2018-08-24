package code.api.util

sealed trait ApiVersion {
  def dottedApiVersion() : String = this.toString.replace("_", ".").replace("v","")
  def vDottedApiVersion() : String = this.toString.replace("_", ".")
  def noV() : String = this.toString.replace("v", "").replace("V","")
  override def toString() = {
    val (head, tail) = getClass().getSimpleName.splitAt(1)
    head.toLowerCase() + tail
  }
}

object ApiVersion {
  case class V1_0() extends ApiVersion
  lazy val v1_0 = V1_0()
  case class V1_1() extends ApiVersion
  lazy val v1_1 = V1_1()
  case class V1_2() extends ApiVersion
  lazy val v1_2 = V1_2()
  case class V1_2_1() extends ApiVersion
  lazy val v1_2_1 = V1_2_1()
  case class V1_3_0() extends ApiVersion
  lazy val v1_3_0 = V1_3_0()
  case class V1_4_0() extends ApiVersion
  lazy val v1_4_0 = V1_4_0()
  case class V2_0_0() extends ApiVersion
  lazy val v2_0_0 = V2_0_0()
  case class V2_1_0() extends ApiVersion
  lazy val v2_1_0 = V2_1_0()
  case class V2_2_0() extends ApiVersion
  lazy val v2_2_0 = V2_2_0()
  case class V3_0_0() extends ApiVersion
  lazy val v3_0_0 = V3_0_0()
  case class V3_3_0() extends ApiVersion
  lazy val v3_1_0 = V3_1_0()
  case class V3_1_0() extends ApiVersion
  lazy val v3_3_0 = V3_3_0()
  case class ImporterApi() extends ApiVersion
  lazy val importerApi = ImporterApi()
  case class AccountsApi() extends ApiVersion
  lazy val accountsApi = AccountsApi()
  case class BankMockApi() extends ApiVersion
  lazy val bankMockApi = BankMockApi()
  case class BerlinGroupV1()  extends ApiVersion {
    override def toString() = "v1"
    //override def toString() = "berlin_group_v1" // TODO don't want to confuse with OBP
  }
  lazy val berlinGroupV1 = BerlinGroupV1()
  case class UKOpenBankingV200()  extends ApiVersion {
    override def toString() = "v2_0"
    // override def toString() = "uk_v2.0.0" // TODO don't want to confuse with OBP
  }
  lazy val ukOpenBankingV200 = UKOpenBankingV200()
  case class OpenIdConnect1() extends ApiVersion
  lazy val openIdConnect1 = OpenIdConnect1()
  case class Sandbox() extends ApiVersion
  lazy val sandbox = Sandbox()
  
  case class APIBuilder() extends ApiVersion {
    override def toString() = "b1"
    //override def toString() = "api_builder_v1" // TODO don't want to confuse with OBP
  }
  lazy val apiBuilder = APIBuilder()


  private val versions =
//    v1_0 ::
//      v1_1 ::
//      v1_2 ::
      v1_2_1 ::
      v1_3_0 ::
      v1_4_0 ::
      v2_0_0 ::
      v2_1_0 ::
      v2_2_0 ::
      v3_0_0 ::
      v3_1_0 ::
      v3_3_0 ::
      importerApi ::
      accountsApi ::
      bankMockApi ::
      openIdConnect1 ::
      sandbox ::
      berlinGroupV1 ::
      ukOpenBankingV200 ::
      apiBuilder::
      Nil

  def valueOf(value: String): ApiVersion = {
    versions.filter(_.vDottedApiVersion == value) match {
      case x :: Nil => x // We find exactly one Role
      case x :: _ => throw new Exception("Duplicated version: " + x) // We find more than one Role
      case _ => throw new IllegalArgumentException("Incorrect ApiVersion value: " + value) // There is no Role
    }
  }


}