package code.api.v6_0_0

import code.setup.{DefaultUsers, ServerSetupWithTestData}
import dispatch.Req

trait V600ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v4_0_0_Request: Req = baseRequest / "obp" / "v4.0.0"
  def v5_0_0_Request: Req = baseRequest / "obp" / "v5.0.0"
  def v5_1_0_Request: Req = baseRequest / "obp" / "v5.1.0"
  def v6_0_0_Request: Req = baseRequest / "obp" / "v6.0.0"
  
}