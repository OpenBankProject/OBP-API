package code.model.dataAccess

import net.liftweb.mapper._

class MappedKafkaBankAccountData extends LongKeyedMapper[MappedKafkaBankAccountData] with IdPK with CreatedUpdated {

  override def getSingleton = MappedKafkaBankAccountData

  object bankId extends MappedString(this, 255)
  def getBankId = bankId.get
  def setBankId(value: String) = bankId.set(value)

  object accountId extends MappedString(this, 255)
  def getAccountId = accountId.get
  def setAccountId(value: String) = accountId.set(value)

  object accountLabel extends MappedString(this, 255)
  def getLabel = accountLabel.get
  def setLabel(value: String) = accountLabel.set(value)

}

object MappedKafkaBankAccountData extends MappedKafkaBankAccountData with LongKeyedMetaMapper[MappedKafkaBankAccountData] {
  override def dbIndexes = UniqueIndex(bankId, accountId) :: super.dbIndexes
}
