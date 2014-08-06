package code.model.dataAccess

import net.liftweb.mapper._

class MappedAccountHolder extends LongKeyedMapper[MappedAccountHolder] with IdPK {

  def getSingleton = MappedAccountHolder

  object user extends MappedLongForeignKey(this, APIUser)

  object accountBankPermalink extends MappedString(this, 255)
  object accountPermalink extends MappedString(this, 255)

}

object MappedAccountHolder extends MappedAccountHolder with LongKeyedMetaMapper[MappedAccountHolder] {
  override def dbIndexes = Index(accountBankPermalink, accountPermalink) :: Nil
}
