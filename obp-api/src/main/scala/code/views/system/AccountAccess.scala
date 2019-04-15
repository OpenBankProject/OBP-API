package code.views.system

import code.model.dataAccess.{MappedBank, MappedBankAccount, ResourceUser, ViewImpl}
import net.liftweb.mapper._

class AccountAccess extends LongKeyedMapper[AccountAccess] with IdPK with CreatedUpdated {
  def getSingleton = AccountAccess
  object user extends MappedLongForeignKey(this, ResourceUser)
  object view extends MappedLongForeignKey(this, ViewImpl)
  object bank extends MappedLongForeignKey(this, MappedBank)
  object account extends MappedLongForeignKey(this, MappedBankAccount)
}
object AccountAccess extends AccountAccess with LongKeyedMetaMapper[AccountAccess] {
  override def dbIndexes: List[BaseIndex[AccountAccess]] = UniqueIndex(bank, account, view, user) :: super.dbIndexes
}
