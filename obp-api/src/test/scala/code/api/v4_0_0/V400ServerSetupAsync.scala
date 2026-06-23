package code.api.v4_0_0

import code.setup._
import code.setup.OBPReq

trait V400ServerSetupAsync extends ServerSetupWithTestDataAsync with DefaultUsers {

  def v4_0_0_Request: OBPReq = baseRequest / "obp" / "v4.0.0"
  
}