package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.scope._
import code.scope.{MappedScopesProvider, RemotedataScopesCaseClasses}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Box

class RemotedataScopesActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedScopesProvider
  val cc = RemotedataScopesCaseClasses

  def receive = {

    case cc.getScope(bankId: String, consumerId: String, roleName: String) =>
      logger.debug("getScope(" + bankId +", "+ consumerId +", "+ roleName + ")")
      sender ! extractResult(mapper.getScope(bankId, consumerId, roleName))

    case cc.getScopeById(scopeId: String) =>
      logger.debug("getScopeById(" + scopeId + ")")
      sender ! extractResult(mapper.getScopeById(scopeId))

    case cc.getScopesByConsumerId(consumerId: String) =>
      logger.debug("getScopesByConsumerId(" + consumerId + ")")
      sender ! extractResult(mapper.getScopesByConsumerId(consumerId))

    case cc.getScopesByConsumerIdFuture(consumerId: String) =>
      logger.debug("getScopesByConsumerIdFuture(" + consumerId + ")")
      sender ! (mapper.getScopesByConsumerId(consumerId))

    case cc.deleteScope(scope: Box[Scope]) =>
      logger.debug("deleteScope(" + scope + ")")
      sender ! extractResult(mapper.deleteScope(scope))

    case cc.getScopes() =>
      logger.debug("getScopes(" + ")")
      sender ! extractResult(mapper.getScopes())

    case cc.getScopesFuture() =>
      logger.debug("getScopesFuture(" + ")")
      sender ! (mapper.getScopes())

    case cc.addScope(bankId: String, consumerId: String, roleName: String) =>
      logger.debug("addScope(" + bankId +", "+ consumerId +", "+ roleName + ")")
      sender ! extractResult(mapper.addScope(bankId, consumerId, roleName))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


