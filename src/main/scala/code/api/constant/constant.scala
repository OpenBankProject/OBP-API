package code.api

import code.api.util.{APIUtil, ErrorMessages}
import code.util.Helper.MdcLoggable


// Note: Import this with: import code.api.Constant._
object Constant extends MdcLoggable {
  logger.info("Instantiating Constants")

  final val HostName = APIUtil.getPropsValue("hostname").openOrThrowException(ErrorMessages.HostnameNotSpecified)

  // This is the part before the version. Do not change this default!
  final val ApiPathZero = APIUtil.getPropsValue("apiPathZero", "obp")

}




object ChargePolicy extends Enumeration {
  type ChargePolicy = Value
  val SHARED, SENDER, RECEIVER = Value
}
