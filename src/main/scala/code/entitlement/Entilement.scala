package code.entitlement


import code.api.util.APIUtil
import code.remotedata.RemotedataEntitlements
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future

object Entitlement extends SimpleInjector {

  val entitlement = new Inject(buildOne _) {}

  def buildOne: EntitlementProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedEntitlementsProvider
      case true => RemotedataEntitlements     // We will use Akka as a middleware
    }
}

trait EntitlementProvider {
  def getEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement]
  def getEntitlementById(entitlementId: String) : Box[Entitlement]
  def getEntitlementsByUserId(userId: String) : Box[List[Entitlement]]
  def getEntitlementsByUserIdFuture(userId: String) : Future[Box[List[Entitlement]]]
  def deleteEntitlement(entitlement: Box[Entitlement]) : Box[Boolean]
  def getEntitlements() : Box[List[Entitlement]]
  def getEntitlementsByRole(roleName: String): Box[List[Entitlement]]
  def getEntitlementsFuture() : Future[Box[List[Entitlement]]]
  def getEntitlementsByRoleFuture(roleName: String) : Future[Box[List[Entitlement]]]
  def addEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement]
}

trait Entitlement {
  def entitlementId: String
  def bankId : String
  def userId : String
  def roleName : String
}

class RemotedataEntitlementsCaseClasses {
  case class getEntitlement(bankId: String, userId: String, roleName: String)
  case class getEntitlementById(entitlementId: String)
  case class getEntitlementsByUserId(userId: String)
  case class getEntitlementsByUserIdFuture(userId: String)
  case class deleteEntitlement(entitlement: Box[Entitlement])
  case class getEntitlements()
  case class getEntitlementsByRole(roleName: String)
  case class getEntitlementsFuture()
  case class getEntitlementsByRoleFuture(roleName: String)
  case class addEntitlement(bankId: String, userId: String, roleName: String)
}

object RemotedataEntitlementsCaseClasses extends RemotedataEntitlementsCaseClasses