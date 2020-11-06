package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.entitlement.{ Entitlement, MappedEntitlementsProvider, RemotedataEntitlementsCaseClasses }
import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import akka.pattern.pipe
import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataEntitlementsActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedEntitlementsProvider
  val cc = RemotedataEntitlementsCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getEntitlement(bankId: String, userId: String, roleName: String) =>
      logger.debug(s"getEntitlement($bankId, $userId, $roleName)")
      sender ! (mapper.getEntitlement(bankId, userId, roleName))

    case cc.getEntitlementById(entitlementId: String) =>
      logger.debug(s"getEntitlementById($entitlementId)")
      sender ! (mapper.getEntitlementById(entitlementId))

    case cc.getEntitlementsByUserId(userId: String) =>
      logger.debug(s"getEntitlementsByUserId($userId)")
      sender ! (mapper.getEntitlementsByUserId(userId))

    case cc.getEntitlementsByUserIdFuture(userId: String) =>
      logger.debug(s"getEntitlementsByUserIdFuture($userId)")
      sender ! (mapper.getEntitlementsByUserId(userId))

    case cc.getEntitlementsByBankId(bankId: String) =>
      logger.debug(s"getEntitlementsByBankId($bankId)")
      (mapper.getEntitlementsByBankId(bankId)) pipeTo sender
    
    case cc.deleteEntitlement(entitlement: Box[Entitlement]) =>
      logger.debug(s"deleteEntitlement($entitlement)")
      sender ! (mapper.deleteEntitlement(entitlement))

    case cc.getEntitlements() =>
      logger.debug(s"getEntitlements()")
      sender ! (mapper.getEntitlements())

    case cc.getEntitlementsFuture() =>
      logger.debug("getEntitlementsFuture()")
      sender ! (mapper.getEntitlements())

    case cc.getEntitlementsByRoleFuture(role) =>
      logger.debug(s"getEntitlementsByRoleFuture($role)")
      (mapper.getEntitlementsByRoleFuture(role)) pipeTo sender

    case cc.getEntitlementsByRole(role) =>
      logger.debug(s"getEntitlementsByRole($role)")
      sender ! (mapper.getEntitlementsByRole(role))

    case cc.addEntitlement(bankId: String, userId: String, roleName: String) =>
      logger.debug(s"addEntitlement($bankId, $userId, $roleName)")
      sender ! (mapper.addEntitlement(bankId, userId, roleName))

    case cc.deleteDynamicEntityEntitlement(entityName: String, bankId:Option[String]) =>
      logger.debug(s"deleteDynamicEntityEntitlement($entityName) bankId($bankId)")
      sender ! (mapper.deleteDynamicEntityEntitlement(entityName, bankId))

    case cc.deleteEntitlements(entityNames) =>
      logger.debug(s"deleteEntitlements($entityNames)")
      sender ! (mapper.deleteEntitlements(entityNames))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

