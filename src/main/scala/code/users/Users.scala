package code.users

import net.liftweb.util.SimpleInjector
import net.liftweb.common.Box
import code.model.User

object Users  extends SimpleInjector {

  val users = new Inject(buildOne _) {}
  
  def buildOne: Users = LiftUsers
  
}

trait Users {
  def getUserByApiId(id : Long) : Box[User]
  
  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User]

  def getUserByUserId(userId : String) : Box[User]
}