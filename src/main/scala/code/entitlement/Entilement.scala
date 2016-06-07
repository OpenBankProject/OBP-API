package code.entitlement


import code.model.{BankId, User}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object Entitlements extends SimpleInjector {
  val entitlementProvider = new Inject(buildOne _) {}
  def buildOne: EntitlementProvider = MappedEntitlementsProvider
}

trait EntitlementProvider {
  def getEntitlement(bankId: String, userId: String, roleName: String) : Box[Entitlement]
  def getEntitlements(userId: String) : Box[List[Entitlement]]
  def addEntitlement(bankId: String, userId: String, roleName: String) : Box[MappedEntitlement]
}

trait Entitlement {
  def entitlementId: String
  def bankId : String
  def userId : String
  def roleName : String
}