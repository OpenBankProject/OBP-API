package code.scope

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object Scope extends SimpleInjector {

  val scope = new Inject(buildOne _) {}

  def buildOne: ScopeProvider = MappedScopesProvider 
  
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
