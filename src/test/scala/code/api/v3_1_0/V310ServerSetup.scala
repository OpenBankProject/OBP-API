package code.api.v3_1_0

import code.setup.{DefaultUsers, ServerSetupWithTestData, User1AllPrivileges}
import dispatch.Req

/**
  * Created by Marko Milić on 07/09/18.
  */
trait V310ServerSetup extends ServerSetupWithTestData with User1AllPrivileges with DefaultUsers {

  def v3_1_0_Request: Req = baseRequest / "obp" / "v3.1.0"
  
}