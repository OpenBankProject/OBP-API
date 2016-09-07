package code.entitlement


import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object Entitlement extends SimpleInjector {

  val entitlement = new Inject(buildOne _) {}

  def buildOne: Entitlement = MappedEntitlement
}

trait Entitlement {
  def entitlementId: String
  def bankId : String
  def userId : String
  def roleName : String

  def getEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement]
  def getEntitlement(entitlementId: String) : Box[Entitlement]
  def getEntitlements(userId: String) : Box[List[Entitlement]]
  def deleteEntitlement(entitlement: Box[Entitlement]) : Box[Boolean]
  def getEntitlements() : Box[List[Entitlement]]
  def addEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement]
}