package code.consent

import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import java.util.Date

object Consents extends SimpleInjector {
  val consentProvider = new Inject(buildOne _) {}
  def buildOne: ConsentProvider = MappedConsentProvider
}

trait ConsentProvider {
  def getConsentByConsentId(consentId: String): Box[MappedConsent]
  def getConsentsByUser(userId: String): List[MappedConsent]
  def createConsent(user: User): Box[MappedConsent]
  def setJsonWebToken(consentId: String, jwt: String): Box[MappedConsent]
  def revoke(consentId: String): Box[MappedConsent]
  def checkAnswer(consentId: String, challenge: String): Box[MappedConsent]
  def createBerlinGroupConsent(
    user: User,
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean): Box[Consent]  
  def updateBerlinGroupConsent(
    consentId: String,
    user: User,
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean): Box[Consent]
}

trait Consent {
  def consentId: String
  def userId: String
  def secret: String
  def status: String
  def challenge: String
  def jsonWebToken: String

  //The following are added for BerlinGroup
  def recurringIndicator: Boolean
  def validUntil: Date
  def frequencyPerDay : Int
  def combinedServiceIndicator: Boolean
  def lastActionDate: Date
}

object ConsentStatus extends Enumeration {
  type ConsentStatus = Value
  val INITIATED, ACCEPTED, REJECTED, REVOKED,
      //The following are for BelinGroup
      RECEIVED, VALID, REVOKEDBYPSU, EXPIRED, TERMINATEDBYTPP = Value
}








