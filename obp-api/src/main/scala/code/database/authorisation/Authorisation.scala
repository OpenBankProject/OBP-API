package code.database.authorisation

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object Authorisations extends SimpleInjector {
  val authorisationProvider = new Inject(buildOne _) {}
  def buildOne: AuthorisationProvider = MappedAuthorisationProvider
}

trait AuthorisationProvider {
  def getAuthorizationByAuthorizationId(authorizationId: String): Box[Authorisation]
  def createAuthorization(authenticationType: String, 
                          authenticationMethodId: String,
                          scaStatus: String
                         ): Box[Authorisation]
}