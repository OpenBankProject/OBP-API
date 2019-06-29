package code.database.authorisation

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object Authorisations extends SimpleInjector {
  val authorisationProvider = new Inject(buildOne _) {}
  def buildOne: AuthorisationProvider = MappedAuthorisationProvider
}

trait AuthorisationProvider {
  def getAuthorizationByAuthorizationId(paymentId: String, authorizationId: String): Box[Authorisation]
  def getAuthorizationByPaymentId(paymentId: String): Box[List[Authorisation]]
  def createAuthorization(paymentId: String,
                          authenticationType: String, 
                          authenticationMethodId: String,
                          scaStatus: String
                         ): Box[Authorisation]
}