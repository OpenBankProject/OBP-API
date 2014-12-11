package code.users

import code.model.dataAccess.APIUser
import code.users.UserAuth.AuthResult
import net.liftweb.common.{Full, Box, Failure}
import net.liftweb.mapper.By
import net.liftweb.util.SimpleInjector

object UserAuth  extends SimpleInjector {

  case class AuthResult(email : Option[String], displayName : Option[String])

  val authChecker = new Inject(buildOne _) {}

  //Replace this default auth checker with one that actually does something
  def buildOne: AuthChecker = DummyAuthChecker

}

trait AuthChecker {
  //The id of the authentication provider this trait checks against. e.g. "Foo Bank", "auth.example.com", etc.
  val authProviderId : String

  //Override this if you want to display a different message if authentication fails
  val authFailedMessage = "Authentication Failed: Invalid username/password"

  //Implement this method to ask your authentication provider if the userId and password match
  //and return Some[AuthResult] if so, None if authentication failed. If you don't have any information
  //about the user's email or name, simply return AuthResult(None, None)
  protected def checkAuth(userId : String, password : String) : Option[AuthResult]


  final def getAPIUser(userId : String, password : String) : Box[APIUser] = {
    checkAuth(userId, password) match {
      case Some(AuthResult(email, displayName)) => Full(getOrCreateAPIUser(userId, email, displayName))
      case None => Failure(authFailedMessage)
    }
  }

  private def getOrCreateAPIUser(userId : String, email : Option[String], displayName : Option[String]) = {
    getAPIUser(userId) match {
      case Some(user) => user
      case None => createAPIUser(userId, email, displayName)
    }
  }

  private def getAPIUser(userId : String) : Option[APIUser] =
    APIUser.find(By(APIUser.provider_, authProviderId), By(APIUser.providerId, userId))

  private def createAPIUser(userId : String, email : Option[String], displayName : Option[String]) : APIUser = {
    val apiUser = APIUser.create

    email.foreach(e => apiUser.email(e))

    apiUser
      .name_(displayName.getOrElse(""))
      .provider_(authProviderId)
      .providerId(userId)

    apiUser.saveMe
  }
}
