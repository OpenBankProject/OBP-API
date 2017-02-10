package code.users

import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import code.model.User
import code.views.AkkaMapperViews

object Users  extends SimpleInjector {

  val users = new Inject(buildOne _) {}
  
  def buildOne: Users = LiftUsers
  //def buildOne: Users = AkkaMapperViews
  
}

trait Users {
  def getUserByApiId(id : Long) : Box[User]
  
  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User]

  def getUserByUserId(userId : String) : Box[User]
}

class RemoteUserCaseClasses {
  case class getUserByApiId(id : Long)
  case class getUserByProviderId(provider : String, idGivenByProvider : String)
  case class getUserByUserId(userId : String)
}

object RemoteUserCaseClasses extends RemoteUserCaseClasses