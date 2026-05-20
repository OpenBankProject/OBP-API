package code.api.v2_1_0

import net.liftweb.http.rest.RestHelper

trait APIMethods210 { self: RestHelper => }

object APIMethods210 extends RestHelper with APIMethods210 {
  val Implementations2_1_0 = Http4s210.Implementations2_1_0
}
