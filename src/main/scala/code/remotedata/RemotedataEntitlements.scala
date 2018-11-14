package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.entitlement.{Entitlement, EntitlementProvider, RemotedataEntitlementsCaseClasses}
import net.liftweb.common.Box
import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataEntitlements extends ObpActorInit with EntitlementProvider {

  val cc = RemotedataEntitlementsCaseClasses

  def getEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement] =
    extractFutureToBox(actor ? cc.getEntitlement(bankId, userId, roleName))

  def getEntitlementById(entitlementId: String) : Box[Entitlement] =
    extractFutureToBox(actor ? cc.getEntitlementById(entitlementId))

  def getEntitlementsByUserId(userId: String) : Box[List[Entitlement]] =
    extractFutureToBox(actor ? cc.getEntitlementsByUserId(userId))

  def getEntitlementsByUserIdFuture(userId: String) : Future[Box[List[Entitlement]]] =
    (actor ? cc.getEntitlementsByUserIdFuture(userId)).mapTo[Box[List[Entitlement]]]

  def deleteEntitlement(entitlement: Box[Entitlement]) : Box[Boolean] =
    extractFutureToBox(actor ? cc.deleteEntitlement(entitlement))

  def getEntitlements() : Box[List[Entitlement]] =
    extractFutureToBox(actor ? cc.getEntitlements())

  def getEntitlementsByRole(roleName: String): Box[List[Entitlement]] =
    extractFutureToBox(actor ? cc.getEntitlementsByRole(roleName))

  def getEntitlementsFuture() : Future[Box[List[Entitlement]]] =
    (actor ? cc.getEntitlementsFuture()).mapTo[Box[List[Entitlement]]]

  def getEntitlementsByRoleFuture(roleName: String) : Future[Box[List[Entitlement]]] =
    (actor ? cc.getEntitlementsByRoleFuture(roleName)).mapTo[Box[List[Entitlement]]]

  def addEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement] =
    extractFutureToBox(actor ? cc.addEntitlement(bankId, userId, roleName))

}
