package code.scope

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object UserScope extends SimpleInjector {

  val userScope = new Inject(buildOne _) {}

  def buildOne: UserScopeProvider = MappedUserScopeProvider
}

trait UserScope {
  def scopeId: String
  def userId : String
}

trait UserScopeProvider {
  def addUserScope(scopeId: String, userId: String): Box[UserScope]
  def deleteUserScope(scopeId: String, userId: String): Box[Boolean]
  def getUserScope(scopeId: String, userId: String): Box[UserScope] 
  def getUserScopesByScopeId(scopeId: String): Box[List[UserScope]]
  def getUserScopesByUserId(userId: String): Box[List[UserScope]]
}
