package code.api.v3_0_0

import code.setup._
import dispatch.Req

trait V300ServerSetupAsync extends ServerSetupWithTestDataAsync with DefaultUsers {

  def v3_0Request: Req = baseRequest / "obp" / "v3.0.0"
  
}