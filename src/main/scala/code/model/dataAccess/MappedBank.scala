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

  override def bankId: BankId = BankId(permalink.get)
  override def fullName: String = fullBankName.get
  override def shortName: String = shortBankName.get
  override def logoUrl: String = logoURL.get
  override def websiteUrl: String = websiteURL.get
}

object MappedBank extends MappedBank with LongKeyedMetaMapper[MappedBank] {
  override def dbIndexes = Index(permalink) :: super.dbIndexes

  def findByBankId(bankId : BankId) =
    MappedBank.find(By(MappedBank.permalink, bankId.value))
}