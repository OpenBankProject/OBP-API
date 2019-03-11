package code.scope

import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object UserScope extends SimpleInjector {

  val userScope = new Inject(buildOne _) {}

  def buildOne: UserScopeProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedUserScopeProvider
      case true => MappedUserScopeProvider //RemotedataScopes     // We will use Akka as a middleware
    }
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

class RemotedataUserScopeCaseClasses {
  case class addUserScope(scopeId: String, userId: String)
  case class deleteUserScope(scopeId: String, userId: String)
  case class getUserScope(scopeId: String, userId: String)
  case class getUserScopesByScopeId(scopeId: String)
  case class getUserScopesByUserId(userId: String)
}

object RemotedataUserScopeCaseClasses extends RemotedataUserScopeCaseClasses