package code.api.v2_2_0

import net.liftweb.http.rest.RestHelper

trait APIMethods220 { self: RestHelper => }

object APIMethods220 extends RestHelper with APIMethods220 {
  val Implementations2_2_0 = Http4s220.Implementations2_2_0
}
