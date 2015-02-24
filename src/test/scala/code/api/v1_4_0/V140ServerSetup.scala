package code.api.v1_4_0

import code.api.test.ServerSetup
import dispatch._

trait V140ServerSetup extends ServerSetup {

  def v1_4Request = baseRequest / "obp" / "v1.4.0"

}
