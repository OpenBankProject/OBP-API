package code.consent

import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import java.util.Date

import code.consent.ConsentStatus.ConsentStatus
import scala.collection.immutable.List

object Consents extends SimpleInjector {
  val consentProvider = new Inject(buildOne _) {}
  def buildOne: ConsentProvider = MappedConsentProvider
}

trait ConsentProvider {
  def getConsentByConsentId(consentId: String): Box[MappedConsent]
  def updateConsentStatus(consentId: String, status: ConsentStatus): Box[MappedConsent]
  def updateConsentUser(consentId: String, user: User): Box[MappedConsent]
  def getConsentsByUser(userId: String): List[MappedConsent]
  def createConsent(user: User, challenge: String): Box[MappedConsent]
  def setJsonWebToken(consentId: String, jwt: String): Box[MappedConsent]
  def revoke(consentId: String): Box[MappedConsent]
  def checkAnswer(consentId: String, challenge: String): Box[MappedConsent]
  def createBerlinGroupConsent(
    user: User,
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean,
    apiStandard: Option[String],
    apiVersion: Option[String]): Box[Consent]  
  def updateBerlinGroupConsent(
    consentId: String,
    user: User,
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean): Box[Consent]

  def saveUKConsent(
    user: Option[User],
    bankId: Option[String],//for UK Open Banking endpoints, there is no BankId there.
    accountIds: Option[List[String]],//for UK Open Banking endpoints, there is no accountIds there.
    consumerId: Option[String],
    permissions: List[String],
    expirationDateTime: Date,
    transactionFromDateTime: Date,
    transactionToDateTime: Date,
    apiStandard: Option[String],
    apiVersion: Option[String]
  ): Box[Consent]
}

trait Consent {
  def consentId: String
  def userId: String
  def secret: String
  def status: String
  // The hashed challenge using the OpenBSD bcrypt scheme
  // The salt to hash with (generated using BCrypt.gensalt)
  def challenge: String

  /**
   * this is the structure of the jwt token, try to see the case class directly, to see the all the fields.
   * case class ConsentJWT(
   *   createdByUserId: String,
   *   sub: String,
   *   iss: String,
   *   aud: String,
   *   jti: String,
   *   iat: Long,
   *   nbf: Long,
   *   exp: Long,
   *   name: Option[String],
   *   email: Option[String],
   *   entitlements: List[Role],
   *   views: List[ConsentView]
   * ) 
   */
  def jsonWebToken: String

  /**
   * This field identifies the standard of API of a related consent
   * For instance: OBP, Berlin-Group, UKOpenBanking etc.
   * @return API standard
   */
  def apiStandard: String
  /**
   * This field identifies the version of API of a related consent
   * * For instance: 4.0.0, 1.3, 2.0.0 etc.
   * @return API version
   */
  def apiVersion: String

  //The following recurringIndicator, validUntil, frequencyPerDay, combinedServiceIndicator, lastActionDate are added for BerlinGroup
  /**
   * recurringIndicator*	recurringIndicator boolean
   * example: false
   *   "true", if the consent is for recurring access to the account data.
   *   "false", if the consent is for one access to the account data.
   */
  def recurringIndicator: Boolean
  /**
   *validUntil* validUntil string($date)
   *example: 2020-12-31
   *This parameter is requesting a valid until date for the requested consent. The content is the local ASPSP date in ISO-Date Format, e.g. 2017-10-30.
   *Future dates might get adjusted by ASPSP.If a maximal available date is requested, a date in far future is to be used: "9999-12-31".
   *In both cases the consent object to be retrieved by the GET Consent Request will contain the adjusted date.
   */
  def validUntil: Date
  /**
   * frequencyPerDay*	frequencyPerDay integer
   * example: 4
   * minimum: 1
   * exclusiveMinimum: false
   * This field indicates the requested maximum frequency for an access without PSU involvement per day. For a one-off access, this attribute is set to "1".
   * The frequency needs to be greater equal to one.
   * If not otherwise agreed bilaterally between TPP and ASPSP, the frequency is less equal to 4.
   */
  def frequencyPerDay : Int
  /**
   * combinedServiceIndicator* 	boolean                                                               
   * example: false                                                                                    
   * If "true" indicates that a payment initiation service will be addressed in the same "session".    
   */
  def combinedServiceIndicator: Boolean
  /**
   * lastActionDatestring($date)                                                                                                                                             
   * example: 2018-07-01                                                                                                                                                     
   * This date is containing the date of the last action on the consent object either through the XS2A interface or the PSU/ASPSP interface having an impact on the status.  
   *
   * @return
   */
  def lastActionDate: Date

  /**
   * CreationDateTime*	CreationDateTimestring($date-time)
   * Date and time in which the consent was created.
   */
  def creationDateTime: Date
  /**
   * StatusUpdateDateTime*	StatusUpdateDateTimestring($date-time)                                      
   * Date and time when the status of the consent changed due to an action performed by the Client.    
   */
  def statusUpdateDateTime: Date
  /**
   * ExpirationDateTime	string($date-time)
   * Date and time in which the permissions granted by the Client expire. The date must be selected by the Client.
   */
  def expirationDateTime: Date
  /**
   * TransactionFromDateTime	string($date-time)
   * Specified start date and time for the transaction query period. If the field does not contain information or if it is not sent in the request, the start date will be 90 calendar days prior to the creation of the consent.
   */
  def transactionFromDateTime: Date
  /**
   * TransactionToDateTime	string($date-time)
   * Specified end date and time for the transaction query period. If the field does not contain information or if it is not sent in the request, the end date will be 90 calendar days prior to the creation of the consent.
   */
  def transactionToDateTime: Date
}

object ConsentStatus extends Enumeration {
  type ConsentStatus = Value
  val INITIATED, ACCEPTED, REJECTED, REVOKED,
      //The following are for BelinGroup
      RECEIVED, VALID, REVOKEDBYPSU, EXPIRED, TERMINATEDBYTPP ,
     //these added for UK Open Banking 
     AUTHORISED, AWAITINGAUTHORISATION = Value
}








