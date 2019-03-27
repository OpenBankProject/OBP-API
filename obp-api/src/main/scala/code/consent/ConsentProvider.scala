package code.consent

import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object Consents extends SimpleInjector {
  val consentProvider = new Inject(buildOne _) {}
  def buildOne: ConsentProvider = MappedConsentProvider
}

trait ConsentProvider {
  def getConsentByConsentId(consentId: String): Box[MappedConsent]
  def getConsentsByUser(userId: String): List[MappedConsent]
  def createConsent(user: User): Box[MappedConsent]
  def setJsonWebToken(consentId: String, jwt: String): Box[MappedConsent]
  def checkAnswer(consentId: String, challenge: String): Box[MappedConsent]
}

trait Consent {
  def consentId: String
  def userId: String
  def secret: String
  def status: String
  def challenge: String
  def jsonWebToken: String
}

object ConsentStatus extends Enumeration {
  type ConsentStatus = Value
  val INITIATED, ACCEPTED, REJECTED, REVOKED = Value
}








