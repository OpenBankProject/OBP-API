package code.api

import code.api.util.{APIUtil, ApiStandards, ErrorMessages}
import code.util.Helper.MdcLoggable


// Note: Import this with: import code.api.Constant._
object Constant extends MdcLoggable {
  logger.info("Instantiating Constants")

  final val HostName = APIUtil.getPropsValue("hostname").openOrThrowException(ErrorMessages.HostnameNotSpecified)

  // This is the part before the version. Do not change this default!
  final val ApiPathZero = APIUtil.getPropsValue("apiPathZero", ApiStandards.obp.toString)

}




object ChargePolicy extends Enumeration {
  type ChargePolicy = Value
  val SHARED, SENDER, RECEIVER = Value
}

object RequestHeader {
  final lazy val `Consent-Id` = "Consent-Id"
  final lazy val `PSD2-CERT` = "PSD2-CERT"
}
object ResponseHeader {
  final lazy val `Correlation-Id` = "Correlation-Id"
}

object BerlinGroup extends Enumeration {
  object ScaStatus extends Enumeration{
    type ChargePolicy = Value
    val received, psuIdentified, psuAuthenticated, scaMethodSelected, started, finalised, failed, exempted = Value
  }
  object AuthenticationType extends Enumeration{
    type ChargePolicy = Value
    val SMS_OTP, CHIP_OTP, PHOTO_OTP, PUSH_OTP = Value
  }
}