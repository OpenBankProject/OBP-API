package code.entitlement


import code.api.util.APIUtil
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future

object Entitlement extends SimpleInjector {

  val entitlement = new Inject(buildOne _) {}

  def buildOne: EntitlementProvider = MappedEntitlementsProvider 
  
}

trait EntitlementProvider {
  def getEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement]
  def getEntitlementById(entitlementId: String) : Box[Entitlement]
  def getEntitlementsByUserId(userId: String) : Box[List[Entitlement]]
  def getEntitlementsByUserIdFuture(userId: String) : Future[Box[List[Entitlement]]]
  def getEntitlementsByBankId(bankId: String) : Future[Box[List[Entitlement]]]
  def deleteEntitlement(entitlement: Box[Entitlement]) : Box[Boolean]
  def getEntitlements() : Box[List[Entitlement]]
  def getEntitlementsByRole(roleName: String): Box[List[Entitlement]]
  def getEntitlementsFuture() : Future[Box[List[Entitlement]]]
  def getEntitlementsByRoleFuture(roleName: String) : Future[Box[List[Entitlement]]]
  def addEntitlement(bankId: String, userId: String, roleName: String, createdByProcess: String="manual", grantorUserId: Option[String]=None) : Box[Entitlement]
  def deleteDynamicEntityEntitlement(entityName: String, bankId:Option[String]) : Box[Boolean]
  def deleteEntitlements(entityNames: List[String]) : Box[Boolean]
}

trait Entitlement {
  def entitlementId: String
  def bankId : String
  def userId : String
  def roleName : String
  def createdByProcess : String
}
