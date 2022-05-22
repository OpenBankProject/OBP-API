package code.api.v5_0_0

import code.setup._
import dispatch.Req

trait V500ServerSetupAsync extends ServerSetupWithTestDataAsync with DefaultUsers {

  def v5_0_0_Request: Req = baseRequest / "obp" / "v5.0.0"
  
}