package code.remotedata

import akka.actor.Actor
import code.entitlement.{Entitlement, MappedEntitlementsProvider, RemotedataEntitlementsCaseClasses}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Box


class RemotedataEntitlementsActor extends Actor with ActorHelper with MdcLoggable {

  val mapper = MappedEntitlementsProvider
  val cc = RemotedataEntitlementsCaseClasses

  def receive = {

    case cc.getEntitlement(bankId: String, userId: String, roleName: String) =>
      logger.debug("getEntitlement(" + bankId +", "+ userId +", "+ roleName + ")")
      sender ! extractResult(mapper.getEntitlement(bankId, userId, roleName))

    case cc.getEntitlementById(entitlementId: String) =>
      logger.debug("getEntitlementById(" + entitlementId + ")")
      sender ! extractResult(mapper.getEntitlementById(entitlementId))

    case cc.getEntitlementsByUserId(userId: String) =>
      logger.debug("getEntitlementsByUserId(" + userId + ")")
      sender ! extractResult(mapper.getEntitlementsByUserId(userId))

    case cc.deleteEntitlement(entitlement: Box[Entitlement]) =>
      logger.debug("deleteEntitlement(" + entitlement + ")")
      sender ! extractResult(mapper.deleteEntitlement(entitlement))

    case cc.getEntitlements() =>
      logger.debug("getEntitlements(" + ")")
      sender ! extractResult(mapper.getEntitlements())

    case cc.addEntitlement(bankId: String, userId: String, roleName: String) =>
      logger.debug("addEntitlement(" + bankId +", "+ userId +", "+ roleName + ")")
      sender ! extractResult(mapper.addEntitlement(bankId, userId, roleName))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


