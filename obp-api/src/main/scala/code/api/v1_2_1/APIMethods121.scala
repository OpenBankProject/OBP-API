package code.api.v1_2_1

import net.liftweb.http.rest.RestHelper

trait APIMethods121 { self: RestHelper => }
object APIMethods121 extends RestHelper with APIMethods121 {
  val Implementations1_2_1 = Http4s121.Implementations1_2_1
}
