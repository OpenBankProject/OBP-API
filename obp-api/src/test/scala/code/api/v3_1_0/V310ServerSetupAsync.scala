package code.api.v3_1_0

import code.setup._
import dispatch.Req

trait V310ServerSetupAsync extends ServerSetupWithTestDataAsync with DefaultUsers {

  def v3_1_0_Request: Req = baseRequest / "obp" / "v3.1.0"
  
}