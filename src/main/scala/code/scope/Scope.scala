package code.scope

import code.api.util.APIUtil
import code.remotedata.RemotedataScopes
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object Scope extends SimpleInjector {

  val scope = new Inject(buildOne _) {}

  def buildOne: ScopeProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedScopesProvider
      case true => RemotedataScopes     // We will use Akka as a middleware
    }
}

trait Scope {
  def scopeId: String
  def bankId : String
  def consumerId : String
  def roleName : String
}

trait ScopeProvider {
  def getScope(bankId: String, consumerId: String, roleName: String) : Box[Scope]
  def getScopeById(ScopeId: String) : Box[Scope]
  def getScopesByConsumerId(consumerId: String) : Box[List[Scope]]
  def getScopesByConsumerIdFuture(consumerId: String) : Future[Box[List[Scope]]]
  def deleteScope(Scope: Box[Scope]) : Box[Boolean]
  def getScopes() : Box[List[Scope]]
  def getScopesFuture() : Future[Box[List[Scope]]]
  def addScope(bankId: String, consumerId: String, roleName: String) : Box[Scope]
}

class RemotedataScopesCaseClasses {
  case class getScope(bankId: String, consumerId: String, roleName: String)
  case class getScopeById(consumerScopeId: String)
  case class getScopesByConsumerId(consumerId: String)
  case class getScopesByConsumerIdFuture(consumerId: String)
  case class deleteScope(consumerScope: Box[Scope])
  case class getScopes()
  case class getScopesFuture()
  case class addScope(bankId: String, consumerId: String, roleName: String)
}

object RemotedataScopesCaseClasses extends RemotedataScopesCaseClasses