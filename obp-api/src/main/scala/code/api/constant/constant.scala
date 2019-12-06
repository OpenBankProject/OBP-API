package code.api

import code.api.util.{APIUtil, ApiStandards, ErrorMessages}
import code.util.Helper.MdcLoggable


// Note: Import this with: import code.api.Constant._
object Constant extends MdcLoggable {
  logger.info("Instantiating Constants")

  final val HostName = APIUtil.getPropsValue("hostname").openOrThrowException(ErrorMessages.HostnameNotSpecified)

  // This is the part before the version. Do not change this default!
  final val ApiPathZero = APIUtil.getPropsValue("apiPathZero", ApiStandards.obp.toString)
  
  //Set this to `owner`. This is fro legacy.for the existing accounts, we do not modify them, just keep them as it is 
  //eg: one account, already have the owner view with bankId and accountId, so we keep it. actually it is a custom view,
  //    but there is no underscore there. 
  //But for new accounts, we only allow to create with with under score, and all the accounts will share the same System Views. 
  final val CUSTOM_OWNER_VIEW_ID = "owner"
  final val SYSTEM_OWNER_VIEW_ID = "owner"
  final val SYSTEM_AUDITOR_VIEW_ID = "auditor"
  final val SYSTEM_ACCOUNTANT_VIEW_ID = "accountant"
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

