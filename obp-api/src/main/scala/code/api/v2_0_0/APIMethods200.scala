package code.api.v2_0_0

import net.liftweb.http.rest.RestHelper

trait APIMethods200 { self: RestHelper => }

object APIMethods200 extends RestHelper with APIMethods200 {
  val Implementations2_0_0 = Http4s200.Implementations2_0_0
}
