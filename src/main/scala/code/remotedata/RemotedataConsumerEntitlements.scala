package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.scope.{Scope, ScopeProvider, RemotedataScopesCaseClasses}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataScopes extends ObpActorInit with ScopeProvider {

  val cc = RemotedataScopesCaseClasses

  def getScope(bankId: String, consumerId: String, roleName: String) : Box[Scope] = getValueFromFuture(
    (actor ? cc.getScope(bankId, consumerId, roleName)).mapTo[Box[Scope]]
  )

  def getScopeById(scopeId: String) : Box[Scope] = getValueFromFuture(
    (actor ? cc.getScopeById(scopeId)).mapTo[Box[Scope]]
  )

  def getScopesByConsumerId(consumerId: String) : Box[List[Scope]] = getValueFromFuture(
    (actor ? cc.getScopesByConsumerId(consumerId)).mapTo[Box[List[Scope]]]
  )

  def getScopesByConsumerIdFuture(consumerId: String) : Future[Box[List[Scope]]] =
    (actor ? cc.getScopesByConsumerIdFuture(consumerId)).mapTo[Box[List[Scope]]]

  def deleteScope(scope: Box[Scope]) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.deleteScope(scope)).mapTo[Box[Boolean]]
  )

  def getScopes() : Box[List[Scope]] = getValueFromFuture(
    (actor ? cc.getScopes()).mapTo[Box[List[Scope]]]
  )

  def getScopesFuture() : Future[Box[List[Scope]]] =
    (actor ? cc.getScopesFuture()).mapTo[Box[List[Scope]]]

  def addScope(bankId: String, consumerId: String, roleName: String) : Box[Scope] = getValueFromFuture(
    (actor ? cc.addScope(bankId, consumerId, roleName)).mapTo[Box[Scope]]
  )

}
