package code.api.v4_0_0

import code.setup._
import dispatch.Req

trait V400ServerSetupAsync extends ServerSetupWithTestDataAsync with DefaultUsers {

  def v4_0_0_Request: Req = baseRequest / "obp" / "v4.0.0"
  
}