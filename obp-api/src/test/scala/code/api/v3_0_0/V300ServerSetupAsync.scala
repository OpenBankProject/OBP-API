package code.api.v3_0_0

import code.setup._
import code.setup.OBPReq

trait V300ServerSetupAsync extends ServerSetupWithTestDataAsync with DefaultUsers {

  def v3_0Request: OBPReq = baseRequest / "obp" / "v3.0.0"
  
}