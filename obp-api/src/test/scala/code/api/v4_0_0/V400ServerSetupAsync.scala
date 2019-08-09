package code.api.v4_0_0

import code.setup._
import dispatch.Req

/**
 * Created by Marko Milić on 07/09/18.
 */
trait V400ServerSetupAsync extends ServerSetupWithTestDataAsync with User1AllPrivilegesAsync with DefaultUsers {

  def v4_0_0_Request: Req = baseRequest / "obp" / "v4.0.0"
  
}