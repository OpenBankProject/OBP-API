package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.entitlement.{Entitlement, EntitlementProvider, RemotedataEntitlementsCaseClasses}
import net.liftweb.common.Box
import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataEntitlements extends ObpActorInit with EntitlementProvider {

  val cc = RemotedataEntitlementsCaseClasses

  def getEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement] = getValueFromFuture(
    (actor ? cc.getEntitlement(bankId, userId, roleName)).mapTo[Box[Entitlement]]
  )

  def getEntitlementById(entitlementId: String) : Box[Entitlement] = getValueFromFuture(
    (actor ? cc.getEntitlementById(entitlementId)).mapTo[Box[Entitlement]]
  )

  def getEntitlementsByUserId(userId: String) : Box[List[Entitlement]] = getValueFromFuture(
    (actor ? cc.getEntitlementsByUserId(userId)).mapTo[Box[List[Entitlement]]]
  )

  def getEntitlementsByUserIdFuture(userId: String) : Future[Box[List[Entitlement]]] =
    (actor ? cc.getEntitlementsByUserIdFuture(userId)).mapTo[Box[List[Entitlement]]]

  def getEntitlementsByBankId(bankId: String) : Future[Box[List[Entitlement]]] =
    (actor ? cc.getEntitlementsByBankId(bankId)).mapTo[Box[List[Entitlement]]]

  def deleteEntitlement(entitlement: Box[Entitlement]) : Box[Boolean] = getValueFromFuture(
    (actor ? cc.deleteEntitlement(entitlement)).mapTo[Box[Boolean]]
  )

  def getEntitlements() : Box[List[Entitlement]] = getValueFromFuture(
    (actor ? cc.getEntitlements()).mapTo[Box[List[Entitlement]]]
  )

  def getEntitlementsByRole(roleName: String): Box[List[Entitlement]] =getValueFromFuture(
    (actor ? cc.getEntitlementsByRole(roleName)).mapTo[Box[List[Entitlement]]]
  )

  def getEntitlementsFuture() : Future[Box[List[Entitlement]]] =
    (actor ? cc.getEntitlementsFuture()).mapTo[Box[List[Entitlement]]]

  def getEntitlementsByRoleFuture(roleName: String) : Future[Box[List[Entitlement]]] =
    (actor ? cc.getEntitlementsByRoleFuture(roleName)).mapTo[Box[List[Entitlement]]]

  def addEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement] = getValueFromFuture(
    (actor ? cc.addEntitlement(bankId, userId, roleName)).mapTo[Box[Entitlement]]
  )

  override def deleteDynamicEntityEntitlement(entityName: String, bankId:Option[String]): Box[Boolean] = getValueFromFuture(
    (actor ? cc.deleteDynamicEntityEntitlement(entityName, bankId)).mapTo[Box[Boolean]]
  )

  override def deleteEntitlements(entityNames: List[String]) : Box[Boolean]  = getValueFromFuture(
    (actor ? cc.deleteEntitlements(entityNames)).mapTo[Box[Boolean]]
  )

}
