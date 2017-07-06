package code.entitlement


import code.remotedata.RemotedataEntitlements
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}


object Entitlement extends SimpleInjector {

  val entitlement = new Inject(buildOne _) {}

  def buildOne: EntitlementProvider =
    Props.getBool("use_akka", false) match {
      case false  => MappedEntitlementsProvider
      case true => RemotedataEntitlements     // We will use Akka as a middleware
    }
}

trait EntitlementProvider {
  def getEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement]
  def getEntitlementById(entitlementId: String) : Box[Entitlement]
  def getEntitlementsByUserId(userId: String) : Box[List[Entitlement]]
  def deleteEntitlement(entitlement: Box[Entitlement]) : Box[Boolean]
  def getEntitlements() : Box[List[Entitlement]]
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
  case class deleteEntitlement(entitlement: Box[Entitlement])
  case class getEntitlements()
  case class addEntitlement(bankId: String, userId: String, roleName: String)
}

object RemotedataEntitlementsCaseClasses extends RemotedataEntitlementsCaseClasses