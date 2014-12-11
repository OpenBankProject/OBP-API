package code.users

import code.users.UserAuth.AuthResult
import net.liftweb.common.Failure

object DummyAuthChecker extends AuthChecker {
  override val authProviderId: String = "dummy-auth-provider"

  override val authFailedMessage = "No auth checker implemented"

  override def checkAuth(userId : String, password : String) : Option[AuthResult] = None
}
