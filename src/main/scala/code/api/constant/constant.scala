package code.api

import code.api.util.ErrorMessages
import code.util.Helper.MdcLoggable
import net.liftweb.util.Props


// Note: Import this with: import code.api.Constant._
object Constant extends MdcLoggable {
  logger.info("Instantiating Constants")

  final val HostName = Props.get("hostname").openOrThrowException(ErrorMessages.HostnameNotSpecified)

  // This is the part before the version. Do not change this default!
  final val ApiPathZero = Props.get("apiPathZero", "obp")

}




object ChargePolicy extends Enumeration {
  type ChargePolicy = Value
  val SHARED, SENDER, RECEIVER = Value
}
