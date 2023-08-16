package code.api

import code.api.util.{APIUtil, ErrorMessages}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiStandards


// Note: Import this with: import code.api.Constant._
object Constant extends MdcLoggable {
  logger.info("Instantiating Constants")
  
  object Pagination {
    final val offset = 0
    final val limit = 500
  }
  
  final val shortEndpointTimeoutInMillis = APIUtil.getPropsAsLongValue(nameOfProperty = "short_endpoint_timeout", 1L * 1000L)
  final val mediumEndpointTimeoutInMillis = APIUtil.getPropsAsLongValue(nameOfProperty = "medium_endpoint_timeout", 7L * 1000L)
  final val longEndpointTimeoutInMillis = APIUtil.getPropsAsLongValue(nameOfProperty = "long_endpoint_timeout", 60L * 1000L)
  
  final val h2DatabaseDefaultUrlValue = "jdbc:h2:mem:OBPTest_H2_v2.1.214;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=10"

  final val HostName = APIUtil.getPropsValue("hostname").openOrThrowException(ErrorMessages.HostnameNotSpecified)
  def localIdentityProvider = APIUtil.getPropsValue("local_identity_provider", HostName)

  // This is the part before the version. Do not change this default!
  final val ApiPathZero = APIUtil.getPropsValue("apiPathZero", ApiStandards.obp.toString)
  
  final val CUSTOM_PUBLIC_VIEW_ID = "_public"
  final val SYSTEM_OWNER_VIEW_ID = "owner" // From this commit new owner views are system views
  final val SYSTEM_AUDITOR_VIEW_ID = "auditor"
  final val SYSTEM_ACCOUNTANT_VIEW_ID = "accountant"
  final val SYSTEM_FIREHOSE_VIEW_ID = "firehose"
  final val SYSTEM_STANDARD_VIEW_ID = "standard"
  final val SYSTEM_STAGE_ONE_VIEW_ID = "StageOne"
  final val SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID = "ManageCustomViews"
  final val SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID = "ReadAccountsBasic"
  final val SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID = "ReadAccountsDetail"
  final val SYSTEM_READ_BALANCES_VIEW_ID = "ReadBalances"
  final val SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID = "ReadTransactionsBasic"
  final val SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID = "ReadTransactionsDebits"
  final val SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID = "ReadTransactionsDetail"
  // Berlin Group
  final val SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID = "ReadAccountsBerlinGroup"
  final val SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID = "ReadBalancesBerlinGroup"
  final val SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID = "ReadTransactionsBerlinGroup"

  //TODO, this need to be double check
  final val ALL_SYSTEM_VIEWS_CREATED_FROM_BOOT = List(
    SYSTEM_OWNER_VIEW_ID,
    SYSTEM_AUDITOR_VIEW_ID,
    SYSTEM_ACCOUNTANT_VIEW_ID,
    SYSTEM_FIREHOSE_VIEW_ID,
    SYSTEM_STANDARD_VIEW_ID,
    SYSTEM_STAGE_ONE_VIEW_ID,
    SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID,
    SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID,
    SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID,
    SYSTEM_READ_BALANCES_VIEW_ID,
    SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID,
    SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID,
    SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID,
    SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID,
    SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID,
    SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID
  ) 
  //These are the default incoming and outgoing account ids. we will create both during the boot.scala.
  final val INCOMING_SETTLEMENT_ACCOUNT_ID = "OBP-INCOMING-SETTLEMENT-ACCOUNT"    
  final val OUTGOING_SETTLEMENT_ACCOUNT_ID = "OBP-OUTGOING-SETTLEMENT-ACCOUNT"    
  final val ALL_CONSUMERS = "ALL_CONSUMERS"  

  final val PARAM_LOCALE = "locale"
  final val PARAM_TIMESTAMP = "_timestamp_"
}




object ChargePolicy extends Enumeration {
  type ChargePolicy = Value
  val SHARED, SENDER, RECEIVER = Value
}

object RequestHeader {
  final lazy val `Consumer-Key` = "Consumer-Key"
  @deprecated("Use Consent-JWT","11-03-2020")
  final lazy val `Consent-Id` = "Consent-Id"
  final lazy val `Consent-ID` = "Consent-ID" // Berlin Group
  final lazy val `Consent-JWT` = "Consent-JWT"
  final lazy val `PSD2-CERT` = "PSD2-CERT"
  final lazy val `If-None-Match` = "If-None-Match"
  /**
   * The If-Modified-Since request HTTP header makes the request conditional: 
   * the server sends back the requested resource, with a 200 status, 
   * only if it has been last modified after the given date. 
   * If the resource has not been modified since, the response is a 304 without any body; 
   * the Last-Modified response header of a previous request contains the date of last modification. 
   * Unlike If-Unmodified-Since, If-Modified-Since can only be used with a GET or HEAD.
   *
   * When used in combination with If-None-Match, it is ignored, unless the server doesn't support If-None-Match. 
   */
  final lazy val `If-Modified-Since` = "If-Modified-Since"
}
object ResponseHeader {
  final lazy val `Correlation-Id` = "Correlation-Id"
  final lazy val `WWW-Authenticate` = "WWW-Authenticate"
  final lazy val ETag = "ETag"
  final lazy val `Cache-Control` = "Cache-Control"
  final lazy val Connection = "Connection"
}

object BerlinGroup extends Enumeration {
  object ScaStatus extends Enumeration{
    type ChargePolicy = Value
    val received, psuIdentified, psuAuthenticated, scaMethodSelected, started, finalised, failed, exempted = Value
  }
  object AuthenticationType extends Enumeration{
    type ChargePolicy = Value
//    - 'SMS_OTP': An SCA method, where an OTP linked to the transaction to be authorised is sent to the PSU through a SMS channel.
//      - 'CHIP_OTP': An SCA method, where an OTP is generated by a chip card, e.g. a TOP derived from an EMV cryptogram.
//      To contact the card, the PSU normally needs a (handheld) device.
//    With this device, the PSU either reads the challenging data through a visual interface like flickering or
//      the PSU types in the challenge through the device key pad.
//      The device then derives an OTP from the challenge data and displays the OTP to the PSU.
//      - 'PHOTO_OTP': An SCA method, where the challenge is a QR code or similar encoded visual data
//      which can be read in by a consumer device or specific mobile app.
//      The device resp. the specific app than derives an OTP from the visual challenge data and displays
//      the OTP to the PSU.
//      - 'PUSH_OTP': An OTP is pushed to a dedicated authentication APP and displayed to the PSU.
    val SMS_OTP, CHIP_OTP, PHOTO_OTP, PUSH_OTP = Value
  }
}

