package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.entitlement.{Entitlement, EntitlementProvider, RemotedataEntitlementsCaseClasses}
import net.liftweb.common.Box
import scala.collection.immutable.List


object RemotedataEntitlements extends ObpActorInit with EntitlementProvider {

  val cc = RemotedataEntitlementsCaseClasses

  def getEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement] =
    extractFutureToBox(actor ? cc.getEntitlement(bankId, userId, roleName))

  def getEntitlementById(entitlementId: String) : Box[Entitlement] =
    extractFutureToBox(actor ? cc.getEntitlementById(entitlementId))

  def getEntitlementsByUserId(userId: String) : Box[List[Entitlement]] =
    extractFutureToBox(actor ? cc.getEntitlementsByUserId(userId))

  def deleteEntitlement(entitlement: Box[Entitlement]) : Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteEntitlement(entitlement))

  def getEntitlements() : Box[List[Entitlement]] =
    extractFutureToBox(actor ? cc.getEntitlements())

  def addEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement] =
    extractFutureToBox(actor ? cc.addEntitlement(bankId, userId, roleName))

}
