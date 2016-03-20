package code.api

import code.api.util.ErrorMessages
import net.liftweb.common.Loggable
import net.liftweb.util.Props


// Note: Import this with: import code.api.Constant._
object Constant extends Loggable {
  logger.info("Instantiating Constants")

  final val HostName = Props.get("hostname").openOrThrowException(ErrorMessages.HostnameNotSpecified)

  // This is the part before the version. Do not change this default!
  final val ApiPathZero = Props.get("apiPathZero", "obp")

}
