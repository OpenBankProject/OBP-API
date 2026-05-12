package code.payeelookup

import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedPayeeLookupProvider extends PayeeLookupProvider {

  override def createPayeeLookup(
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
  ): Box[PayeeLookupTrait] = {
    val now = System.currentTimeMillis()
    tryo {
      PayeeLookup.create
        .LookupId(lookupId)
        .IdentifierType(identifierType)
        .Identifier(identifier)
        .FspId(fspId.getOrElse(""))
        .NetworkProvider(networkProvider.getOrElse(""))
        .FullName(fullName)
        .AccountCategory(accountCategory.getOrElse(""))
        .AccountType(accountType.getOrElse(""))
        .IdentityType(identityType.getOrElse(""))
        .IdentityValue(identityValue.getOrElse(""))
        .FromBankId(fromBankId)
        .FromAccountId(fromAccountId)
        .CreatedByUserId(createdByUserId)
        .CreationDate(new java.util.Date(now))
        .ExpiresAt(new java.util.Date(now + ttlSeconds * 1000))
        .saveMe()
    }
  }

  override def getActivePayeeLookup(lookupId: String): Box[PayeeLookupTrait] = {
    PayeeLookup.find(By(PayeeLookup.LookupId, lookupId)).filter(!_.isExpired)
  }
}

class PayeeLookup extends PayeeLookupTrait with LongKeyedMapper[PayeeLookup] with IdPK {
  def getSingleton = PayeeLookup

  object LookupId extends MappedString(this, 64)
  object IdentifierType extends MappedString(this, 64)
  object Identifier extends MappedString(this, 255)
  object FspId extends MappedString(this, 32)
  object NetworkProvider extends MappedString(this, 64)
  object FullName extends MappedString(this, 255)
  object AccountCategory extends MappedString(this, 32)
  object AccountType extends MappedString(this, 32)
  object IdentityType extends MappedString(this, 32)
  object IdentityValue extends MappedString(this, 64)
  object FromBankId extends MappedString(this, 255)
  object FromAccountId extends MappedString(this, 255)
  object CreatedByUserId extends MappedString(this, 255)
  object CreationDate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
  object ExpiresAt extends MappedDateTime(this)

  private def opt(s: String): Option[String] =
    if (s == null || s.isEmpty) None else Some(s)

  override def lookupId: String = LookupId.get
  override def identifierType: String = IdentifierType.get
  override def identifier: String = Identifier.get
  override def fspId: Option[String] = opt(FspId.get)
  override def networkProvider: Option[String] = opt(NetworkProvider.get)
  override def fullName: String = FullName.get
  override def accountCategory: Option[String] = opt(AccountCategory.get)
  override def accountType: Option[String] = opt(AccountType.get)
  override def identityType: Option[String] = opt(IdentityType.get)
  override def identityValue: Option[String] = opt(IdentityValue.get)
  override def fromBankId: String = FromBankId.get
  override def fromAccountId: String = FromAccountId.get
  override def createdByUserId: String = CreatedByUserId.get
  override def createdAt: java.util.Date = CreationDate.get
  override def expiresAt: java.util.Date = ExpiresAt.get
}

object PayeeLookup extends PayeeLookup with LongKeyedMetaMapper[PayeeLookup] {
  override def dbTableName = "PayeeLookup"
  override def dbIndexes = UniqueIndex(LookupId) :: Index(ExpiresAt) :: super.dbIndexes
}
