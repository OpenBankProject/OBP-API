package code.api

import net.liftweb.common.Loggable
import net.liftweb.util.Props


// Note: Import this with import code.api.Constant._
object Constant extends Loggable {
  logger.info("Instantiating Constants")
  // OBP API urls typically start with /obp/v2.0.0/my/accounts
  // There are two parts to the API prefix. This and the version
  final val ApiPathZero = Props.get("apiPathZero", "obp")

}
