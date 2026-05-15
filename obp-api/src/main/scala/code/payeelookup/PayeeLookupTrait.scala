package code.payeelookup

import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object PayeeLookups extends SimpleInjector {
  val payeeLookup = new Inject(buildOne _) {}

  def buildOne: PayeeLookupProvider = MappedPayeeLookupProvider
}

trait PayeeLookupProvider {
  def createPayeeLookup(
    lookupId: String,
    identifierType: String,
    identifier: String,
    fspId: Option[String],
    networkProvider: Option[String],
    fullName: String,
    accountCategory: Option[String],
    accountType: Option[String],
    identityType: Option[String],
    identityValue: Option[String],
    fromBankId: String,
    fromAccountId: String,
    createdByUserId: String,
    ttlSeconds: Long
  ): Box[PayeeLookupTrait]

  /** Returns the lookup if found and not expired. Expired rows are NOT auto-deleted. */
  def getActivePayeeLookup(lookupId: String): Box[PayeeLookupTrait]
}

trait PayeeLookupTrait {
  def lookupId: String
  def identifierType: String
  def identifier: String
  def fspId: Option[String]
  def networkProvider: Option[String]
  def fullName: String
  def accountCategory: Option[String]
  def accountType: Option[String]
  def identityType: Option[String]
  def identityValue: Option[String]
  def fromBankId: String
  def fromAccountId: String
  def createdByUserId: String
  def createdAt: java.util.Date
  def expiresAt: java.util.Date
  def isExpired: Boolean = expiresAt.getTime <= System.currentTimeMillis()
}
