package code.api.v1_4_0

import net.liftweb.http.rest.RestHelper

trait APIMethods140 { self: RestHelper => }
object APIMethods140 extends RestHelper with APIMethods140 {
  val Implementations1_4_0 = Http4s140.Implementations1_4_0
}
