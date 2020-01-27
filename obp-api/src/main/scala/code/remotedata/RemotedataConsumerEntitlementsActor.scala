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
      logger.debug(s"getScope($bankId, $consumerId, $roleName)")
      sender ! (mapper.getScope(bankId, consumerId, roleName))

    case cc.getScopeById(scopeId: String) =>
      logger.debug(s"getScopeById($scopeId")
      sender ! (mapper.getScopeById(scopeId))

    case cc.getScopesByConsumerId(consumerId: String) =>
      logger.debug(s"getScopesByConsumerId($consumerId)")
      sender ! (mapper.getScopesByConsumerId(consumerId))

    case cc.getScopesByConsumerIdFuture(consumerId: String) =>
      logger.debug(s"getScopesByConsumerIdFuture($consumerId)")
      sender ! (mapper.getScopesByConsumerId(consumerId))

    case cc.deleteScope(scope: Box[Scope]) =>
      logger.debug(s"deleteScope($scope)")
      sender ! (mapper.deleteScope(scope))

    case cc.getScopes() =>
      logger.debug("getScopes()")
      sender ! (mapper.getScopes())

    case cc.getScopesFuture() =>
      logger.debug("getScopesFuture()")
      sender ! (mapper.getScopes())

    case cc.addScope(bankId: String, consumerId: String, roleName: String) =>
      logger.debug(s"addScope($bankId, $consumerId, $roleName)")
      sender ! (mapper.addScope(bankId, consumerId, roleName))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


