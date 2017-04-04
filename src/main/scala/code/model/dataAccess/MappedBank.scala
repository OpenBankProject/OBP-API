package code.model.dataAccess

import code.model.{BankId, Bank}
import net.liftweb.mapper._

class MappedBank extends Bank with LongKeyedMapper[MappedBank] with IdPK with CreatedUpdated {
  def getSingleton = MappedBank

  object permalink extends MappedString(this, 255)
  object fullBankName extends MappedString(this, 255)
  object shortBankName extends MappedString(this, 100)
  object logoURL extends MappedString(this, 255)
  object websiteURL extends MappedString(this, 255)
  object swiftBIC extends MappedString(this, 255)
  object national_identifier extends MappedString(this, 255)
  object mBankRoutingScheme extends MappedString(this, 255)
  object mBankRoutingAddress extends MappedString(this, 255)
  

  override def bankId: BankId = BankId(permalink.get) // This is the bank id used in URLs
  override def fullName: String = fullBankName.get
  override def shortName: String = shortBankName.get
  override def logoUrl: String = logoURL.get
  override def websiteUrl: String = websiteURL.get
  override def swiftBic: String = swiftBIC.get
  override def nationalIdentifier: String = national_identifier.get
  override def bankRoutingScheme = mBankRoutingScheme.get
  override def bankRoutingAddress = mBankRoutingAddress.get
}

object MappedBank extends MappedBank with LongKeyedMetaMapper[MappedBank] {
  // permalink should be unique
  // TODO should have UniqueIndex on permalink but need to modify tests see createBank
  // TODO Other Models should be able to foreign key to this but would need to expose IdPK then?
  override def dbIndexes = Index(permalink) :: super.dbIndexes

  def findByBankId(bankId : BankId) =
    MappedBank.find(By(MappedBank.permalink, bankId.value))
}
