package code.api.v1_4_0

import code.api.OBPRestHelper
import net.liftweb.common.Loggable

object OBPAPI1_4_0 extends OBPRestHelper with APIMethods140 with Loggable {


  val VERSION = "1.4.0"

  val routes = List(
    Implementations1_4_0.getCustomerInfo,
    Implementations1_4_0.getCustomerMessages,
    Implementations1_4_0.addCustomerMessage,
    Implementations1_4_0.getBranches)

  routes.foreach(route => {
    oauthServe(apiPrefix{route})
  })
}
