package code.api.v3_1_0

import code.setup._
import code.setup.OBPReq

trait V310ServerSetupAsync extends ServerSetupWithTestDataAsync with DefaultUsers {

  def v3_1_0_Request: OBPReq = baseRequest / "obp" / "v3.1.0"
  
}