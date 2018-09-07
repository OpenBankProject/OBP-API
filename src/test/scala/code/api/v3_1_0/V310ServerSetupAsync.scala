package code.api.v3_1_0

import code.setup._
import dispatch.Req

/**
 * Created by Marko Milić on 07/09/18.
 */
trait V310ServerSetupAsync extends ServerSetupWithTestDataAsync with User1AllPrivilegesAsync with DefaultUsers {

  def v3_1_0_Request: Req = baseRequest / "obp" / "v3.1.0"
  
}