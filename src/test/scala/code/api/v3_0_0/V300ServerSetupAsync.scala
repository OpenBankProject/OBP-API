package code.api.v3_0_0

import code.setup._
import dispatch.Req

/**
 * Created by Marko Milić on 09/04/18.
 */
trait V300ServerSetupAsync extends ServerSetupWithTestDataAsync with User1AllPrivilegesAsync with DefaultUsers {

  def v3_0Request: Req = baseRequest / "obp" / "v3.0.0"
  
}