package code.api

import code.api.util.{APIUtil, ErrorMessages}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiStandards


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
  final val CUSTOM_PUBLIC_VIEW_ID = "_public"
  // If two owner views exists OBP will return custom owner view. But from this commit custom owner views are forbidden.
  final val CUSTOM_OWNER_VIEW_ID = "owner" // Legacy custom owner view maybe called this but creation of new custom owner views is now disabled with this commit
  final val SYSTEM_OWNER_VIEW_ID = "owner" // From this commit new owner views are system views
  final val SYSTEM_AUDITOR_VIEW_ID = "auditor"
  final val SYSTEM_ACCOUNTANT_VIEW_ID = "accountant"
  final val SYSTEM_FIREHOSE_VIEW_ID = "firehose"

  //These are the default incoming and outgoing account ids. we will create both during the boot.scala.
  final val INCOMING_ACCOUNT_ID= "OBP_DEFAULT_INCOMING_ACCOUNT_ID"    
  final val OUTGOING_ACCOUNT_ID= "OBP_DEFAULT_OUTGOING_ACCOUNT_ID"    

}




object ChargePolicy extends Enumeration {
  type ChargePolicy = Value
  val SHARED, SENDER, RECEIVER = Value
}

object RequestHeader {
  final lazy val `Consumer-Key` = "Consumer-Key"
  @deprecated("Use Consent-JWT","11-03-2020")
  final lazy val `Consent-Id` = "Consent-Id"
  final lazy val `Consent-JWT` = "Consent-JWT"
  final lazy val `PSD2-CERT` = "PSD2-CERT"
}
object ResponseHeader {
  final lazy val `Correlation-Id` = "Correlation-Id"
  final lazy val `WWW-Authenticate` = "WWW-Authenticate"
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

