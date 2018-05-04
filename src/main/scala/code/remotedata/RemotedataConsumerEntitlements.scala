package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.scope.{Scope, ScopeProvider, RemotedataScopesCaseClasses}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataScopes extends ObpActorInit with ScopeProvider {

  val cc = RemotedataScopesCaseClasses

  def getScope(bankId: String, consumerId: String, roleName: String) : Box[Scope] =
    extractFutureToBox(actor ? cc.getScope(bankId, consumerId, roleName))

  def getScopeById(scopeId: String) : Box[Scope] =
    extractFutureToBox(actor ? cc.getScopeById(scopeId))

  def getScopesByConsumerId(consumerId: String) : Box[List[Scope]] =
    extractFutureToBox(actor ? cc.getScopesByConsumerId(consumerId))

  def getScopesByConsumerIdFuture(consumerId: String) : Future[Box[List[Scope]]] =
    (actor ? cc.getScopesByConsumerIdFuture(consumerId)).mapTo[Box[List[Scope]]]

  def deleteScope(scope: Box[Scope]) : Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteScope(scope))

  def getScopes() : Box[List[Scope]] =
    extractFutureToBox(actor ? cc.getScopes())

  def getScopesFuture() : Future[Box[List[Scope]]] =
    (actor ? cc.getScopesFuture()).mapTo[Box[List[Scope]]]

  def addScope(bankId: String, consumerId: String, roleName: String) : Box[Scope] =
    extractFutureToBox(actor ? cc.addScope(bankId, consumerId, roleName))

}
